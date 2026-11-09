package com.quickcare.backend.cadastro;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.AtuacaoEventoRepository;
import com.quickcare.backend.atuacao.AtuacaoRepository;
import com.quickcare.backend.atuacao.MontagemAtuacoes;
import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilPacienteRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilProfissionalRequest;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.ArquivoInvalidoException;
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
import com.quickcare.backend.seguranca.HerancaPorDocumentoService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock private AtuacaoRepository atuacaoRepository;
    @Mock private AtuacaoEventoRepository atuacaoEventoRepository;
    @Mock private HerancaPorDocumentoService herancaPorDocumentoService;

    private CadastroService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new CadastroService(usuarioRepository, pacienteRepository, profissionalRepository, documentoRepository,
                armazenamentoDocumentoService, passwordEncoder, geocodificacaoService, validacaoService,
                administradorRepository, new MontagemAtuacoes(especialidadeRepository), atuacaoRepository,
                atuacaoEventoRepository, herancaPorDocumentoService);
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
        return profissionalRequest(List.of(new AtuacaoRequest(categoria, especialidadeIds, registro, registroUf)));
    }

    private static NovoPerfilProfissionalRequest profissionalRequest(List<AtuacaoRequest> atuacoes) {
        return new NovoPerfilProfissionalRequest(null, atuacoes,
                LocalDate.of(1990, 1, 1), Genero.FEMININO, List.of(), true, true, false,
                ModalidadeAtendimento.PRESENCIAL, BigDecimal.TEN,
                "01310100", "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "sp");
    }

    private void profissionalSalvo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(profissionalRepository.existsByUsuarioId(1L)).thenReturn(false);
        when(geocodificacaoService.geocodificar(any())).thenReturn(Optional.empty());
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(atuacaoRepository.saveAll(anyList())).thenAnswer(invocacao -> invocacao.getArgument(0));
        documentosSalvos();
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
        profissionalSalvo();
        when(especialidadeRepository.findAllByIdIn(List.of(7L))).thenReturn(List.of(cuidadoIdosos));

        service.criarPerfilProfissional(1L, profissionalRequest(CategoriaProfissional.CUIDADOR, List.of(7L), null, null),
                DOCUMENTOS_PESSOAIS, TIPOS_PESSOAIS);

        ArgumentCaptor<Profissional> profissional = ArgumentCaptor.forClass(Profissional.class);
        verify(validacaoService).iniciar(profissional.capture(), anyMap(), anyMap());
        assertThat(profissional.getValue().getUsuario()).isSameAs(usuario);
        assertThat(profissional.getValue().atuacoesAtivas()).singleElement().satisfies(atuacao -> {
            assertThat(atuacao.getCategoria()).isEqualTo(CategoriaProfissional.CUIDADOR);
            assertThat(atuacao.getEspecialidades()).containsExactly(cuidadoIdosos);
        });
        assertThat(usuario.getUltimoPerfil()).isEqualTo(TipoPerfil.PROFISSIONAL);
    }

    @Test
    void cadastraVariasAtuacoesCadaUmaComSeuDocumentoDeRegistro() {
        Especialidade curativos = Especialidade.builder().id(5L).nome("Curativos").categoria(CategoriaProfissional.ENFERMEIRO).build();
        Especialidade psicologia = Especialidade.builder().id(18L).nome("Psicologia").categoria(CategoriaProfissional.OUTRO).build();
        Especialidade nutricao = Especialidade.builder().id(21L).nome("Nutrição").categoria(CategoriaProfissional.OUTRO).build();
        profissionalSalvo();
        when(especialidadeRepository.findAllByIdIn(List.of(5L))).thenReturn(List.of(curativos));
        when(especialidadeRepository.findAllByIdIn(List.of(18L))).thenReturn(List.of(psicologia));
        when(especialidadeRepository.findAllByIdIn(List.of(21L))).thenReturn(List.of(nutricao));

        service.criarPerfilProfissional(1L, profissionalRequest(List.of(
                        new AtuacaoRequest(CategoriaProfissional.ENFERMEIRO, List.of(5L), "123456", "sp"),
                        new AtuacaoRequest(CategoriaProfissional.OUTRO, List.of(18L), "CRP 06/1234", "SP"),
                        new AtuacaoRequest(CategoriaProfissional.OUTRO, List.of(21L), "CRN-3 4321", "SP"))),
                List.of(arquivo("cpf.pdf"), arquivo("rg.pdf"), arquivo("coren.pdf"), arquivo("crp.pdf"), arquivo("crn.pdf")),
                List.of(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF, TipoDocumento.REGISTRO_PROFISSIONAL,
                        TipoDocumento.REGISTRO_PROFISSIONAL, TipoDocumento.REGISTRO_PROFISSIONAL));

        ArgumentCaptor<Profissional> profissional = ArgumentCaptor.forClass(Profissional.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Atuacao, Documento>> registros = ArgumentCaptor.forClass(Map.class);
        verify(validacaoService).iniciar(profissional.capture(), anyMap(), registros.capture());

        List<Atuacao> atuacoes = profissional.getValue().atuacoesAtivas();
        assertThat(atuacoes).extracting(Atuacao::rotulo).containsExactly("Enfermeiro(a)", "Psicologia", "Nutrição");
        assertThat(atuacoes.getFirst().getRegistroProfissionalUf()).isEqualTo("SP");
        assertThat(registros.getValue()).hasSize(3);
        assertThat(registros.getValue().get(atuacoes.get(1)).getNomeArquivo()).isEqualTo("crp.pdf");
        verify(atuacaoEventoRepository, times(3)).save(any());
    }

    @Test
    void naoRepeteAMesmaEspecialidadeDeOutrosProfissionais() {
        Especialidade psicologia = Especialidade.builder().id(18L).nome("Psicologia").categoria(CategoriaProfissional.OUTRO).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(profissionalRepository.existsByUsuarioId(1L)).thenReturn(false);
        when(especialidadeRepository.findAllByIdIn(List.of(18L))).thenReturn(List.of(psicologia));

        assertThatThrownBy(() -> service.criarPerfilProfissional(1L, profissionalRequest(List.of(
                        new AtuacaoRequest(CategoriaProfissional.OUTRO, List.of(18L), "CRP 1", "SP"),
                        new AtuacaoRequest(CategoriaProfissional.OUTRO, List.of(18L), "CRP 2", "SP"))),
                List.of(arquivo("cpf.pdf"), arquivo("rg.pdf"), arquivo("crp.pdf"), arquivo("crp2.pdf")),
                List.of(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF, TipoDocumento.REGISTRO_PROFISSIONAL,
                        TipoDocumento.REGISTRO_PROFISSIONAL)))
                .isInstanceOf(EspecialidadeInvalidaException.class)
                .hasMessageContaining("Psicologia");
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void naoRepeteACategoria() {
        Especialidade curativos = Especialidade.builder().id(5L).nome("Curativos").categoria(CategoriaProfissional.ENFERMEIRO).build();
        Especialidade sondagem = Especialidade.builder().id(6L).nome("Sondagem").categoria(CategoriaProfissional.ENFERMEIRO).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(profissionalRepository.existsByUsuarioId(1L)).thenReturn(false);
        when(especialidadeRepository.findAllByIdIn(List.of(5L))).thenReturn(List.of(curativos));
        when(especialidadeRepository.findAllByIdIn(List.of(6L))).thenReturn(List.of(sondagem));

        assertThatThrownBy(() -> service.criarPerfilProfissional(1L, profissionalRequest(List.of(
                        new AtuacaoRequest(CategoriaProfissional.ENFERMEIRO, List.of(5L), "1", "SP"),
                        new AtuacaoRequest(CategoriaProfissional.ENFERMEIRO, List.of(6L), "2", "SP"))),
                List.of(arquivo("cpf.pdf"), arquivo("rg.pdf"), arquivo("a.pdf"), arquivo("b.pdf")),
                List.of(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF, TipoDocumento.REGISTRO_PROFISSIONAL,
                        TipoDocumento.REGISTRO_PROFISSIONAL)))
                .isInstanceOf(EspecialidadeInvalidaException.class)
                .hasMessageContaining("Inclua as especialidades nela");
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
    void recusaDocumentoSemTipo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.criarPerfilPaciente(1L, pacienteRequest(),
                List.of(arquivo("cpf.pdf"), arquivo("rg.pdf"), arquivo("extra.pdf")),
                Arrays.asList(TipoDocumento.COMPROVANTE_CPF, TipoDocumento.VALIDACAO_CPF, null)))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("Escolha a que se refere cada documento enviado.");
        verify(armazenamentoDocumentoService, never()).enviar(any(), any());
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
