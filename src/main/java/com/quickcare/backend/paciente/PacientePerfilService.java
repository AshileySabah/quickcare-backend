package com.quickcare.backend.paciente;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.paciente.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.paciente.dto.PerfilPacienteResponse;
import com.quickcare.backend.paciente.exception.PacientePerfilNaoEncontradoException;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PacientePerfilService {

    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public PerfilPacienteResponse buscar(Long usuarioId) {
        Paciente paciente = buscarPacienteOuFalhar(usuarioId);
        Usuario usuario = paciente.getUsuario();

        return new PerfilPacienteResponse(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getTelefone(),
                paciente.getDataNascimento(),
                paciente.getGenero(),
                mapContatosParaResponse(paciente.getContatosEmergencia()),
                paciente.getAlergias(),
                paciente.getCondicoesSaude(),
                paciente.getMedicamentosEmUso(),
                paciente.getCep(),
                paciente.getLogradouro(),
                paciente.getNumero(),
                paciente.getComplemento(),
                paciente.getBairro(),
                paciente.getCidade(),
                paciente.getUf()
        );
    }

    @Transactional
    public PerfilPacienteResponse atualizar(Long usuarioId, AtualizarPerfilPacienteRequest request) {
        Paciente paciente = buscarPacienteOuFalhar(usuarioId);
        Usuario usuario = paciente.getUsuario();

        usuario.setNome(request.nome());
        usuario.setTelefone(request.telefone());
        usuarioRepository.save(usuario);

        paciente.setDataNascimento(request.dataNascimento());
        paciente.setGenero(request.genero());
        paciente.setContatosEmergencia(mapContatosParaEntidade(request.contatosEmergencia()));
        paciente.setAlergias(request.alergias());
        paciente.setCondicoesSaude(request.condicoesSaude());
        paciente.setMedicamentosEmUso(request.medicamentosEmUso());
        paciente.setCep(request.cep());
        paciente.setLogradouro(request.logradouro());
        paciente.setNumero(request.numero());
        paciente.setComplemento(request.complemento());
        paciente.setBairro(request.bairro());
        paciente.setCidade(request.cidade());
        paciente.setUf(request.uf().toUpperCase());
        pacienteRepository.save(paciente);

        return buscar(usuarioId);
    }

    private Paciente buscarPacienteOuFalhar(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(PacientePerfilNaoEncontradoException::new);
    }

    private List<ContatoEmergenciaRequest> mapContatosParaResponse(List<ContatoEmergencia> contatos) {
        List<ContatoEmergenciaRequest> resultado = new ArrayList<>();
        for (ContatoEmergencia contato : contatos) {
            resultado.add(new ContatoEmergenciaRequest(contato.getNome(), contato.getTelefone()));
        }
        return resultado;
    }

    private List<ContatoEmergencia> mapContatosParaEntidade(List<ContatoEmergenciaRequest> contatos) {
        List<ContatoEmergencia> resultado = new ArrayList<>();
        if (contatos != null) {
            for (ContatoEmergenciaRequest contato : contatos) {
                resultado.add(new ContatoEmergencia(contato.nome(), contato.telefone()));
            }
        }
        return resultado;
    }
}