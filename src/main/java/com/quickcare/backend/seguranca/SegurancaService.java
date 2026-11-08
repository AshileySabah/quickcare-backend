package com.quickcare.backend.seguranca;

import com.quickcare.backend.administrador.AdministradorRepository;
import com.quickcare.backend.auth.AuthService;
import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.RedefinicaoSenhaService;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.seguranca.dto.CodigoEmailRequest;
import com.quickcare.backend.seguranca.dto.CodigoEncerramentoRequest;
import com.quickcare.backend.seguranca.dto.EncerramentoRequest;
import com.quickcare.backend.seguranca.dto.PerfilSegurancaResponse;
import com.quickcare.backend.seguranca.dto.RecuperarContaRequest;
import com.quickcare.backend.seguranca.dto.SegurancaResponse;
import com.quickcare.backend.seguranca.dto.TrocarSenhaRequest;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.seguranca.exception.SenhaIncorretaException;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SegurancaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioRepository usuarioRepository;
    private final AdministradorRepository administradorRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final CodigoSegurancaService codigoSegurancaService;
    private final EncerramentoService encerramentoService;
    private final AuthService authService;
    private final RedefinicaoSenhaService redefinicaoSenhaService;
    private final EmailService emailService;
    private final Clock clock;

    @Value("${quickcare.frontend-url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public SegurancaResponse situacao(Long usuarioId) {
        Usuario usuario = buscar(usuarioId);
        List<PerfilSegurancaResponse> perfis = new ArrayList<>();
        pacienteRepository.findByUsuarioId(usuarioId)
                .ifPresent(paciente -> perfis.add(new PerfilSegurancaResponse(TipoPerfil.PACIENTE, false, null, null)));
        pacienteRepository.findCongeladoDoTitular(usuarioId)
                .ifPresent(paciente -> perfis.add(encerrado(TipoPerfil.PACIENTE, paciente.getEncerradoEm())));
        profissionalRepository.findByUsuarioId(usuarioId)
                .ifPresent(profissional -> perfis.add(new PerfilSegurancaResponse(TipoPerfil.PROFISSIONAL, false, null, null)));
        profissionalRepository.findCongeladoDoTitular(usuarioId)
                .ifPresent(profissional -> perfis.add(encerrado(TipoPerfil.PROFISSIONAL, profissional.getEncerradoEm())));
        return new SegurancaResponse(usuario.getEmail(), perfis);
    }

    @Transactional
    public void pedirCodigoDeEmail(Long usuarioId, CodigoEmailRequest request) {
        Usuario usuario = buscar(usuarioId);
        exigirSenha(usuario, request.senha());
        String novoEmail = request.novoEmail().trim();
        exigirEmailLivre(usuario, novoEmail);
        codigoSegurancaService.enviarCodigo(usuario, FinalidadeCodigo.TROCAR_EMAIL, novoEmail, novoEmail,
                "confirmar o seu novo e-mail no QuickCare");
    }

    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public LoginResult confirmarEmail(Long usuarioId, String codigo) {
        Usuario usuario = buscar(usuarioId);
        String novoEmail = codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, codigo);
        exigirEmailLivre(usuario, novoEmail);
        String emailAnterior = usuario.getEmail();
        Optional<String> desfazer = codigoSegurancaService.gerarTokenDeDesfazerEmail(
                usuario, emailAnterior, usuario.getSenhaHash());
        usuario.setEmail(novoEmail);
        usuario.setSenhaAlteradaEm(Instant.now(clock));

        String link = desfazer.map(token -> UriComponentsBuilder.fromUriString(frontendUrl)
                        .path("/desfazer-troca-de-email")
                        .queryParam("token", token)
                        .toUriString())
                .orElse(null);
        String mensagem = "O e-mail da sua conta no QuickCare foi trocado para " + novoEmail
                + " e os outros dispositivos foram desconectados.";
        avisar(emailAnterior, usuario.getNome(), "E-mail alterado", link == null
                        ? mensagem + " Se não foi você, use o link do primeiro aviso de troca para desfazer."
                        : mensagem + " Se não foi você, use o botão abaixo em até "
                                + CodigoSegurancaService.VALIDADE_DESFAZER_EMAIL.toDays() + " dias para desfazer a troca."
                                + " Vamos pedir a senha que a conta tinha antes da troca.",
                link, "Não fui eu");
        return authService.iniciarSessao(usuario);
    }

    @Transactional(readOnly = true)
    public void validarDesfazerEmail(String token) {
        codigoSegurancaService.validarTokenDeDesfazerEmail(token);
    }

    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public String desfazerTrocaDeEmail(String token, String senha) {
        CodigoSeguranca desfazer = codigoSegurancaService.usarTokenDeDesfazerEmail(token,
                senhaHash -> senha != null && senhaHash != null && passwordEncoder.matches(senha, senhaHash));
        Usuario usuario = desfazer.getUsuario();
        if (usuario.contaEncerrada() || usuario.getExcluidoEm() != null) {
            throw new CodigoInvalidoException("Este link para desfazer a troca de e-mail não vale mais.");
        }
        String emailAnterior = desfazer.getDado();
        usuarioRepository.findByEmailIgnoreCase(emailAnterior)
                .filter(outro -> !outro.getId().equals(usuario.getId()))
                .ifPresent(outro -> {
                    throw new TransicaoStatusInvalidaException("O e-mail anterior já está em uso em outra conta.");
                });

        String emailTrocado = usuario.getEmail();
        usuario.setEmail(emailAnterior);
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setSenhaAlteradaEm(Instant.now(clock));
        codigoSegurancaService.descartarTodos(usuario);
        avisar(emailTrocado, usuario.getNome(), "Troca de e-mail desfeita",
                "A troca de e-mail da sua conta no QuickCare foi desfeita pelo e-mail anterior. "
                        + "Este endereço deixou de estar ligado à conta.", null, null);
        return redefinicaoSenhaService.gerarToken(usuario);
    }

    @Transactional
    public LoginResult trocarSenha(Long usuarioId, TrocarSenhaRequest request) {
        Usuario usuario = buscar(usuarioId);
        exigirSenha(usuario, request.senhaAtual());
        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenhaHash())) {
            throw new TransicaoStatusInvalidaException("A nova senha precisa ser diferente da atual.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuario.setSenhaAlteradaEm(Instant.now(clock));
        avisar(usuario.getEmail(), usuario.getNome(), "Senha alterada",
                "A senha da sua conta no QuickCare foi alterada e as sessões em outros dispositivos foram encerradas."
                        + " Se não foi você, redefina a senha pela tela de login.", null, null);
        return authService.iniciarSessao(usuario);
    }

    @Transactional
    public void pedirCodigoDeEncerramento(Long usuarioId, CodigoEncerramentoRequest request) {
        Usuario usuario = buscar(usuarioId);
        exigirSenha(usuario, request.senha());
        exigirAlvoValido(usuario, request.alvo());
        codigoSegurancaService.enviarCodigo(usuario, FinalidadeCodigo.ENCERRAR, request.alvo().name(), usuario.getEmail(),
                switch (request.alvo()) {
                    case PACIENTE -> "confirmar o encerramento do seu perfil de paciente";
                    case PROFISSIONAL -> "confirmar o encerramento do seu perfil de profissional";
                    case CONTA -> "confirmar o encerramento da sua conta";
                });
    }

    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public Optional<LoginResponse> encerrar(Long usuarioId, EncerramentoRequest request) {
        Usuario usuario = buscar(usuarioId);
        String alvoDoCodigo = codigoSegurancaService.confirmar(usuario, FinalidadeCodigo.ENCERRAR, request.codigo());
        if (!request.alvo().name().equals(alvoDoCodigo)) {
            throw new CodigoInvalidoException("Este código foi pedido para outra opção de encerramento. Peça um novo.");
        }
        exigirAlvoValido(usuario, request.alvo());

        if (request.alvo() == AlvoEncerramento.CONTA) {
            encerramentoService.encerrarConta(usuario);
            Instant excluiEm = EncerramentoService.excluiEm(usuario.getEncerradoEm());
            String token = codigoSegurancaService.gerarTokenDeRecuperacao(usuario, excluiEm);
            String link = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/recuperar-conta")
                    .queryParam("token", token)
                    .toUriString();
            avisar(usuario.getEmail(), usuario.getNome(), "Conta encerrada",
                    "Sua conta no QuickCare foi encerrada. Se mudar de ideia, você pode recuperá-la até "
                            + DIA.format(excluiEm.atZone(FUSO)) + " pelo botão abaixo ou entrando com seu e-mail e senha."
                            + " Depois disso, seus dados pessoais são apagados.", link, "Recuperar minha conta");
            return Optional.empty();
        }

        TipoPerfil perfil = request.alvo().perfil();
        encerramentoService.encerrarPerfil(usuario, perfil);
        Instant excluiEm = EncerramentoService.excluiEm(Instant.now(clock));
        avisar(usuario.getEmail(), usuario.getNome(), "Perfil encerrado",
                "Seu perfil de " + (perfil == TipoPerfil.PACIENTE ? "paciente" : "profissional")
                        + " no QuickCare foi encerrado. Se mudar de ideia, você pode recuperá-lo até "
                        + DIA.format(excluiEm.atZone(FUSO)) + " na tela de Segurança.", null, null);
        return Optional.of(authService.montarResposta(usuario));
    }

    @Transactional
    public LoginResponse recuperarPerfil(Long usuarioId, TipoPerfil perfil) {
        Usuario usuario = buscar(usuarioId);
        encerramentoService.recuperarPerfil(usuario, perfil);
        return authService.montarResposta(usuario);
    }

    @Transactional
    public LoginResult recuperarConta(RecuperarContaRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email().trim())
                .filter(Usuario::contaEncerrada)
                .orElseThrow(LoginInvalidoException::new);
        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new LoginInvalidoException();
        }
        return recuperar(usuario);
    }

    @Transactional
    public LoginResult recuperarPorLink(String token) {
        Usuario usuario = codigoSegurancaService.usarTokenDeRecuperacao(token);
        if (!usuario.contaEncerrada()) {
            throw new CodigoInvalidoException("Este link de recuperação não vale mais.");
        }
        return recuperar(usuario);
    }

    private LoginResult recuperar(Usuario usuario) {
        encerramentoService.recuperarConta(usuario);
        codigoSegurancaService.descartarTodos(usuario);
        avisar(usuario.getEmail(), usuario.getNome(), "Conta recuperada",
                "Sua conta no QuickCare foi recuperada. O que foi cancelado no encerramento não volta.", null, null);
        return authService.iniciarSessao(usuario);
    }

    private Usuario buscar(Long usuarioId) {
        if (administradorRepository.existsByUsuarioId(usuarioId)) {
            throw new PerfilNaoAutorizadoException("A tela de Segurança é só para pacientes e profissionais.");
        }
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> !usuario.contaEncerrada())
                .orElseThrow(TokenInvalidoException::new);
    }

    private void exigirSenha(Usuario usuario, String senha) {
        if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new SenhaIncorretaException();
        }
    }

    private void exigirEmailLivre(Usuario usuario, String novoEmail) {
        if (novoEmail.equalsIgnoreCase(usuario.getEmail())) {
            throw new TransicaoStatusInvalidaException("Este já é o e-mail da sua conta.");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(novoEmail)) {
            throw new TransicaoStatusInvalidaException("Este e-mail já está em uso em outra conta.");
        }
    }

    private void exigirAlvoValido(Usuario usuario, AlvoEncerramento alvo) {
        if (alvo == AlvoEncerramento.CONTA) {
            return;
        }
        boolean temPaciente = pacienteRepository.findByUsuarioId(usuario.getId()).isPresent();
        boolean temProfissional = profissionalRepository.findByUsuarioId(usuario.getId()).isPresent();
        boolean temOAlvo = alvo == AlvoEncerramento.PACIENTE ? temPaciente : temProfissional;
        if (!temOAlvo) {
            throw new TransicaoStatusInvalidaException(alvo == AlvoEncerramento.PACIENTE
                    ? "Você não tem perfil de paciente ativo."
                    : "Você não tem perfil de profissional ativo.");
        }
        if (!(temPaciente && temProfissional)) {
            throw new TransicaoStatusInvalidaException("Este é o seu único perfil ativo. Para encerrá-lo, encerre a conta.");
        }
    }

    private static PerfilSegurancaResponse encerrado(TipoPerfil perfil, Instant encerradoEm) {
        return new PerfilSegurancaResponse(perfil, true, encerradoEm, EncerramentoService.excluiEm(encerradoEm));
    }

    private void avisar(String destinatario, String nome, String titulo, String mensagem, String link, String linkTexto) {
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("nome", nome);
        variaveis.put("titulo", titulo);
        variaveis.put("mensagem", mensagem);
        if (link != null) {
            variaveis.put("link", link);
            variaveis.put("linkTexto", linkTexto);
        }
        emailService.enviarEmailTemplate(destinatario, titulo + " - QuickCare", "seguranca", variaveis);
    }

}
