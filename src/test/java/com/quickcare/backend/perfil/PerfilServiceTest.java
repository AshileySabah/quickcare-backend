package com.quickcare.backend.perfil;

import com.quickcare.backend.avaliacao.AvaliacaoService;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.geolocalizacao.GeocodificacaoService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.perfil.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.perfil.dto.PerfilPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilProfissionalResponse;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final Long USUARIO_ID = 1L;
    private static final LocalDate NASCIMENTO = LocalDate.of(1980, 5, 12);

    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;
    @Mock private GeocodificacaoService geocodificacaoService;
    @Mock private AvaliacaoService avaliacaoService;

    @InjectMocks
    private PerfilService service;

    private final Usuario usuario = Usuario.builder().id(USUARIO_ID).nome("Paciente Teste").email("paciente@quickcare.dev").build();

    @Test
    void oPerfilDoPacienteTrazAsDeclaracoesParaConsulta() {
        when(pacienteRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(paciente()));

        PerfilPacienteResponse perfil = service.buscarPaciente(USUARIO_ID);

        assertThat(perfil.declaracoes().confirmacaoInformacoesVerdadeiras()).isTrue();
        assertThat(perfil.declaracoes().consentimentoLgpd()).isTrue();
    }

    @Test
    void atualizarOPerfilNaoMudaDataDeNascimentoNemGenero() {
        Paciente paciente = paciente();
        when(pacienteRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(paciente));

        PerfilPacienteResponse perfil = service.atualizarPaciente(USUARIO_ID, new AtualizarPerfilPacienteRequest(
                "Paciente Atualizada", "11999990000", List.of(), null, null, null,
                "01311200", "Rua Haddock Lobo", "300", null, "Cerqueira César", "São Paulo", "sp", false), null);

        assertThat(perfil.nome()).isEqualTo("Paciente Atualizada");
        assertThat(paciente.getDataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(paciente.getGenero()).isEqualTo(Genero.FEMININO);
    }

    @Test
    void oPerfilDoProfissionalTrazAModalidadeEOsContatos() {
        Profissional profissional = Profissional.builder().id(20L).usuario(usuario)
                .modalidadeAtendimento(ModalidadeAtendimento.AMBOS)
                .contatosEmergencia(new ArrayList<>(List.of(new ContatoEmergencia("Marcos", "11911112222"))))
                .build();
        when(profissionalRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(profissional));

        PerfilProfissionalResponse perfil = service.buscarProfissional(USUARIO_ID);

        assertThat(perfil.modalidadeAtendimento()).isEqualTo(ModalidadeAtendimento.AMBOS);
        assertThat(perfil.contatosEmergencia()).hasSize(1);
    }

    @Test
    void oProprioPerfilTrazAsAvaliacoesRecebidas() {
        ResumoAvaliacoesResponse doPaciente = new ResumoAvaliacoesResponse(4.5, 2, List.of());
        ResumoAvaliacoesResponse doProfissional = new ResumoAvaliacoesResponse(null, 0, List.of());
        when(pacienteRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(paciente()));
        when(profissionalRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(Profissional.builder().id(20L).usuario(usuario).contatosEmergencia(new ArrayList<>()).build()));
        when(avaliacaoService.resumoDoPaciente(10L)).thenReturn(doPaciente);
        when(avaliacaoService.resumoDoProfissional(20L)).thenReturn(doProfissional);

        assertThat(service.buscarPaciente(USUARIO_ID).avaliacoes()).isSameAs(doPaciente);
        assertThat(service.buscarProfissional(USUARIO_ID).avaliacoes()).isSameAs(doProfissional);
    }

    private Paciente paciente() {
        return Paciente.builder().id(10L).usuario(usuario)
                .dataNascimento(NASCIMENTO).genero(Genero.FEMININO)
                .confirmacaoInformacoesVerdadeiras(true).consentimentoLgpd(true)
                .contatosEmergencia(new ArrayList<>()).uf("SP").build();
    }

}
