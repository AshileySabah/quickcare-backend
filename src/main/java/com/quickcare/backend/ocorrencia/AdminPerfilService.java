package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcaoAdminRepository;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.ocorrencia.dto.BloqueioResponse;
import com.quickcare.backend.ocorrencia.dto.OcorrenciaResponse;
import com.quickcare.backend.ocorrencia.dto.PerfilAdminDetalheResponse;
import com.quickcare.backend.ocorrencia.dto.PerfilAdminResumoResponse;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminPerfilService {

    public enum Situacao {
        TODOS,
        COM_OCORRENCIAS,
        COM_RECURSO,
        BLOQUEADOS
    }

    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final OcorrenciaRepository ocorrenciaRepository;
    private final BloqueioRepository bloqueioRepository;
    private final RecursoOcorrenciaRepository recursoRepository;
    private final OcorrenciaService ocorrenciaService;
    private final AcaoAdminRepository acaoAdminRepository;
    private final AcessoAdmin acessoAdmin;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PerfilAdminResumoResponse> listar(Long adminUsuarioId, TipoPerfil perfil, String busca, Situacao situacao) {
        acessoAdmin.exigir(adminUsuarioId);
        String termo = normalizar(busca);

        return perfis(perfil)
                .filter(linha -> termo.isEmpty() || normalizar(linha.usuario().getNome()).contains(termo)
                        || normalizar(linha.usuario().getEmail()).contains(termo))
                .map(this::resumo)
                .filter(resumo -> switch (situacao == null ? Situacao.TODOS : situacao) {
                    case TODOS -> true;
                    case COM_OCORRENCIAS -> resumo.ocorrenciasContando() > 0;
                    case COM_RECURSO -> resumo.recursosEmAnalise() > 0;
                    case BLOQUEADOS -> resumo.bloqueio() != null;
                })
                .sorted(Comparator.comparing((PerfilAdminResumoResponse resumo) -> resumo.recursosEmAnalise() == 0)
                        .thenComparing(resumo -> resumo.bloqueio() == null)
                        .thenComparing(PerfilAdminResumoResponse::ocorrenciasContando, Comparator.reverseOrder())
                        .thenComparing(PerfilAdminResumoResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public PerfilAdminDetalheResponse detalhar(Long adminUsuarioId, TipoPerfil perfil, Long usuarioId) {
        acessoAdmin.exigir(adminUsuarioId);
        return detalhe(carregar(perfil, usuarioId));
    }

    @Transactional
    public PerfilAdminDetalheResponse lancarOcorrencia(Long adminUsuarioId, TipoPerfil perfil, Long usuarioId, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Linha linha = carregar(perfil, usuarioId);
        Ocorrencia ocorrencia = ocorrenciaService.lancarManual(admin, linha.usuario(), perfil, justificativa);
        acessoAdmin.registrar(admin, TipoAcaoAdmin.OCORRENCIA_LANCADA, AcaoAdmin.Alvo.OCORRENCIA, ocorrencia.getId(),
                perfil.name(), justificativa);
        return detalhe(linha);
    }

    @Transactional
    public PerfilAdminDetalheResponse anularOcorrencia(Long adminUsuarioId, Long ocorrenciaId, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Ocorrencia ocorrencia = ocorrenciaService.anular(admin, ocorrenciaId, justificativa);
        acessoAdmin.registrar(admin, TipoAcaoAdmin.OCORRENCIA_ANULADA, AcaoAdmin.Alvo.OCORRENCIA, ocorrencia.getId(),
                ocorrencia.getMotivo().name(), justificativa);
        return detalhe(carregar(ocorrencia.getPerfil(), ocorrencia.getUsuario().getId()));
    }

    @Transactional
    public PerfilAdminDetalheResponse aplicarBloqueio(Long adminUsuarioId, TipoPerfil perfil, Long usuarioId, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Linha linha = carregar(perfil, usuarioId);
        Bloqueio bloqueio = ocorrenciaService.aplicarBloqueio(admin, linha.usuario(), perfil, justificativa);
        acessoAdmin.registrar(admin, TipoAcaoAdmin.BLOQUEIO_APLICADO, AcaoAdmin.Alvo.BLOQUEIO, bloqueio.getId(),
                "Nível " + bloqueio.getNivel(), justificativa);
        return detalhe(linha);
    }

    @Transactional
    public PerfilAdminDetalheResponse removerBloqueio(Long adminUsuarioId, Long bloqueioId, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Bloqueio bloqueio = ocorrenciaService.removerBloqueio(admin, bloqueioId, justificativa);
        acessoAdmin.registrar(admin, TipoAcaoAdmin.BLOQUEIO_REMOVIDO, AcaoAdmin.Alvo.BLOQUEIO, bloqueio.getId(),
                "Nível " + bloqueio.getNivel(), justificativa);
        return detalhe(carregar(bloqueio.getPerfil(), bloqueio.getUsuario().getId()));
    }

    @Transactional
    public PerfilAdminDetalheResponse decidirRecurso(Long adminUsuarioId, Long recursoId, boolean anular, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        RecursoOcorrencia recurso = ocorrenciaService.decidirRecurso(admin, recursoId, anular, justificativa);
        Ocorrencia ocorrencia = recurso.getOcorrencia();
        acessoAdmin.registrar(admin, anular ? TipoAcaoAdmin.RECURSO_ACEITO : TipoAcaoAdmin.RECURSO_NEGADO,
                AcaoAdmin.Alvo.OCORRENCIA, ocorrencia.getId(), ocorrencia.getMotivo().name(), justificativa);
        return detalhe(carregar(ocorrencia.getPerfil(), ocorrencia.getUsuario().getId()));
    }

    @Transactional(readOnly = true)
    public long contarRecursosEmAnalise(Long adminUsuarioId) {
        acessoAdmin.exigir(adminUsuarioId);
        return ocorrenciaService.recursosEmAnalise();
    }

    private record Linha(Usuario usuario, TipoPerfil perfil, StatusValidacao statusValidacao, boolean banido) {

        static Linha de(Paciente paciente) {
            return new Linha(paciente.getUsuario(), TipoPerfil.PACIENTE, paciente.getStatusValidacao(), paciente.isBanido());
        }

        static Linha de(Profissional profissional) {
            return new Linha(profissional.getUsuario(), TipoPerfil.PROFISSIONAL, profissional.getStatusValidacao(),
                    profissional.isBanido());
        }
    }

    private Stream<Linha> perfis(TipoPerfil perfil) {
        Stream<Linha> pacientes = perfil == TipoPerfil.PROFISSIONAL ? Stream.empty()
                : pacienteRepository.findAllByOrderByCriadoEmAsc().stream().map(Linha::de);
        Stream<Linha> profissionais = perfil == TipoPerfil.PACIENTE ? Stream.empty()
                : profissionalRepository.findAllByOrderByCriadoEmAsc().stream().map(Linha::de);
        return Stream.concat(pacientes, profissionais);
    }

    private Linha carregar(TipoPerfil perfil, Long usuarioId) {
        return (perfil == TipoPerfil.PACIENTE
                ? pacienteRepository.findByUsuarioId(usuarioId).map(Linha::de)
                : profissionalRepository.findByUsuarioId(usuarioId).map(Linha::de))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil não encontrado."));
    }

    private PerfilAdminResumoResponse resumo(Linha linha) {
        Long usuarioId = linha.usuario().getId();
        Instant agora = Instant.now(clock);
        return new PerfilAdminResumoResponse(usuarioId, linha.perfil(), linha.usuario().getNome(), linha.usuario().getEmail(),
                linha.statusValidacao(), linha.banido(), ocorrenciaService.contando(usuarioId, linha.perfil()),
                recursoRepository.countByStatusAndOcorrenciaUsuarioIdAndOcorrenciaPerfil(
                        RecursoOcorrencia.Status.EM_ANALISE, usuarioId, linha.perfil()),
                ocorrenciaService.bloqueioAtivo(usuarioId, linha.perfil()).map(bloqueio -> BloqueioResponse.de(bloqueio, agora))
                        .orElse(null));
    }

    private PerfilAdminDetalheResponse detalhe(Linha linha) {
        Long usuarioId = linha.usuario().getId();
        Instant agora = Instant.now(clock);
        List<Ocorrencia> ocorrencias = ocorrenciaRepository.findByUsuarioIdAndPerfilOrderByCriadoEmDesc(usuarioId, linha.perfil());
        List<Bloqueio> bloqueios = bloqueioRepository.findByUsuarioIdAndPerfilOrderByInicioDesc(usuarioId, linha.perfil());
        Bloqueio corte = ocorrenciaService.corte(usuarioId, linha.perfil(), agora);

        List<AcaoAdmin> acoes = new ArrayList<>();
        if (!ocorrencias.isEmpty()) {
            acoes.addAll(acaoAdminRepository.findByAlvoTipoAndAlvoIdIn(AcaoAdmin.Alvo.OCORRENCIA,
                    ocorrencias.stream().map(Ocorrencia::getId).toList()));
        }
        if (!bloqueios.isEmpty()) {
            acoes.addAll(acaoAdminRepository.findByAlvoTipoAndAlvoIdIn(AcaoAdmin.Alvo.BLOQUEIO,
                    bloqueios.stream().map(Bloqueio::getId).toList()));
        }

        return new PerfilAdminDetalheResponse(
                resumo(linha),
                RegrasBloqueio.LIMITE,
                (int) RegrasBloqueio.JANELA_CONTAGEM.toDays(),
                ocorrencias.stream().map(ocorrencia -> OcorrenciaResponse.paraOAdmin(ocorrencia, corte)).toList(),
                bloqueios.stream().map(bloqueio -> BloqueioResponse.de(bloqueio, agora)).toList(),
                acoes.stream()
                        .sorted(Comparator.comparing(AcaoAdmin::getCriadoEm).reversed())
                        .map(AcaoAdminResponse::de)
                        .toList());
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
