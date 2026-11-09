package com.quickcare.backend.atuacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atuacao.dto.AlterarEspecialidadesRequest;
import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.atuacao.dto.AtuacaoResponse;
import com.quickcare.backend.atuacao.dto.ImpactoAtuacaoResponse;
import com.quickcare.backend.atuacao.dto.PropostaAfetadaResponse;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.documento.DocumentoObrigatorioException;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.documento.TipoDocumento;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.ocorrencia.RegrasOcorrencia;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.PropostaService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.validacao.StatusEtapa;
import com.quickcare.backend.validacao.ValidacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtuacaoService {

    private static final Set<StatusAtendimento> IMPEDEM_ALTERACAO = Set.of(
            StatusAtendimento.EM_ANDAMENTO, StatusAtendimento.FINALIZADO,
            StatusAtendimento.PROBLEMA_RELATADO, StatusAtendimento.EM_DISPUTA);

    private final ProfissionalRepository profissionalRepository;
    private final AtuacaoRepository atuacaoRepository;
    private final AtuacaoEventoRepository eventoRepository;
    private final MontagemAtuacoes montagemAtuacoes;
    private final PropostaRepository propostaRepository;
    private final PropostaService propostaService;
    private final AtendimentoRepository atendimentoRepository;
    private final ValidacaoService validacaoService;
    private final DocumentoRepository documentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AtuacaoResponse> listar(Long usuarioId) {
        return responder(buscarProfissional(usuarioId));
    }

    @Transactional
    public List<AtuacaoResponse> incluir(Long usuarioId, AtuacaoRequest request, MultipartFile documento) {
        Profissional profissional = buscarProfissional(usuarioId);
        Atuacao atuacao = montagemAtuacoes.montar(profissional, List.of(request), profissional.atuacoesAtivas()).getFirst();
        boolean temDocumento = documento != null && !documento.isEmpty();
        if (atuacao.exigeRegistro() && !temDocumento) {
            throw new DocumentoObrigatorioException("Envie um documento que valide o registro profissional desta atuação.");
        }

        Atuacao salva = atuacaoRepository.save(atuacao);
        profissional.getAtuacoes().add(salva);
        registrar(salva, TipoEventoAtuacao.INCLUIDA, "Incluída pelo profissional.");
        validacaoService.iniciarAtuacao(salva, salva.exigeRegistro() ? salvarDocumento(documento, profissional) : null);
        return responder(profissional);
    }

    @Transactional(readOnly = true)
    public ImpactoAtuacaoResponse impacto(Long usuarioId, Long atuacaoId, List<Long> especialidadeIds) {
        Profissional profissional = buscarProfissional(usuarioId);
        Atuacao atuacao = buscarAtuacao(profissional, atuacaoId);
        if (especialidadeIds == null) {
            exigirOutraAtuacao(profissional);
            return analisar(atuacao, proposta -> true);
        }
        Set<Long> novas = idsValidados(atuacao, especialidadeIds);
        return analisar(atuacao, deixaDeAtender(atuacao, novas));
    }

    @Transactional
    public List<AtuacaoResponse> alterarEspecialidades(Long usuarioId, Long atuacaoId, AlterarEspecialidadesRequest request) {
        Profissional profissional = buscarProfissional(usuarioId);
        Atuacao atuacao = buscarAtuacao(profissional, atuacaoId);
        Set<Long> novas = idsValidados(atuacao, request.especialidadeIds());
        if (novas.equals(atuacao.especialidadeIds())) {
            return responder(profissional);
        }

        ImpactoAtuacaoResponse impacto = analisar(atuacao, deixaDeAtender(atuacao, novas));
        exigirPermitido(impacto);
        List<Proposta> afetadas = afetadas(atuacao, deixaDeAtender(atuacao, novas));

        String antes = nomes(atuacao.getEspecialidades());
        Set<Especialidade> especialidades = montagemAtuacoes.especialidadesDa(atuacao.getCategoria(), List.copyOf(novas));
        atuacao.getEspecialidades().clear();
        atuacao.getEspecialidades().addAll(especialidades);
        atuacaoRepository.saveAndFlush(atuacao);

        afetadas.forEach(propostaService::encerrarPeloProfissional);
        registrar(atuacao, TipoEventoAtuacao.ESPECIALIDADES_ALTERADAS, "De " + antes + " para " + nomes(especialidades)
                + resumoDasCanceladas(afetadas));
        return responder(profissional);
    }

    @Transactional
    public List<AtuacaoResponse> remover(Long usuarioId, Long atuacaoId) {
        Profissional profissional = buscarProfissional(usuarioId);
        Atuacao atuacao = buscarAtuacao(profissional, atuacaoId);
        exigirOutraAtuacao(profissional);
        exigirPermitido(analisar(atuacao, proposta -> true));

        List<Proposta> afetadas = afetadas(atuacao, proposta -> true);
        afetadas.forEach(propostaService::encerrarPeloProfissional);
        atuacao.setRemovidoEm(Instant.now(clock));
        atuacaoRepository.saveAndFlush(atuacao);
        registrar(atuacao, TipoEventoAtuacao.REMOVIDA, "Removida pelo profissional" + resumoDasCanceladas(afetadas));
        validacaoService.recalcular(profissional);
        return responder(profissional);
    }

    private ImpactoAtuacaoResponse analisar(Atuacao atuacao, Predicate<Proposta> afeta) {
        List<Proposta> doAtuacao = propostaRepository.findByAtuacaoIdAndStatusIn(atuacao.getId(), StatusProposta.ATIVAS);
        Map<Long, Atendimento> atendimentos = ultimosAtendimentos(doAtuacao);
        Instant agora = Instant.now(clock);

        boolean impedido = doAtuacao.stream()
                .filter(proposta -> proposta.getStatus() == StatusProposta.ACEITA)
                .filter(afeta)
                .map(proposta -> atendimentos.get(proposta.getId()))
                .anyMatch(atendimento -> atendimento != null && IMPEDEM_ALTERACAO.contains(atendimento.getStatus()));

        List<PropostaAfetadaResponse> propostas = doAtuacao.stream()
                .filter(afeta)
                .filter(proposta -> cancelavel(proposta, atendimentos.get(proposta.getId())))
                .sorted(Comparator.comparing(Proposta::getInicio))
                .map(proposta -> PropostaAfetadaResponse.de(proposta, geraOcorrencia(atendimentos.get(proposta.getId()), agora)))
                .toList();

        return new ImpactoAtuacaoResponse(
                !impedido,
                impedido
                        ? "Há atendimento desta atuação em andamento, finalizado há menos de 72 horas ou com problema relatado. "
                                + "Espere ele ser concluído para alterar a atuação."
                        : null,
                propostas
        );
    }

    private List<Proposta> afetadas(Atuacao atuacao, Predicate<Proposta> afeta) {
        List<Proposta> doAtuacao = propostaRepository.findByAtuacaoIdAndStatusIn(atuacao.getId(), StatusProposta.ATIVAS);
        Map<Long, Atendimento> atendimentos = ultimosAtendimentos(doAtuacao);
        return doAtuacao.stream()
                .filter(afeta)
                .filter(proposta -> cancelavel(proposta, atendimentos.get(proposta.getId())))
                .toList();
    }

    private static boolean cancelavel(Proposta proposta, Atendimento atendimento) {
        return proposta.getStatus() == StatusProposta.PENDENTE || (atendimento != null && atendimento.getStatus().ativo());
    }

    private static boolean geraOcorrencia(Atendimento atendimento, Instant agora) {
        return atendimento != null
                && atendimento.getStatus() == StatusAtendimento.AGENDADO
                && RegrasOcorrencia.cancelamentoConta(atendimento, agora);
    }

    private Map<Long, Atendimento> ultimosAtendimentos(List<Proposta> propostas) {
        if (propostas.isEmpty()) {
            return Map.of();
        }
        return atendimentoRepository.findByPropostaIdInOrderByIdAsc(propostas.stream().map(Proposta::getId).toList()).stream()
                .collect(Collectors.toMap(atendimento -> atendimento.getProposta().getId(), Function.identity(),
                        (anterior, maisRecente) -> maisRecente));
    }

    private static Predicate<Proposta> deixaDeAtender(Atuacao atuacao, Set<Long> especialidadeIds) {
        return proposta -> {
            SolicitacaoProfissional vaga = proposta.getVaga();
            if (proposta.getStatus() == StatusProposta.ACEITA && vaga != null) {
                return !atuacao.atende(vaga, especialidadeIds);
            }
            return !proposta.getSolicitacao().temLinhaPara(atuacao, especialidadeIds);
        };
    }

    private Set<Long> idsValidados(Atuacao atuacao, List<Long> especialidadeIds) {
        if (atuacao.getCategoria() == CategoriaProfissional.OUTRO) {
            throw new EspecialidadeInvalidaException("Em \"Outros profissionais\", a especialidade define a atuação e o "
                    + "registro. Para trocar, inclua a nova atuação e remova esta.");
        }
        return montagemAtuacoes.especialidadesDa(atuacao.getCategoria(), especialidadeIds).stream()
                .map(Especialidade::getId)
                .collect(Collectors.toSet());
    }

    private static void exigirPermitido(ImpactoAtuacaoResponse impacto) {
        if (!impacto.permitido()) {
            throw new TransicaoStatusInvalidaException(impacto.impedimento());
        }
    }

    private static void exigirOutraAtuacao(Profissional profissional) {
        if (profissional.atuacoesAtivas().size() <= 1) {
            throw new TransicaoStatusInvalidaException(
                    "Você precisa de pelo menos uma atuação para usar a plataforma. Inclua outra antes de remover esta.");
        }
    }

    private void registrar(Atuacao atuacao, TipoEventoAtuacao tipo, String detalhe) {
        eventoRepository.save(AtuacaoEvento.builder().atuacao(atuacao).tipo(tipo).detalhe(limitar(detalhe)).build());
    }

    private static String resumoDasCanceladas(List<Proposta> afetadas) {
        if (afetadas.isEmpty()) {
            return ".";
        }
        return "; propostas encerradas: " + afetadas.stream()
                .map(proposta -> "#" + proposta.getId())
                .collect(Collectors.joining(", ")) + ".";
    }

    private static String nomes(Set<Especialidade> especialidades) {
        return especialidades.stream().map(Especialidade::getNome).sorted().collect(Collectors.joining(", "));
    }

    private static String limitar(String texto) {
        return texto.length() <= 1000 ? texto : texto.substring(0, 997) + "...";
    }

    private Documento salvarDocumento(MultipartFile arquivo, Profissional profissional) {
        String storageKey = armazenamentoDocumentoService.enviar(TipoDocumento.REGISTRO_PROFISSIONAL, arquivo);
        return documentoRepository.save(Documento.builder()
                .profissional(profissional)
                .tipoDocumento(TipoDocumento.REGISTRO_PROFISSIONAL)
                .nomeArquivo(arquivo.getOriginalFilename())
                .contentType(arquivo.getContentType())
                .tamanhoBytes(arquivo.getSize())
                .storageKey(storageKey)
                .build());
    }

    private List<AtuacaoResponse> responder(Profissional profissional) {
        Map<Long, StatusEtapa> registros = validacaoService.statusDosRegistros(profissional);
        return profissional.atuacoesAtivas().stream()
                .map(atuacao -> AtuacaoResponse.de(atuacao, registros.get(atuacao.getId()),
                        ValidacaoService.liberada(atuacao, registros)))
                .toList();
    }

    private Atuacao buscarAtuacao(Profissional profissional, Long atuacaoId) {
        return atuacaoRepository.findByIdAndProfissionalId(atuacaoId, profissional.getId())
                .filter(Atuacao::ativa)
                .orElseThrow(AtuacaoNaoEncontradaException::new);
    }

    private Profissional buscarProfissional(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(profissional -> !profissional.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Você não possui um perfil de profissional ativo."));
    }

}
