package com.quickcare.backend.seguranca;

import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Congelamento {

    public static final String MENSAGEM = "Congelado: o perfil foi encerrado e as pendências dele ficam paradas "
            + "até a pessoa recuperar a conta ou até o prazo de recuperação terminar.";

    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;

    public boolean perfilCongelado(Long usuarioId, TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE
                ? pacienteRepository.findCongeladoDoTitular(usuarioId).isPresent()
                : profissionalRepository.findCongeladoDoTitular(usuarioId).isPresent();
    }

    public void exigirNaoCongelado(Long usuarioId, TipoPerfil perfil) {
        if (perfilCongelado(usuarioId, perfil)) {
            throw new TransicaoStatusInvalidaException(MENSAGEM);
        }
    }

    public static void exigirNaoCongelados(Paciente paciente, Profissional profissional) {
        if (paciente.congelado() || profissional.congelado()) {
            throw new TransicaoStatusInvalidaException(MENSAGEM);
        }
    }

}
