package com.quickcare.backend.validacao.externa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ConsultaCpfService {

    static final String PROVEDOR = "Serpro (Receita Federal)";
    private static final String CAMPO = "CPF";
    private static final String SITUACAO_REGULAR = "0";
    private static final DateTimeFormatter NASCIMENTO_SERPRO = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestClient client;

    public ConsultaCpfService(ValidacaoExternaProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSegundos()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSegundos()));

        this.client = builder
                .baseUrl(properties.cpf().url())
                .defaultHeader("Authorization", "Bearer " + properties.cpf().token())
                .requestFactory(requestFactory)
                .build();
    }

    public VerificacaoExterna verificar(String cpf, String nomeCadastrado, LocalDate nascimentoCadastrado) {
        RespostaSerpro resposta;
        try {
            resposta = client.get()
                    .uri("/cpf/{cpf}", cpf)
                    .retrieve()
                    .body(RespostaSerpro.class);
        } catch (HttpClientErrorException ex) {
            return tratarErro(ex);
        } catch (RestClientException ex) {
            log.warn("Falha ao consultar CPF na Serpro: {}", ex.getMessage());
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "Não foi possível consultar a Serpro agora.");
        }

        if (resposta == null) {
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "A Serpro respondeu sem dados.");
        }

        return avaliar(resposta, nomeCadastrado, nascimentoCadastrado);
    }

    private VerificacaoExterna tratarErro(HttpClientErrorException ex) {
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL,
                    List.of("CPF não encontrado na base consultada."),
                    List.of("O ambiente de demonstração da Serpro só conhece CPFs fictícios; "
                            + "um CPF real só é encontrado com o contrato de produção."));
        }
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL,
                    List.of("A Serpro recusou o número como CPF inválido."), List.of());
        }
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "Limite de consultas da Serpro excedido.");
        }
        log.warn("Serpro respondeu {} ao consultar CPF", ex.getStatusCode());
        return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "A Serpro respondeu com erro (" + ex.getStatusCode().value() + ").");
    }

    static VerificacaoExterna avaliar(RespostaSerpro resposta, String nomeCadastrado, LocalDate nascimentoCadastrado) {
        List<String> divergencias = new ArrayList<>();
        List<String> conferem = new ArrayList<>();
        conferem.add("CPF encontrado na base da Receita.");

        String situacao = resposta.situacao() == null ? "desconhecida" : resposta.situacao().descricao();
        if (resposta.situacao() != null && SITUACAO_REGULAR.equals(resposta.situacao().codigo())) {
            conferem.add("Situação cadastral: " + situacao + ".");
        } else {
            divergencias.add("Situação cadastral irregular: " + situacao + ".");
        }

        if (TextoComparavel.normalizar(resposta.nome()).equals(TextoComparavel.normalizar(nomeCadastrado))) {
            conferem.add("Nome confere: " + resposta.nome() + ".");
        } else {
            divergencias.add("Nome diverge: cadastro \"" + nomeCadastrado + "\", Receita \"" + resposta.nome() + "\".");
        }

        LocalDate nascimentoReceita = lerNascimento(resposta.nascimento());
        if (nascimentoReceita != null && Objects.equals(nascimentoReceita, nascimentoCadastrado)) {
            conferem.add("Data de nascimento confere: " + DATA_BR.format(nascimentoReceita) + ".");
        } else {
            divergencias.add("Data de nascimento diverge: cadastro " + formatar(nascimentoCadastrado)
                    + ", Receita " + formatar(nascimentoReceita) + ".");
        }

        return divergencias.isEmpty()
                ? new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.FAVORAVEL, conferem, List.of())
                : new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL, divergencias, conferem);
    }

    private static LocalDate lerNascimento(String nascimento) {
        try {
            return nascimento == null ? null : LocalDate.parse(nascimento, NASCIMENTO_SERPRO);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String formatar(LocalDate data) {
        return data == null ? "não informada" : DATA_BR.format(data);
    }

    record RespostaSerpro(String ni, String nome, Situacao situacao, String nascimento) {
    }

    record Situacao(String codigo, String descricao) {
    }

}
