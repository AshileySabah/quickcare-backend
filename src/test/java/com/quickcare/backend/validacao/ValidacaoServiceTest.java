package com.quickcare.backend.validacao;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.AtuacaoEventoRepository;
import com.quickcare.backend.atuacao.AtuacaoRepository;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.documento.TipoDocumento;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.dto.DecisaoEtapaRequest;
import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.exception.TransicaoEtapaInvalidaException;
import com.quickcare.backend.validacao.exception.ValidacaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidacaoServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long USUARIO_ID = 1L;
    private static final MockMultipartFile ARQUIVO =
            new MockMultipartFile("documento", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

    @Mock
    private EtapaValidacaoRepository etapaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private AdministradorRepository administradorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private ArmazenamentoDocumentoService armazenamentoDocumentoService;
    @Mock
    private ValidacaoResponses responses;
    @Mock
    private AtuacaoRepository atuacaoRepository;
    @Mock
    private AtuacaoEventoRepository atuacaoEventoRepository;

    @Mock
    private Notificador notificador;

    @InjectMocks
    private ValidacaoService service;

    private Usuario usuario;
    private Profissional profissional;
    private Atuacao enfermeiro;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(USUARIO_ID).nome("Maria Souza").cpf("52998224725").build();
        profissional = Profissional.builder()
                .id(20L)
                .usuario(usuario)
                .cnpj("00000000000191")
                .build();
        enfermeiro = atuacao(200L, CategoriaProfissional.ENFERMEIRO, "123456");
        profissional.getAtuacoes().add(enfermeiro);

        lenient().when(administradorRepository.existsByUsuarioId(ADMIN_ID)).thenReturn(true);
        lenient().when(etapaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void profissionalComCnpjERegistroPassaPorQuatroEtapas() {
        Documento cpf = Documento.builder().tipoDocumento(TipoDocumento.COMPROVANTE_CPF).build();
        Documento rg = Documento.builder().tipoDocumento(TipoDocumento.VALIDACAO_CPF).build();
        Documento coren = Documento.builder().tipoDocumento(TipoDocumento.REGISTRO_PROFISSIONAL).build();

        service.iniciar(profissional, Map.of(TipoDocumento.COMPROVANTE_CPF, cpf, TipoDocumento.VALIDACAO_CPF, rg),
                Map.of(enfermeiro, coren));

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(4)).save(etapas.capture());
        assertThat(etapas.getAllValues()).extracting(EtapaValidacao::getTipo).containsExactly(TipoEtapaValidacao.CPF,
                TipoEtapaValidacao.IDENTIDADE, TipoEtapaValidacao.CNPJ, TipoEtapaValidacao.REGISTRO_PROFISSIONAL);
        assertThat(etapas.getAllValues()).allMatch(etapa -> etapa.getStatus() == StatusEtapa.PENDENTE);
        assertThat(etapas.getAllValues().get(0).getDocumento()).isSameAs(cpf);
        assertThat(etapas.getAllValues().get(1).getDocumento()).isSameAs(rg);
        assertThat(etapas.getAllValues().get(3).getAtuacao()).isSameAs(enfermeiro);
        assertThat(etapas.getAllValues().get(3).getDocumento()).isSameAs(coren);
    }

    @Test
    void cadaAtuacaoComRegistroGanhaASuaEtapa() {
        Atuacao psicologia = atuacao(201L, CategoriaProfissional.OUTRO, "CRP 06/1234");
        Atuacao cuidador = atuacao(202L, CategoriaProfissional.CUIDADOR, null);
        profissional.getAtuacoes().addAll(List.of(psicologia, cuidador));
        profissional.setCnpj(null);

        service.iniciar(profissional, Map.of(), Map.of());

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(4)).save(etapas.capture());
        assertThat(etapas.getAllValues()).extracting(EtapaValidacao::getAtuacao)
                .containsExactly(null, null, enfermeiro, psicologia);
    }

    @Test
    void pacienteComecaPeloCpfEPeloDocumentoDeIdentidade() {
        Paciente paciente = Paciente.builder().id(30L).usuario(usuario).build();

        service.iniciar(paciente, Map.of());

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(2)).save(etapas.capture());
        assertThat(etapas.getAllValues()).extracting(EtapaValidacao::getTipo)
                .containsExactly(TipoEtapaValidacao.CPF, TipoEtapaValidacao.IDENTIDADE);
    }

    @Test
    void cuidadorSemCnpjPassaSoPelaIdentidade() {
        profissional.setCnpj(null);
        enfermeiro.setCategoria(CategoriaProfissional.CUIDADOR);

        service.iniciar(profissional, Map.of(), Map.of());

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(2)).save(etapas.capture());
        assertThat(etapas.getAllValues()).extracting(EtapaValidacao::getTipo)
                .containsExactly(TipoEtapaValidacao.CPF, TipoEtapaValidacao.IDENTIDADE);
    }

    @Test
    void soAdministradorDecide() {
        assertThatThrownBy(() -> service.decidir(USUARIO_ID, PerfilValidado.PROFISSIONAL, 20L,
                TipoEtapaValidacao.CNPJ, null, new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CNPJ ativo na Receita."), null))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void aprovarUmaEtapaComOutrasPendentesMantemCadastroPendente() {
        comEtapas(pendente(TipoEtapaValidacao.IDENTIDADE), pendente(TipoEtapaValidacao.CNPJ));

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "  CNPJ ativo na Receita.  "), null);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
    }

    @Test
    void aprovarAUltimaEtapaPendenteAprovaOCadastro() {
        EtapaValidacao identidade = aprovada(TipoEtapaValidacao.IDENTIDADE);
        EtapaValidacao cnpj = pendente(TipoEtapaValidacao.CNPJ);
        comEtapas(identidade, cnpj);

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "  CNPJ ativo na Receita.  "), null);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.APROVADO);
        assertThat(cnpj.getJustificativa()).isEqualTo("CNPJ ativo na Receita.");
        assertThat(cnpj.getAvaliadoEm()).isNotNull();
    }

    @Test
    void reprovarUmaEtapaReprovaOCadastroEGuardaOAnexo() {
        EtapaValidacao cnpj = pendente(TipoEtapaValidacao.CNPJ);
        comEtapas(pendente(TipoEtapaValidacao.IDENTIDADE), cnpj);
        when(armazenamentoDocumentoService.enviarAnexoValidacao(ARQUIVO)).thenReturn("anexos-validacao/x.pdf");

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "CNPJ baixado na Receita."), ARQUIVO);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.REPROVADO);
        assertThat(cnpj.getAnexoStorageKey()).isEqualTo("anexos-validacao/x.pdf");
        assertThat(cnpj.getAnexoNomeArquivo()).isEqualTo("doc.pdf");
    }

    @Test
    void naoReavaliaEtapaJaDecidida() {
        comEtapas(aprovada(TipoEtapaValidacao.CNPJ));

        assertThatThrownBy(() -> service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "Mudei de ideia."), null))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void naoDecideEtapaQueNaoSeAplica() {
        comEtapas(pendente(TipoEtapaValidacao.IDENTIDADE));

        assertThatThrownBy(() -> service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CNPJ ativo na Receita."), null))
                .isInstanceOf(EtapaNaoEncontradaException.class);
    }

    @Test
    void editarEtapaAindaPendenteAtualizaAMesmaAvaliacao() {
        EtapaValidacao cnpj = pendente(TipoEtapaValidacao.CNPJ);
        comEtapasDoUsuario(cnpj);
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CNPJ, ARQUIVO)).thenReturn("validacao_cnpj/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("11222333000181"), ARQUIVO);

        assertThat(profissional.getCnpj()).isEqualTo("11222333000181");
        assertThat(cnpj.getDocumento().getStorageKey()).isEqualTo("validacao_cnpj/novo.pdf");
        verify(etapaRepository, never()).save(any());
    }

    @Test
    void semMudarNadaAindaAssimReabreAEtapaComOMesmoDocumento() {
        EtapaValidacao reprovada = reprovada(TipoEtapaValidacao.CNPJ);
        Documento anterior = Documento.builder().storageKey("validacao_cnpj/antigo.pdf").build();
        reprovada.setDocumento(anterior);
        comEtapasDoUsuario(reprovada);

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("00000000000191"), null);

        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getStatus()).isEqualTo(StatusEtapa.PENDENTE);
        assertThat(nova.getValue().getDocumento()).isSameAs(anterior);
    }

    @Test
    void incluirCnpjSemDocumentoNaoEntra() {
        profissional.setCnpj(null);
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE));

        assertThatThrownBy(() -> service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null,
                reenvioCnpj("11222333000181"), null))
                .isInstanceOf(ValidacaoInvalidaException.class)
                .hasMessageContaining("Envie o documento");
        verify(etapaRepository, never()).save(any());
    }

    @Test
    void entregaODocumentoDaPropriaEtapaPeloServidorENaoOdeOutraPessoa() {
        EtapaValidacao cpf = aprovada(TipoEtapaValidacao.CPF);
        cpf.setId(7L);
        cpf.setDocumento(Documento.builder().nomeArquivo("cpf.pdf").contentType("application/pdf").storageKey("comprovante_cpf/a.pdf").build());
        comEtapasDoUsuario(cpf);
        when(armazenamentoDocumentoService.baixar("comprovante_cpf/a.pdf")).thenReturn(new byte[]{9});

        ArquivoBaixado arquivo = service.meuArquivo(USUARIO_ID, PerfilValidado.PROFISSIONAL, 7L, ArquivoDaEtapa.DOCUMENTO);

        assertThat(arquivo.nomeArquivo()).isEqualTo("cpf.pdf");
        assertThat(arquivo.conteudo()).containsExactly(9);
        assertThatThrownBy(() -> service.meuArquivo(USUARIO_ID, PerfilValidado.PROFISSIONAL, 99L, ArquivoDaEtapa.DOCUMENTO))
                .isInstanceOf(EtapaNaoEncontradaException.class);
        assertThatThrownBy(() -> service.meuArquivo(USUARIO_ID, PerfilValidado.PROFISSIONAL, 7L, ArquivoDaEtapa.ANEXO))
                .isInstanceOf(EtapaNaoEncontradaException.class);
    }

    @Test
    void mudarSoOsDadosMantemODocumentoAtual() {
        EtapaValidacao aprovada = aprovada(TipoEtapaValidacao.CNPJ);
        Documento atual = Documento.builder().storageKey("validacao_cnpj/atual.pdf").build();
        aprovada.setDocumento(atual);
        comEtapasDoUsuario(aprovada);

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("11222333000181"), null);

        assertThat(profissional.getCnpj()).isEqualTo("11222333000181");
        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getDocumento()).isSameAs(atual);
        verify(armazenamentoDocumentoService, never()).enviar(any(), any());
    }

    @Test
    void trocarSoODocumentoSemMudarDadosReabreAEtapa() {
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.CNPJ));
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CNPJ, ARQUIVO)).thenReturn("validacao_cnpj/legivel.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("00000000000191"), ARQUIVO);

        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getStatus()).isEqualTo(StatusEtapa.PENDENTE);
        assertThat(nova.getValue().getDocumento().getStorageKey()).isEqualTo("validacao_cnpj/legivel.pdf");
    }

    @Test
    void aprovadoQueEditaAIdentidadeContinuaAprovadoEVaiParaRevalidacao() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        profissional.setGenero(Genero.FEMININO);
        profissional.setDataNascimento(LocalDate.of(1990, 1, 1));
        usuario.setDocumentoIdentidadeTipo(TipoDocumentoIdentidade.CNH);
        usuario.setDocumentoIdentidadeNumero("02650306461");
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE), aprovada(TipoEtapaValidacao.CNPJ));
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CPF, ARQUIVO)).thenReturn("validacao_cpf/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE, null,
                new ReenvioEtapaRequest(null, "52998224725", TipoDocumentoIdentidade.CNH, "02650306461", null,
                        LocalDate.of(1990, 1, 2), Genero.FEMININO, null, null, null), ARQUIVO);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.APROVADO);
        assertThat(profissional.getDataNascimento()).isEqualTo(LocalDate.of(1990, 1, 2));
        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getStatus()).isEqualTo(StatusEtapa.PENDENTE);
        verify(pacienteRepository, never()).findByUsuarioId(any());
    }

    @Test
    void reenvioComProfissionalMenorDeIdadeERecusado() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        profissional.setDataNascimento(LocalDate.of(1990, 1, 1));
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE));
        LocalDate dezessete = LocalDate.now().minusYears(18).plusDays(1);

        assertThatThrownBy(() -> service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE,
                null, new ReenvioEtapaRequest(null, "52998224725", TipoDocumentoIdentidade.CNH, "02650306461", null,
                        dezessete, Genero.FEMININO, null, null, null), ARQUIVO))
                .isInstanceOf(ValidacaoInvalidaException.class)
                .hasMessage("O profissional deve ter pelo menos 18 anos.");
        assertThat(profissional.getDataNascimento()).isEqualTo(LocalDate.of(1990, 1, 1));
        verify(etapaRepository, never()).save(any());
    }

    @Test
    void emRevalidacaoQuandoAprovadoTemEtapaQueNaoEstaAprovada() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        when(etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(20L))
                .thenReturn(List.of(aprovada(TipoEtapaValidacao.IDENTIDADE), pendente(TipoEtapaValidacao.CNPJ)));

        assertThat(service.emRevalidacao(profissional)).isTrue();
        assertThatThrownBy(() -> service.exigirLiberadoParaCriar(profissional))
                .isInstanceOf(CadastroNaoValidadoException.class);
    }

    @Test
    void profissionalSemCnpjPodeIncluirUmComDocumento() {
        profissional.setCnpj(null);
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE));
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CNPJ, ARQUIVO)).thenReturn("validacao_cnpj/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("11222333000181"), ARQUIVO);

        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getTipo()).isEqualTo(TipoEtapaValidacao.CNPJ);
        assertThat(profissional.getCnpj()).isEqualTo("11222333000181");
    }

    @Test
    void aprovarAUltimaEtapaDaRevalidacaoAvisaQueARevalidacaoFoiAprovada() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        comEtapas(aprovada(TipoEtapaValidacao.IDENTIDADE), pendente(TipoEtapaValidacao.CNPJ));

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ, null,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CNPJ novo ativo na Receita."), null);

        verify(notificador).revalidacaoAprovada(usuario, TipoPerfil.PROFISSIONAL);
        verify(notificador, never()).cadastroAprovado(any(), any());
    }

    @Test
    void reenvioDeCnpjAbreNovaAvaliacaoPendente() {
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE), reprovada(TipoEtapaValidacao.CNPJ));
        profissional.setStatusValidacao(StatusValidacao.REPROVADO);
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CNPJ, ARQUIVO)).thenReturn("validacao_cnpj/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, null, reenvioCnpj("11222333000181"), ARQUIVO);

        assertThat(profissional.getCnpj()).isEqualTo("11222333000181");
        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getStatus()).isEqualTo(StatusEtapa.PENDENTE);
        assertThat(nova.getValue().getDocumento().getStorageKey()).isEqualTo("validacao_cnpj/novo.pdf");
    }

    @Test
    void trocarONomeNoReenvioReabreAIdentidadeDoOutroPerfil() {
        comEtapasDoUsuario(reprovada(TipoEtapaValidacao.IDENTIDADE));
        Paciente paciente = Paciente.builder().id(30L).usuario(usuario).statusValidacao(StatusValidacao.APROVADO).build();
        when(pacienteRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(paciente));
        when(etapaRepository.findByPacienteIdOrderByCriadoEmAscIdAsc(30L))
                .thenReturn(new ArrayList<>(List.of(aprovada(TipoEtapaValidacao.IDENTIDADE))));

        ReenvioEtapaRequest request = new ReenvioEtapaRequest("Maria Souza Lima", "52998224725",
                TipoDocumentoIdentidade.CNH, "02650306461", null, null, null, null, null, null);
        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE, null, request, ARQUIVO);

        assertThat(usuario.getNome()).isEqualTo("Maria Souza Lima");
        assertThat(usuario.getDocumentoIdentidadeTipo()).isEqualTo(TipoDocumentoIdentidade.CNH);
        assertThat(paciente.getStatusValidacao()).isEqualTo(StatusValidacao.APROVADO);
        verify(etapaRepository, times(2)).save(any());
    }

    @Test
    void trocarOCpfReabreSoOCpfAquiENoOutroPerfil() {
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE));
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        Paciente paciente = Paciente.builder().id(30L).usuario(usuario).statusValidacao(StatusValidacao.APROVADO).build();
        when(pacienteRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(paciente));
        when(etapaRepository.findByPacienteIdOrderByCriadoEmAscIdAsc(30L))
                .thenReturn(new ArrayList<>(List.of(aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE))));
        when(armazenamentoDocumentoService.enviar(TipoDocumento.COMPROVANTE_CPF, ARQUIVO)).thenReturn("comprovante_cpf/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CPF, null,
                new ReenvioEtapaRequest(null, "11144477735", null, null, null, null, null, null, null, null), ARQUIVO);

        assertThat(usuario.getCpf()).isEqualTo("11144477735");
        ArgumentCaptor<EtapaValidacao> novas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(2)).save(novas.capture());
        assertThat(novas.getAllValues()).extracting(EtapaValidacao::getTipo)
                .containsOnly(TipoEtapaValidacao.CPF);
        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.APROVADO);
    }

    @Test
    void cpfDeOutraPessoaNaoEntra() {
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.CPF));
        when(usuarioRepository.findByCpf("11144477735")).thenReturn(Optional.of(Usuario.builder().id(5L).build()));

        assertThatThrownBy(() -> service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CPF, null,
                new ReenvioEtapaRequest(null, "11144477735", null, null, null, null, null, null, null, null), ARQUIVO))
                .isInstanceOf(CpfJaCadastradoException.class);
    }

    @Test
    void manterOsDadosDaContaNoReenvioNaoMexeNoOutroPerfil() {
        usuario.setDocumentoIdentidadeTipo(TipoDocumentoIdentidade.RG);
        usuario.setDocumentoIdentidadeNumero("246781312");
        usuario.setDocumentoIdentidadeUf("SP");
        comEtapasDoUsuario(reprovada(TipoEtapaValidacao.IDENTIDADE));

        ReenvioEtapaRequest request = new ReenvioEtapaRequest("Maria Souza", "52998224725",
                TipoDocumentoIdentidade.RG, "24.678.131-2", "SP", null, null, null, null, null);
        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE, null, request, ARQUIVO);

        assertThat(usuario.getDocumentoIdentidadeNumero()).isEqualTo("246781312");
        verify(pacienteRepository, never()).findByUsuarioId(any());
    }

    private void comEtapas(EtapaValidacao... etapas) {
        when(profissionalRepository.findById(20L)).thenReturn(Optional.of(profissional));
        when(etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(20L)).thenReturn(new ArrayList<>(List.of(etapas)));
    }

    private void comEtapasDoUsuario(EtapaValidacao... etapas) {
        when(profissionalRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(profissional));
        when(etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(20L)).thenReturn(new ArrayList<>(List.of(etapas)));
    }

    private static ReenvioEtapaRequest reenvioCnpj(String cnpj) {
        return new ReenvioEtapaRequest(null, null, null, null, null, null, null, cnpj, null, null);
    }

    @Test
    void registroDeAtuacaoNovaEmAnaliseNaoTravaAContaNemTiraOAprovado() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        Atuacao medico = atuacao(201L, CategoriaProfissional.MEDICO, "98765");
        profissional.getAtuacoes().add(medico);
        when(etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(20L)).thenReturn(new ArrayList<>(List.of(
                aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE),
                registro(enfermeiro, StatusEtapa.APROVADA), registro(medico, StatusEtapa.PENDENTE))));

        assertThat(service.emRevalidacao(profissional)).isFalse();
        assertThat(service.atuacoesLiberadas(profissional)).containsExactly(enfermeiro);
        assertThat(service.atuacaoLiberada(medico)).isFalse();
    }

    @Test
    void comUmRegistroAprovadoOCadastroEhAprovadoMesmoComOutroReprovado() {
        Atuacao medico = atuacao(201L, CategoriaProfissional.MEDICO, "98765");
        profissional.getAtuacoes().add(medico);
        EtapaValidacao registroMedico = registro(medico, StatusEtapa.PENDENTE);
        comEtapas(aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE),
                aprovada(TipoEtapaValidacao.CNPJ), registro(enfermeiro, StatusEtapa.APROVADA), registroMedico);

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.REGISTRO_PROFISSIONAL, 201L,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "CRM não encontrado no portal do CFM."), null);

        assertThat(registroMedico.getStatus()).isEqualTo(StatusEtapa.REPROVADA);
        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.APROVADO);
        verify(notificador).etapaReprovada(usuario, TipoPerfil.PROFISSIONAL, "Registro profissional — Médico(a)",
                "CRM não encontrado no portal do CFM.");
    }

    @Test
    void todosOsRegistrosReprovadosReprovaOCadastro() {
        comEtapas(aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE),
                aprovada(TipoEtapaValidacao.CNPJ), registro(enfermeiro, StatusEtapa.PENDENTE));

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.REGISTRO_PROFISSIONAL, 200L,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "COREN suspenso no conselho."), null);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.REPROVADO);
    }

    @Test
    void aprovarRegistroDeAtuacaoNovaAvisaQueElaFoiLiberada() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        Atuacao medico = atuacao(201L, CategoriaProfissional.MEDICO, "98765");
        profissional.getAtuacoes().add(medico);
        comEtapas(aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE),
                registro(enfermeiro, StatusEtapa.APROVADA), registro(medico, StatusEtapa.PENDENTE));

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.REGISTRO_PROFISSIONAL, 201L,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CRM ativo no portal do CFM."), null);

        verify(notificador).registroAprovado(usuario, "Médico(a)");
        verify(notificador, never()).revalidacaoAprovada(any(), any());
    }

    @Test
    void editarORegistroDeUmaAtuacaoAbreNovaEtapaSoDela() {
        profissional.setStatusValidacao(StatusValidacao.APROVADO);
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.CPF), registro(enfermeiro, StatusEtapa.APROVADA));
        when(atuacaoRepository.findByIdAndProfissionalId(200L, 20L)).thenReturn(Optional.of(enfermeiro));
        when(armazenamentoDocumentoService.enviar(TipoDocumento.REGISTRO_PROFISSIONAL, ARQUIVO)).thenReturn("registro/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.REGISTRO_PROFISSIONAL, 200L,
                new ReenvioEtapaRequest(null, null, null, null, null, null, null, null, "654321", "rj"), ARQUIVO);

        assertThat(enfermeiro.getRegistroProfissional()).isEqualTo("654321");
        assertThat(enfermeiro.getRegistroProfissionalUf()).isEqualTo("RJ");
        ArgumentCaptor<EtapaValidacao> nova = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(nova.capture());
        assertThat(nova.getValue().getAtuacao()).isSameAs(enfermeiro);
        assertThat(nova.getValue().getStatus()).isEqualTo(StatusEtapa.PENDENTE);
        verify(atuacaoEventoRepository).save(any());
        assertThat(service.emRevalidacao(profissional)).isFalse();
    }

    @Test
    void etapasDeAtuacaoRemovidaNaoContam() {
        Atuacao medico = atuacao(201L, CategoriaProfissional.MEDICO, "98765");
        medico.setRemovidoEm(java.time.Instant.parse("2026-11-01T10:00:00Z"));
        profissional.getAtuacoes().add(medico);
        when(etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(20L)).thenReturn(new ArrayList<>(List.of(
                aprovada(TipoEtapaValidacao.CPF), aprovada(TipoEtapaValidacao.IDENTIDADE),
                registro(enfermeiro, StatusEtapa.PENDENTE), registro(medico, StatusEtapa.APROVADA))));

        service.recalcular(profissional);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
    }

    private Atuacao atuacao(Long id, CategoriaProfissional categoria, String registro) {
        return Atuacao.builder()
                .id(id)
                .profissional(profissional)
                .categoria(categoria)
                .registroProfissional(registro)
                .registroProfissionalUf(registro == null ? null : "SP")
                .build();
    }

    private EtapaValidacao registro(Atuacao atuacao, StatusEtapa status) {
        EtapaValidacao etapa = EtapaValidacao.pendente(TipoEtapaValidacao.REGISTRO_PROFISSIONAL, null, null, profissional, atuacao);
        etapa.setStatus(status);
        return etapa;
    }

    private EtapaValidacao pendente(TipoEtapaValidacao tipo) {
        return EtapaValidacao.pendente(tipo, null, null, profissional, null);
    }

    private EtapaValidacao aprovada(TipoEtapaValidacao tipo) {
        EtapaValidacao etapa = pendente(tipo);
        etapa.setStatus(StatusEtapa.APROVADA);
        return etapa;
    }

    private EtapaValidacao reprovada(TipoEtapaValidacao tipo) {
        EtapaValidacao etapa = pendente(tipo);
        etapa.setStatus(StatusEtapa.REPROVADA);
        return etapa;
    }

}
