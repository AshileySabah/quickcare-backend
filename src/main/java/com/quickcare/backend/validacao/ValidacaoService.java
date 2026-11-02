package com.quickcare.backend.validacao;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.cadastro.DocumentoIdentidade;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.documento.TipoDocumento;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.dto.DecisaoEtapaRequest;
import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import com.quickcare.backend.validacao.dto.ValidacaoResumoResponse;
import com.quickcare.backend.validacao.exception.CadastroNaoEncontradoException;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.exception.TransicaoEtapaInvalidaException;
import com.quickcare.backend.validacao.exception.ValidacaoInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Validação cadastral manual: o admin aprova ou reprova cada etapa com
 * justificativa (e anexo opcional); o status do cadastro é derivado das etapas.
 * Quem foi reprovado corrige os dados da etapa e reenvia o documento.
 */
@Service
@RequiredArgsConstructor
public class ValidacaoService {

    private final EtapaValidacaoRepository etapaRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AdministradorRepository administradorRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoRepository documentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final ValidacaoResponses responses;
    private final Notificador notificador;

    // ---------------------------------------------------------------- cadastro

    /** Abre a etapa de identidade do paciente recém-cadastrado. */
    public void iniciar(Paciente paciente, Map<TipoDocumento, Documento> documentos) {
        CadastroAvaliado cadastro = CadastroAvaliado.de(paciente);
        etapaRepository.save(cadastro.novaEtapaPendente(TipoEtapaValidacao.IDENTIDADE, documentos.get(TipoDocumento.VALIDACAO_CPF)));
    }

    /** Abre as etapas que se aplicam ao profissional recém-cadastrado. */
    public void iniciar(Profissional profissional, Map<TipoDocumento, Documento> documentos) {
        CadastroAvaliado cadastro = CadastroAvaliado.de(profissional);
        etapasAplicaveis(profissional).forEach(tipo ->
                etapaRepository.save(cadastro.novaEtapaPendente(tipo, documentos.get(tipo.getTipoDocumento()))));
    }

    private List<TipoEtapaValidacao> etapasAplicaveis(Profissional profissional) {
        return Stream.of(TipoEtapaValidacao.values())
                .filter(tipo -> switch (tipo) {
                    case IDENTIDADE -> true;
                    case CNPJ -> profissional.getCnpj() != null && !profissional.getCnpj().isBlank();
                    case REGISTRO_PROFISSIONAL -> profissional.getRegistroProfissional() != null;
                })
                .toList();
    }

    // ------------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<ValidacaoResumoResponse> listar(Long adminUsuarioId, PerfilValidado perfil, StatusValidacao status) {
        exigirAdministrador(adminUsuarioId);

        List<CadastroAvaliado> cadastros = perfil == PerfilValidado.PACIENTE
                ? (status == null ? pacienteRepository.findAllByOrderByCriadoEmAsc() : pacienteRepository.findByStatusValidacaoOrderByCriadoEmAsc(status))
                        .stream().map(CadastroAvaliado::de).toList()
                : (status == null ? profissionalRepository.findAllByOrderByCriadoEmAsc() : profissionalRepository.findByStatusValidacaoOrderByCriadoEmAsc(status))
                        .stream().map(CadastroAvaliado::de).toList();

