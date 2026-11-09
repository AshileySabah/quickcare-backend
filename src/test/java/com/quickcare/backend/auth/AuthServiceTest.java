package com.quickcare.backend.auth;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.jwt.JwtService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AdministradorRepository administradorRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;

    @InjectMocks
    private AuthService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(1L).nome("Maria Silva").email("maria@email.com").ultimoPerfil(TipoPerfil.PROFISSIONAL).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void trocaParaUmPerfilAtivoEDevolveASessaoComOUltimoPerfil() {
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Paciente.builder().banido(false).build()));
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Profissional.builder().banido(false).build()));

        LoginResponse resposta = service.definirPerfilAtivo(1L, TipoPerfil.PACIENTE);

        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PACIENTE);
        assertThat(resposta.ultimoPerfil()).isEqualTo(TipoPerfil.PACIENTE);
        assertThat(resposta.perfis()).extracting("tipo").containsExactly("PACIENTE", "PROFISSIONAL");
    }

    @Test
    void naoTrocaParaPerfilQueOUsuarioNaoTem() {
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definirPerfilAtivo(1L, TipoPerfil.PACIENTE))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

    @Test
    void trocaParaPerfilBanidoSoParaConsulta() {
        usuario.setUltimoPerfil(TipoPerfil.PACIENTE);
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Profissional.builder().banido(true).build()));

        service.definirPerfilAtivo(1L, TipoPerfil.PROFISSIONAL);

        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

}
