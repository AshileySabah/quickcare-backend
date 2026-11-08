package com.quickcare.backend.denuncia;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcaoAdminRepository;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.denuncia.dto.DecisaoDenunciaRequest;
import com.quickcare.backend.denuncia.dto.DenunciaDetalheResponse;
import com.quickcare.backend.denuncia.dto.DenunciaEnviadaResponse;
import com.quickcare.backend.denuncia.dto.DenunciaRequest;
import com.quickcare.backend.denuncia.dto.DenunciaResumoResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.ocorrencia.RecursoOcorrencia;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.perfil.exception.PerfilNaoEncontradoException;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DenunciaService {

    private static final Set<StatusAtendimento> QUALQUER_STATUS = Set.of(StatusAtendimento.values());

    private final DenunciaRepository denunciaRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final ArmazenamentoDocumentoService armazenamento;
    private final OcorrenciaService ocorrenciaService;
    private final AcaoAdminRepository acaoAdminRepository;
    private final AcessoAdmin acessoAdmin;
    private final Notificador notificador;
    private final Clock clock;

    @Transactional
    public DenunciaEnviadaResponse denunciar(Long usuarioId, DenunciaRequest request, MultipartFile anexo) {
        Paciente paciente;
        Profissional profissional;
        LadoAtendimento denunciante;
        if (request.perfil() == TipoPerfil.PACIENTE) {
            paciente = pacienteDoUsuario(usuarioId);
            profissional = profissionalRepository.findById(request.denunciadoId()).orElseThrow(PerfilNaoEncontradoException::new);
            denunciante = LadoAtendimento.PACIENTE;
        } else {
            profissional = profissionalDoUsuario(usuarioId);
            paciente = pacienteRepository.findById(request.denunciadoId()).orElseThrow(PerfilNaoEncontradoException::new);
            denunciante = LadoAtendimento.PROFISSIONAL;
        }
        if (paciente.getUsuario().getId().equals(profissional.getUsuario().getId())) {
            throw new PerfilNaoAutorizadoException("Você não pode denunciar o seu próprio perfil.");
        }
        if (!temVinculo(paciente.getId(), profissional.getId())) {
            throw new PerfilNaoAutorizadoException("Só dá para denunciar quem já teve um atendimento com você.");
        }
        if (denunciaRepository.existsByPacienteIdAndProfissionalIdAndDenuncianteAndStatusIn(
                paciente.getId(), profissional.getId(), denunciante, StatusDenuncia.PENDENTES)) {
            throw new TransicaoStatusInvalidaException(
                    "Você já tem uma denúncia em análise sobre este perfil. Aguarde o resultado antes de enviar outra.");
        }

        boolean comAnexo = anexo != null && !anexo.isEmpty();
        Denuncia denuncia = denunciaRepository.save(Denuncia.builder()
                .paciente(paciente)
                .profissional(profissional)
                .denunciante(denunciante)
                .motivo(request.motivo())
                .descricao(request.descricao().trim())
                .status(StatusDenuncia.ABERTA)
                .anexoNome(comAnexo ? nomeDoArquivo(anexo) : null)
                .anexoContentType(comAnexo ? anexo.getContentType() : null)
                .anexoTamanhoBytes(comAnexo ? anexo.getSize() : null)
                .anexoStorageKey(comAnexo ? armazenamento.enviarAnexoDenuncia(anexo) : null)
                .criadoEm(Instant.now(clock))
                .build());

        notificador.denunciaRecebida(denuncia);
        return DenunciaEnviadaResponse.de(denuncia);
    }

    @Transactional(readOnly = true)
    public boolean temVinculo(Long pacienteId, Long profissionalId) {
        return atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissionalId, pacienteId, QUALQUER_STATUS);
    }

    @Transactional(readOnly = true)
    public List<DenunciaResumoResponse> listar(Long adminUsuarioId, Collection<StatusDenuncia> status) {
        acessoAdmin.exigir(adminUsuarioId);
        Collection<StatusDenuncia> filtro = status == null || status.isEmpty() ? Set.of(StatusDenuncia.values()) : status;
        return denunciaRepository.findByStatusInOrderByCriadoEmAscIdAsc(filtro).stream()
                .sorted(Comparator.comparing((Denuncia denuncia) -> !denuncia.getStatus().pendente()))
                .map(DenunciaResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarAbertas(Long adminUsuarioId) {
        acessoAdmin.exigir(adminUsuarioId);
        return denunciaRepository.countByStatus(StatusDenuncia.ABERTA);
    }

    @Transactional(readOnly = true)
    public DenunciaDetalheResponse detalhar(Long adminUsuarioId, Long id) {
        acessoAdmin.exigir(adminUsuarioId);
        return detalhe(buscar(id));
    }

    @Transactional
    public DenunciaDetalheResponse iniciarAnalise(Long adminUsuarioId, Long id) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Denuncia denuncia = buscar(id);
        denuncia.iniciarAnalise(admin, Instant.now(clock));
        acessoAdmin.registrar(admin, TipoAcaoAdmin.DENUNCIA_EM_ANALISE, AcaoAdmin.Alvo.DENUNCIA, denuncia.getId(),
                null, "Análise iniciada.");
        return detalhe(denuncia);
    }

    @Transactional
    public DenunciaDetalheResponse julgar(Long adminUsuarioId, Long id, DecisaoDenunciaRequest request) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Denuncia denuncia = buscar(id);
        if (request.resultado() == StatusDenuncia.PROCEDENTE && request.consequencia() == ConsequenciaDenuncia.BANIMENTO) {
            throw new TransicaoStatusInvalidaException(
                    "O banimento ainda não está disponível. Por enquanto, escolha lançar uma ocorrência.");
        }

        denuncia.julgar(request.resultado(), request.consequencia(), request.justificativa(), admin, Instant.now(clock));
        if (denuncia.getConsequencia() == ConsequenciaDenuncia.OCORRENCIA) {
            Ocorrencia ocorrencia = ocorrenciaService.lancarPorDenuncia(admin, denuncia.usuarioDenunciado(),
                    denuncia.perfilDenunciado(), request.justificativa());
            denuncia.vincularOcorrencia(ocorrencia);
        }

        boolean procedente = denuncia.getStatus() == StatusDenuncia.PROCEDENTE;
        acessoAdmin.registrar(admin,
                procedente ? TipoAcaoAdmin.DENUNCIA_PROCEDENTE : TipoAcaoAdmin.DENUNCIA_IMPROCEDENTE,
                AcaoAdmin.Alvo.DENUNCIA, denuncia.getId(),
                procedente ? "Consequência: " + denuncia.getConsequencia().name() : null,
                request.justificativa());
        notificador.denunciaJulgada(denuncia);
        if (procedente) {
            notificador.denunciaProcedente(denuncia);
        }
        return detalhe(denuncia);
    }

    @Transactional(readOnly = true)
    public String urlDoAnexo(Long adminUsuarioId, Long id) {
        acessoAdmin.exigir(adminUsuarioId);
        Denuncia denuncia = buscar(id);
        if (!denuncia.temAnexo()) {
            throw new RecursoNaoEncontradoException("Esta denúncia não tem anexo.");
        }
        return armazenamento.gerarUrlAssinada(denuncia.getAnexoStorageKey());
    }

    private DenunciaDetalheResponse detalhe(Denuncia denuncia) {
        Long pacienteId = denuncia.getPaciente().getId();
        Long profissionalId = denuncia.getProfissional().getId();
        long outras = denuncia.getDenunciante() == LadoAtendimento.PACIENTE
                ? denunciaRepository.countByProfissionalIdAndDenuncianteAndIdNot(profissionalId, LadoAtendimento.PACIENTE, denuncia.getId())
                : denunciaRepository.countByPacienteIdAndDenuncianteAndIdNot(pacienteId, LadoAtendimento.PROFISSIONAL, denuncia.getId());

        return new DenunciaDetalheResponse(
                DenunciaResumoResponse.de(denuncia),
                denuncia.getDescricao(),
                denuncia.temAnexo()
                        ? new DenunciaDetalheResponse.Anexo(denuncia.getAnexoNome(), denuncia.getAnexoContentType(),
                                armazenamento.gerarUrlAssinada(denuncia.getAnexoStorageKey()))
                        : null,
                denuncia.getConsequencia(),
                denuncia.getJustificativaDecisao(),
                denuncia.getAnaliseIniciadaEm(),
                denuncia.getAnalisadaPor() == null ? null : denuncia.getAnalisadaPor().getNome(),
                denuncia.getDecididaEm(),
                denuncia.getDecididaPor() == null ? null : denuncia.getDecididaPor().getNome(),
                ocorrenciaGerada(denuncia.getOcorrencia()),
                atendimentoRepository.findByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                                profissionalId, pacienteId, QUALQUER_STATUS).stream()
                        .sorted(Comparator.comparing(Atendimento::getInicio, Comparator.reverseOrder()))
                        .map(atendimento -> new DenunciaDetalheResponse.AtendimentoVinculo(
                                atendimento.getId(),
                                atendimento.getSolicitacao().getId(),
                                Notificador.servico(atendimento.getSolicitacao()),
                                atendimento.getStatus(),
                                atendimento.getInicio(),
                                atendimento.getTermino()))
                        .toList(),
                outras,
                acaoAdminRepository.findByAlvoTipoAndAlvoIdOrderByCriadoEmDesc(AcaoAdmin.Alvo.DENUNCIA, denuncia.getId())
                        .stream().map(AcaoAdminResponse::de).toList());
    }

    private static DenunciaDetalheResponse.OcorrenciaGerada ocorrenciaGerada(Ocorrencia ocorrencia) {
        if (ocorrencia == null) {
            return null;
        }
        RecursoOcorrencia recurso = ocorrencia.getRecurso();
        return new DenunciaDetalheResponse.OcorrenciaGerada(
                ocorrencia.getId(),
                ocorrencia.anulada(),
                recurso == null ? null : new DenunciaDetalheResponse.Recurso(
                        recurso.getId(),
                        recurso.getStatus().name(),
                        recurso.getTexto(),
                        recurso.getCriadoEm(),
                        recurso.getJustificativaDecisao(),
                        recurso.getDecididoEm()));
    }

    private Denuncia buscar(Long id) {
        return denunciaRepository.findWithPartesById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Denúncia não encontrada."));
    }

    private Paciente pacienteDoUsuario(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .filter(paciente -> !paciente.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Você não possui um perfil de paciente ativo."));
    }

    private Profissional profissionalDoUsuario(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(profissional -> !profissional.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Você não possui um perfil de profissional ativo."));
    }

    private static String nomeDoArquivo(MultipartFile anexo) {
        return anexo.getOriginalFilename() == null || anexo.getOriginalFilename().isBlank()
                ? "anexo"
                : anexo.getOriginalFilename();
    }

}
