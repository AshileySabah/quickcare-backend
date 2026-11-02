package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.bloqueiousuario.dto.BloqueioUsuarioAdminResponse;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminBloqueioUsuarioService {

    private final BloqueioUsuarioRepository bloqueioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AtendimentoService atendimentoService;
    private final AcessoAdmin acessoAdmin;

    @Transactional(readOnly = true)
    public List<BloqueioUsuarioAdminResponse> listar(Long adminUsuarioId, String busca) {
        acessoAdmin.exigir(adminUsuarioId);
        String termo = normalizar(busca);
        return bloqueioRepository.findAllByOrderByCriadoEmDesc().stream()
                .filter(bloqueio -> termo.isEmpty() || Stream.of(
                                bloqueio.getPaciente().getUsuario(), bloqueio.getProfissional().getUsuario())
                        .anyMatch(usuario -> normalizar(usuario.getNome()).contains(termo)
                                || normalizar(usuario.getEmail()).contains(termo)))
                .map(BloqueioUsuarioAdminResponse::de)
                .toList();
    }

    @Transactional
    public BloqueioUsuarioAdminResponse bloquear(Long adminUsuarioId, Long pacienteUsuarioId, Long profissionalUsuarioId,
                                                 String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Paciente paciente = pacienteRepository.findByUsuarioId(pacienteUsuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado."));
        Profissional profissional = profissionalRepository.findByUsuarioId(profissionalUsuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrado."));
        if (paciente.getUsuario().getId().equals(profissional.getUsuario().getId())) {
            throw new TransicaoStatusInvalidaException("O paciente e o profissional são a mesma pessoa.");
        }
        if (bloqueioRepository.findByPacienteIdAndProfissionalIdAndBloqueadoPor(
                paciente.getId(), profissional.getId(), AutorBloqueio.ADMIN).isPresent()) {
            throw new TransicaoStatusInvalidaException("Estes dois já estão bloqueados pelo administrador.");
        }

        atendimentoService.encerrarPorBloqueio(paciente.getId(), profissional.getId(), LadoAtendimento.SISTEMA);
        BloqueioUsuario bloqueio = bloqueioRepository.save(BloqueioUsuario.builder()
                .paciente(paciente)
                .profissional(profissional)
                .bloqueadoPor(AutorBloqueio.ADMIN)
                .criadoPor(admin)
                .justificativa(justificativa.trim())
                .build());
        acessoAdmin.registrar(admin, TipoAcaoAdmin.BLOQUEIO_USUARIO_CRIADO, AcaoAdmin.Alvo.BLOQUEIO_USUARIO, bloqueio.getId(),
                descricao(bloqueio), justificativa);
        return BloqueioUsuarioAdminResponse.de(bloqueio);
    }

    @Transactional
    public void remover(Long adminUsuarioId, Long id, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        BloqueioUsuario bloqueio = bloqueioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado."));
        acessoAdmin.registrar(admin, TipoAcaoAdmin.BLOQUEIO_USUARIO_REMOVIDO, AcaoAdmin.Alvo.BLOQUEIO_USUARIO, bloqueio.getId(),
                descricao(bloqueio), justificativa);
        bloqueioRepository.delete(bloqueio);
    }

    private static String descricao(BloqueioUsuario bloqueio) {
        return bloqueio.getPaciente().getUsuario().getNome() + " × " + bloqueio.getProfissional().getUsuario().getNome()
                + " (por " + bloqueio.getBloqueadoPor().name().toLowerCase(Locale.ROOT) + ")";
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

}
