package com.quickcare.backend.paciente;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.paciente.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.paciente.dto.PerfilPacienteResponse;
import com.quickcare.backend.paciente.exception.PacientePerfilNaoEncontradoException;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacientePerfilServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PacientePerfilService service;

    private Usuario usuario;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nome("Maria Silva")
                .email("maria@email.com")
                .cpf("12345678900")
                .telefone("11999998888")
                .build();

        paciente = Paciente.builder()
                .id(10L)
                .usuario(usuario)
                .dataNascimento(LocalDate.of(1990, 5, 10))
                .genero(Genero.FEMININO)
                .cep("01310100")
                .logradouro("Avenida Paulista")
                .numero("1000")
                .bairro("Bela Vista")
                .cidade("São Paulo")
                .uf("SP")
                .build();
    }

    @Test
    void deveBuscarPerfilDoPacienteLogado() {
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));

        PerfilPacienteResponse response = service.buscar(1L);

        assertThat(response.nome()).isEqualTo("Maria Silva");
        assertThat(response.email()).isEqualTo("maria@email.com");
        assertThat(response.cidade()).isEqualTo("São Paulo");
    }

    @Test
    void deveLancarExcecaoQuandoUsuarioNaoTemPerfilDePaciente() {
        when(pacienteRepository.findByUsuarioId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(99L))
                .isInstanceOf(PacientePerfilNaoEncontradoException.class);
    }

    @Test
    void deveAtualizarDadosDoPacienteENomeDoUsuario() {
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));

        AtualizarPerfilPacienteRequest request = new AtualizarPerfilPacienteRequest(
                "Maria Silva Souza",
                "11988887777",
                LocalDate.of(1990, 5, 10),
                Genero.FEMININO,
                List.of(new ContatoEmergenciaRequest("João Souza", "11977776666")),
                "Dipirona",
                null,
                null,
                "01310100",
                "Avenida Paulista",
                "1000",
                "Apto 12",
                "Bela Vista",
                "São Paulo",
                "SP"
        );

        PerfilPacienteResponse response = service.atualizar(1L, request);

        assertThat(usuario.getNome()).isEqualTo("Maria Silva Souza");
        assertThat(usuario.getTelefone()).isEqualTo("11988887777");
        assertThat(paciente.getComplemento()).isEqualTo("Apto 12");
        assertThat(response.nome()).isEqualTo("Maria Silva Souza");
    }
}