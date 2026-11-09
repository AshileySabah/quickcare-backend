package com.quickcare.backend.seguranca;

import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class CodigoSegurancaService {

    public static final Duration VALIDADE_CODIGO = Duration.ofMinutes(10);
    public static final Duration VALIDADE_DESFAZER_EMAIL = Duration.ofDays(7);
    static final Duration INTERVALO_MINIMO = Duration.ofMinutes(1);
    static final int TENTATIVAS_MAXIMAS = 5;
    private static final int BYTES_TOKEN = 32;

    private final CodigoSegurancaRepository repository;
    private final EmailService emailService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void enviarCodigo(Usuario usuario, FinalidadeCodigo finalidade, String dado, String destinatario, String acao) {
        Instant agora = Instant.now(clock);
        Optional<CodigoSeguranca> anterior = repository.findByUsuarioIdAndFinalidade(usuario.getId(), finalidade);
        if (anterior.isPresent() && anterior.get().getCriadoEm().plus(INTERVALO_MINIMO).isAfter(agora)) {
            throw new CodigoInvalidoException("Já enviamos um código há menos de um minuto. Confira seu e-mail ou espere para pedir outro.");
        }
        anterior.ifPresent(codigo -> repository.excluir(usuario.getId(), finalidade));

        String codigo = String.format("%06d", secureRandom.nextInt(1_000_000));
        repository.save(CodigoSeguranca.builder()
                .usuario(usuario)
                .finalidade(finalidade)
                .codigoHash(hash(codigo))
                .dado(dado)
                .expiraEm(agora.plus(VALIDADE_CODIGO))
                .criadoEm(agora)
                .build());

        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("nome", usuario.getNome());
        variaveis.put("titulo", "Código de segurança");
        variaveis.put("mensagem", "Use o código abaixo para " + acao + ". Ele vale por "
                + VALIDADE_CODIGO.toMinutes() + " minutos. Se não foi você, ignore este e-mail e troque sua senha.");
        variaveis.put("codigo", codigo);
        emailService.enviarEmailTemplate(destinatario, "Seu código de segurança - QuickCare", "seguranca", variaveis);
    }

    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public String confirmar(Usuario usuario, FinalidadeCodigo finalidade, String codigo) {
        Instant agora = Instant.now(clock);
        CodigoSeguranca salvo = repository.findByUsuarioIdAndFinalidade(usuario.getId(), finalidade)
                .orElseThrow(() -> new CodigoInvalidoException("Peça um código antes de confirmar."));

        if (salvo.expirado(agora)) {
            repository.delete(salvo);
            throw new CodigoInvalidoException("O código expirou. Peça um novo.");
        }
        if (codigo == null || !salvo.getCodigoHash().equals(hash(codigo.trim()))) {
            if (salvo.registrarTentativa() >= TENTATIVAS_MAXIMAS) {
                repository.delete(salvo);
                throw new CodigoInvalidoException("Código incorreto muitas vezes. Peça um novo código.");
            }
            throw new CodigoInvalidoException("Código incorreto. Confira o e-mail e tente de novo.");
        }

        repository.delete(salvo);
        return salvo.getDado();
    }

    @Transactional
    public String gerarTokenDeRecuperacao(Usuario usuario, Instant expiraEm) {
        repository.excluir(usuario.getId(), FinalidadeCodigo.RECUPERAR_CONTA);
        String token = novoToken();
        repository.save(CodigoSeguranca.builder()
                .usuario(usuario)
                .finalidade(FinalidadeCodigo.RECUPERAR_CONTA)
                .codigoHash(hash(token))
                .expiraEm(expiraEm)
                .criadoEm(Instant.now(clock))
                .build());
        return token;
    }

    @Transactional
    public Usuario usarTokenDeRecuperacao(String token) {
        Instant agora = Instant.now(clock);
        CodigoSeguranca salvo = repository.findByCodigoHashAndFinalidade(hash(token == null ? "" : token.trim()),
                        FinalidadeCodigo.RECUPERAR_CONTA)
                .filter(encontrado -> !encontrado.expirado(agora))
                .orElseThrow(() -> new CodigoInvalidoException("Este link de recuperação não vale mais."));
        repository.delete(salvo);
        return salvo.getUsuario();
    }

    @Transactional
    public Optional<String> gerarTokenDeDesfazerEmail(Usuario usuario, String emailAnterior, String senhaHashAnterior) {
        Instant agora = Instant.now(clock);
        boolean jaExiste = repository.findByUsuarioIdAndFinalidade(usuario.getId(), FinalidadeCodigo.DESFAZER_EMAIL)
                .filter(vigente -> !vigente.expirado(agora))
                .isPresent();
        if (jaExiste) {
            return Optional.empty();
        }
        repository.excluir(usuario.getId(), FinalidadeCodigo.DESFAZER_EMAIL);
        String token = novoToken();
        repository.save(CodigoSeguranca.builder()
                .usuario(usuario)
                .finalidade(FinalidadeCodigo.DESFAZER_EMAIL)
                .codigoHash(hash(token))
                .dado(emailAnterior)
                .senhaHashAnterior(senhaHashAnterior)
                .expiraEm(agora.plus(VALIDADE_DESFAZER_EMAIL))
                .criadoEm(agora)
                .build());
        return Optional.of(token);
    }

    @Transactional(readOnly = true)
    public void validarTokenDeDesfazerEmail(String token) {
        buscarDesfazerEmail(token);
    }

    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public CodigoSeguranca usarTokenDeDesfazerEmail(String token, Predicate<String> senhaConfere) {
        CodigoSeguranca salvo = buscarDesfazerEmail(token);
        if (!senhaConfere.test(salvo.getSenhaHashAnterior())) {
            if (salvo.registrarTentativa() >= TENTATIVAS_MAXIMAS) {
                repository.delete(salvo);
                throw new CodigoInvalidoException("Senha incorreta muitas vezes. Este link deixou de valer.");
            }
            throw new CodigoInvalidoException("Senha incorreta. Use a senha que a conta tinha antes da troca de e-mail.");
        }
        repository.delete(salvo);
        return salvo;
    }

    private CodigoSeguranca buscarDesfazerEmail(String token) {
        Instant agora = Instant.now(clock);
        return repository.findByCodigoHashAndFinalidade(hash(token == null ? "" : token.trim()), FinalidadeCodigo.DESFAZER_EMAIL)
                .filter(encontrado -> !encontrado.expirado(agora))
                .orElseThrow(() -> new CodigoInvalidoException("Este link para desfazer a troca de e-mail não vale mais."));
    }

    private String novoToken() {
        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void descartarTodos(Usuario usuario) {
        repository.excluirDoUsuario(usuario.getId());
    }

    static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }

}
