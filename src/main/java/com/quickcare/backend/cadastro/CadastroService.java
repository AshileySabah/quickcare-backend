package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.cadastro.exception.CredenciaisInvalidasException;
import com.quickcare.backend.cadastro.exception.DadosCadastraisConflitantesException;
import com.quickcare.backend.cadastro.exception.EmailJaCadastradoException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
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

        Profissional profissional = profissionalRepository.save(
                Profissional.builder()
                        .usuario(usuario)
                        .banido(false)
                        .modalidadeAtendimento(request.modalidadeAtendimento())
                        .raioAtendimentoKm(request.raioAtendimentoKm())
                        .latitude(request.latitude())
                        .longitude(request.longitude())
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
