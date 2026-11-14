package com.quickcare.backend.cadastro;

import com.quickcare.backend.auth.AuthService;
import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.HerancaPorDocumentoService;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConfirmacaoEmailService {

    public static final Duration PRAZO = Duration.ofHours(12);
    static final Duration INTERVALO_MINIMO = Duration.ofMinutes(1);
    private static final int BYTES_TOKEN = 32;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final DocumentoRepository documentoRepository;
    private final TokenConfirmacaoEmailRepository tokenRepository;
    private final ArmazenamentoDocumentoService armazenamento;
    private final HerancaPorDocumentoService herancaPorDocumentoService;
    private final AuthService authService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transacao;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${quickcare.frontend-url}")
    private String frontendUrl;

    public ConfirmacaoEmailService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfissionalRepository profissionalRepository,
            DocumentoRepository documentoRepository,
            TokenConfirmacaoEmailRepository tokenRepository,
            ArmazenamentoDocumentoService armazenamento,
            HerancaPorDocumentoService herancaPorDocumentoService,
            AuthService authService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarioRepository = usuarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.documentoRepository = documentoRepository;
        this.tokenRepository = tokenRepository;
        this.armazenamento = armazenamento;
        this.herancaPorDocumentoService = herancaPorDocumentoService;
        this.authService = authService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static Instant prazoDe(Usuario usuario) {
        return usuario.getCriadoEm().plus(PRAZO);
    }

    @Transactional
    public void enviarLink(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isConfirmacaoEmailPendente)
                .orElse(null);
        if (usuario == null) {
            return;
        }
        Instant agora = Instant.now(clock);
        Instant prazo = prazoDe(usuario);
        tokenRepository.excluirDoUsuario(usuario.getId());

        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(TokenConfirmacaoEmail.builder()
                .usuario(usuario)
                .tokenHash(hash(token))
                .expiraEm(prazo)
                .criadoEm(agora)
                .build());

        String link = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/confirmar-email")
                .queryParam("token", token)
                .toUriString();
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("nome", usuario.getNome());
        variaveis.put("titulo", "Confirme seu e-mail");
        variaveis.put("mensagem", "Falta pouco! Confirme seu e-mail para entrar no QuickCare. O link vale até "
                + QUANDO.format(prazo.atZone(FUSO)) + ". Se você não confirmar até lá, o cadastro é apagado e"
                + " será preciso fazer de novo.");
        variaveis.put("link", link);
        variaveis.put("linkTexto", "Confirmar e entrar");
        emailService.enviarEmailTemplate(usuario.getEmail(), "Confirme seu e-mail - QuickCare", "seguranca", variaveis);
    }

    @Transactional
    public void reenviar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email.trim()).orElseThrow(LoginInvalidoException::new);
        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new LoginInvalidoException();
        }
        if (!usuario.isConfirmacaoEmailPendente()) {
            throw new TransicaoStatusInvalidaException("Este e-mail já foi confirmado. Entre com seu e-mail e senha.");
        }
        Instant agora = Instant.now(clock);
        if (!prazoDe(usuario).isAfter(agora)) {
            throw new TransicaoStatusInvalidaException(
                    "O prazo para confirmar o e-mail acabou e o cadastro será apagado. Faça o cadastro de novo.");
        }
        boolean enviadoAgora = tokenRepository.findByUsuarioId(usuario.getId())
                .filter(anterior -> anterior.getCriadoEm().plus(INTERVALO_MINIMO).isAfter(agora))
                .isPresent();
        if (enviadoAgora) {
            throw new TransicaoStatusInvalidaException(
                    "Já enviamos um link há menos de um minuto. Confira seu e-mail ou espere para pedir outro.");
        }
        enviarLink(usuario.getId());
    }

    @Transactional
    public LoginResult confirmar(String token) {
        Instant agora = Instant.now(clock);
        TokenConfirmacaoEmail confirmacao = tokenRepository.findByTokenHash(hash(token == null ? "" : token.trim()))
                .filter(encontrado -> !encontrado.expirado(agora))
                .orElseThrow(() -> new CodigoInvalidoException(
                        "Este link de confirmação não vale mais. Entre com seu e-mail e senha para pedir um novo."));
        Usuario usuario = confirmacao.getUsuario();
        concluirConfirmacao(usuario);
        return authService.iniciarSessao(usuario);
    }

    @Transactional
    public void concluirConfirmacao(Usuario usuario) {
        if (!usuario.isConfirmacaoEmailPendente()) {
            return;
        }
        usuario.setConfirmacaoEmailPendente(false);
        tokenRepository.excluirDoUsuario(usuario.getId());
        pacienteRepository.findByUsuarioId(usuario.getId())
                .ifPresent(paciente -> herancaPorDocumentoService.herdar(usuario, paciente));
        profissionalRepository.findByUsuarioId(usuario.getId())
                .ifPresent(profissional -> herancaPorDocumentoService.herdar(usuario, profissional));
    }

    public void excluirNaoConfirmados() {
        Instant limite = Instant.now(clock).minus(PRAZO);
        for (Long id : usuarioRepository.findIdsComConfirmacaoVencida(limite)) {
            try {
                List<String> arquivos = transacao.execute(status -> excluir(id));
                if (arquivos != null) {
                    arquivos.forEach(armazenamento::excluir);
                }
            } catch (RuntimeException ex) {
                log.warn("Não foi possível apagar o cadastro não confirmado {}: {}", id, ex.getMessage());
            }
        }
    }

    private List<String> excluir(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isConfirmacaoEmailPendente)
                .orElse(null);
        if (usuario == null) {
            return List.of();
        }
        List<String> arquivos = new ArrayList<>(documentoRepository.findChavesDoUsuario(usuarioId));
        if (usuario.getAvatarStorageKey() != null) {
            arquivos.add(usuario.getAvatarStorageKey());
        }
        pacienteRepository.excluirDoUsuario(usuarioId);
        profissionalRepository.excluirDoUsuario(usuarioId);
        usuarioRepository.excluirNaoConfirmado(usuarioId);
        return arquivos;
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
