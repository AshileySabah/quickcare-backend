package com.quickcare.backend.common;

import com.quickcare.backend.auth.exception.LoginInvalidoException;
import com.quickcare.backend.auth.exception.TokenRedefinicaoInvalidoException;
import com.quickcare.backend.cadastro.exception.CpfJaCadastradoException;
import com.quickcare.backend.cadastro.exception.CredenciaisInvalidasException;
import com.quickcare.backend.cadastro.exception.DocumentoIdentidadeInvalidoException;
import com.quickcare.backend.cadastro.exception.DadosCadastraisConflitantesException;
import com.quickcare.backend.cadastro.exception.EmailJaCadastradoException;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.cadastro.exception.PerfilJaExistenteException;
import com.quickcare.backend.cadastro.exception.RaioAtendimentoObrigatorioException;
import com.quickcare.backend.cadastro.exception.RegistroProfissionalObrigatorioException;
import com.quickcare.backend.documento.ArquivoInvalidoException;
import com.quickcare.backend.documento.DocumentoObrigatorioException;
import com.quickcare.backend.jwt.TokenInvalidoException;
import com.quickcare.backend.perfil.exception.PerfilNaoEncontradoException;
import com.quickcare.backend.proposta.exception.PropostaJaEnviadaException;
import com.quickcare.backend.atendimento.exception.AtendimentoNaoEncontradoException;
import com.quickcare.backend.notificacao.exception.NotificacaoNaoEncontradaException;
import com.quickcare.backend.proposta.exception.PropostaNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.validacao.exception.CadastroNaoEncontradoException;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.exception.TransicaoEtapaInvalidaException;
import com.quickcare.backend.validacao.exception.ValidacaoInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ApiError> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CpfJaCadastradoException.class)
    public ResponseEntity<ApiError> handleCpfJaCadastrado(CpfJaCadastradoException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DadosCadastraisConflitantesException.class)
    public ResponseEntity<ApiError> handleDadosConflitantes(DadosCadastraisConflitantesException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PerfilJaExistenteException.class)
    public ResponseEntity<ApiError> handlePerfilJaExistente(PerfilJaExistenteException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EspecialidadeInvalidaException.class)
    public ResponseEntity<ApiError> handleEspecialidadeInvalida(EspecialidadeInvalidaException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RaioAtendimentoObrigatorioException.class)
    public ResponseEntity<ApiError> handleRaioAtendimentoObrigatorio(RaioAtendimentoObrigatorioException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RegistroProfissionalObrigatorioException.class)
    public ResponseEntity<ApiError> handleRegistroProfissionalObrigatorio(RegistroProfissionalObrigatorioException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DocumentoObrigatorioException.class)
    public ResponseEntity<ApiError> handleDocumentoObrigatorio(DocumentoObrigatorioException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ApiError> handleArquivoInvalido(ArquivoInvalidoException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ApiError> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(LoginInvalidoException.class)
    public ResponseEntity<ApiError> handleLoginInvalido(LoginInvalidoException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ApiError> handleTokenInvalido(TokenInvalidoException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(TokenRedefinicaoInvalidoException.class)
    public ResponseEntity<ApiError> handleTokenRedefinicaoInvalido(TokenRedefinicaoInvalidoException ex) {
        return build(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(SolicitacaoNaoEncontradaException.class)
    public ResponseEntity<ApiError> handleSolicitacaoNaoEncontrada(SolicitacaoNaoEncontradaException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AtendimentoNaoEncontradoException.class)
    public ResponseEntity<ApiError> handleAtendimentoNaoEncontrado(AtendimentoNaoEncontradoException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** O job (ou o outro lado) mexeu no mesmo registro ao mesmo tempo. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConflitoDeVersao() {
        return build(HttpStatus.CONFLICT, "Este atendimento acabou de mudar. Recarregue a página e tente de novo.");
    }

    @ExceptionHandler(NotificacaoNaoEncontradaException.class)
    public ResponseEntity<ApiError> handleNotificacaoNaoEncontrada(NotificacaoNaoEncontradaException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PropostaNaoEncontradaException.class)
    public ResponseEntity<ApiError> handlePropostaNaoEncontrada(PropostaNaoEncontradaException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PropostaJaEnviadaException.class)
    public ResponseEntity<ApiError> handlePropostaJaEnviada(PropostaJaEnviadaException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SolicitacaoInvalidaException.class)
    public ResponseEntity<ApiError> handleSolicitacaoInvalida(SolicitacaoInvalidaException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TransicaoStatusInvalidaException.class)
    public ResponseEntity<ApiError> handleTransicaoStatusInvalida(TransicaoStatusInvalidaException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PerfilNaoAutorizadoException.class)
    public ResponseEntity<ApiError> handlePerfilNaoAutorizado(PerfilNaoAutorizadoException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(DocumentoIdentidadeInvalidoException.class)
    public ResponseEntity<ApiError> handleDocumentoIdentidadeInvalido(DocumentoIdentidadeInvalidoException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PerfilNaoEncontradoException.class)
    public ResponseEntity<ApiError> handlePerfilNaoEncontrado(PerfilNaoEncontradoException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CadastroNaoEncontradoException.class)
    public ResponseEntity<ApiError> handleCadastroNaoEncontrado(CadastroNaoEncontradoException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EtapaNaoEncontradaException.class)
    public ResponseEntity<ApiError> handleEtapaNaoEncontrada(EtapaNaoEncontradaException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransicaoEtapaInvalidaException.class)
    public ResponseEntity<ApiError> handleTransicaoEtapaInvalida(TransicaoEtapaInvalidaException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ValidacaoInvalidaException.class)
    public ResponseEntity<ApiError> handleValidacaoInvalida(ValidacaoInvalidaException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CadastroNaoValidadoException.class)
    public ResponseEntity<ApiError> handleCadastroNaoValidado(CadastroNaoValidadoException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, mensagem);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), mensagem));
    }

}
