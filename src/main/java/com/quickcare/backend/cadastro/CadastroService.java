package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.cadastro.exception.CredenciaisInvalidasException;
import com.quickcare.backend.cadastro.exception.DadosCadastraisConflitantesException;
import com.quickcare.backend.cadastro.exception.EmailJaCadastradoException;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.cadastro.exception.RegistroProfissionalObrigatorioException;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CadastroResponse cadastrarPaciente(CadastroPacienteRequest request) {
        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone());

        if (pacienteRepository.existsByUsuarioId(usuario.getId())) {
            throw new PerfilJaExistenteException("paciente");
        }

        Paciente paciente = pacienteRepository.save(
                Paciente.builder()
                        .usuario(usuario)
                        .banido(false)
                        .cep(request.cep())
                        .logradouro(request.logradouro())
                        .numero(request.numero())
                        .complemento(request.complemento())
                        .bairro(request.bairro())
                        .cidade(request.cidade())
                        .uf(request.uf().toUpperCase())
                        .build()
        );

        return new CadastroResponse(usuario.getId(), paciente.getId(), usuario.getNome(), usuario.getEmail(), "PACIENTE");
    }

    @Transactional
    public CadastroResponse cadastrarProfissional(CadastroProfissionalRequest request) {
        Usuario usuario = resolverUsuario(request.nome(), request.email(), request.senha(), request.cpf(), request.telefone());

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

        boolean registroObrigatorio = request.categoria() != CategoriaProfissional.CUIDADOR;
        if (registroObrigatorio && (request.registroProfissional() == null || request.registroProfissional().isBlank())) {
            throw new RegistroProfissionalObrigatorioException();
        }

        if (request.modalidadeAtendimento() != ModalidadeAtendimento.REMOTO && request.raioAtendimentoKm() == null) {
            throw new RaioAtendimentoObrigatorioException();
        }

        Profissional profissional = profissionalRepository.save(
                Profissional.builder()
                        .usuario(usuario)
                        .banido(false)
                        .categoria(request.categoria())
                        .registroProfissional(registroObrigatorio ? request.registroProfissional() : null)
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
                        .build()
        );

        return new CadastroResponse(usuario.getId(), profissional.getId(), usuario.getNome(), usuario.getEmail(), "PROFISSIONAL");
    }

    private Usuario resolverUsuario(String nome, String email, String senha, String cpf, String telefone) {
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

        return usuarioRepository.save(
                Usuario.builder()
                        .nome(nome)
                        .email(email)
                        .senhaHash(passwordEncoder.encode(senha))
                        .cpf(cpf)
                        .telefone(telefone)
                        .build()
        );
    }

}
