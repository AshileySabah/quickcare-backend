package com.quickcare.backend.documento;

import com.quickcare.backend.storage.ArmazenamentoArquivos;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> TIPOS_IMAGEM_ACEITOS = Set.of("image/jpeg", "image/png");
    private static final Set<String> TIPOS_ANEXO_CHAT_ACEITOS = Set.of(
            "image/jpeg", "image/png", "application/pdf", "video/mp4",
            "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/ogg", "audio/webm", "audio/wav", "audio/x-wav");
    private static final long TAMANHO_MAXIMO_MB = 5;
    private static final long TAMANHO_MAXIMO_ANEXO_CHAT_MB = 15;
    private static final Duration EXPIRACAO_URL_ASSINADA = Duration.ofHours(1);
    private static final Duration EXPIRACAO_URL_ASSINADA_CHAT = Duration.ofHours(24);

    private final ArmazenamentoArquivos armazenamento;

    public String enviar(TipoDocumento tipo, MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta(tipo.name().toLowerCase(), arquivo);
    }

    public String enviarAvatar(MultipartFile arquivo) {
        validar(arquivo, TIPOS_IMAGEM_ACEITOS, "Envie uma imagem JPG ou PNG.");
        return enviarParaPasta("avatares", arquivo);
    }

    public String enviarAnexoValidacao(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-validacao", arquivo);
    }

    public String enviarAnexoProblema(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-problema", arquivo);
    }

    /** Anexos do chat (imagem, PDF, vídeo ou áudio) ficam em pasta própria, com limite maior. */
    public String enviarAnexoChat(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ANEXO_CHAT_ACEITOS,
                "Envie uma imagem JPG/PNG, um PDF, um vídeo MP4 ou um áudio (MP3, M4A, OGG, WAV).",
                TAMANHO_MAXIMO_ANEXO_CHAT_MB);
        return enviarParaPasta("chat-anexos", arquivo);
    }

    public String enviarAnexoDenuncia(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-denuncia", arquivo);
    }

    public void validarAnexo(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
    }

    private String enviarParaPasta(String pasta, MultipartFile arquivo) {
        String chave = pasta + "/" + UUID.randomUUID() + extrairExtensao(arquivo.getOriginalFilename());

        try {
            armazenamento.enviar(chave, arquivo.getBytes(), arquivo.getContentType());
        } catch (IOException ex) {
            throw new ArquivoInvalidoException("Não foi possível ler o arquivo \"%s\".".formatted(arquivo.getOriginalFilename()));
        } catch (Exception ex) {
            log.warn("Falha ao enviar \"{}\" para o storage: {}", arquivo.getOriginalFilename(), ex.getMessage());
            throw new ArquivoInvalidoException("Não foi possível enviar o arquivo \"%s\". Tente novamente.".formatted(arquivo.getOriginalFilename()));
        }

        return chave;
    }

    public void excluir(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            armazenamento.excluir(storageKey);
        } catch (Exception ex) {
            log.warn("Falha ao excluir \"{}\" do storage: {}", storageKey, ex.getMessage());
        }
    }

    public byte[] baixar(String storageKey) {
        try {
            byte[] conteudo = armazenamento.baixar(storageKey);
            if (conteudo == null) {
                throw new ArquivoInvalidoException("Arquivo indisponível.");
            }
            return conteudo;
        } catch (ArquivoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao baixar \"{}\" do storage: {}", storageKey, ex.getMessage());
            throw new ArquivoInvalidoException("Não foi possível abrir o arquivo. Tente novamente.");
        }
    }

    public String urlDoAvatar(Usuario usuario) {
        return usuario.getAvatarStorageKey() != null ? gerarUrlAssinada(usuario.getAvatarStorageKey()) : null;
    }

    public String gerarUrlAssinada(String storageKey) {
        return gerarUrlAssinada(storageKey, EXPIRACAO_URL_ASSINADA);
    }

    /** Os anexos do chat valem por 24h para a conversa não quebrar enquanto a tela fica aberta. */
    public String urlDoAnexoChat(String storageKey) {
        return gerarUrlAssinada(storageKey, EXPIRACAO_URL_ASSINADA_CHAT);
    }

    private String gerarUrlAssinada(String storageKey, Duration expiracao) {
        try {
            return armazenamento.urlAssinada(storageKey, expiracao);
        } catch (Exception ex) {
            return null;
        }
    }

    private void validar(MultipartFile arquivo, Set<String> tiposAceitos, String mensagemFormatoInvalido) {
        validar(arquivo, tiposAceitos, mensagemFormatoInvalido, TAMANHO_MAXIMO_MB);
    }

    private void validar(MultipartFile arquivo, Set<String> tiposAceitos, String mensagemFormatoInvalido, long tamanhoMaximoMb) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo não informado.");
        }

        if (!tiposAceitos.contains(arquivo.getContentType())) {
            throw new ArquivoInvalidoException("Formato inválido para \"%s\". %s".formatted(arquivo.getOriginalFilename(), mensagemFormatoInvalido));
        }

        if (arquivo.getSize() > tamanhoMaximoMb * 1024 * 1024) {
            throw new ArquivoInvalidoException("O arquivo \"%s\" deve ter no máximo %dMB.".formatted(arquivo.getOriginalFilename(), tamanhoMaximoMb));
        }
    }

    private String extrairExtensao(String nomeOriginal) {
        if (nomeOriginal == null) {
            return "";
        }

        int ponto = nomeOriginal.lastIndexOf('.');
        return ponto >= 0 ? nomeOriginal.substring(ponto) : "";
    }

}