        return cadastros.stream()
                .map(cadastro -> responses.resumo(cadastro, etapas(cadastro)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ValidacaoDetalheResponse detalhar(Long adminUsuarioId, PerfilValidado perfil, Long id) {
        exigirAdministrador(adminUsuarioId);
        CadastroAvaliado cadastro = carregar(perfil, id);
        return responses.detalhe(cadastro, etapas(cadastro), true);
    }

    @Transactional
    public ValidacaoDetalheResponse decidir(
            Long adminUsuarioId,
            PerfilValidado perfil,
            Long id,
            TipoEtapaValidacao tipo,
            DecisaoEtapaRequest request,
            MultipartFile anexo
    ) {
        exigirAdministrador(adminUsuarioId);

        if (request.decisao() == StatusEtapa.PENDENTE) {
            throw new ValidacaoInvalidaException("A decisão deve ser aprovar ou reprovar.");
        }

        CadastroAvaliado cadastro = carregar(perfil, id);
        List<EtapaValidacao> todas = etapas(cadastro);
        EtapaValidacao etapa = Optional.ofNullable(EtapasAtuais.de(todas).get(tipo))
                .orElseThrow(EtapaNaoEncontradaException::new);

        if (etapa.getStatus() != StatusEtapa.PENDENTE) {
            throw new TransicaoEtapaInvalidaException("Esta etapa já foi avaliada.");
        }

        if (anexo != null && !anexo.isEmpty()) {
            etapa.setAnexoStorageKey(armazenamentoDocumentoService.enviarAnexoValidacao(anexo));
            etapa.setAnexoNomeArquivo(anexo.getOriginalFilename());
            etapa.setAnexoContentType(anexo.getContentType());
        }
        etapa.setStatus(request.decisao());
        etapa.setJustificativa(request.justificativa().trim());
        etapa.setAvaliadoPor(usuarioRepository.getReferenceById(adminUsuarioId));
        etapa.setAvaliadoEm(Instant.now());

        StatusValidacao anterior = cadastro.statusValidacao();
        recalcularStatus(cadastro, todas);
        avisarDecisao(cadastro, etapa, anterior);
        return responses.detalhe(cadastro, todas, true);
    }

    // --------------------------------------------------------- próprio usuário

    @Transactional(readOnly = true)
    public ValidacaoDetalheResponse minhaValidacao(Long usuarioId, PerfilValidado perfil) {
        CadastroAvaliado cadastro = carregarDoUsuario(usuarioId, perfil);
        return responses.detalhe(cadastro, etapas(cadastro), false);
    }

    /**
     * Corrige os dados de uma etapa reprovada e envia um novo documento, abrindo
     * uma nova avaliação pendente. Trocar nome ou CPF reabre a identidade também
     * no outro perfil do mesmo usuário, já que esses dados são compartilhados.
     */
    @Transactional
    public ValidacaoDetalheResponse reenviar(
            Long usuarioId,
            PerfilValidado perfil,
            TipoEtapaValidacao tipo,
            ReenvioEtapaRequest request,
            MultipartFile arquivo
    ) {
        CadastroAvaliado cadastro = carregarDoUsuario(usuarioId, perfil);
        List<EtapaValidacao> todas = etapas(cadastro);
        EtapaValidacao atual = Optional.ofNullable(EtapasAtuais.de(todas).get(tipo))
                .orElseThrow(EtapaNaoEncontradaException::new);

        if (atual.getStatus() != StatusEtapa.REPROVADA) {
            throw new TransicaoEtapaInvalidaException("Só é possível reenviar uma etapa reprovada.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ValidacaoInvalidaException("Envie o documento atualizado desta etapa.");
        }

        boolean identidadeAlterada = switch (tipo) {
            case IDENTIDADE -> aplicarIdentidade(cadastro.usuario(), request);
            case CNPJ -> {
                aplicarCnpj(cadastro.profissional(), request);
                yield false;
            }
            case REGISTRO_PROFISSIONAL -> {
                aplicarRegistro(cadastro.profissional(), request);
                yield false;
            }
        };

        Documento documento = salvarDocumento(tipo.getTipoDocumento(), arquivo, cadastro);
        EtapaValidacao nova = etapaRepository.save(cadastro.novaEtapaPendente(tipo, documento));
        todas.add(nova);
        recalcularStatus(cadastro, todas);

        if (identidadeAlterada) {
            outroPerfil(cadastro).ifPresent(outro -> reabrirIdentidade(outro, documento));
        }

        return responses.detalhe(cadastro, todas, false);
    }

    private boolean aplicarIdentidade(Usuario usuario, ReenvioEtapaRequest request) {
        if (request.nome() == null || request.nome().isBlank() || request.cpf() == null) {
            throw new ValidacaoInvalidaException("Informe nome e CPF.");
        }

        DocumentoIdentidade identidade = DocumentoIdentidade.validar(
                request.documentoIdentidadeTipo(), request.documentoIdentidadeNumero(), request.documentoIdentidadeUf());

        String nome = request.nome().trim();
        boolean cpfAlterado = !request.cpf().equals(usuario.getCpf());
        if (cpfAlterado && usuarioRepository.findByCpf(request.cpf()).isPresent()) {
            throw new CpfJaCadastradoException();
        }
        boolean alterada = cpfAlterado || !nome.equals(usuario.getNome());

        usuario.setNome(nome);
        usuario.setCpf(request.cpf());
        usuario.setDocumentoIdentidadeTipo(identidade.tipo());
        usuario.setDocumentoIdentidadeNumero(identidade.numero());
        usuario.setDocumentoIdentidadeUf(identidade.uf());
        return alterada;
    }

    private void aplicarCnpj(Profissional profissional, ReenvioEtapaRequest request) {
        if (request.cnpj() == null) {
            throw new ValidacaoInvalidaException("Informe o CNPJ.");
        }
        if (profissionalRepository.existsByCnpjAndIdNot(request.cnpj(), profissional.getId())) {
            throw new ValidacaoInvalidaException("CNPJ já cadastrado para outro profissional.");
        }
        profissional.setCnpj(request.cnpj());
    }

    private void aplicarRegistro(Profissional profissional, ReenvioEtapaRequest request) {
        if (request.registroProfissional() == null || request.registroProfissional().isBlank()
                || request.registroProfissionalUf() == null) {
            throw new ValidacaoInvalidaException("Informe o número e a UF do registro profissional.");
        }
        profissional.setRegistroProfissional(request.registroProfissional().trim());
        profissional.setRegistroProfissionalUf(request.registroProfissionalUf().toUpperCase(Locale.ROOT));
    }

    private Optional<CadastroAvaliado> outroPerfil(CadastroAvaliado cadastro) {
        Long usuarioId = cadastro.usuario().getId();
        return cadastro.perfil() == PerfilValidado.PACIENTE
                ? profissionalRepository.findByUsuarioId(usuarioId).map(CadastroAvaliado::de)
                : pacienteRepository.findByUsuarioId(usuarioId).map(CadastroAvaliado::de);
    }

    /** A identidade do outro perfil volta a pendente, conferida contra o novo documento. */
    private void reabrirIdentidade(CadastroAvaliado cadastro, Documento documento) {
        List<EtapaValidacao> todas = etapas(cadastro);
        EtapaValidacao atual = EtapasAtuais.de(todas).get(TipoEtapaValidacao.IDENTIDADE);
        if (atual != null && atual.getStatus() == StatusEtapa.PENDENTE) {
            return;
        }
        todas.add(etapaRepository.save(cadastro.novaEtapaPendente(TipoEtapaValidacao.IDENTIDADE, documento)));
        recalcularStatus(cadastro, todas);
    }

    private Documento salvarDocumento(TipoDocumento tipo, MultipartFile arquivo, CadastroAvaliado cadastro) {
        String storageKey = armazenamentoDocumentoService.enviar(tipo, arquivo);
        return documentoRepository.save(
                Documento.builder()
                        .paciente(cadastro.paciente())
                        .profissional(cadastro.profissional())
                        .tipoDocumento(tipo)
                        .nomeArquivo(arquivo.getOriginalFilename())
                        .contentType(arquivo.getContentType())
                        .tamanhoBytes(arquivo.getSize())
                        .storageKey(storageKey)
                        .build()
        );
    }

    // ------------------------------------------------------------------ apoio

    void exigirAdministrador(Long usuarioId) {
        if (!administradorRepository.existsByUsuarioId(usuarioId)) {
            throw new PerfilNaoAutorizadoException("Acesso restrito a administradores.");
        }
    }

    CadastroAvaliado carregar(PerfilValidado perfil, Long id) {
        return perfil == PerfilValidado.PACIENTE
                ? pacienteRepository.findById(id).map(CadastroAvaliado::de).orElseThrow(CadastroNaoEncontradoException::new)
                : profissionalRepository.findById(id).map(CadastroAvaliado::de).orElseThrow(CadastroNaoEncontradoException::new);
    }

    /** Lista mutável, em ordem de criação. */
    List<EtapaValidacao> etapas(CadastroAvaliado cadastro) {
        List<EtapaValidacao> etapas = cadastro.perfil() == PerfilValidado.PACIENTE
                ? etapaRepository.findByPacienteIdOrderByCriadoEmAscIdAsc(cadastro.id())
                : etapaRepository.findByProfissionalIdOrderByCriadoEmAscIdAsc(cadastro.id());
        return new ArrayList<>(etapas);
    }

    private CadastroAvaliado carregarDoUsuario(Long usuarioId, PerfilValidado perfil) {
        Optional<CadastroAvaliado> cadastro = perfil == PerfilValidado.PACIENTE
                ? pacienteRepository.findByUsuarioId(usuarioId).filter(p -> !p.isBanido()).map(CadastroAvaliado::de)
                : profissionalRepository.findByUsuarioId(usuarioId).filter(p -> !p.isBanido()).map(CadastroAvaliado::de);
        return cadastro.orElseThrow(() -> new PerfilNaoAutorizadoException(
                "Você não possui um perfil de " + (perfil == PerfilValidado.PACIENTE ? "paciente" : "profissional") + " ativo."));
    }

    /** Cada etapa reprovada avisa (com a justificativa); a aprovação, só quando o cadastro todo passa. */
    private void avisarDecisao(CadastroAvaliado cadastro, EtapaValidacao etapa, StatusValidacao anterior) {
        TipoPerfil perfil = cadastro.perfil() == PerfilValidado.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;

        if (etapa.getStatus() == StatusEtapa.REPROVADA) {
            notificador.etapaReprovada(cadastro.usuario(), perfil, etapa.getTipo(), etapa.getJustificativa());
        } else if (cadastro.statusValidacao() == StatusValidacao.APROVADO && anterior != StatusValidacao.APROVADO) {
            notificador.cadastroAprovado(cadastro.usuario(), perfil);
        }
    }

    private void recalcularStatus(CadastroAvaliado cadastro, List<EtapaValidacao> todas) {
        cadastro.definirStatusValidacao(EtapasAtuais.statusDo(EtapasAtuais.de(todas).values(), cadastro.statusValidacao()));
    }

}
