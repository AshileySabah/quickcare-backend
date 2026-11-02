package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;

    /** Participa da transação de quem desistiu. */
    @Transactional
    public void registrar(Usuario usuario, TipoPerfil perfil, MotivoOcorrencia motivo, Atendimento atendimento) {
        if (motivo == MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO
                && ocorrenciaRepository.existsByUsuarioIdAndSolicitacaoIdAndMotivo(
                        usuario.getId(), atendimento.getSolicitacao().getId(), motivo)) {
            return;
        }

        ocorrenciaRepository.save(Ocorrencia.builder()
                .usuario(usuario)
                .perfil(perfil)
                .motivo(motivo)
                .solicitacao(atendimento.getSolicitacao())
                .atendimento(atendimento)
                .build());
    }

}
