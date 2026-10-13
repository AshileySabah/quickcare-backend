package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
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
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
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

    @Transactional
    public CadastroResponse cadastrarPaciente(
            CadastroPacienteRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento,
            MultipartFile avatar
    ) {
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);

        if (documentosValidados.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CPF)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide seu CPF.");
        }

        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone(), avatar);

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

        for (DocumentoParaSalvar documento : documentosValidados) {
            salvarDocumento(documento.tipo(), documento.arquivo(), paciente, null);
        }

        return new CadastroResponse(usuario.getId(), paciente.getId(), usuario.getNome(), usuario.getEmail(), "PACIENTE");
    }

    @Transactional
    public CadastroResponse cadastrarProfissional(
            CadastroProfissionalRequest request,
            List<MultipartFile> documentos,
            List<TipoDocumento> tiposDocumento,
            MultipartFile avatar
    ) {
        List<DocumentoParaSalvar> documentosValidados = validarDocumentos(documentos, tiposDocumento);

        boolean temCnpj = request.cnpj() != null && !request.cnpj().isBlank();
        boolean registroObrigatorio = request.categoria() != CategoriaProfissional.CUIDADOR;

        if (documentosValidados.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CPF)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide seu CPF.");
        }

        if (temCnpj && documentosValidados.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.VALIDACAO_CNPJ)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide o CNPJ informado.");
        }

        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone(), avatar);

        if (profissionalRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("profissional");
        }

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

        if (registroObrigatorio && documentosValidados.stream().noneMatch(doc -> doc.tipo() == TipoDocumento.REGISTRO_PROFISSIONAL)) {
            throw new DocumentoObrigatorioException("Envie um documento que valide seu registro profissional.");
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
                .cnpj(request.cnpj())
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

        for (DocumentoParaSalvar documento : documentosValidados) {
            salvarDocumento(documento.tipo(), documento.arquivo(), null, profissional);
        }

        return new CadastroResponse(usuario.getId(), profissional.getId(), usuario.getNome(), usuario.getEmail(), "PROFISSIONAL");
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

    private void salvarDocumento(TipoDocumento tipo, MultipartFile arquivo, Paciente paciente, Profissional profissional) {
        String storageKey = armazenamentoDocumentoService.enviar(tipo, arquivo);

        documentoRepository.save(
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

    private Usuario resolverUsuario(String nome, String email, String senha, String cpf, String telefone, MultipartFile avatar) {
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

        return usuarioRepository.save(
                Usuario.builder()
                        .nome(nome)
                        .email(email)
                        .senhaHash(passwordEncoder.encode(senha))
                        .cpf(cpf)
                        .telefone(telefone)
                        .avatarStorageKey(avatarStorageKey)
                        .build()
        );
    }

}
