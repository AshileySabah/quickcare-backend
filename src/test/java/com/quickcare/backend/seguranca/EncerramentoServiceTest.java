package com.quickcare.backend.seguranca;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.problema.ProblemaRepository;
import com.quickcare.backend.problema.StatusProblema;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.PropostaService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncerramentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AtendimentoService atendimentoService;
    @Mock private PropostaService propostaService;
    @Mock private ProblemaRepository problemaRepository;
    @Mock private PendenciasDoPerfil pendencias;
    @Mock private CodigoSegurancaService codigoSegurancaService;
    @Mock private Notificador notificador;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlatformTransactionManager transactionManager;

    private EncerramentoService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new EncerramentoService(usuarioRepository, pacienteRepository, profissionalRepository,
                solicitacaoRepository, propostaRepository, atendimentoRepository, atendimentoService, propostaService,
                problemaRepository, pendencias, codigoSegurancaService, notificador, passwordEncoder,
                Clock.fixed(AGORA, ZoneOffset.UTC), transactionManager);
        usuario = Usuario.builder().id(1L).nome("Ana").email("ana@quickcare.dev").cpf("52998224725")
                .ultimoPerfil(TipoPerfil.PACIENTE).build();
    }

    private Usuario lapideDe(Usuario titular, Instant encerradoEm) {
        return Usuario.builder().id(500L).nome(EncerramentoService.NOME_PERFIL_ENCERRADO)
                .email("x@encerrado.quickcare.invalid").lapide(true).titular(titular)
                .cpfEncerrado(titular.getCpf()).encerradoEm(encerradoEm).build();
    }

    @Test
    void encerrarOPerfilDePacienteCancelaOQueEstaEmCursoEPassaOPerfilParaUmaLapide() {
        Paciente paciente = Paciente.builder().id(10L).usuario(usuario).build();
        Solicitacao emCurso = Solicitacao.builder().id(50L).paciente(paciente).status(StatusSolicitacao.RECEBENDO_PROPOSTAS).build();
        Solicitacao concluida = Solicitacao.builder().id(51L).paciente(paciente).status(StatusSolicitacao.CONCLUIDA).build();
        Proposta pendente = Proposta.builder().id(70L).solicitacao(emCurso).status(StatusProposta.PENDENTE).build();
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));
        when(solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(10L)).thenReturn(List.of(emCurso, concluida));
        when(atendimentoRepository.findBySolicitacaoIdAndStatusIn(eq(50L), anyCollection())).thenReturn(List.of());
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE))
                .thenReturn(new ArrayList<>(List.of(pendente)));
        when(atendimentoRepository.existsBySolicitacaoIdAndStatusIn(eq(50L), anyCollection())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        service.encerrarPerfil(usuario, TipoPerfil.PACIENTE);

        assertThat(emCurso.getStatus()).isEqualTo(StatusSolicitacao.CANCELADA);
        assertThat(concluida.getStatus()).isEqualTo(StatusSolicitacao.CONCLUIDA);
        assertThat(pendente.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        verify(atendimentoService).solicitacaoCancelada(emCurso);
        verify(notificador).solicitacaoCancelada(List.of(pendente));

        Usuario lapide = paciente.getUsuario();
        assertThat(lapide.isLapide()).isTrue();
        assertThat(lapide.getNome()).isEqualTo("Perfil encerrado");
        assertThat(lapide.getTitular()).isEqualTo(usuario);
        assertThat(lapide.getCpf()).isNull();
        assertThat(lapide.getCpfEncerrado()).isEqualTo("52998224725");
        assertThat(paciente.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

    @Test
    void encerrarOPerfilDeProfissionalDesisteDasPropostasPendentesEAceitasEmCurso() {
        Profissional profissional = Profissional.builder().id(20L).usuario(usuario).build();
        Proposta pendente = Proposta.builder().id(80L).status(StatusProposta.PENDENTE).build();
        Proposta recusada = Proposta.builder().id(81L).status(StatusProposta.RECUSADA).build();
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(profissional));
        when(propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(20L)).thenReturn(List.of(pendente, recusada));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        service.encerrarPerfil(usuario, TipoPerfil.PROFISSIONAL);

        verify(propostaService).encerrarPeloProfissional(pendente);
        verify(propostaService, never()).encerrarPeloProfissional(recusada);
        assertThat(profissional.getUsuario().isLapide()).isTrue();
        assertThat(profissional.getEncerradoEm()).isEqualTo(AGORA);
    }

    @Test
    void encerrarAContaMarcaOEncerramentoEDerrubaAsSessoes() {
        service.encerrarConta(usuario);

        assertThat(usuario.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        assertThat(usuario.contaEncerrada()).isTrue();
    }

    @Test
    void recuperarOPerfilDevolveAoTitularEProrrogaOsPrazosPeloTempoCongelado() {
        Instant encerradoEm = AGORA.minus(Duration.ofDays(10));
        Usuario lapide = lapideDe(usuario, encerradoEm);
        Paciente paciente = Paciente.builder().id(10L).usuario(lapide).encerradoEm(encerradoEm).build();
        ProblemaAtendimento problema = ProblemaAtendimento.builder().id(3L).status(StatusProblema.AGUARDANDO_RESPOSTA)
                .prazoResposta(encerradoEm.plus(Duration.ofDays(2))).build();
        when(pacienteRepository.findCongeladoDoTitular(1L)).thenReturn(Optional.of(paciente));
        when(problemaRepository.findAguardandoDoPaciente(10L)).thenReturn(List.of(problema));
        when(pendencias.banido(usuario, TipoPerfil.PACIENTE, AGORA)).thenReturn(true);
        when(pendencias.descrever(usuario, TipoPerfil.PACIENTE, AGORA)).thenReturn(List.of("banimento até 20/11/2026"));

        service.recuperarPerfil(usuario, TipoPerfil.PACIENTE);

        assertThat(paciente.getUsuario()).isEqualTo(usuario);
        assertThat(paciente.getEncerradoEm()).isNull();
        assertThat(paciente.isBanido()).isTrue();
        assertThat(lapide.getTitular()).isNull();
        assertThat(lapide.getCpfEncerrado()).isNull();
        assertThat(problema.getPrazoResposta()).isEqualTo(AGORA.plus(Duration.ofDays(2)));
        verify(pendencias).prorrogar(usuario, TipoPerfil.PACIENTE, encerradoEm, Duration.ofDays(10));
        verify(notificador).perfilRecuperado(usuario, TipoPerfil.PACIENTE, List.of("banimento até 20/11/2026"));
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PACIENTE);
    }

    @Test
    void recuperarAContaVoltaComOsPerfisCongeladosELimpaOEncerramento() {
        Instant encerradoEm = AGORA.minus(Duration.ofDays(3));
        usuario.setEncerradoEm(encerradoEm);
        Profissional profissional = Profissional.builder().id(20L).usuario(lapideDe(usuario, encerradoEm))
                .encerradoEm(encerradoEm).build();
        when(pacienteRepository.findCongeladoDoTitular(1L)).thenReturn(Optional.empty());
        when(profissionalRepository.findCongeladoDoTitular(1L)).thenReturn(Optional.of(profissional));
        when(pendencias.descrever(usuario, TipoPerfil.PROFISSIONAL, AGORA)).thenReturn(List.of());

        List<TipoPerfil> recuperados = service.recuperarConta(usuario);

        assertThat(recuperados).containsExactly(TipoPerfil.PROFISSIONAL);
        assertThat(usuario.getEncerradoEm()).isNull();
        assertThat(profissional.getUsuario()).isEqualTo(usuario);
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

    @Test
    void naoRecuperaContaQueNaoEstaEncerrada() {
        assertThatThrownBy(() -> service.recuperarConta(usuario)).isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void depoisDeTrintaDiasApagaOsDadosPessoaisEGuardaODocumento() {
        Instant encerradoEm = AGORA.minus(Duration.ofDays(31));
        usuario.setEncerradoEm(encerradoEm);
        Usuario lapide = lapideDe(usuario, encerradoEm);
        Paciente paciente = Paciente.builder().id(10L).usuario(lapide).encerradoEm(encerradoEm)
                .alergias("Dipirona").condicoesSaude("Asma").cep("01310100").logradouro("Avenida Paulista").build();
        Instant limite = AGORA.minus(EncerramentoService.PRAZO_RECUPERACAO);
        when(pacienteRepository.findIdsParaExcluir(limite)).thenReturn(List.of(10L));
        when(pacienteRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(profissionalRepository.findIdsParaExcluir(limite)).thenReturn(List.of());
        when(usuarioRepository.findIdsParaExcluir(limite)).thenReturn(List.of(1L));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        service.processarVencidos();

        assertThat(paciente.getAlergias()).isNull();
        assertThat(paciente.getCondicoesSaude()).isNull();
        assertThat(paciente.getExcluidoEm()).isEqualTo(AGORA);
        assertThat(lapide.getTitular()).isNull();
        assertThat(lapide.getExcluidoEm()).isEqualTo(AGORA);

        assertThat(usuario.getCpf()).isNull();
        assertThat(usuario.getCpfEncerrado()).isEqualTo("52998224725");
        assertThat(usuario.getNome()).isEqualTo("Usuário encerrado");
        assertThat(usuario.getEmail()).endsWith("@encerrado.quickcare.invalid");
        assertThat(usuario.getExcluidoEm()).isEqualTo(AGORA);
        verify(codigoSegurancaService).descartarTodos(usuario);
    }

}
