package com.quickcare.backend.cadastro;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.cadastro.dto.NovoPerfilPacienteRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilProfissionalRequest;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.documento.DocumentoObrigatorioException;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.documento.TipoDocumento;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.geolocalizacao.GeocodificacaoService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.ValidacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastroServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GeocodificacaoService geocodificacaoService;
    @Mock private ValidacaoService validacaoService;
    @Mock private AdministradorRepository administradorRepository;

    @InjectMocks
    private CadastroService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(1L).nome("Maria Silva").email("maria@email.com").ultimoPerfil(TipoPerfil.PACIENTE).build();
    }

    private static MockMultipartFile arquivo(String nome) {
        return new MockMultipartFile("documentos", nome, "application/pdf", new byte[]{1, 2, 3});
    }

    private static final List<MultipartFile> DOCUMENTOS_PESSOAIS = List.of(arquivo("cpf.pdf"), arquivo("rg.pdf"));
    private static final List<TipoDocumento> TIPOS_PESSOAIS = List.of(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF);

    private static NovoPerfilPacienteRequest pacienteRequest() {
        return new NovoPerfilPacienteRequest(LocalDate.of(1990, 1, 1), Genero.FEMININO, List.of(), true, true,
                null, null, null, "01310100", "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "sp");
    }

    private static NovoPerfilProfissionalRequest profissionalRequest(CategoriaProfissional categoria, List<Long> especialidadeIds,
                                                                     String registro, String registroUf) {
        return new NovoPerfilProfissionalRequest(null, categoria, especialidadeIds, registro, registroUf,
                LocalDate.of(1990, 1, 1), Genero.FEMININO, List.of(), true, true, false,
                ModalidadeAtendimento.PRESENCIAL, BigDecimal.TEN,
                "01310100", "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "sp");
    }

    private void documentosSalvos() {
        when(armazenamentoDocumentoService.enviar(any(TipoDocumento.class), any(MultipartFile.class))).thenReturn("chave");
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    void criaPerfilDePacienteParaUsuarioLogadoComValidacaoCompletaEOTornaAtivo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.existsByUsuarioId(1L)).thenReturn(false);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        documentosSalvos();

        service.criarPerfilPaciente(1L, pacienteRequest(), DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS);

        ArgumentCaptor<Paciente> paciente = ArgumentCaptor.forClass(Paciente.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<TipoDocumento, Documento>> documentos = ArgumentCaptor.forClass(Map.class);
        verify(validacaoService).iniciar(paciente.capture(), documentos.capture());

        assertThat(paciente.getValue().getUsuario()).isSameAs(usuario);
        assertThat(paciente.getValue().getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
        assertThat(paciente.getValue().getUf()).isEqualTo("SP");
        assertThat(documentos.getValue()).containsOnlyKeys(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF);
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PACIENTE);
    }

    @Test
    void criaPerfilDeProfissionalParaUsuarioLogadoEOTornaAtivo() {
        Especialidade cuidadoIdosos = Especialidade.builder().id(7L).nome("Cuidador de Idosos")
                .categoria(CategoriaProfissional.CUIDADOR).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(profissionalRepository.existsByUsuarioId(1L)).thenReturn(false);
        when(especialidadeRepository.findAllByIdIn(List.of(7L))).thenReturn(List.of(cuidadoIdosos));
        when(geocodificacaoService.geocodificar(any())).thenReturn(Optional.empty());
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        documentosSalvos();

        service.criarPerfilProfissional(1L, profissionalRequest(CategoriaProfissional.CUIDADOR, List.of(7L), null, null),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS);

        ArgumentCaptor<Profissional> profissional = ArgumentCaptor.forClass(Profissional.class);
        verify(validacaoService).iniciar(profissional.capture(), anyMap());
        assertThat(profissional.getValue().getUsuario()).isSameAs(usuario);
        assertThat(profissional.getValue().getEspecialidades()).containsExactly(cuidadoIdosos);
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

    @Test
    void adminNaoPodeCriarPerfilDePacienteNemDeProfissional() {
        when(administradorRepository.existsByUsuarioId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.criarPerfilPaciente(1L, pacienteRequest(),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
        assertThatThrownBy(() -> service.criarPerfilProfissional(1L,
                profissionalRequest(CategoriaProfissional.CUIDADOR, List.of(7L), null, null),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS))
                .isInstanceOf(PerfilNaoAutorizadoException.class);

        verify(pacienteRepository, never()).save(any());
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void naoCriaPerfilQueOUsuarioJaTem() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.existsByUsuarioId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.criarPerfilPaciente(1L, pacienteRequest(),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS))
                .isInstanceOf(PerfilJaExistenteException.class);
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void exigeODocumentoDoCpfMesmoComIdentidadeJaAprovadaNoOutroPerfil() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.criarPerfilPaciente(1L, pacienteRequest(), List.of(), List.of()))
                .isInstanceOf(DocumentoObrigatorioException.class)
                .hasMessageContaining("CPF");
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void soODocumentoDeIdentidadeNaoBasta() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.criarPerfilPaciente(1L, pacienteRequest(),
                List.of(arquivo("rg.pdf")), List.of(TipoDocumento.VALIDACAO_CPF)))
                .isInstanceOf(DocumentoObrigatorioException.class)
                .hasMessageContaining("comprove seu CPF");
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void profissionalComRegistroObrigatorioPrecisaDoDocumentoDoRegistro() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.criarPerfilProfissional(1L,
                profissionalRequest(CategoriaProfissional.ENFERMEIRO, List.of(1L), "123456", "SP"),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS))
                .isInstanceOf(DocumentoObrigatorioException.class)
                .hasMessageContaining("registro profissional");
        verify(profissionalRepository, never()).save(any());
    }

}
