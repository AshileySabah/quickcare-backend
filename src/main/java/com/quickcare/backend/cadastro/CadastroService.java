package com.quickcare.backend.cadastro;

import com.quickcare.backend.administrador.AdministradorRepository;
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
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.cadastro.exception.RegistroProfissionalObrigatorioException;
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
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import com.quickcare.backend.validacao.ValidacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final DocumentoRepository documentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final PasswordEncoder passwordEncoder;
    private final GeocodificacaoService geocodificacaoService;
    private final ValidacaoService validacaoService;
    private final AdministradorRepository administradorRepository;

    @Transactional
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

        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone(), identidade, avatar);

        return criarPaciente(usuario, request.perfil(), documentosValidados);
    }

    @Transactional
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

        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone(), identidade, avatar);

        return criarProfissional(usuario, request.perfil(), documentosValidados);
    }

    /**
     * Quem já está logado cria o outro perfil sem refazer a conta. O perfil novo
     * passa pela validação completa, inclusive a identidade, mesmo que ela já
     * tenha sido aprovada no outro perfil.
     */
    @Transactional
    public CadastroResponse criarPerfilPaciente(
            Long usuarioId,
            NovoPerfilPacienteRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento
    ) {
        Usuario usuario = buscarUsuarioQuePodeTerOutroPerfil(usuarioId);
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentoCpf(documentosValidados);

        return criarPaciente(usuario, request, documentosValidados);
    }

    @Transactional
    public CadastroResponse criarPerfilProfissional(
            Long usuarioId,
            NovoPerfilProfissionalRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento
    ) {
        Usuario usuario = buscarUsuarioQuePodeTerOutroPerfil(usuarioId);
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);
        exigirDocumentosDoProfissional(request, documentosValidados);

        return criarProfissional(usuario, request, documentosValidados);
    }

    private Usuario buscarUsuarioQuePodeTerOutroPerfil(Long usuarioId) {
        if (administradorRepository.existsByUsuarioId(usuarioId)) {
            throw new PerfilNaoAutorizadoException("Administradores não podem ter perfil de paciente ou profissional.");
        }
        return usuarioRepository.findById(usuarioId).orElseThrow(TokenInvalidoException::new);
    }

    /** O perfil recém-criado passa a ser o ativo, para a pessoa já cair nele. */
    private CadastroResponse criarPaciente(Usuario usuario, NovoPerfilPacienteRequest request,
                                           List<DocumentoParaSalvar> documentosValidados) {
        if (pacienteRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("paciente");
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

        validacaoService.iniciar(paciente, salvarDocumentos(documentosValidados, paciente, null));
        usuario.setUltimoPerfil(TipoPerfil.PACIENTE);

        return new CadastroResponse(usuario.getId(), paciente.getId(), usuario.getNome(), usuario.getEmail(), "PACIENTE");
    }

    private CadastroResponse criarProfissional(Usuario usuario, NovoPerfilProfissionalRequest request,
                                               List<DocumentoParaSalvar> documentosValidados) {
        if (profissionalRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("profissional");
        }

        boolean registroObrigatorio = registroObrigatorio(request);
        List<Long> especialidadeIds = request.especialidadeIds();

        Map<Long, Especialidade> especialidadesPorId = especialidadeRepository.findAllByIdIn(especialidadeIds).stream()
                .collect(Collectors.toMap(Especialidade::getId, Function.identity()));

        if (especialidadesPorId.size() != new HashSet<>(especialidadeIds).size()) {
            throw new EspecialidadeInvalidaException();
        }

        boolean categoriasDivergentes = especialidadesPorId.values().stream()
                .anyMatch(especialidade -> especialidade.getCategoria() != request.categoria());
        if (categoriasDivergentes) {
            throw new EspecialidadeInvalidaException("As especialidades informadas não pertencem à categoria profissional selecionada");
        }

        if (request.categoria() == CategoriaProfissional.OUTRO && especialidadeIds.size() > 1) {
            throw new EspecialidadeInvalidaException("Profissionais da categoria \"Outros profissionais\" devem selecionar apenas uma especialidade");
        }

        if (registroObrigatorio && (request.registroProfissional() == null || request.registroProfissional().isBlank())) {
            throw new RegistroProfissionalObrigatorioException();
        }

        if (registroObrigatorio && (request.registroProfissionalUf() == null || request.registroProfissionalUf().isBlank())) {
            throw new RegistroProfissionalObrigatorioException();
        }

        if (request.modalidadeAtendimento() != ModalidadeAtendimento.REMOTO && request.raioAtendimentoKm() == null) {
            throw new RaioAtendimentoObrigatorioException();
        }

        Profissional novo = Profissional.builder()
                .usuario(usuario)
                .banido(false)
                .categoria(request.categoria())
                .registroProfissional(registroObrigatorio ? request.registroProfissional() : null)
                .registroProfissionalUf(registroObrigatorio ? request.registroProfissionalUf().toUpperCase() : null)
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
                .especialidades(new HashSet<>(especialidadesPorId.values()))
                .build();
        novo.definirCoordenadas(geocodificacaoService.geocodificar(novo.endereco()).orElse(null));
        Profissional profissional = profissionalRepository.save(novo);

        validacaoService.iniciar(profissional, salvarDocumentos(documentosValidados, null, profissional));
        usuario.setUltimoPerfil(TipoPerfil.PROFISSIONAL);

        return new CadastroResponse(usuario.getId(), profissional.getId(), usuario.getNome(), usuario.getEmail(), "PROFISSIONAL");
    }

    private void exigirDocumentoCpf(List<DocumentoParaSalvar> documentos) {
        if (documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CPF)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide seu CPF.");
        }
    }

    private void exigirDocumentosDoProfissional(NovoPerfilProfissionalRequest request, List<DocumentoParaSalvar> documentos) {
        exigirDocumentoCpf(documentos);

        if (temCnpj(request) && documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CNPJ)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide o CNPJ informado.");
        }

        if (registroObrigatorio(request) && documentos.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.REGISTRO_PROFISSIONAL)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide seu registro profissional.");
        }
    }

    private static boolean temCnpj(NovoPerfilProfissionalRequest request) {
        return request.cnpj() != null && !request.cnpj().isBlank();
    }

    private static boolean registroObrigatorio(NovoPerfilProfissionalRequest request) {
        return request.categoria() != CategoriaProfissional.CUIDADOR;
    }

    private record DocumentoParaSalvar(TipoDocumento tipo, MultipartFile arquivo) {
    }

    /**
     * Documento e tipo chegam como duas listas paralelas (multipart não tem um jeito
     * nativo de amarrar um arquivo a um campo companheiro), então a ordem de envio no
     * front precisa bater com a ordem aqui — cada índice i de `documentos` corresponde
     * ao tipo em `tiposDocumento[i]`.
     */
    private List<DocumentoParaSalvar> validarDocumentos(List<MultipartFile> documentos, List<TipoDocumento> tiposDocumento) {
        List<MultipartFile> arquivos = documentos == null ? Collections.emptyList() : documentos;
        List<TipoDocumento> tipos = tiposDocumento == null ? Collections.emptyList() : tiposDocumento;

        if (arquivos.size() != tipos.size()) {
            throw new ArquivoInvalidoException("Cada documento enviado precisa ter um tipo correspondente.");
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

    /** Devolve o último documento salvo de cada tipo, que é o conferido na etapa correspondente. */
    private Map<TipoDocumento, Documento> salvarDocumentos(List<DocumentoParaSalvar> documentos, Paciente paciente, Profissional profissional) {
        Map<TipoDocumento, Documento> salvos = new EnumMap<>(TipoDocumento.class);
        for (DocumentoParaSalvar documento : documentos) {
            salvos.put(documento.tipo(), salvarDocumento(documento.tipo(), documento.arquivo(), paciente, profissional));
        }
        return salvos;
    }

    private Documento salvarDocumento(TipoDocumento tipo, MultipartFile arquivo, Paciente paciente, Profissional profissional) {
        String storageKey = armazenamentoDocumentoService.enviar(tipo, arquivo);

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

    /**
     * Quem já tem conta (mesmo e-mail e CPF) e cria o outro perfil reaproveita o
     * usuário; o documento de identidade só é gravado se ainda não havia um.
     */
    private Usuario resolverUsuario(String nome, String email, String senha, String cpf, String telefone,
                                    DocumentoIdentidade identidade, MultipartFile avatar) {
        Optional<Usuario> porEmail = usuarioRepository.findByEmail(email);
        Optional<Usuario> porCpf = usuarioRepository.findByCpf(cpf);

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
            return usuario;
        }

        if (porEmail.isPresent()) {
            throw new EmailJaCadastradoException();
        }

        if (porCpf.isPresent()) {
            throw new CpfJaCadastradoException();
        }

        String avatarStorageKey = avatar != null && !avatar.isEmpty()
                ? armazenamentoDocumentoService.enviarAvatar(avatar)
                : null;

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
