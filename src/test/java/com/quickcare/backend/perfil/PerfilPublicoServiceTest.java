package com.quickcare.backend.perfil;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.perfil.dto.PerfilPublicoPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilPublicoProfissionalResponse;
import com.quickcare.backend.perfil.exception.PerfilNaoEncontradoException;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilPublicoServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final Long PACIENTE_ID = 10L;
    private static final Long PROFISSIONAL_ID = 20L;

    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;
    @Mock private BloqueioUsuarioRepository bloqueioUsuarioRepository;

    @InjectMocks
    private PerfilPublicoService service;

    private Profissional profissional;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        profissional = Profissional.builder()
                .id(PROFISSIONAL_ID)
                .usuario(Usuario.builder().id(USUARIO_PROFISSIONAL_ID).nome("Joana Enfermeira").build())
                .statusValidacao(StatusValidacao.APROVADO)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .registroProfissional("123456")
                .registroProfissionalUf("SP")
                .cidade("São Paulo")
                .uf("SP")
                .especialidades(Set.of(
                        Especialidade.builder().id(6L).nome("Sondagem").build(),
                        Especialidade.builder().id(5L).nome("Curativos").build()))
                .contatosEmergencia(List.of(new ContatoEmergencia("Marcos", "11911112222")))
                .criadoEm(Instant.parse("2026-01-10T12:00:00Z"))
                .build();
        paciente = Paciente.builder()
                .id(PACIENTE_ID)
                .usuario(Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Paciente Teste").build())
                .dataNascimento(LocalDate.now().minusYears(40).minusDays(1))
                .cidade("São Paulo")
                .uf("SP")
                .alergias("Dipirona")
                .contatosEmergencia(List.of(new ContatoEmergencia("Filha", "11999990000")))
                .build();
    }

    @Test
    void mostraOProfissionalAprovadoSemDadosDeContato() {
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(atendimentoRepository.countByPropostaProfissionalIdAndStatusIn(PROFISSIONAL_ID, StatusAtendimento.REALIZADOS)).thenReturn(4L);

        PerfilPublicoProfissionalResponse perfil = service.buscarProfissional(USUARIO_PROFISSIONAL_ID, PROFISSIONAL_ID);

        assertThat(perfil.especialidades()).containsExactly("Curativos", "Sondagem");
        assertThat(perfil.siglaRegistro()).isEqualTo("COREN");
        assertThat(perfil.atendimentosConcluidos()).isEqualTo(4);
        assertThat(perfil.contatosEmergencia()).isNull();
    }

    @Test
    void pacienteComAtendimentoIniciadoVeOsContatosDeEmergenciaDoProfissional() {
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        mockAtendimentoEmAndamento(true);

        PerfilPublicoProfissionalResponse perfil = service.buscarProfissional(USUARIO_PACIENTE_ID, PROFISSIONAL_ID);

        assertThat(perfil.contatosEmergencia()).singleElement()
                .satisfies(contato -> assertThat(contato.nome()).isEqualTo("Marcos"));
    }

    @Test
    void pacienteComPropostaAceitaMasAtendimentoNaoIniciadoNaoVeOsContatos() {
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        mockAtendimentoEmAndamento(false);

        assertThat(service.buscarProfissional(USUARIO_PACIENTE_ID, PROFISSIONAL_ID).contatosEmergencia()).isNull();
    }

    @Test
    void profissionalBanidoOuNaoAprovadoNaoTemPerfilVisivel() {
        profissional.setStatusValidacao(StatusValidacao.PENDENTE);
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));

        assertThatThrownBy(() -> service.buscarProfissional(USUARIO_PACIENTE_ID, PROFISSIONAL_ID))
                .isInstanceOf(PerfilNaoEncontradoException.class);

        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        profissional.setBanido(true);
        assertThatThrownBy(() -> service.buscarProfissional(USUARIO_PACIENTE_ID, PROFISSIONAL_ID))
                .isInstanceOf(PerfilNaoEncontradoException.class);
    }

    @Test
    void semPropostaAceitaNaoVeOPerfilDoPaciente() {
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                PROFISSIONAL_ID, PACIENTE_ID, StatusProposta.ACEITA)).thenReturn(false);

        assertThatThrownBy(() -> service.buscarPaciente(USUARIO_PROFISSIONAL_ID, PACIENTE_ID))
                .isInstanceOf(PerfilNaoEncontradoException.class);
    }

    @Test
    void propostaAceitaSemAtendimentoIniciadoMostraASaudeMasNaoOsContatos() {
        mockPacienteComPropostaAceita();
        mockFase(StatusSolicitacao.EM_CURSO, true);
        mockAtendimentoEmAndamento(false);

        PerfilPublicoPacienteResponse perfil = service.buscarPaciente(USUARIO_PROFISSIONAL_ID, PACIENTE_ID);

        assertThat(perfil.idade()).isEqualTo(40);
        assertThat(perfil.saude().alergias()).isEqualTo("Dipirona");
        assertThat(perfil.contatosEmergencia()).isNull();
    }

    @Test
    void comAtendimentoIniciadoMostraTambemOsContatosDeEmergencia() {
        mockPacienteComPropostaAceita();
        mockFase(StatusSolicitacao.EM_CURSO, true);
        mockAtendimentoEmAndamento(true);

        PerfilPublicoPacienteResponse perfil = service.buscarPaciente(USUARIO_PROFISSIONAL_ID, PACIENTE_ID);

        assertThat(perfil.contatosEmergencia()).singleElement()
                .satisfies(contato -> assertThat(contato.nome()).isEqualTo("Filha"));
    }

    @Test
    void atendimentoEncerradoNaoMostraSaudeNemContatos() {
        mockPacienteComPropostaAceita();

        PerfilPublicoPacienteResponse perfil = service.buscarPaciente(USUARIO_PROFISSIONAL_ID, PACIENTE_ID);

        assertThat(perfil.saude()).isNull();
        assertThat(perfil.contatosEmergencia()).isNull();
    }

    @Test
    void comBloqueioEmQualquerSentidoNenhumDosDoisVeOPerfilDoOutro() {
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                PROFISSIONAL_ID, PACIENTE_ID, StatusProposta.ACEITA)).thenReturn(true);
        when(bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(PACIENTE_ID, PROFISSIONAL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.buscarProfissional(USUARIO_PACIENTE_ID, PROFISSIONAL_ID))
                .isInstanceOf(PerfilNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarPaciente(USUARIO_PROFISSIONAL_ID, PACIENTE_ID))
                .isInstanceOf(PerfilNaoEncontradoException.class);
    }

    private void mockPacienteComPropostaAceita() {
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                PROFISSIONAL_ID, PACIENTE_ID, StatusProposta.ACEITA)).thenReturn(true);
        when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.of(paciente));
    }

    private void mockAtendimentoEmAndamento(boolean resultado) {
        lenient().when(atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                PROFISSIONAL_ID, PACIENTE_ID, Set.of(StatusAtendimento.EM_ANDAMENTO))).thenReturn(resultado);
    }

    private void mockFase(Set<StatusSolicitacao> fase, boolean resultado) {
        lenient().when(propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatusAndSolicitacaoStatusIn(
                PROFISSIONAL_ID, PACIENTE_ID, StatusProposta.ACEITA, fase)).thenReturn(resultado);
    }

}
