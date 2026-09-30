package com.quickcare.backend.perfil;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.perfil.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.perfil.dto.AtualizarPerfilProfissionalRequest;
import com.quickcare.backend.perfil.dto.ContatoEmergenciaResponse;
import com.quickcare.backend.perfil.dto.PerfilPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilProfissionalResponse;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    @Transactional(readOnly = true)
    public PerfilPacienteResponse buscarPaciente(Long usuarioId) {
        return paraResponse(buscarPacienteDoUsuario(usuarioId));
    }

    @Transactional
    public PerfilPacienteResponse atualizarPaciente(Long usuarioId, AtualizarPerfilPacienteRequest request, MultipartFile avatar) {
        Paciente paciente = buscarPacienteDoUsuario(usuarioId);

        atualizarUsuario(paciente.getUsuario(), request.nome(), request.telefone(), avatar, request.removerAvatar());

        paciente.setDataNascimento(request.dataNascimento());
        paciente.setGenero(request.genero());
        substituirContatos(paciente.getContatosEmergencia(), request.contatosEmergencia());
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

        return paraResponse(paciente);
    }

    @Transactional(readOnly = true)
    public PerfilProfissionalResponse buscarProfissional(Long usuarioId) {
        return paraResponse(buscarProfissionalDoUsuario(usuarioId));
    }

    @Transactional
    public PerfilProfissionalResponse atualizarProfissional(Long usuarioId, AtualizarPerfilProfissionalRequest request, MultipartFile avatar) {
        Profissional profissional = buscarProfissionalDoUsuario(usuarioId);

        boolean atendePresencial = request.modalidadeAtendimento() != ModalidadeAtendimento.REMOTO;
        if (atendePresencial && request.raioAtendimentoKm() == null) {
            throw new RaioAtendimentoObrigatorioException();
        }

        atualizarUsuario(profissional.getUsuario(), request.nome(), request.telefone(), avatar, request.removerAvatar());

        profissional.setDataNascimento(request.dataNascimento());
        profissional.setGenero(request.genero());
        substituirContatos(profissional.getContatosEmergencia(), request.contatosEmergencia());
        profissional.setPossuiSeguroResponsabilidadeCivil(request.possuiSeguroResponsabilidadeCivil());
        profissional.setModalidadeAtendimento(request.modalidadeAtendimento());
        profissional.setRaioAtendimentoKm(atendePresencial ? request.raioAtendimentoKm() : null);
        profissional.setCep(request.cep());
        profissional.setLogradouro(request.logradouro());
        profissional.setNumero(request.numero());
        profissional.setComplemento(request.complemento());
        profissional.setBairro(request.bairro());
        profissional.setCidade(request.cidade());
        profissional.setUf(request.uf().toUpperCase());

        return paraResponse(profissional);
    }

    private Paciente buscarPacienteDoUsuario(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .filter(paciente -> !paciente.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Você não possui um perfil de paciente ativo."));
    }

    private Profissional buscarProfissionalDoUsuario(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(profissional -> !profissional.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Você não possui um perfil de profissional ativo."));
    }

    private void atualizarUsuario(Usuario usuario, String nome, String telefone, MultipartFile avatar, boolean removerAvatar) {
        usuario.setNome(nome);
        usuario.setTelefone(telefone);

        if (avatar != null && !avatar.isEmpty()) {
            usuario.setAvatarStorageKey(armazenamentoDocumentoService.enviarAvatar(avatar));
        } else if (removerAvatar) {
            usuario.setAvatarStorageKey(null);
        }
    }

    private void substituirContatos(List<ContatoEmergencia> atuais, List<ContatoEmergenciaRequest> novos) {
        atuais.clear();
        if (novos != null) {
            novos.forEach(contato -> atuais.add(new ContatoEmergencia(contato.nome(), contato.telefone())));
        }
    }

    private PerfilPacienteResponse paraResponse(Paciente paciente) {
        Usuario usuario = paciente.getUsuario();
        return new PerfilPacienteResponse(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                avatarUrl(usuario),
                paciente.getDataNascimento(),
                paciente.getGenero(),
                paraResponse(paciente.getContatosEmergencia()),
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

    private PerfilProfissionalResponse paraResponse(Profissional profissional) {
        Usuario usuario = profissional.getUsuario();
        return new PerfilProfissionalResponse(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                avatarUrl(usuario),
                profissional.getDataNascimento(),
                profissional.getGenero(),
                paraResponse(profissional.getContatosEmergencia()),
                profissional.isPossuiSeguroResponsabilidadeCivil(),
                profissional.getModalidadeAtendimento(),
                profissional.getRaioAtendimentoKm(),
                profissional.getCep(),
                profissional.getLogradouro(),
                profissional.getNumero(),
                profissional.getComplemento(),
                profissional.getBairro(),
                profissional.getCidade(),
                profissional.getUf()
        );
    }

    private List<ContatoEmergenciaResponse> paraResponse(List<ContatoEmergencia> contatos) {
        return contatos.stream()
                .map(contato -> new ContatoEmergenciaResponse(contato.getNome(), contato.getTelefone()))
                .toList();
    }

    private String avatarUrl(Usuario usuario) {
        return usuario.getAvatarStorageKey() != null
                ? armazenamentoDocumentoService.gerarUrlAssinada(usuario.getAvatarStorageKey())
                : null;
    }

}
