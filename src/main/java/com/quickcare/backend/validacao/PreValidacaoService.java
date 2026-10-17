package com.quickcare.backend.validacao;

import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.dto.PreValidacaoResponse;
import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;
import com.quickcare.backend.validacao.externa.ConsultaCnpjService;
import com.quickcare.backend.validacao.externa.ConsultaCpfService;
import com.quickcare.backend.validacao.externa.Parecer;
import com.quickcare.backend.validacao.externa.VerificacaoExterna;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Consulta APIs de terceiros para ajudar o admin numa etapa. Só roda quando o
 * admin pede; o resultado não é salvo e nunca decide a etapa sozinho.
 */
@Service
@RequiredArgsConstructor
public class PreValidacaoService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ValidacaoService validacaoService;
    private final ConsultaCpfService consultaCpfService;
    private final ConsultaCnpjService consultaCnpjService;

    @Transactional(readOnly = true)
    public PreValidacaoResponse preValidar(Long adminUsuarioId, PerfilValidado perfil, Long id, TipoEtapaValidacao tipo) {
        validacaoService.exigirAdministrador(adminUsuarioId);
        CadastroAvaliado cadastro = validacaoService.carregar(perfil, id);

        if (!EtapasAtuais.de(validacaoService.etapas(cadastro)).containsKey(tipo)) {
            throw new EtapaNaoEncontradaException();
        }

        List<VerificacaoExterna> verificacoes = switch (tipo) {
            case IDENTIDADE -> List.of(verificarCpf(cadastro), verificarDocumentoIdentidade(cadastro.usuario()));
            case CNPJ -> List.of(consultaCnpjService.verificar(cadastro.profissional().getCnpj(), cadastro.usuario().getNome()));
            case REGISTRO_PROFISSIONAL -> List.of(verificarRegistro(cadastro.profissional()));
        };

        Parecer parecer = Parecer.geral(verificacoes.stream().map(VerificacaoExterna::parecer).toList());
        Instant agora = Instant.now();

        return new PreValidacaoResponse(tipo, parecer, verificacoes, sugestaoJustificativa(verificacoes, agora), agora);
    }

    private VerificacaoExterna verificarCpf(CadastroAvaliado cadastro) {
        Usuario usuario = cadastro.usuario();
        return consultaCpfService.verificar(usuario.getCpf(), usuario.getNome(), cadastro.dataNascimento());
    }

    private VerificacaoExterna verificarDocumentoIdentidade(Usuario usuario) {
        TipoDocumentoIdentidade tipo = usuario.getDocumentoIdentidadeTipo();
        String campo = tipo == null ? "RG/CNH" : tipo.name();

        if (tipo == null || usuario.getDocumentoIdentidadeNumero() == null) {
            return VerificacaoExterna.semApi(campo, "Número do documento de identidade não informado; confira o arquivo enviado.", List.of());
        }

        String numero = usuario.getDocumentoIdentidadeNumero()
                + (usuario.getDocumentoIdentidadeUf() != null ? " (" + usuario.getDocumentoIdentidadeUf() + ")" : "");
        String motivo = tipo == TipoDocumentoIdentidade.CNH
                ? "Não há API pública gratuita para consultar CNH (a da Serpro é paga); confira o arquivo enviado."
                : "Não há API pública para consultar RG (cada estado tem sua base); confira o arquivo enviado.";

        return VerificacaoExterna.semApi(campo, motivo,
                List.of("Número " + numero + " está no formato válido (conferido no cadastro)."));
    }

    private VerificacaoExterna verificarRegistro(Profissional profissional) {
        return VerificacaoExterna.semApi("Registro profissional",
                "Não há API pública gratuita dos conselhos profissionais; consulte o portal do conselho.",
                List.of("Registro informado: " + profissional.getRegistroProfissional()
                        + " / " + profissional.getRegistroProfissionalUf() + "."));
    }

    private String sugestaoJustificativa(List<VerificacaoExterna> verificacoes, Instant consultadoEm) {
        String linhas = verificacoes.stream()
                .map(verificacao -> "- " + verificacao.campo()
                        + (verificacao.provedor() != null ? " via " + verificacao.provedor() : "")
                        + ": " + descrever(verificacao.parecer()) + ". "
                        + String.join(" ", verificacao.motivos()))
                .collect(Collectors.joining("\n"));

        return "Pré-validação automática em " + DATA_HORA.format(consultadoEm) + ":\n" + linhas;
    }

    private static String descrever(Parecer parecer) {
        return switch (parecer) {
            case FAVORAVEL -> "parecer favorável";
            case DESFAVORAVEL -> "parecer desfavorável";
            case INDISPONIVEL -> "consulta indisponível";
            case SEM_API -> "sem API, validação manual";
        };
    }

}
