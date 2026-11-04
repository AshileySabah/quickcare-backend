package com.quickcare.backend.problema;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcaoAdminRepository;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.atendimento.AtendimentoEventoRepository;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.problema.dto.DecisaoDisputaRequest;
import com.quickcare.backend.problema.dto.DisputaDetalheResponse;
import com.quickcare.backend.problema.dto.DisputaResumoResponse;
import com.quickcare.backend.problema.dto.ProblemaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminDisputaService {

    private final ProblemaRepository problemaRepository;
    private final ProblemaService problemaService;
    private final AtendimentoEventoRepository eventoRepository;
    private final AcaoAdminRepository acaoAdminRepository;
    private final ArmazenamentoDocumentoService armazenamento;
    private final AcessoAdmin acessoAdmin;

    @Transactional(readOnly = true)
    public List<DisputaResumoResponse> listar(Long adminUsuarioId, Collection<StatusProblema> status) {
        acessoAdmin.exigir(adminUsuarioId);
        Collection<StatusProblema> filtro = status == null || status.isEmpty() ? Set.of(StatusProblema.values()) : status;
        return problemaRepository.findByStatusInOrderByCriadoEmAsc(filtro).stream()
                .sorted((a, b) -> Boolean.compare(b.getStatus() == StatusProblema.EM_DISPUTA, a.getStatus() == StatusProblema.EM_DISPUTA))
                .map(DisputaResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarEmDisputa(Long adminUsuarioId) {
        acessoAdmin.exigir(adminUsuarioId);
        return problemaRepository.countByStatus(StatusProblema.EM_DISPUTA);
    }

    @Transactional(readOnly = true)
    public DisputaDetalheResponse detalhar(Long adminUsuarioId, Long id) {
        acessoAdmin.exigir(adminUsuarioId);
        return detalhe(problemaService.buscar(id));
    }

    @Transactional
    public DisputaDetalheResponse decidir(Long adminUsuarioId, Long id, DecisaoDisputaRequest request) {
        return detalhe(problemaService.decidir(adminUsuarioId, id, request));
    }

    private DisputaDetalheResponse detalhe(ProblemaAtendimento problema) {
        return new DisputaDetalheResponse(
                DisputaResumoResponse.de(problema),
                problema.getAtendimento().getStatus(),
                problema.getStatusAnterior(),
                ProblemaResponse.de(problema, anexo -> armazenamento.gerarUrlAssinada(anexo.getStorageKey())),
                eventoRepository.findByAtendimentoIdOrderByCriadoEmAscIdAsc(problema.getAtendimento().getId()).stream()
                        .map(DisputaDetalheResponse.Evento::de)
                        .toList(),
                acaoAdminRepository.findByAlvoTipoAndAlvoIdOrderByCriadoEmDesc(AcaoAdmin.Alvo.PROBLEMA, problema.getId())
                        .stream().map(AcaoAdminResponse::de).toList());
    }

}
