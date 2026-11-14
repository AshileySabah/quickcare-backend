package com.quickcare.backend.cadastro;

import com.quickcare.backend.auth.AuthService;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.HerancaPorDocumentoService;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmacaoEmailServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-14T12:00:00Z");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private TokenConfirmacaoEmailRepository tokenRepository;
    @Mock private ArmazenamentoDocumentoService armazenamento;
    @Mock private HerancaPorDocumentoService herancaPorDocumentoService;
    @Mock private AuthService authService;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlatformTransactionManager transactionManager;

    private ConfirmacaoEmailService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new ConfirmacaoEmailService(usuarioRepository, pacienteRepository, profissionalRepository,
                documentoRepository, tokenRepository, armazenamento, herancaPorDocumentoService, authService, emailService,
                passwordEncoder, Clock.fixed(AGORA, ZoneOffset.UTC), transactionManager);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
        usuario = Usuario.builder().id(1L).nome("Ana").email("ana@quickcare.dev").senhaHash("hash")
                .confirmacaoEmailPendente(true).criadoEm(AGORA.minus(Duration.ofHours(1))).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaOLinkQueValeAteDozeHorasDepoisDoCadastro() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        ArgumentCaptor<TokenConfirmacaoEmail> token = ArgumentCaptor.forClass(TokenConfirmacaoEmail.class);
        ArgumentCaptor<Map<String, Object>> variaveis = ArgumentCaptor.forClass(Map.class);

        service.enviarLink(1L);

        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(11)));
        verify(emailService).enviarEmailTemplate(eq("ana@quickcare.dev"), anyString(), eq("seguranca"), variaveis.capture());
        assertThat((String) variaveis.getValue().get("link")).startsWith("http://localhost:4200/confirmar-email?token=");
    }

    @Test
    void confirmarLiberaAContaHerdaAsPendenciasDoDocumentoEEntra() {
        String token = "tok";
        Paciente paciente = Paciente.builder().id(10L).usuario(usuario).build();
        when(tokenRepository.findByTokenHash(ConfirmacaoEmailService.hash(token))).thenReturn(Optional.of(
                TokenConfirmacaoEmail.builder().usuario(usuario).tokenHash("x").expiraEm(AGORA.plusSeconds(60)).build()));
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));

        service.confirmar(token);

        assertThat(usuario.isConfirmacaoEmailPendente()).isFalse();
        verify(herancaPorDocumentoService).herdar(usuario, paciente);
        verify(authService).iniciarSessao(usuario);
    }

    @Test
    void linkVencidoNaoConfirma() {
        when(tokenRepository.findByTokenHash(ConfirmacaoEmailService.hash("tok"))).thenReturn(Optional.of(
                TokenConfirmacaoEmail.builder().usuario(usuario).tokenHash("x").expiraEm(AGORA.minusSeconds(1)).build()));

        assertThatThrownBy(() -> service.confirmar("tok")).isInstanceOf(CodigoInvalidoException.class);
        assertThat(usuario.isConfirmacaoEmailPendente()).isTrue();
        verify(authService, never()).iniciarSessao(any());
    }

    @Test
    void reenviarExigeASenhaENaoRepeteAntesDeUmMinuto() {
        when(usuarioRepository.findByEmail("ana@quickcare.dev")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.reenviar("ana@quickcare.dev", "errada")).isInstanceOf(LoginInvalidoException.class);

        when(passwordEncoder.matches("Senha@123", "hash")).thenReturn(true);
        when(tokenRepository.findByUsuarioId(1L)).thenReturn(Optional.of(TokenConfirmacaoEmail.builder()
                .usuario(usuario).tokenHash("x").expiraEm(AGORA.plusSeconds(600)).criadoEm(AGORA.minusSeconds(20)).build()));
        assertThatThrownBy(() -> service.reenviar("ana@quickcare.dev", "Senha@123"))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("menos de um minuto");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void depoisDeDozeHorasSemConfirmarApagaOCadastroEOsArquivos() {
        usuario.setCriadoEm(AGORA.minus(Duration.ofHours(13)));
        usuario.setAvatarStorageKey("avatares/a.png");
        when(usuarioRepository.findIdsComConfirmacaoVencida(AGORA.minus(ConfirmacaoEmailService.PRAZO))).thenReturn(List.of(1L));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(documentoRepository.findChavesDoUsuario(1L)).thenReturn(List.of("comprovante_cpf/1.pdf"));

        service.excluirNaoConfirmados();

        verify(pacienteRepository).excluirDoUsuario(1L);
        verify(profissionalRepository).excluirDoUsuario(1L);
        verify(usuarioRepository).excluirNaoConfirmado(1L);
        verify(armazenamento).excluir("comprovante_cpf/1.pdf");
        verify(armazenamento).excluir("avatares/a.png");
    }

}
