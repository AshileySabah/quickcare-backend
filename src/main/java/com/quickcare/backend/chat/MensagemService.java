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
 * Persiste as mensagens e as repassa em tempo real aos participantes do canal que ainda estão
 * na conversa, exceto o remetente, pela fila privada de cada um ({@code /user/queue/mensagens}).
 * Não há canais públicos: o cliente nunca publica nem assina {@code /topic}. Quem saiu da
 * conversa lê o histórico até a saída, mas não envia nem recebe mensagens novas.
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
        Long profissionalId = request.profissionalUsuarioId();
        conversaService.exigirPodeEscrever(solicitacaoId, profissionalId, remetenteId);

        boolean possuiArquivo = arquivo != null && !arquivo.isEmpty();
        String conteudo = request.conteudo() == null ? "" : request.conteudo().trim();
        if (conteudo.isEmpty() && !possuiArquivo) {
            throw new MensagemVaziaException();
        }

        Mensagem mensagem = new Mensagem(remetenteId, solicitacaoId, profissionalId, conteudo);
        if (possuiArquivo) {
            String storageKey = armazenamentoDocumentoService.enviarAnexoChat(arquivo);
            mensagem.anexar(storageKey, arquivo.getOriginalFilename(), arquivo.getContentType());
        }

        MensagemChatResponse response = paraResponse(mensagemRepository.save(mensagem));

        notificarParticipantes(solicitacaoId, profissionalId, remetenteId, response);

        return response;
    }

    /**
     * Histórico do canal ({@code profissionalId} nulo é o grupo). Não é somente leitura porque o
     * acesso sincroniza a participação com as propostas antes de responder.
     */
    @Transactional
    public List<MensagemChatResponse> listarPorConversa(Long usuarioId, Long solicitacaoId, Long profissionalId) {
        AcessoAoCanal acesso = conversaService.acessoDe(solicitacaoId, profissionalId, usuarioId);

        return mensagemRepository.listarDoCanal(solicitacaoId, profissionalId).stream()
                .filter(m -> acesso.enxerga(m.getDataEnvio()))
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
        conversaService.exigirPodeEscrever(
                mensagem.getSolicitacaoId(), mensagem.getProfissionalUsuarioId(), usuarioId);

        mensagem.excluir();

        notificarParticipantes(mensagem.getSolicitacaoId(), mensagem.getProfissionalUsuarioId(), usuarioId,
                MensagemExcluidaEvento.de(mensagem.getId()));
    }

    /**
     * Entrega o payload na fila privada de cada participante ativo do canal, exceto
     * {@code autorId} (que já recebe a resposta HTTP). A falha de um destinatário não pode
     * desfazer a mensagem já persistida nem impedir a entrega aos demais.
     */
    private void notificarParticipantes(Long solicitacaoId, Long profissionalId, Long autorId, Object payload) {
        for (Long participanteId : conversaService.destinatarios(solicitacaoId, profissionalId)) {
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
