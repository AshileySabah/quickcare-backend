package com.quickcare.backend.seguranca;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.auth.AuthService;
import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.RedefinicaoSenhaService;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.dto.CodigoEmailRequest;
import com.quickcare.backend.seguranca.dto.CodigoEncerramentoRequest;
import com.quickcare.backend.seguranca.dto.EncerramentoRequest;
import com.quickcare.backend.seguranca.dto.RecuperarContaRequest;
import com.quickcare.backend.seguranca.dto.TrocarSenhaRequest;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.seguranca.exception.SenhaIncorretaException;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegurancaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AdministradorRepository administradorRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CodigoSegurancaService codigoSegurancaService;
    @Mock private EncerramentoService encerramentoService;
    @Mock private AuthService authService;
    @Mock private RedefinicaoSenhaService redefinicaoSenhaService;
    @Mock private EmailService emailService;

    private SegurancaService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new SegurancaService(usuarioRepository, administradorRepository, pacienteRepository,
                profissionalRepository, passwordEncoder, codigoSegurancaService, encerramentoService, authService,
                redefinicaoSenhaService, emailService, Clock.fixed(AGORA, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
        usuario = Usuario.builder().id(1L).nome("Ana").email("ana@quickcare.dev").senhaHash("hash-atual").build();
    }

    private void usuarioLogado() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void adminNaoUsaATelaDeSeguranca() {
        when(administradorRepository.existsByUsuarioId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.situacao(1L)).isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void trocarSenhaExigeASenhaAtual() {
        usuarioLogado();
        when(passwordEncoder.matches("errada", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.trocarSenha(1L, new TrocarSenhaRequest("errada", "Nova@1234")))
                .isInstanceOf(SenhaIncorretaException.class);
        verify(authService, never()).iniciarSessao(any());
    }

    @Test
    void trocarSenhaSalvaANovaDerrubaOsOutrosDispositivosEMantemEsteConectado() {
        usuarioLogado();
        when(passwordEncoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        when(passwordEncoder.matches("Nova@1234", "hash-atual")).thenReturn(false);
        when(passwordEncoder.encode("Nova@1234")).thenReturn("hash-novo");
        LoginResult sessao = new LoginResult(null, "access", "refresh");
        when(authService.iniciarSessao(usuario)).thenReturn(sessao);

        LoginResult resultado = service.trocarSenha(1L, new TrocarSenhaRequest("Atual@123", "Nova@1234"));

        assertThat(resultado).isEqualTo(sessao);
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        verify(emailService).enviarEmailTemplate(eq("ana@quickcare.dev"), anyString(), eq("seguranca"), any());
    }

    @Test
    void naoPedeCodigoParaUmEmailQueJaEstaEmUso() {
        usuarioLogado();
        when(passwordEncoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        when(usuarioRepository.existsByEmailIgnoreCase("outra@quickcare.dev")).thenReturn(true);

        assertThatThrownBy(() -> service.pedirCodigoDeEmail(1L, new CodigoEmailRequest("outra@quickcare.dev", "Atual@123")))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("em uso");
        verify(codigoSegurancaService, never()).enviarCodigo(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmarOCodigoTrocaOEmailDerrubaOsOutrosDispositivosEMandaOLinkDeDesfazerAoEmailAntigo() {
        usuarioLogado();
        when(codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, "123456")).thenReturn("nova@quickcare.dev");
        when(codigoSegurancaService.gerarTokenDeDesfazerEmail(usuario, "ana@quickcare.dev", "hash-atual"))
                .thenReturn(Optional.of("tok"));
        ArgumentCaptor<Map<String, Object>> variaveis = ArgumentCaptor.forClass(Map.class);

        service.confirmarEmail(1L, "123456");

        assertThat(usuario.getEmail()).isEqualTo("nova@quickcare.dev");
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        verify(emailService).enviarEmailTemplate(eq("ana@quickcare.dev"), anyString(), eq("seguranca"), variaveis.capture());
        assertThat(variaveis.getValue().get("link")).isEqualTo("http://localhost:4200/desfazer-troca-de-email?token=tok");
        verify(authService).iniciarSessao(usuario);
    }

    @Test
    @SuppressWarnings("unchecked")
    void desfazerComASenhaAnteriorVoltaOEmailETrancaASenhaAtual() {
        usuario.setEmail("ladrao@quickcare.dev");
        CodigoSeguranca desfazer = CodigoSeguranca.builder().id(7L).usuario(usuario)
                .finalidade(FinalidadeCodigo.DESFAZER_EMAIL).dado("ana@quickcare.dev").senhaHashAnterior("hash-antigo")
                .codigoHash("x").expiraEm(AGORA.plusSeconds(60)).build();
        when(codigoSegurancaService.usarTokenDeDesfazerEmail(eq("tok"), any())).thenAnswer(invocacao -> {
            Predicate<String> senhaConfere = invocacao.getArgument(1);
            assertThat(senhaConfere.test("hash-antigo")).isTrue();
            return desfazer;
        });
        when(passwordEncoder.matches("Antiga@123", "hash-antigo")).thenReturn(true);
        when(usuarioRepository.findByEmailIgnoreCase("ana@quickcare.dev")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-aleatorio");
        when(redefinicaoSenhaService.gerarToken(usuario)).thenReturn("token-redefinicao");

        String token = service.desfazerTrocaDeEmail("tok", "Antiga@123");

        assertThat(token).isEqualTo("token-redefinicao");
        assertThat(usuario.getEmail()).isEqualTo("ana@quickcare.dev");
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-aleatorio");
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        verify(codigoSegurancaService).descartarTodos(usuario);
        verify(emailService).enviarEmailTemplate(eq("ladrao@quickcare.dev"), anyString(), eq("seguranca"), any(Map.class));
    }

    @Test
    void naoDesfazSeOEmailAnteriorJaEDeOutraConta() {
        CodigoSeguranca desfazer = CodigoSeguranca.builder().id(7L).usuario(usuario)
                .finalidade(FinalidadeCodigo.DESFAZER_EMAIL).dado("antigo@quickcare.dev").senhaHashAnterior("hash-antigo")
                .codigoHash("x").expiraEm(AGORA.plusSeconds(60)).build();
        when(codigoSegurancaService.usarTokenDeDesfazerEmail(eq("tok"), any())).thenReturn(desfazer);
        when(usuarioRepository.findByEmailIgnoreCase("antigo@quickcare.dev"))
                .thenReturn(Optional.of(Usuario.builder().id(2L).build()));

        assertThatThrownBy(() -> service.desfazerTrocaDeEmail("tok", "Antiga@123"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThat(usuario.getEmail()).isEqualTo("ana@quickcare.dev");
        verify(redefinicaoSenhaService, never()).gerarToken(any());
    }

    @Test
    void comUmSoPerfilNaoDaParaEncerrarSoEle() {
        usuarioLogado();
        when(passwordEncoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Paciente.builder().id(10L).build()));
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pedirCodigoDeEncerramento(1L,
                new CodigoEncerramentoRequest(AlvoEncerramento.PACIENTE, "Atual@123")))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("encerre a conta");
    }

    @Test
    void codigoPedidoParaOutroAlvoNaoEncerra() {
        usuarioLogado();
        when(codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.ENCERRAR, "123456")).thenReturn("PACIENTE");

        assertThatThrownBy(() -> service.encerrar(1L, new EncerramentoRequest(AlvoEncerramento.CONTA, "123456")))
                .isInstanceOf(CodigoInvalidoException.class);
        verify(encerramentoService, never()).encerrarConta(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void encerrarAContaEnviaOLinkDeRecuperacao() {
        usuarioLogado();
        when(codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.ENCERRAR, "123456")).thenReturn("CONTA");
        doAnswer(invocacao -> {
            usuario.setEncerradoEm(AGORA);
            return null;
        }).when(encerramentoService).encerrarConta(usuario);
        when(codigoSegurancaService.gerarTokenDeRecuperacao(usuario, EncerramentoService.excluiEm(AGORA))).thenReturn("tok");
        ArgumentCaptor<Map<String, Object>> variaveis = ArgumentCaptor.forClass(Map.class);

        assertThat(service.encerrar(1L, new EncerramentoRequest(AlvoEncerramento.CONTA, "123456"))).isEmpty();

        verify(emailService).enviarEmailTemplate(eq("ana@quickcare.dev"), anyString(), eq("seguranca"), variaveis.capture());
        assertThat(variaveis.getValue().get("link")).isEqualTo("http://localhost:4200/recuperar-conta?token=tok");
        assertThat((String) variaveis.getValue().get("mensagem")).contains("08/12/2026");
    }

    @Test
    void encerrarSoUmPerfilDevolveASessaoComOOutro() {
        usuarioLogado();
        when(codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.ENCERRAR, "123456")).thenReturn("PROFISSIONAL");
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Paciente.builder().id(10L).build()));
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Profissional.builder().id(20L).build()));
        LoginResponse sessao = new LoginResponse(1L, "Ana", "ana@quickcare.dev", null, List.of(), TipoPerfil.PACIENTE);
        when(authService.montarResposta(usuario)).thenReturn(sessao);

        assertThat(service.encerrar(1L, new EncerramentoRequest(AlvoEncerramento.PROFISSIONAL, "123456"))).contains(sessao);

        verify(encerramentoService).encerrarPerfil(usuario, TipoPerfil.PROFISSIONAL);
    }

    @Test
    void recuperarAContaComSenhaErradaNaoRevelaNada() {
        usuario.setEncerradoEm(AGORA.minusSeconds(60));
        when(usuarioRepository.findByEmail("ana@quickcare.dev")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.recuperarConta(new RecuperarContaRequest("ana@quickcare.dev", "errada")))
                .isInstanceOf(LoginInvalidoException.class);
        verify(encerramentoService, never()).recuperarConta(any());
    }

    @Test
    void recuperarAContaComSenhaCertaVoltaEEntra() {
        usuario.setEncerradoEm(AGORA.minusSeconds(60));
        when(usuarioRepository.findByEmail("ana@quickcare.dev")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Atual@123", "hash-atual")).thenReturn(true);

        service.recuperarConta(new RecuperarContaRequest("ana@quickcare.dev", "Atual@123"));

        verify(encerramentoService).recuperarConta(usuario);
        verify(codigoSegurancaService).descartarTodos(usuario);
        verify(authService).iniciarSessao(usuario);
    }

}
