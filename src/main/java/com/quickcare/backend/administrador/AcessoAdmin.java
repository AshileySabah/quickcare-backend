package com.quickcare.backend.administrador;

import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcessoAdmin {

    private final AdministradorRepository administradorRepository;
    private final AcaoAdminRepository acaoAdminRepository;

    public Usuario exigir(Long usuarioId) {
        return administradorRepository.findByUsuarioId(usuarioId)
                .map(Administrador::getUsuario)
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Acesso restrito a administradores."));
    }

    public void registrar(Usuario admin, TipoAcaoAdmin tipo, AcaoAdmin.Alvo alvo, Long alvoId,
                          String detalhe, String justificativa) {
        acaoAdminRepository.save(AcaoAdmin.builder()
                .admin(admin)
                .tipo(tipo)
                .alvoTipo(alvo)
                .alvoId(alvoId)
                .detalhe(detalhe)
                .justificativa(justificativa.trim())
                .build());
    }

}
