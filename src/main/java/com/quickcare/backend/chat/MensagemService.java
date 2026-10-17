package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import com.quickcare.backend.chat.dto.MensagemExcluidaEvento;
import com.quickcare.backend.chat.exception.DestinatarioInvalidoException;
import com.quickcare.backend.chat.exception.ExclusaoMensagemNaoAutorizadaException;
import com.quickcare.backend.chat.exception.MensagemNaoEncontradaException;
import com.quickcare.backend.chat.exception.MensagemVaziaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Persiste as mensagens e as repassa em tempo real apenas às sessões WebSocket
 * do destinatário ({@code /user/queue/mensagens}).
 */
@Service
@RequiredArgsConstructor
public class MensagemService {

    static final String FILA_MENSAGENS = "/queue/mensagens";

    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    @Transactional
    public MensagemChatResponse enviar(Long remetenteId, EnviarMensagemRequest request, MultipartFile arquivo) {
        if (request.destinatarioId().equals(remetenteId)) {
            throw new DestinatarioInvalidoException("Não é possível enviar mensagem para si mesmo.");
        }
        if (!usuarioRepository.existsById(request.destinatarioId())) {
            throw new DestinatarioInvalidoException("Destinatário não encontrado.");
        }

        boolean possuiArquivo = arquivo != null && !arquivo.isEmpty();
        String conteudo = request.conteudo() == null ? "" : request.conteudo().trim();
        if (conteudo.isEmpty() && !possuiArquivo) {
            throw new MensagemVaziaException();
        }

        Mensagem mensagem = new Mensagem(remetenteId, request.destinatarioId(), conteudo);
        if (possuiArquivo) {
            String storageKey = armazenamentoDocumentoService.enviarAnexoChat(arquivo);
            mensagem.anexar(storageKey, arquivo.getOriginalFilename(), arquivo.getContentType());
        }

        MensagemChatResponse response = paraResponse(mensagemRepository.save(mensagem));

        messagingTemplate.convertAndSendToUser(String.valueOf(request.destinatarioId()), FILA_MENSAGENS, response);

        return response;
    }

    @Transactional(readOnly = true)
    public List<MensagemChatResponse> listar(Long usuarioId) {
        return mensagemRepository.findMensagensAtivasPorUsuario(usuarioId).stream()
                .map(this::paraResponse)
                .toList();
    }

    @Transactional
    public void excluir(Long usuarioId, Long mensagemId) {
        Mensagem mensagem = mensagemRepository.findById(mensagemId)
                .filter(Mensagem::isAtivo)
                .orElseThrow(MensagemNaoEncontradaException::new);

        if (!mensagem.getRemetenteId().equals(usuarioId)) {
            throw new ExclusaoMensagemNaoAutorizadaException();
        }

        mensagem.excluir();

        messagingTemplate.convertAndSendToUser(
                String.valueOf(mensagem.getDestinatarioId()),
                FILA_MENSAGENS,
                MensagemExcluidaEvento.de(mensagem.getId()));
    }

    /** A URL do anexo é assinada e expira, por isso é gerada a cada leitura. */
    private MensagemChatResponse paraResponse(Mensagem mensagem) {
        String anexoUrl = mensagem.possuiAnexo()
                ? armazenamentoDocumentoService.gerarUrlAssinada(mensagem.getAnexoStorageKey())
                : null;
        return MensagemChatResponse.de(mensagem, anexoUrl);
    }

}
