package com.quickcare.backend.validacao;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.dto.AtuacaoResumoResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.dto.ArquivoResponse;
import com.quickcare.backend.validacao.dto.DadosCadastraisResponse;
import com.quickcare.backend.validacao.dto.EtapaValidacaoResponse;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import com.quickcare.backend.validacao.dto.ValidacaoResumoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class ValidacaoResponses {

    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    ValidacaoDetalheResponse detalhe(CadastroAvaliado cadastro, List<EtapaValidacao> todasEtapas, boolean exibirAvaliador) {
        return new ValidacaoDetalheResponse(
                cadastro.id(),
                cadastro.perfil(),
                cadastro.statusValidacao(),
                emRevalidacao(cadastro, todasEtapas),
                bloqueiaCriacao(cadastro, todasEtapas),
                cadastro.criadoEm(),
                dados(cadastro),
                etapas(cadastro, todasEtapas, exibirAvaliador)
        );
    }

    ValidacaoResumoResponse resumo(CadastroAvaliado cadastro, List<EtapaValidacao> todasEtapas) {
        long pendentes = EtapasAtuais.de(todasEtapas).values().stream()
                .filter(etapa -> etapa.getStatus() == StatusEtapa.PENDENTE)
                .count();
        Usuario usuario = cadastro.usuario();

        return new ValidacaoResumoResponse(
                cadastro.id(),
                cadastro.perfil(),
                usuario.getNome(),
                usuario.getEmail(),
                cadastro.profissional() == null
                        ? List.of()
                        : cadastro.profissional().atuacoesAtivas().stream().map(Atuacao::rotulo).toList(),
                cadastro.statusValidacao(),
                emRevalidacao(cadastro, todasEtapas),
                cadastro.criadoEm(),
                pendentes
        );
    }

    static boolean emRevalidacao(CadastroAvaliado cadastro, List<EtapaValidacao> todasEtapas) {
        return cadastro.statusValidacao() == StatusValidacao.APROVADO
                && !EtapasAtuais.todasAprovadas(EtapasAtuais.de(todasEtapas).values());
    }

    static boolean bloqueiaCriacao(CadastroAvaliado cadastro, List<EtapaValidacao> todasEtapas) {
        return cadastro.statusValidacao() == StatusValidacao.APROVADO
                && !EtapasAtuais.contaAprovada(EtapasAtuais.de(todasEtapas).values());
    }

    private DadosCadastraisResponse dados(CadastroAvaliado cadastro) {
        Usuario usuario = cadastro.usuario();
        Paciente paciente = cadastro.paciente();
        Profissional profissional = cadastro.profissional();
        boolean ehPaciente = paciente != null;

        return new DadosCadastraisResponse(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                url(usuario.getAvatarStorageKey()),
                usuario.getCpf(),
                cadastro.dataNascimento(),
                ehPaciente ? paciente.getGenero() : profissional.getGenero(),
                usuario.getDocumentoIdentidadeTipo(),
                usuario.getDocumentoIdentidadeNumero(),
                usuario.getDocumentoIdentidadeUf(),
                ehPaciente ? paciente.getCep() : profissional.getCep(),
                ehPaciente ? paciente.getLogradouro() : profissional.getLogradouro(),
                ehPaciente ? paciente.getNumero() : profissional.getNumero(),
                ehPaciente ? paciente.getComplemento() : profissional.getComplemento(),
                ehPaciente ? paciente.getBairro() : profissional.getBairro(),
                ehPaciente ? paciente.getCidade() : profissional.getCidade(),
                ehPaciente ? paciente.getUf() : profissional.getUf(),
                ehPaciente ? List.of() : profissional.atuacoesAtivas().stream().map(AtuacaoResumoResponse::de).toList(),
                ehPaciente ? null : profissional.getCnpj()
        );
    }

    private List<EtapaValidacaoResponse> etapas(CadastroAvaliado cadastro, List<EtapaValidacao> todasEtapas, boolean paraOAdmin) {
        Map<ChaveEtapa, List<EtapaValidacao>> porChave = new LinkedHashMap<>();
        todasEtapas.stream()
                .filter(EtapaValidacao::vigente)
                .forEach(etapa -> porChave.computeIfAbsent(etapa.chave(), chave -> new ArrayList<>()).add(etapa));

        return porChave.values().stream()
                .map(avaliacoes -> {
                    List<EtapaValidacao> maisRecentesPrimeiro = new ArrayList<>(avaliacoes);
                    Collections.reverse(maisRecentesPrimeiro);
                    List<EtapaValidacaoResponse> historico = maisRecentesPrimeiro.stream()
                            .skip(1)
                            .map(anterior -> etapa(cadastro, anterior, paraOAdmin, List.of()))
                            .toList();
                    return etapa(cadastro, maisRecentesPrimeiro.getFirst(), paraOAdmin, historico);
                })
                .toList();
    }

    private EtapaValidacaoResponse etapa(CadastroAvaliado cadastro, EtapaValidacao etapa, boolean paraOAdmin,
                                         List<EtapaValidacaoResponse> historico) {
        return new EtapaValidacaoResponse(
                etapa.getId(),
                etapa.getTipo(),
                etapa.descricao(),
                etapa.getAtuacao() == null ? null : AtuacaoResumoResponse.de(etapa.getAtuacao()),
                etapa.getStatus(),
                etapa.getJustificativa(),
                documento(cadastro, etapa, paraOAdmin),
                anexo(cadastro, etapa, paraOAdmin),
                paraOAdmin && etapa.getAvaliadoPor() != null ? etapa.getAvaliadoPor().getNome() : null,
                etapa.getAvaliadoEm(),
                etapa.getCriadoEm(),
                historico
        );
    }

    private ArquivoResponse documento(CadastroAvaliado cadastro, EtapaValidacao etapa, boolean paraOAdmin) {
        Documento documento = etapa.getDocumento();
        if (documento == null) {
            return null;
        }
        return new ArquivoResponse(documento.getNomeArquivo(), documento.getContentType(), documento.getTamanhoBytes(),
                documento.getEnviadoEm(), caminho(cadastro, etapa, ArquivoDaEtapa.DOCUMENTO, paraOAdmin));
    }

    private ArquivoResponse anexo(CadastroAvaliado cadastro, EtapaValidacao etapa, boolean paraOAdmin) {
        if (etapa.getAnexoStorageKey() == null) {
            return null;
        }
        return new ArquivoResponse(etapa.getAnexoNomeArquivo(), etapa.getAnexoContentType(), null,
                etapa.getAvaliadoEm(), caminho(cadastro, etapa, ArquivoDaEtapa.ANEXO, paraOAdmin));
    }

    private static String caminho(CadastroAvaliado cadastro, EtapaValidacao etapa, ArquivoDaEtapa arquivo, boolean paraOAdmin) {
        boolean paciente = cadastro.perfil() == PerfilValidado.PACIENTE;
        return paraOAdmin
                ? "/admin/validacoes/%s/%d/etapas/%d/%s".formatted(paciente ? "pacientes" : "profissionais", cadastro.id(),
                        etapa.getId(), arquivo.caminho())
                : "/validacao/%s/etapas/%d/%s".formatted(paciente ? "paciente" : "profissional", etapa.getId(), arquivo.caminho());
    }

    private String url(String storageKey) {
        return storageKey == null ? null : armazenamentoDocumentoService.gerarUrlAssinada(storageKey);
    }

}
