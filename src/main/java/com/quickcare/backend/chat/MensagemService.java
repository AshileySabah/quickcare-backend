package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import com.quickcare.backend.chat.dto.MensagemExcluidaEvento;
import com.quickcare.backend.chat.exception.ExclusaoMensagemNaoAutorizadaException;
import com.quickcare.backend.chat.exception.MensagemNaoEncontradaException;
import com.quickcare.backend.chat.exception.MensagemVaziaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Persiste as mensagens e as repassa em tempo real a cada participante da conversa, exceto o
 * remetente, pela fila privada de cada um ({@code /user/queue/mensagens}). Não há canais
 * públicos: o cliente nunca publica nem assina {@code /topic}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MensagemService {

    static final String FILA_MENSAGENS = "/queue/mensagens";

    private final MensagemRepository mensagemRepository;
    private final ConversaService conversaService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    @Transactional
    public MensagemChatResponse enviar(Long remetenteId, EnviarMensagemRequest request, MultipartFile arquivo) {
        Long solicitacaoId = request.solicitacaoId();
        conversaService.exigirParticipante(solicitacaoId, remetenteId);

        boolean possuiArquivo = arquivo != null && !arquivo.isEmpty();
        String conteudo = request.conteudo() == null ? "" : request.conteudo().trim();
        if (conteudo.isEmpty() && !possuiArquivo) {
            throw new MensagemVaziaException();
        }

        Mensagem mensagem = new Mensagem(remetenteId, solicitacaoId, conteudo);
        if (possuiArquivo) {
            String storageKey = armazenamentoDocumentoService.enviarAnexoChat(arquivo);
            mensagem.anexar(storageKey, arquivo.getOriginalFilename(), arquivo.getContentType());
        }

        MensagemChatResponse response = paraResponse(mensagemRepository.save(mensagem));

        notificarParticipantes(solicitacaoId, remetenteId, response);

        return response;
    }

    @Transactional(readOnly = true)
    public List<MensagemChatResponse> listarPorConversa(Long usuarioId, Long solicitacaoId) {
        conversaService.exigirParticipante(solicitacaoId, usuarioId);

        return mensagemRepository.findBySolicitacaoIdAndAtivoTrueOrderByDataEnvioAsc(solicitacaoId).stream()
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

        notificarParticipantes(mensagem.getSolicitacaoId(), usuarioId, MensagemExcluidaEvento.de(mensagem.getId()));
    }

    /**
     * Entrega o payload na fila privada de cada participante, exceto {@code autorId} (que já
     * recebe a resposta HTTP). A falha de um destinatário não pode desfazer a mensagem já
     * persistida nem impedir a entrega aos demais.
     */
    private void notificarParticipantes(Long solicitacaoId, Long autorId, Object payload) {
        for (Long participanteId : conversaService.participantes(solicitacaoId)) {
            if (participanteId.equals(autorId)) {
                continue;
            }
            try {
                messagingTemplate.convertAndSendToUser(String.valueOf(participanteId), FILA_MENSAGENS, payload);
            } catch (RuntimeException ex) {
                log.warn("Não foi possível entregar em tempo real ao usuário {} (solicitação {}).",
                        participanteId, solicitacaoId, ex);
            }
        }
    }

    /** A URL do anexo é assinada e expira, por isso é gerada a cada leitura. */
    private MensagemChatResponse paraResponse(Mensagem mensagem) {
        String anexoUrl = mensagem.possuiAnexo()
                ? armazenamentoDocumentoService.urlDoAnexoChat(mensagem.getAnexoStorageKey())
                : null;
        return MensagemChatResponse.de(mensagem, anexoUrl);
    }

}
