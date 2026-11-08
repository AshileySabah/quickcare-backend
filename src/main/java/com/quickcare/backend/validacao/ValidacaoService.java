package com.quickcare.backend.validacao;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.AtuacaoEvento;
import com.quickcare.backend.atuacao.AtuacaoEventoRepository;
import com.quickcare.backend.atuacao.AtuacaoRepository;
import com.quickcare.backend.atuacao.TipoEventoAtuacao;
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
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.dto.DecisaoEtapaRequest;
import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import com.quickcare.backend.validacao.dto.ValidacaoResumoResponse;
import com.quickcare.backend.common.validation.Maioridade;
import com.quickcare.backend.validacao.exception.CadastroNaoEncontradoException;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.exception.TransicaoEtapaInvalidaException;
import com.quickcare.backend.validacao.exception.ValidacaoInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ValidacaoService {

    private final EtapaValidacaoRepository etapaRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AdministradorRepository administradorRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoRepository documentoRepository;
    private final AtuacaoRepository atuacaoRepository;
    private final AtuacaoEventoRepository atuacaoEventoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final ValidacaoResponses responses;
    private final Notificador notificador;

    public void iniciar(Paciente paciente, Map<TipoDocumento, Documento> documentos) {
        CadastroAvaliado cadastro = CadastroAvaliado.de(paciente);
        List.of(TipoEtapaValidacao.CPF, TipoEtapaValidacao.IDENTIDADE).forEach(tipo ->
                etapaRepository.save(cadastro.novaEtapaPendente(tipo, documentos.get(tipo.getTipoDocumento()))));
    }

    public void iniciar(Profissional profissional, Map<TipoDocumento, Documento> documentos,
                        Map<Atuacao, Documento> documentosDeRegistro) {
        CadastroAvaliado cadastro = CadastroAvaliado.de(profissional);
        List<TipoEtapaValidacao> daConta = new ArrayList<>(List.of(TipoEtapaValidacao.CPF, TipoEtapaValidacao.IDENTIDADE));
        if (profissional.getCnpj() != null && !profissional.getCnpj().isBlank()) {
            daConta.add(TipoEtapaValidacao.CNPJ);
        }
        daConta.forEach(tipo ->
                etapaRepository.save(cadastro.novaEtapaPendente(tipo, documentos.get(tipo.getTipoDocumento()))));
        profissional.atuacoesAtivas().stream()
                .filter(Atuacao::exigeRegistro)
                .forEach(atuacao -> etapaRepository.save(cadastro.novaEtapaDeRegistro(atuacao, documentosDeRegistro.get(atuacao))));
    }

    @Transactional
    public void iniciarAtuacao(Atuacao atuacao, Documento documento) {
        Profissional profissional = atuacao.getProfissional();
        CadastroAvaliado cadastro = CadastroAvaliado.de(profissional);
        List<EtapaValidacao> todas = etapas(cadastro);
        if (atuacao.exigeRegistro()) {
            todas.add(etapaRepository.save(cadastro.novaEtapaDeRegistro(atuacao, documento)));
        }
        recalcularStatus(cadastro, todas);
    }

    @Transactional
    public void recalcular(Profissional profissional) {
        CadastroAvaliado cadastro = CadastroAvaliado.de(profissional);
        recalcularStatus(cadastro, etapas(cadastro));
    }

    @Transactional(readOnly = true)
    public Map<Long, StatusEtapa> statusDosRegistros(Profissional profissional) {
        Map<Long, StatusEtapa> status = new HashMap<>();
        EtapasAtuais.de(etapas(CadastroAvaliado.de(profissional))).values().stream()
                .filter(etapa -> etapa.getAtuacao() != null)
                .forEach(etapa -> status.put(etapa.getAtuacao().getId(), etapa.getStatus()));
        return status;
    }

    @Transactional(readOnly = true)
    public List<Atuacao> atuacoesLiberadas(Profissional profissional) {
        Map<Long, StatusEtapa> registros = statusDosRegistros(profissional);
        return profissional.atuacoesAtivas().stream()
                .filter(atuacao -> liberada(atuacao, registros))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean atuacaoLiberada(Atuacao atuacao) {
        return atuacao.ativa() && liberada(atuacao, statusDosRegistros(atuacao.getProfissional()));
    }

    @Transactional(readOnly = true)
    public void exigirAtuacaoLiberada(Atuacao atuacao) {
        if (!atuacao.ativa()) {
            throw new CadastroNaoValidadoException("Você removeu a atuação " + atuacao.rotulo()
                    + " do seu perfil. As propostas dela não podem mais ser enviadas nem editadas.");
        }
        if (!atuacaoLiberada(atuacao)) {
            throw new CadastroNaoValidadoException("O seu registro de " + atuacao.rotulo() + " está em validação. "
                    + "Até o administrador concluir, você não pode enviar nem editar propostas dessa atuação; "
                    + "o que já está em andamento continua normalmente.");
        }
    }

    public static boolean liberada(Atuacao atuacao, Map<Long, StatusEtapa> registros) {
        StatusEtapa status = registros.get(atuacao.getId());
        return !atuacao.exigeRegistro() || status == null || status == StatusEtapa.APROVADA;
    }

    @Transactional(readOnly = true)
    public List<ValidacaoResumoResponse> listar(Long adminUsuarioId, PerfilValidado perfil, StatusValidacao status,
                                                boolean somenteRevalidacao) {
        exigirAdministrador(adminUsuarioId);
        if (somenteRevalidacao) {
            return listar(adminUsuarioId, perfil, StatusValidacao.APROVADO, false).stream()
                    .filter(ValidacaoResumoResponse::emRevalidacao)
                    .toList();
        }

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
            Long atuacaoId,
            DecisaoEtapaRequest request,
            MultipartFile anexo
    ) {
        exigirAdministrador(adminUsuarioId);

        if (request.decisao() == StatusEtapa.PENDENTE) {
            throw new ValidacaoInvalidaException("A decisão deve ser aprovar ou reprovar.");
        }

        CadastroAvaliado cadastro = carregar(perfil, id);
        List<EtapaValidacao> todas = etapas(cadastro);
        EtapaValidacao etapa = Optional.ofNullable(EtapasAtuais.de(todas).get(ChaveEtapa.de(tipo, atuacaoId)))
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
        avisarDecisao(cadastro, etapa, anterior, EtapasAtuais.de(todas).values());
        return responses.detalhe(cadastro, todas, true);
    }

    @Transactional(readOnly = true)
    public ValidacaoDetalheResponse minhaValidacao(Long usuarioId, PerfilValidado perfil) {
        CadastroAvaliado cadastro = carregarDoUsuario(usuarioId, perfil);
        return responses.detalhe(cadastro, etapas(cadastro), false);
    }

    @Transactional
    public ValidacaoDetalheResponse reenviar(
            Long usuarioId,
            PerfilValidado perfil,
            TipoEtapaValidacao tipo,
            Long atuacaoId,
            ReenvioEtapaRequest request,
            MultipartFile arquivo
    ) {
        CadastroAvaliado cadastro = carregarDoUsuario(usuarioId, perfil);
        List<EtapaValidacao> todas = etapas(cadastro);
        Atuacao atuacao = tipo == TipoEtapaValidacao.REGISTRO_PROFISSIONAL ? atuacaoDoCadastro(cadastro, atuacaoId) : null;
        EtapaValidacao atual = EtapasAtuais.de(todas).get(ChaveEtapa.de(tipo, atuacaoId));
        boolean novoArquivo = arquivo != null && !arquivo.isEmpty();

        if (atual == null && !(tipo == TipoEtapaValidacao.CNPJ && cadastro.profissional() != null)) {
            throw new EtapaNaoEncontradaException();
        }

        boolean contaCompartilhadaAlterada = switch (tipo) {
            case CPF -> aplicarCpf(cadastro.usuario(), request);
            case IDENTIDADE -> aplicarIdentidade(cadastro, request);
            case CNPJ -> aplicarCnpj(cadastro.profissional(), request);
            case REGISTRO_PROFISSIONAL -> aplicarRegistro(atuacao, request, novoArquivo);
        };
        Documento anterior = atual == null ? null : atual.getDocumento();
        if (!novoArquivo && anterior == null) {
            throw new ValidacaoInvalidaException("Envie o documento desta etapa.");
        }

        Documento documento = novoArquivo ? salvarDocumento(tipo.getTipoDocumento(), arquivo, cadastro) : anterior;
        if (atual != null && atual.getStatus() == StatusEtapa.PENDENTE) {
            atual.setDocumento(documento);
        } else if (atuacao != null) {
            todas.add(etapaRepository.save(cadastro.novaEtapaDeRegistro(atuacao, documento)));
        } else {
            todas.add(etapaRepository.save(cadastro.novaEtapaPendente(tipo, documento)));
        }
        recalcularStatus(cadastro, todas);

        if (contaCompartilhadaAlterada) {
            outroPerfil(cadastro).ifPresent(outro -> reabrir(outro, tipo, documento));
        }

        return responses.detalhe(cadastro, todas, false);
    }

    @Transactional(readOnly = true)
    public ArquivoBaixado meuArquivo(Long usuarioId, PerfilValidado perfil, Long etapaId, ArquivoDaEtapa arquivo) {
        return arquivoDaEtapa(carregarDoUsuario(usuarioId, perfil), etapaId, arquivo);
    }

    @Transactional(readOnly = true)
    public ArquivoBaixado arquivoParaOAdmin(Long adminUsuarioId, PerfilValidado perfil, Long id, Long etapaId,
                                           ArquivoDaEtapa arquivo) {
        exigirAdministrador(adminUsuarioId);
        return arquivoDaEtapa(carregar(perfil, id), etapaId, arquivo);
    }

    private ArquivoBaixado arquivoDaEtapa(CadastroAvaliado cadastro, Long etapaId, ArquivoDaEtapa arquivo) {
        EtapaValidacao etapa = etapas(cadastro).stream()
                .filter(candidata -> candidata.getId().equals(etapaId))
                .findFirst()
                .orElseThrow(EtapaNaoEncontradaException::new);

        if (arquivo == ArquivoDaEtapa.DOCUMENTO) {
            Documento documento = Optional.ofNullable(etapa.getDocumento()).orElseThrow(EtapaNaoEncontradaException::new);
            return new ArquivoBaixado(documento.getNomeArquivo(), documento.getContentType(),
                    armazenamentoDocumentoService.baixar(documento.getStorageKey()));
        }
        if (etapa.getAnexoStorageKey() == null) {
            throw new EtapaNaoEncontradaException();
        }
        return new ArquivoBaixado(etapa.getAnexoNomeArquivo(), etapa.getAnexoContentType(),
                armazenamentoDocumentoService.baixar(etapa.getAnexoStorageKey()));
    }

    @Transactional(readOnly = true)
    public boolean emRevalidacao(Paciente paciente) {
        return bloqueiaCriacao(CadastroAvaliado.de(paciente));
    }

    @Transactional(readOnly = true)
    public boolean emRevalidacao(Profissional profissional) {
        return bloqueiaCriacao(CadastroAvaliado.de(profissional));
    }

    @Transactional(readOnly = true)
    public void exigirLiberadoParaCriar(Paciente paciente) {
        if (emRevalidacao(paciente)) {
            throw new CadastroNaoValidadoException("Seus dados estão em revalidação. Até o administrador concluir, "
                    + "você não pode criar novas solicitações; o que já está em andamento continua normalmente.");
        }
    }

    @Transactional(readOnly = true)
    public void exigirLiberadoParaCriar(Profissional profissional) {
        if (emRevalidacao(profissional)) {
            throw new CadastroNaoValidadoException("Seus dados estão em revalidação. Até o administrador concluir, "
                    + "você não pode enviar novas propostas; o que já está em andamento continua normalmente.");
        }
    }

    boolean bloqueiaCriacao(CadastroAvaliado cadastro) {
        return ValidacaoResponses.bloqueiaCriacao(cadastro, etapas(cadastro));
    }

    private Atuacao atuacaoDoCadastro(CadastroAvaliado cadastro, Long atuacaoId) {
        if (cadastro.profissional() == null || atuacaoId == null) {
            throw new EtapaNaoEncontradaException();
        }
        return atuacaoRepository.findByIdAndProfissionalId(atuacaoId, cadastro.profissional().getId())
                .filter(Atuacao::ativa)
                .filter(Atuacao::exigeRegistro)
                .orElseThrow(EtapaNaoEncontradaException::new);
    }

    private boolean aplicarCpf(Usuario usuario, ReenvioEtapaRequest request) {
        if (request.cpf() == null) {
            throw new ValidacaoInvalidaException("Informe o CPF.");
        }
        boolean alterado = !request.cpf().equals(usuario.getCpf());
        if (alterado && usuarioRepository.findByCpf(request.cpf()).isPresent()) {
            throw new CpfJaCadastradoException();
        }
        usuario.setCpf(request.cpf());
        return alterado;
    }

    private boolean aplicarIdentidade(CadastroAvaliado cadastro, ReenvioEtapaRequest request) {
        Usuario usuario = cadastro.usuario();
        DocumentoIdentidade identidade = DocumentoIdentidade.validar(
                request.documentoIdentidadeTipo(), request.documentoIdentidadeNumero(), request.documentoIdentidadeUf());

        String nome = request.nome() == null || request.nome().isBlank() ? usuario.getNome() : request.nome().trim();
        boolean contaAlterada = !nome.equals(usuario.getNome())
                || identidade.tipo() != usuario.getDocumentoIdentidadeTipo()
                || !Objects.equals(identidade.numero(), usuario.getDocumentoIdentidadeNumero())
                || !Objects.equals(identidade.uf(), usuario.getDocumentoIdentidadeUf());

        if (request.dataNascimento() != null && !Maioridade.atingida(request.dataNascimento(), LocalDate.now())) {
            throw new ValidacaoInvalidaException(cadastro.profissional() != null
                    ? "O profissional deve ter pelo menos 18 anos."
                    : "O paciente deve ter pelo menos 18 anos.");
        }
        LocalDate dataNascimento = request.dataNascimento() == null ? cadastro.dataNascimento() : request.dataNascimento();
        Genero genero = request.genero() == null ? cadastro.genero() : request.genero();

        usuario.setNome(nome);
        usuario.setDocumentoIdentidadeTipo(identidade.tipo());
        usuario.setDocumentoIdentidadeNumero(identidade.numero());
        usuario.setDocumentoIdentidadeUf(identidade.uf());
        cadastro.definirDadosPessoais(dataNascimento, genero);
        return contaAlterada;
    }

    private boolean aplicarCnpj(Profissional profissional, ReenvioEtapaRequest request) {
        if (request.cnpj() == null) {
            throw new ValidacaoInvalidaException("Informe o CNPJ.");
        }
        if (profissionalRepository.existsByCnpjAndIdNot(request.cnpj(), profissional.getId())) {
            throw new ValidacaoInvalidaException("CNPJ já cadastrado para outro profissional.");
        }
        profissional.setCnpj(request.cnpj());
        return false;
    }

    private boolean aplicarRegistro(Atuacao atuacao, ReenvioEtapaRequest request, boolean novoArquivo) {
        if (request.registroProfissional() == null || request.registroProfissional().isBlank()
                || request.registroProfissionalUf() == null) {
            throw new ValidacaoInvalidaException("Informe o número e a UF do registro profissional.");
        }
        String numero = request.registroProfissional().trim();
        String uf = request.registroProfissionalUf().toUpperCase(Locale.ROOT);
        String antes = atuacao.getRegistroProfissional() + " / " + atuacao.getRegistroProfissionalUf();

        atuacao.setRegistroProfissional(numero);
        atuacao.setRegistroProfissionalUf(uf);
        atuacaoEventoRepository.save(AtuacaoEvento.builder()
                .atuacao(atuacao)
                .tipo(TipoEventoAtuacao.REGISTRO_ALTERADO)
                .detalhe("De " + antes + " para " + numero + " / " + uf + (novoArquivo ? ", com documento novo." : "."))
                .build());
        return false;
    }

    private Optional<CadastroAvaliado> outroPerfil(CadastroAvaliado cadastro) {
        Long usuarioId = cadastro.usuario().getId();
        return cadastro.perfil() == PerfilValidado.PACIENTE
                ? profissionalRepository.findByUsuarioId(usuarioId).map(CadastroAvaliado::de)
                : pacienteRepository.findByUsuarioId(usuarioId).map(CadastroAvaliado::de);
    }

    private void reabrir(CadastroAvaliado cadastro, TipoEtapaValidacao tipo, Documento documento) {
        List<EtapaValidacao> todas = etapas(cadastro);
        EtapaValidacao atual = EtapasAtuais.de(todas).get(ChaveEtapa.de(tipo, null));
        if (atual != null && atual.getStatus() == StatusEtapa.PENDENTE) {
            return;
        }
        todas.add(etapaRepository.save(cadastro.novaEtapaPendente(tipo, documento)));
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

    private void avisarDecisao(CadastroAvaliado cadastro, EtapaValidacao etapa, StatusValidacao anterior,
                               Collection<EtapaValidacao> atuais) {
        TipoPerfil perfil = cadastro.perfil() == PerfilValidado.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
        boolean jaAprovado = anterior == StatusValidacao.APROVADO;

        if (etapa.getStatus() == StatusEtapa.REPROVADA) {
            notificador.etapaReprovada(cadastro.usuario(), perfil, etapa.descricao(), etapa.getJustificativa());
        } else if (jaAprovado && etapa.getAtuacao() != null) {
            notificador.registroAprovado(cadastro.usuario(), etapa.getAtuacao().rotulo());
        } else if (jaAprovado && EtapasAtuais.contaAprovada(atuais)) {
            notificador.revalidacaoAprovada(cadastro.usuario(), perfil);
        } else if (cadastro.statusValidacao() == StatusValidacao.APROVADO && !jaAprovado) {
            notificador.cadastroAprovado(cadastro.usuario(), perfil);
        }
    }

    private void recalcularStatus(CadastroAvaliado cadastro, List<EtapaValidacao> todas) {
        Collection<EtapaValidacao> atuais = EtapasAtuais.de(todas).values();
        Set<Long> atuacoesComEtapa = atuais.stream()
                .filter(etapa -> etapa.getAtuacao() != null)
                .map(etapa -> etapa.getAtuacao().getId())
                .collect(Collectors.toSet());
        cadastro.definirStatusValidacao(EtapasAtuais.statusDo(atuais, cadastro.statusValidacao(),
                cadastro.profissional() != null, cadastro.temAtuacaoLiberadaSemEtapa(atuacoesComEtapa)));
    }

}
