package com.quickcare.backend.solicitacao;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.dto.EnderecoSolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;

    @Mock
    private SolicitacaoRepository solicitacaoRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private EspecialidadeRepository especialidadeRepository;

    @InjectMocks
    private SolicitacaoService service;

    private Paciente paciente;
    private Especialidade nutricao;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder().id(USUARIO_PACIENTE_ID).build();
        paciente = Paciente.builder().id(10L).usuario(usuario).build();
        nutricao = Especialidade.builder().id(4L).nome("Nutrição").categoria(CategoriaProfissional.values()[0]).build();
    }

    @Test
    void criaSolicitacaoOnlineAbertaSemEndereco() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(especialidadeRepository.findById(4L)).thenReturn(Optional.of(nutricao));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EnderecoSolicitacaoRequest enderecoIgnorado = new EnderecoSolicitacaoRequest("Rua A", "São Paulo", "SP");
        SolicitacaoResponse response = service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.ONLINE, enderecoIgnorado));

        assertThat(response.status()).isEqualTo(StatusSolicitacao.ABERTA);
        assertThat(response.endereco()).isNull();
        assertThat(response.especialidadeNome()).isEqualTo("Nutrição");
    }

    @Test
    void exigeEnderecoNoAtendimentoPresencial() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(especialidadeRepository.findById(4L)).thenReturn(Optional.of(nutricao));

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.PRESENCIAL, null)))
                .isInstanceOf(SolicitacaoInvalidaException.class);
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void rejeitaUfInexistente() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(especialidadeRepository.findById(4L)).thenReturn(Optional.of(nutricao));

        EnderecoSolicitacaoRequest endereco = new EnderecoSolicitacaoRequest("Rua A", "Cidade", "XX");

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.PRESENCIAL, endereco)))
                .isInstanceOf(SolicitacaoInvalidaException.class)
                .hasMessage("UF inválida.");
    }

    @Test
    void profissionalNaoPodeCriarSolicitacao() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(USUARIO_PROFISSIONAL_ID, request(ModalidadeSolicitacao.ONLINE, null)))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void bloqueiaEdicaoDeSolicitacaoEmAndamento() {
        mockSolicitacaoDoPaciente(StatusSolicitacao.EM_ANDAMENTO);

        assertThatThrownBy(() -> service.atualizar(USUARIO_PACIENTE_ID, 50L, request(ModalidadeSolicitacao.ONLINE, null)))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Esta solicitação não pode mais ser editada.");
    }

    @Test
    void cancelaSolicitacaoEmAndamento() {
        Solicitacao solicitacao = mockSolicitacaoDoPaciente(StatusSolicitacao.EM_ANDAMENTO);
        when(solicitacaoRepository.saveAndFlush(solicitacao)).thenReturn(solicitacao);

        SolicitacaoResponse response = service.cancelar(USUARIO_PACIENTE_ID, 50L);

        assertThat(response.status()).isEqualTo(StatusSolicitacao.CANCELADA);
    }

    @Test
    void bloqueiaCancelamentoDeSolicitacaoConcluida() {
        mockSolicitacaoDoPaciente(StatusSolicitacao.CONCLUIDA);

        assertThatThrownBy(() -> service.cancelar(USUARIO_PACIENTE_ID, 50L))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void concluiApenasSolicitacaoEmAndamento() {
        mockSolicitacaoDoPaciente(StatusSolicitacao.ABERTA);

        assertThatThrownBy(() -> service.concluir(USUARIO_PACIENTE_ID, 50L))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Apenas solicitações em andamento podem ser concluídas.");
    }

    @Test
    void escondeSolicitacaoDeQuemNaoEDonoNemDaEspecialidade() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao(StatusSolicitacao.ABERTA)));
        Profissional outraEspecialidade = Profissional.builder()
                .especialidades(Set.of(Especialidade.builder().id(99L).build()))
                .build();
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(outraEspecialidade));

        assertThatThrownBy(() -> service.buscar(USUARIO_PROFISSIONAL_ID, 50L))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void profissionalDaEspecialidadeVeASolicitacao() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao(StatusSolicitacao.ABERTA)));
        Profissional nutricionista = Profissional.builder().especialidades(Set.of(nutricao)).build();
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(nutricionista));

        assertThat(service.buscar(USUARIO_PROFISSIONAL_ID, 50L).id()).isEqualTo(50L);
    }

    private Solicitacao mockSolicitacaoDoPaciente(StatusSolicitacao status) {
        Solicitacao solicitacao = solicitacao(status);
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(solicitacaoRepository.findByIdAndPacienteId(50L, paciente.getId())).thenReturn(Optional.of(solicitacao));
        return solicitacao;
    }

    private Solicitacao solicitacao(StatusSolicitacao status) {
        return Solicitacao.builder()
                .id(50L)
                .paciente(paciente)
                .especialidade(nutricao)
                .descricao("Acompanhamento nutricional para teste automatizado.")
                .modalidade(ModalidadeSolicitacao.ONLINE)
                .prazoDesejado(LocalDate.now().plusDays(10))
                .status(status)
                .build();
    }

    private SolicitacaoRequest request(ModalidadeSolicitacao modalidade, EnderecoSolicitacaoRequest endereco) {
        return new SolicitacaoRequest(4L, "Acompanhamento nutricional para teste automatizado.", modalidade, endereco,
                LocalDate.now().plusDays(10));
    }

}
