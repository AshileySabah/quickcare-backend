package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.LoginRequest;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.auth.dto.PerfilStatusResponse;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.jwt.JwtService;
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
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

        return iniciarSessao(usuario);
    }

    LoginResult iniciarSessao(Usuario usuario) {
        return new LoginResult(montarResposta(usuario), jwtService.gerarAccessToken(usuario), jwtService.gerarRefreshToken(usuario));
    }

    /** Dados da sessão do usuário já autenticado pelo cookie — usado pelo front ao recarregar a página. */
    @Transactional(readOnly = true)
    public LoginResponse sessaoAtual(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(TokenInvalidoException::new);
        return montarResposta(usuario);
    }

    private LoginResponse montarResposta(Usuario usuario) {
        List<PerfilStatusResponse> perfis = new ArrayList<>();
        pacienteRepository.findByUsuarioId(usuario.getId())
                .ifPresent(paciente -> perfis.add(new PerfilStatusResponse("PACIENTE", paciente.isBanido() ? "BANIDO" : "ATIVO")));
        profissionalRepository.findByUsuarioId(usuario.getId())
                .ifPresent(profissional -> perfis.add(new PerfilStatusResponse("PROFISSIONAL", profissional.isBanido() ? "BANIDO" : "ATIVO")));

        String avatarUrl = usuario.getAvatarStorageKey() != null
                ? armazenamentoDocumentoService.gerarUrlAssinada(usuario.getAvatarStorageKey())
                : null;

        return new LoginResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), avatarUrl, perfis);
    }

    @Transactional(readOnly = true)
    public String renovarAccessToken(String refreshToken) {
        Claims claims = jwtService.validarRefreshToken(refreshToken);
        Long usuarioId = Long.valueOf(claims.getSubject());

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(TokenInvalidoException::new);

        if (emitidoAntesDaTrocaDeSenha(claims, usuario)) {
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
