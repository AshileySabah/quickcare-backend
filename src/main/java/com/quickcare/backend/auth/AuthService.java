package com.quickcare.backend.auth;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.auth.dto.LoginRequest;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.auth.dto.PerfilStatusResponse;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.cadastro.exception.EmailNaoConfirmadoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.jwt.JwtService;
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.EncerramentoService;
import com.quickcare.backend.seguranca.exception.ContaEncerradaException;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioRepository usuarioRepository;
    private final AdministradorRepository administradorRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(LoginInvalidoException::new);

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new LoginInvalidoException();
        }
        if (usuario.isConfirmacaoEmailPendente()) {
            throw new EmailNaoConfirmadoException("Confirme seu e-mail para entrar. Enviamos um link para "
                    + usuario.getEmail() + ".");
        }
        if (usuario.contaEncerrada()) {
            throw new ContaEncerradaException("Sua conta foi encerrada e será excluída em "
                    + DIA.format(EncerramentoService.excluiEm(usuario.getEncerradoEm()).atZone(FUSO))
                    + ". Até lá, você pode recuperá-la.");
        }

        return iniciarSessao(usuario);
    }

    public LoginResult iniciarSessao(Usuario usuario) {
        return new LoginResult(montarResposta(usuario), jwtService.gerarAccessToken(usuario), jwtService.gerarRefreshToken(usuario));
    }

    @Transactional(readOnly = true)
    public LoginResponse sessaoAtual(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(encontrado -> !encontrado.contaEncerrada())
                .orElseThrow(TokenInvalidoException::new);
        return montarResposta(usuario);
    }

    public LoginResponse montarResposta(Usuario usuario) {
        List<PerfilStatusResponse> perfis = new ArrayList<>();
        if (administradorRepository.existsByUsuarioId(usuario.getId())) {
            perfis.add(new PerfilStatusResponse("ADMIN", "ATIVO", null));
        }
        pacienteRepository.findByUsuarioId(usuario.getId())
                .ifPresent(paciente -> perfis.add(new PerfilStatusResponse(
                        "PACIENTE", paciente.isBanido() ? "BANIDO" : "ATIVO", paciente.getStatusValidacao())));
        profissionalRepository.findByUsuarioId(usuario.getId())
                .ifPresent(profissional -> perfis.add(new PerfilStatusResponse(
                        "PROFISSIONAL", profissional.isBanido() ? "BANIDO" : "ATIVO", profissional.getStatusValidacao())));

        return new LoginResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                armazenamentoDocumentoService.urlDoAvatar(usuario), perfis,
                usuario.getUltimoPerfil());
    }

    @Transactional
    public LoginResponse definirPerfilAtivo(Long usuarioId, TipoPerfil perfil) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(TokenInvalidoException::new);

        boolean ativo = switch (perfil) {
            case PACIENTE -> pacienteRepository.findByUsuarioId(usuarioId).isPresent();
            case PROFISSIONAL -> profissionalRepository.findByUsuarioId(usuarioId).isPresent();
        };
        if (!ativo) {
            throw new PerfilNaoAutorizadoException("Você não tem um perfil ativo de "
                    + (perfil == TipoPerfil.PACIENTE ? "paciente." : "profissional."));
        }

        usuario.setUltimoPerfil(perfil);
        return montarResposta(usuario);
    }

    @Transactional(readOnly = true)
    public String renovarAccessToken(String refreshToken) {
        Claims claims = jwtService.validarRefreshToken(refreshToken);
        Long usuarioId = Long.valueOf(claims.getSubject());

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(TokenInvalidoException::new);

        if (usuario.contaEncerrada() || emitidoAntesDaTrocaDeSenha(claims, usuario)) {
            throw new TokenInvalidoException();
        }

        return jwtService.gerarAccessToken(usuario);
    }

    private boolean emitidoAntesDaTrocaDeSenha(Claims claims, Usuario usuario) {
        Instant senhaAlteradaEm = usuario.getSenhaAlteradaEm();
        return senhaAlteradaEm != null
                && claims.getIssuedAt().toInstant().isBefore(senhaAlteradaEm.truncatedTo(ChronoUnit.SECONDS));
    }

}
