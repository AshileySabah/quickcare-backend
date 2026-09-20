package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.cadastro.dto.EspecialidadeRegistroRequest;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.cadastro.exception.CredenciaisInvalidasException;
import com.quickcare.backend.cadastro.exception.DadosCadastraisConflitantesException;
import com.quickcare.backend.cadastro.exception.EmailJaCadastradoException;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalEspecialidade;
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

        List<Long> especialidadeIds = request.especialidades().stream()
                .map(EspecialidadeRegistroRequest::especialidadeId)
                .toList();

        Map<Long, Especialidade> especialidadesPorId = especialidadeRepository.findAllByIdIn(especialidadeIds).stream()
                .collect(Collectors.toMap(Especialidade::getId, Function.identity()));

        if (especialidadesPorId.size() != new HashSet<>(especialidadeIds).size()) {
            throw new EspecialidadeInvalidaException();
        }

        if (request.modalidadeAtendimento() != ModalidadeAtendimento.REMOTO && request.raioAtendimentoKm() == null) {
            throw new RaioAtendimentoObrigatorioException();
        }

        Profissional profissional = profissionalRepository.save(
                Profissional.builder()
                        .usuario(usuario)
                        .banido(false)
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
                        .build()
        );

        Set<ProfissionalEspecialidade> vinculos = request.especialidades().stream()
                .map(item -> ProfissionalEspecialidade.builder()
                        .profissional(profissional)
                        .especialidade(especialidadesPorId.get(item.especialidadeId()))
                        .registroProfissional(item.registroProfissional())
                        .build())
                .collect(Collectors.toSet());

        profissional.getEspecialidades().addAll(vinculos);
        profissionalRepository.save(profissional);

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
