package com.quickcare.backend.validacao;

import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.especialidade.Especialidade;
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
import java.util.EnumMap;
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
                cadastro.criadoEm(),
                dados(cadastro),
                etapas(todasEtapas, exibirAvaliador)
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
                cadastro.profissional() != null ? cadastro.profissional().getCategoria() : null,
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
                ehPaciente ? null : profissional.getCategoria(),
                ehPaciente ? null : profissional.getEspecialidades().stream().map(Especialidade::getNome).sorted().toList(),
                ehPaciente ? null : profissional.getCnpj(),
                ehPaciente ? null : profissional.getRegistroProfissional(),
                ehPaciente ? null : profissional.getRegistroProfissionalUf()
        );
    }

    private List<EtapaValidacaoResponse> etapas(List<EtapaValidacao> todasEtapas, boolean exibirAvaliador) {
        Map<TipoEtapaValidacao, List<EtapaValidacao>> porTipo = new EnumMap<>(TipoEtapaValidacao.class);
        todasEtapas.forEach(etapa -> porTipo.computeIfAbsent(etapa.getTipo(), tipo -> new ArrayList<>()).add(etapa));

        return porTipo.values().stream()
                .map(avaliacoes -> {
                    List<EtapaValidacao> maisRecentesPrimeiro = new ArrayList<>(avaliacoes);
                    Collections.reverse(maisRecentesPrimeiro);
                    List<EtapaValidacaoResponse> historico = maisRecentesPrimeiro.stream()
                            .skip(1)
                            .map(anterior -> etapa(anterior, exibirAvaliador, List.of()))
                            .toList();
                    return etapa(maisRecentesPrimeiro.getFirst(), exibirAvaliador, historico);
                })
                .toList();
    }

    private EtapaValidacaoResponse etapa(EtapaValidacao etapa, boolean exibirAvaliador, List<EtapaValidacaoResponse> historico) {
        return new EtapaValidacaoResponse(
                etapa.getId(),
                etapa.getTipo(),
                etapa.getTipo().getDescricao(),
                etapa.getStatus(),
                etapa.getJustificativa(),
                documento(etapa.getDocumento()),
                anexo(etapa),
                exibirAvaliador && etapa.getAvaliadoPor() != null ? etapa.getAvaliadoPor().getNome() : null,
                etapa.getAvaliadoEm(),
                etapa.getCriadoEm(),
                historico
        );
    }

    private ArquivoResponse documento(Documento documento) {
        if (documento == null) {
            return null;
        }
        return new ArquivoResponse(documento.getNomeArquivo(), documento.getContentType(), documento.getTamanhoBytes(),
                documento.getEnviadoEm(), url(documento.getStorageKey()));
    }

    private ArquivoResponse anexo(EtapaValidacao etapa) {
        if (etapa.getAnexoStorageKey() == null) {
            return null;
        }
        return new ArquivoResponse(etapa.getAnexoNomeArquivo(), etapa.getAnexoContentType(), null,
                etapa.getAvaliadoEm(), url(etapa.getAnexoStorageKey()));
    }

    private String url(String storageKey) {
        return storageKey == null ? null : armazenamentoDocumentoService.gerarUrlAssinada(storageKey);
    }

}
