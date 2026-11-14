package com.quickcare.backend.cadastro;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.AtuacaoEvento;
import com.quickcare.backend.atuacao.AtuacaoEventoRepository;
import com.quickcare.backend.atuacao.AtuacaoRepository;
import com.quickcare.backend.atuacao.MontagemAtuacoes;
import com.quickcare.backend.atuacao.TipoEventoAtuacao;
import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilPacienteRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilProfissionalRequest;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.cadastro.exception.CredenciaisInvalidasException;
import com.quickcare.backend.cadastro.exception.DadosCadastraisConflitantesException;
import com.quickcare.backend.cadastro.exception.EmailJaCadastradoException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.ArquivoInvalidoException;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.documento.DocumentoObrigatorioException;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.documento.TipoDocumento;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.geolocalizacao.EnderecoGeocodificavel;
import com.quickcare.backend.geolocalizacao.GeocodificacaoService;
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.EncerramentoService;
import com.quickcare.backend.seguranca.HerancaPorDocumentoService;
import com.quickcare.backend.seguranca.exception.ContaEncerradaException;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.ValidacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final DocumentoRepository documentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final PasswordEncoder passwordEncoder;
    private final GeocodificacaoService geocodificacaoService;
    private final ValidacaoService validacaoService;
    private final AdministradorRepository administradorRepository;
    private final MontagemAtuacoes montagemAtuacoes;
    private final AtuacaoRepository atuacaoRepository;
    private final AtuacaoEventoRepository atuacaoEventoRepository;
    private final HerancaPorDocumentoService herancaPorDocumentoService;
    private final PlatformTransactionManager transactionManager;

    private static final ExecutorService ENVIOS = Executors.newFixedThreadPool(6, tarefa -> {
        Thread thread = new Thread(tarefa, "cadastro-envios");
        thread.setDaemon(true);
        return thread;
    });
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public CadastroResponse cadastrarPaciente(
            CadastroPacienteRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento,
            MultipartFile avatar
    ) {
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentoCpf(documentosValidados);

        DocumentoIdentidade identidade = DocumentoIdentidade.validar(
                request.documentoIdentidadeTipo(), request.documentoIdentidadeNumero(), request.documentoIdentidadeUf());

        ArquivosEnviados enviados = enviarArquivos(documentosValidados, avatar);
        return emTransacao(enviados, () -> {
            Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(),
                    request.telefone(), identidade, enviados.avatar());
            return criarPaciente(usuario, request.perfil(), documentosValidados, enviados);
        });
    }

    public CadastroResponse cadastrarProfissional(
            CadastroProfissionalRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento,
            MultipartFile avatar
    ) {
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentosDoProfissional(request.perfil(), documentosValidados);

        DocumentoIdentidade identidade = DocumentoIdentidade.validar(
                request.documentoIdentidadeTipo(), request.documentoIdentidadeNumero(), request.documentoIdentidadeUf());

        CompletableFuture<Optional<Coordenadas>> coordenadas = geocodificarEmParalelo(request.perfil());
        ArquivosEnviados enviados = enviarArquivos(documentosValidados, avatar);
        Optional<Coordenadas> coordenadasProntas = coordenadas.join();
        return emTransacao(enviados, () -> {
            Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(),
                    request.telefone(), identidade, enviados.avatar());
            return criarProfissional(usuario, request.perfil(), documentosValidados, enviados, coordenadasProntas);
        });
    }

    public CadastroResponse criarPerfilPaciente(
            Long usuarioId,
            NovoPerfilPacienteRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento
    ) {
        buscarUsuarioQuePodeTerOutroPerfil(usuarioId);
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentoCpf(documentosValidados);

        ArquivosEnviados enviados = enviarArquivos(documentosValidados, null);
        return emTransacao(enviados, () ->
                criarPaciente(buscarUsuarioQuePodeTerOutroPerfil(usuarioId), request, documentosValidados, enviados));
    }

    public CadastroResponse criarPerfilProfissional(
            Long usuarioId,
            NovoPerfilProfissionalRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento
    ) {
        buscarUsuarioQuePodeTerOutroPerfil(usuarioId);
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentosDoProfissional(request, documentosValidados);

        CompletableFuture<Optional<Coordenadas>> coordenadas = geocodificarEmParalelo(request);
        ArquivosEnviados enviados = enviarArquivos(documentosValidados, null);
        Optional<Coordenadas> coordenadasProntas = coordenadas.join();
        return emTransacao(enviados, () -> criarProfissional(buscarUsuarioQuePodeTerOutroPerfil(usuarioId), request,
                documentosValidados, enviados, coordenadasProntas));
    }

    private record ArquivosEnviados(Map<DocumentoParaSalvar, String> documentos, String avatar) {
    }

    private ArquivosEnviados enviarArquivos(List<DocumentoParaSalvar> documentos, MultipartFile avatar) {
        Map<DocumentoParaSalvar, CompletableFuture<String>> envios = new LinkedHashMap<>();
        documentos.forEach(documento -> envios.put(documento, CompletableFuture.supplyAsync(
                () -> armazenamentoDocumentoService.enviar(documento.tipo(), documento.arquivo()), ENVIOS)));
        CompletableFuture<String> envioDoAvatar = avatar != null && !avatar.isEmpty()
                ? CompletableFuture.supplyAsync(() -> armazenamentoDocumentoService.enviarAvatar(avatar), ENVIOS)
                : CompletableFuture.completedFuture(null);

        List<CompletableFuture<String>> todos = new ArrayList<>(envios.values());
        todos.add(envioDoAvatar);
        try {
            CompletableFuture.allOf(todos.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ex) {
            todos.stream()
                    .filter(envio -> !envio.isCompletedExceptionally())
                    .map(CompletableFuture::join)
                    .forEach(armazenamentoDocumentoService::excluir);
            throw ex.getCause() instanceof RuntimeException causa ? causa : ex;
        }

        Map<DocumentoParaSalvar, String> chaves = new HashMap<>();
        envios.forEach((documento, envio) -> chaves.put(documento, envio.join()));
        return new ArquivosEnviados(chaves, envioDoAvatar.join());
    }

    private CadastroResponse emTransacao(ArquivosEnviados enviados, Supplier<CadastroResponse> passos) {
        try {
            return new TransactionTemplate(transactionManager).execute(status -> passos.get());
        } catch (RuntimeException ex) {
            enviados.documentos().values().forEach(armazenamentoDocumentoService::excluir);
            armazenamentoDocumentoService.excluir(enviados.avatar());
            throw ex;
        }
    }

    private CompletableFuture<Optional<Coordenadas>> geocodificarEmParalelo(NovoPerfilProfissionalRequest request) {
        EnderecoGeocodificavel endereco = new EnderecoGeocodificavel(request.cep(), request.logradouro(), request.numero(),
                request.cidade(), request.uf() == null ? null : request.uf().toUpperCase());
        return CompletableFuture.supplyAsync(() -> geocodificacaoService.geocodificar(endereco), ENVIOS);
    }

    private Usuario buscarUsuarioQuePodeTerOutroPerfil(Long usuarioId) {
        if (administradorRepository.existsByUsuarioId(usuarioId)) {
            throw new PerfilNaoAutorizadoException("Administradores não podem ter perfil de paciente ou profissional.");
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> !usuario.contaEncerrada())
                .orElseThrow(TokenInvalidoException::new);
    }

    private CadastroResponse criarPaciente(Usuario usuario, NovoPerfilPacienteRequest request,
                                           List<DocumentoParaSalvar> documentosValidados, ArquivosEnviados enviados) {
        if (pacienteRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("paciente");
        }
        if (pacienteRepository.findCongeladoDoTitular(usuario.getId()).isPresent()) {
            throw new TransicaoStatusInvalidaException("Você encerrou seu perfil de paciente há menos de "
                    + EncerramentoService.PRAZO_RECUPERACAO.toDays() + " dias. Recupere-o na tela de Segurança.");
        }

        Paciente paciente = pacienteRepository.save(
                Paciente.builder()
                        .usuario(usuario)
                        .banido(false)
                        .dataNascimento(request.dataNascimento())
                        .genero(request.genero())
                        .contatosEmergencia(mapContatos(request.contatosEmergencia()))
                        .confirmacaoInformacoesVerdadeiras(request.confirmacaoInformacoesVerdadeiras())
                        .consentimentoLgpd(request.consentimentoLgpd())
                        .alergias(request.alergias())
                        .condicoesSaude(request.condicoesSaude())
                        .medicamentosEmUso(request.medicamentosEmUso())
                        .cep(request.cep())
                        .logradouro(request.logradouro())
                        .numero(request.numero())
                        .complemento(request.complemento())
                        .bairro(request.bairro())
                        .cidade(request.cidade())
                        .uf(request.uf().toUpperCase())
                        .build()
        );

        validacaoService.iniciar(paciente, salvarDocumentos(documentosValidados, enviados, paciente, null));
        usuario.setUltimoPerfil(TipoPerfil.PACIENTE);
        herancaPorDocumentoService.herdar(usuario, paciente);

        return new CadastroResponse(usuario.getId(), paciente.getId(), usuario.getNome(), usuario.getEmail(), "PACIENTE");
    }

    private CadastroResponse criarProfissional(Usuario usuario, NovoPerfilProfissionalRequest request,
                                               List<DocumentoParaSalvar> documentosValidados, ArquivosEnviados enviados,
                                               Optional<Coordenadas> coordenadas) {
        if (profissionalRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("profissional");
        }
        if (profissionalRepository.findCongeladoDoTitular(usuario.getId()).isPresent()) {
            throw new TransicaoStatusInvalidaException("Você encerrou seu perfil de profissional há menos de "
                    + EncerramentoService.PRAZO_RECUPERACAO.toDays() + " dias. Recupere-o na tela de Segurança.");
        }

        if (request.modalidadeAtendimento() != ModalidadeAtendimento.REMOTO && request.raioAtendimentoKm() == null) {
            throw new RaioAtendimentoObrigatorioException();
        }

        Profissional novo = Profissional.builder()
                .usuario(usuario)
                .banido(false)
                .dataNascimento(request.dataNascimento())
                .genero(request.genero())
                .contatosEmergencia(mapContatos(request.contatosEmergencia()))
                .confirmacaoInformacoesVerdadeiras(request.confirmacaoInformacoesVerdadeiras())
                .consentimentoLgpd(request.consentimentoLgpd())
                .possuiSeguroResponsabilidadeCivil(request.possuiSeguroResponsabilidadeCivil())
                .modalidadeAtendimento(request.modalidadeAtendimento())
                .raioAtendimentoKm(
                        request.modalidadeAtendimento() == ModalidadeAtendimento.REMOTO
                                ? null
                                : request.raioAtendimentoKm()
                )
                .cnpj(temCnpj(request) ? request.cnpj() : null)
                .cep(request.cep())
                .logradouro(request.logradouro())
                .numero(request.numero())
                .complemento(request.complemento())
                .bairro(request.bairro())
                .cidade(request.cidade())
                .uf(request.uf().toUpperCase())
                .build();
        List<Atuacao> atuacoes = montagemAtuacoes.montar(novo, request.atuacoes(), List.of());
        novo.definirCoordenadas(coordenadas.orElse(null));
        Profissional profissional = profissionalRepository.save(novo);

        atuacoes.forEach(atuacao -> atuacao.setProfissional(profissional));
        profissional.getAtuacoes().addAll(atuacaoRepository.saveAll(atuacoes));
        atuacoes.forEach(atuacao -> atuacaoEventoRepository.save(AtuacaoEvento.builder()
                .atuacao(atuacao)
                .tipo(TipoEventoAtuacao.INCLUIDA)
                .detalhe("No cadastro do perfil.")
                .build()));

        Map<TipoDocumento, Documento> documentosDaConta = salvarDocumentos(
                documentosValidados.stream().filter(doc -> doc.tipo() != TipoDocumento.REGISTRO_PROFISSIONAL).toList(),
                enviados, null, profissional);
        List<Atuacao> comRegistro = atuacoes.stream().filter(Atuacao::exigeRegistro).toList();
        List<DocumentoParaSalvar> registros = documentosValidados.stream()
                .filter(doc -> doc.tipo() == TipoDocumento.REGISTRO_PROFISSIONAL)
                .toList();
        Map<Atuacao, Documento> documentosDeRegistro = new HashMap<>();
        for (int i = 0; i < comRegistro.size(); i++) {
            DocumentoParaSalvar registro = registros.get(i);
            documentosDeRegistro.put(comRegistro.get(i), salvarDocumento(registro, enviados, null, profissional));
        }

        validacaoService.iniciar(profissional, documentosDaConta, documentosDeRegistro);
        usuario.setUltimoPerfil(TipoPerfil.PROFISSIONAL);
        herancaPorDocumentoService.herdar(usuario, profissional);

        return new CadastroResponse(usuario.getId(), profissional.getId(), usuario.getNome(), usuario.getEmail(), "PROFISSIONAL");
    }

    private void exigirDocumentoCpf(List<DocumentoParaSalvar> documentos) {
        if (documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.COMPROVANTE_CPF)) {
            throw new DocumentoObrigatorioException("Envie um documento que comprove seu CPF.");
        }
        if (documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CPF)) {
            throw new DocumentoObrigatorioException("Envie seu documento de identidade (RG ou CNH).");
        }
    }

    private void exigirDocumentosDoProfissional(NovoPerfilProfissionalRequest request, List<DocumentoParaSalvar> documentos) {
        exigirDocumentoCpf(documentos);

        if (temCnpj(request) && documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CNPJ)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide o CNPJ informado.");
        }

        long registrosExigidos = request.atuacoes().stream()
                .filter(atuacao -> Atuacao.exigeRegistro(atuacao.categoria()))
                .count();
        long registrosEnviados = documentos.stream().filter(doc -> doc.tipo() == TipoDocumento.REGISTRO_PROFISSIONAL).count();
        if (registrosEnviados != registrosExigidos) {
            throw new DocumentoObrigatorioException("Envie um documento que valide o registro profissional de cada atuação.");
        }
    }

    private static boolean temCnpj(NovoPerfilProfissionalRequest request) {
        return request.cnpj() != null && !request.cnpj().isBlank();
    }

    private record DocumentoParaSalvar(TipoDocumento tipo, MultipartFile arquivo) {
    }

    private List<DocumentoParaSalvar> validarDocumentos(List<MultipartFile> documentos, List<TipoDocumento> tiposDocumento) {
        List<MultipartFile> arquivos = documentos == null ? Collections.emptyList() : documentos;
        List<TipoDocumento> tipos = tiposDocumento == null ? Collections.emptyList() : tiposDocumento;

        if (arquivos.size() != tipos.size()) {
            throw new ArquivoInvalidoException("Cada documento enviado precisa ter um tipo correspondente.");
        }

        if (tipos.stream().anyMatch(Objects::isNull)) {
            throw new ArquivoInvalidoException("Escolha a que se refere cada documento enviado.");
        }

        List<DocumentoParaSalvar> resultado = new ArrayList<>();
        for (int i = 0; i < arquivos.size(); i++) {
            MultipartFile arquivo = arquivos.get(i);
            if (arquivo != null && !arquivo.isEmpty()) {
                resultado.add(new DocumentoParaSalvar(tipos.get(i), arquivo));
            }
        }

        return resultado;
    }

    private List<ContatoEmergencia> mapContatos(List<ContatoEmergenciaRequest> contatos) {
        if (contatos == null) {
            return new ArrayList<>();
        }

        return contatos.stream()
                .map(contato -> new ContatoEmergencia(contato.nome(), contato.telefone()))
                .toList();
    }

    private Map<TipoDocumento, Documento> salvarDocumentos(List<DocumentoParaSalvar> documentos, ArquivosEnviados enviados,
                                                           Paciente paciente, Profissional profissional) {
        Map<TipoDocumento, Documento> salvos = new EnumMap<>(TipoDocumento.class);
        for (DocumentoParaSalvar documento : documentos) {
            salvos.put(documento.tipo(), salvarDocumento(documento, enviados, paciente, profissional));
        }
        return salvos;
    }

    private Documento salvarDocumento(DocumentoParaSalvar documento, ArquivosEnviados enviados, Paciente paciente,
                                      Profissional profissional) {
        TipoDocumento tipo = documento.tipo();
        MultipartFile arquivo = documento.arquivo();
        String storageKey = enviados.documentos().get(documento);

        return documentoRepository.save(
                Documento.builder()
                        .paciente(paciente)
                        .profissional(profissional)
                        .tipoDocumento(tipo)
                        .nomeArquivo(arquivo.getOriginalFilename())
                        .contentType(arquivo.getContentType())
                        .tamanhoBytes(arquivo.getSize())
                        .storageKey(storageKey)
                        .build()
        );
    }

    private Usuario resolverUsuario(String nome, String email, String senha, String cpf, String telefone,
                                    DocumentoIdentidade identidade, String avatarStorageKey) {
        Optional<Usuario> porEmail = usuarioRepository.findByEmail(email);
        Optional<Usuario> porCpf = usuarioRepository.findByCpf(cpf);

        porEmail.or(() -> porCpf).filter(Usuario::contaEncerrada).ifPresent(encerrada -> {
            throw new ContaEncerradaException("Existe uma conta encerrada com estes dados. Até "
                    + DIA.format(EncerramentoService.excluiEm(encerrada.getEncerradoEm()).atZone(FUSO))
                    + " ela pode ser recuperada: entre com seu e-mail e senha.");
        });

        if (porEmail.isPresent() && porCpf.isPresent()) {
            if (!porEmail.get().getId().equals(porCpf.get().getId())) {
                throw new DadosCadastraisConflitantesException();
            }

            Usuario usuario = porEmail.get();
            if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
                throw new CredenciaisInvalidasException();
            }

            if (usuario.getDocumentoIdentidadeNumero() == null) {
                aplicarIdentidade(usuario, identidade);
            }
            armazenamentoDocumentoService.excluir(avatarStorageKey);
            return usuario;
        }

        if (porEmail.isPresent()) {
            throw new EmailJaCadastradoException();
        }

        if (porCpf.isPresent()) {
            throw new CpfJaCadastradoException();
        }

        Usuario novo = Usuario.builder()
                .nome(nome)
                .email(email)
                .senhaHash(passwordEncoder.encode(senha))
                .cpf(cpf)
                .telefone(telefone)
                .avatarStorageKey(avatarStorageKey)
                .build();
        aplicarIdentidade(novo, identidade);
        return usuarioRepository.save(novo);
    }

    private void aplicarIdentidade(Usuario usuario, DocumentoIdentidade identidade) {
        usuario.setDocumentoIdentidadeTipo(identidade.tipo());
        usuario.setDocumentoIdentidadeNumero(identidade.numero());
        usuario.setDocumentoIdentidadeUf(identidade.uf());
    }

}
