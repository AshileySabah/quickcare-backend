package com.quickcare.backend.validacao;

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
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.dto.DecisaoEtapaRequest;
import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.exception.TransicaoEtapaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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

    @InjectMocks
    private ValidacaoService service;

    private Usuario usuario;
    private Profissional profissional;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(USUARIO_ID).nome("Maria Souza").cpf("52998224725").build();
        profissional = Profissional.builder()
                .id(20L)
                .usuario(usuario)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .cnpj("00000000000191")
                .registroProfissional("123456")
                .registroProfissionalUf("SP")
                .build();

        lenient().when(administradorRepository.existsByUsuarioId(ADMIN_ID)).thenReturn(true);
        lenient().when(etapaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void profissionalComCnpjERegistroPassaPorTresEtapas() {
        Documento cpf = Documento.builder().tipoDocumento(TipoDocumento.VALIDACAO_CPF).build();

        service.iniciar(profissional, Map.of(TipoDocumento.VALIDACAO_CPF, cpf));

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository, times(3)).save(etapas.capture());
        assertThat(etapas.getAllValues()).extracting(EtapaValidacao::getTipo)
                .containsExactly(TipoEtapaValidacao.IDENTIDADE, TipoEtapaValidacao.CNPJ, TipoEtapaValidacao.REGISTRO_PROFISSIONAL);
        assertThat(etapas.getAllValues()).allMatch(etapa -> etapa.getStatus() == StatusEtapa.PENDENTE);
        assertThat(etapas.getAllValues().getFirst().getDocumento()).isSameAs(cpf);
    }

    @Test
    void cuidadorSemCnpjPassaSoPelaIdentidade() {
        profissional.setCnpj(null);
        profissional.setRegistroProfissional(null);

        service.iniciar(profissional, Map.of());

        ArgumentCaptor<EtapaValidacao> etapas = ArgumentCaptor.forClass(EtapaValidacao.class);
        verify(etapaRepository).save(etapas.capture());
        assertThat(etapas.getValue().getTipo()).isEqualTo(TipoEtapaValidacao.IDENTIDADE);
    }

    @Test
    void soAdministradorDecide() {
        assertThatThrownBy(() -> service.decidir(USUARIO_ID, PerfilValidado.PROFISSIONAL, 20L,
                TipoEtapaValidacao.CNPJ, new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CNPJ ativo na Receita."), null))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void aprovarUmaEtapaComOutrasPendentesMantemCadastroPendente() {
        comEtapas(pendente(TipoEtapaValidacao.IDENTIDADE), pendente(TipoEtapaValidacao.CNPJ));

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "  CNPJ ativo na Receita.  "), null);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
    }

    @Test
    void aprovarAUltimaEtapaPendenteAprovaOCadastro() {
        EtapaValidacao identidade = aprovada(TipoEtapaValidacao.IDENTIDADE);
        EtapaValidacao cnpj = pendente(TipoEtapaValidacao.CNPJ);
        comEtapas(identidade, cnpj);

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ,
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

        service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "CNPJ baixado na Receita."), ARQUIVO);

        assertThat(profissional.getStatusValidacao()).isEqualTo(StatusValidacao.REPROVADO);
        assertThat(cnpj.getAnexoStorageKey()).isEqualTo("anexos-validacao/x.pdf");
        assertThat(cnpj.getAnexoNomeArquivo()).isEqualTo("doc.pdf");
    }

    @Test
    void naoReavaliaEtapaJaDecidida() {
        comEtapas(aprovada(TipoEtapaValidacao.CNPJ));

        assertThatThrownBy(() -> service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ,
                new DecisaoEtapaRequest(StatusEtapa.REPROVADA, "Mudei de ideia."), null))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void naoDecideEtapaQueNaoSeAplica() {
        comEtapas(pendente(TipoEtapaValidacao.IDENTIDADE));

        assertThatThrownBy(() -> service.decidir(ADMIN_ID, PerfilValidado.PROFISSIONAL, 20L, TipoEtapaValidacao.CNPJ,
                new DecisaoEtapaRequest(StatusEtapa.APROVADA, "CNPJ ativo na Receita."), null))
                .isInstanceOf(EtapaNaoEncontradaException.class);
    }

    @Test
    void soReenviaEtapaReprovada() {
        comEtapasDoUsuario(pendente(TipoEtapaValidacao.CNPJ));

        assertThatThrownBy(() -> service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ,
                reenvioCnpj("11222333000181"), ARQUIVO))
                .isInstanceOf(TransicaoEtapaInvalidaException.class);
    }

    @Test
    void reenvioDeCnpjAbreNovaAvaliacaoPendente() {
        comEtapasDoUsuario(aprovada(TipoEtapaValidacao.IDENTIDADE), reprovada(TipoEtapaValidacao.CNPJ));
        profissional.setStatusValidacao(StatusValidacao.REPROVADO);
        when(armazenamentoDocumentoService.enviar(TipoDocumento.VALIDACAO_CNPJ, ARQUIVO)).thenReturn("validacao_cnpj/novo.pdf");

        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.CNPJ, reenvioCnpj("11222333000181"), ARQUIVO);

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
                TipoDocumentoIdentidade.CNH, "02650306461", null, null, null, null);
        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE, request, ARQUIVO);

        assertThat(usuario.getNome()).isEqualTo("Maria Souza Lima");
        assertThat(usuario.getDocumentoIdentidadeTipo()).isEqualTo(TipoDocumentoIdentidade.CNH);
        assertThat(paciente.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
        verify(etapaRepository, times(2)).save(any());
    }

    @Test
    void manterNomeECpfNoReenvioNaoMexeNoOutroPerfil() {
        comEtapasDoUsuario(reprovada(TipoEtapaValidacao.IDENTIDADE));

        ReenvioEtapaRequest request = new ReenvioEtapaRequest("Maria Souza", "52998224725",
                TipoDocumentoIdentidade.RG, "24.678.131-2", "SP", null, null, null);
        service.reenviar(USUARIO_ID, PerfilValidado.PROFISSIONAL, TipoEtapaValidacao.IDENTIDADE, request, ARQUIVO);

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
        return new ReenvioEtapaRequest(null, null, null, null, null, cnpj, null, null);
    }

    private EtapaValidacao pendente(TipoEtapaValidacao tipo) {
        return EtapaValidacao.pendente(tipo, null, null, profissional);
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
