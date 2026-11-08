package com.quickcare.backend.consulta;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.atuacao.dto.AtuacaoResumoResponse;
import com.quickcare.backend.avaliacao.AvaliacaoService;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.consulta.dto.ProfissionalPublicoResponse;
import com.quickcare.backend.consulta.dto.SolicitacaoPublicaResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.ValidacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConsultaPublicaService {

    private final ProfissionalRepository profissionalRepository;
    private final PacienteRepository pacienteRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;
    private final ValidacaoService validacaoService;
    private final AvaliacaoService avaliacaoService;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    public record FiltroProfissionais(
            CategoriaProfissional categoria,
            Long especialidadeId,
            String cidade,
            String uf,
            ModalidadeSolicitacao modalidade,
            Double notaMinima
    ) {
    }

    public record FiltroSolicitacoes(
            CategoriaProfissional categoria,
            Long especialidadeId,
            String cidade,
            String uf,
            ModalidadeSolicitacao modalidade
    ) {
    }

    @Transactional(readOnly = true)
    public List<ProfissionalPublicoResponse> profissionais(Long usuarioId, FiltroProfissionais filtro) {
        Optional<Paciente> paciente = usuarioId == null ? Optional.empty() : pacienteRepository.findByUsuarioId(usuarioId);
        Set<Long> bloqueados = paciente
                .map(encontrado -> bloqueioUsuarioRepository.findProfissionalIdsBloqueadosCom(encontrado.getId()))
                .orElse(Set.of());

        return profissionalRepository.findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao.APROVADO).stream()
                .filter(profissional -> !profissional.isBanido())
                .filter(profissional -> usuarioId == null || !profissional.getUsuario().getId().equals(usuarioId))
                .filter(profissional -> !bloqueados.contains(profissional.getId()))
                .filter(profissional -> atendeModalidade(profissional.getModalidadeAtendimento(), filtro.modalidade()))
                .filter(profissional -> mesmoLugar(profissional.getCidade(), profissional.getUf(), filtro.cidade(), filtro.uf()))
                .map(profissional -> resumo(profissional, filtro))
                .flatMap(Optional::stream)
                .filter(resposta -> filtro.notaMinima() == null
                        || (resposta.avaliacoes().media() != null && resposta.avaliacoes().media() >= filtro.notaMinima()))
                .sorted(Comparator.comparing((ProfissionalPublicoResponse resposta) ->
                                resposta.avaliacoes().media() == null ? 0 : resposta.avaliacoes().media(), Comparator.reverseOrder())
                        .thenComparing(ProfissionalPublicoResponse::atendimentosConcluidos, Comparator.reverseOrder())
                        .thenComparing(ProfissionalPublicoResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoPublicaResponse> solicitacoes(Long usuarioId, FiltroSolicitacoes filtro) {
        Optional<Profissional> profissional = usuarioId == null ? Optional.empty() : profissionalRepository.findByUsuarioId(usuarioId);
        Set<Long> bloqueados = profissional
                .map(encontrado -> bloqueioUsuarioRepository.findPacienteIdsBloqueadosCom(encontrado.getId()))
                .orElse(Set.of());

        return solicitacaoRepository.findDistinctByStatusInOrderByCriadoEmDesc(StatusSolicitacao.RECEBENDO_PROPOSTAS_STATUS).stream()
                .filter(solicitacao -> !solicitacao.getPaciente().isBanido())
                .filter(solicitacao -> usuarioId == null || !solicitacao.ehDoUsuario(usuarioId))
                .filter(solicitacao -> !bloqueados.contains(solicitacao.getPaciente().getId()))
                .filter(solicitacao -> filtro.modalidade() == null || solicitacao.getModalidade() == filtro.modalidade())
                .filter(solicitacao -> filtro.cidade() == null && filtro.uf() == null
                        || solicitacao.getModalidade() == ModalidadeSolicitacao.PRESENCIAL
                        && mesmoLugar(solicitacao.getCidade(), solicitacao.getUf(), filtro.cidade(), filtro.uf()))
                .filter(solicitacao -> solicitacao.getProfissionais().stream()
                        .filter(SolicitacaoProfissional::temVaga)
                        .anyMatch(linha -> atendeVaga(linha, filtro.categoria(), filtro.especialidadeId())))
                .map(ConsultaPublicaService::publica)
                .toList();
    }

    private Optional<ProfissionalPublicoResponse> resumo(Profissional profissional, FiltroProfissionais filtro) {
        List<Atuacao> liberadas = validacaoService.atuacoesLiberadas(profissional);
        boolean atende = liberadas.stream().anyMatch(atuacao ->
                (filtro.categoria() == null || atuacao.getCategoria() == filtro.categoria())
                        && (filtro.especialidadeId() == null || atuacao.especialidadeIds().contains(filtro.especialidadeId())));
        if (liberadas.isEmpty() || !atende) {
            return Optional.empty();
        }
        ResumoAvaliacoesResponse avaliacoes = avaliacaoService.resumoDoProfissional(profissional.getId());
        return Optional.of(new ProfissionalPublicoResponse(
                profissional.getId(),
                profissional.getUsuario().getNome(),
                armazenamentoDocumentoService.urlDoAvatar(profissional.getUsuario()),
                liberadas.stream().map(AtuacaoResumoResponse::de).toList(),
                profissional.getModalidadeAtendimento(),
                profissional.getCidade(),
                profissional.getUf(),
                atendimentoRepository.countByPropostaProfissionalIdAndStatusIn(profissional.getId(), StatusAtendimento.REALIZADOS),
                avaliacoes));
    }

    private static SolicitacaoPublicaResponse publica(Solicitacao solicitacao) {
        boolean presencial = solicitacao.getModalidade() == ModalidadeSolicitacao.PRESENCIAL;
        return new SolicitacaoPublicaResponse(
                solicitacao.getId(),
                solicitacao.getModalidade(),
                presencial ? solicitacao.getCidade() : null,
                presencial ? solicitacao.getUf() : null,
                solicitacao.getPrazoDesejado(),
                solicitacao.getCriadoEm(),
                solicitacao.getProfissionais().stream()
                        .sorted(Comparator.comparing(SolicitacaoProfissional::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(linha -> new SolicitacaoPublicaResponse.Vaga(
                                linha.getCategoria(),
                                linha.getCategoria().getRotulo(),
                                linha.getEspecialidades().stream().map(Especialidade::getNome).sorted().toList(),
                                linha.getQuantidade(),
                                Math.max(0, linha.getQuantidade() - linha.getVagasPreenchidas())))
                        .toList());
    }

    private static boolean atendeVaga(SolicitacaoProfissional linha, CategoriaProfissional categoria, Long especialidadeId) {
        return (categoria == null || linha.getCategoria() == categoria)
                && (especialidadeId == null || linha.getEspecialidades().stream()
                .anyMatch(especialidade -> especialidade.getId().equals(especialidadeId)));
    }

    private static boolean atendeModalidade(ModalidadeAtendimento atendimento, ModalidadeSolicitacao procurada) {
        return procurada == null || ModalidadeSolicitacao.atendidasPor(atendimento).contains(procurada);
    }

    private static boolean mesmoLugar(String cidade, String uf, String cidadeProcurada, String ufProcurada) {
        boolean ufConfere = ufProcurada == null || ufProcurada.isBlank() || ufProcurada.equalsIgnoreCase(uf);
        boolean cidadeConfere = cidadeProcurada == null || cidadeProcurada.isBlank()
                || Objects.equals(normalizar(cidadeProcurada), normalizar(cidade));
        return ufConfere && cidadeConfere;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

}
