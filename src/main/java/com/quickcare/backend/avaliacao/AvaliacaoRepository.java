package com.quickcare.backend.avaliacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    String VISIVEL = """
             and a.atendimento.status in :realizados
             and (a.atendimento.termino <= :fimDaJanela
                  or exists (select 1 from Avaliacao b where b.atendimento = a.atendimento and b.avaliador <> a.avaliador))
            """;

    String RECEBIDAS_PELO_PROFISSIONAL = """
             where a.profissional.id = :id
             and a.avaliador = com.quickcare.backend.atendimento.LadoAtendimento.PACIENTE
            """;

    String RECEBIDAS_PELO_PACIENTE = """
             where a.paciente.id = :id
             and a.avaliador = com.quickcare.backend.atendimento.LadoAtendimento.PROFISSIONAL
            """;

    String PENDENTE = """
             and atd.status in :realizados
             and atd.termino > :desde
             and not exists (select 1 from Avaliacao av where av.atendimento = atd and av.avaliador = :lado)
             order by atd.termino desc, atd.id desc
            """;

    interface Resumo {
        Double getMedia();

        long getTotal();
    }

    interface ContagemDestaque {
        DestaqueAvaliacao getDestaque();

        long getTotal();
    }

    boolean existsByAtendimentoIdAndAvaliador(Long atendimentoId, LadoAtendimento avaliador);

    @Query("select a.avaliador from Avaliacao a where a.atendimento.id = :atendimentoId")
    List<LadoAtendimento> findAvaliadoresDoAtendimento(@Param("atendimentoId") Long atendimentoId);

    @Query("select avg(a.nota) as media, count(a) as total from Avaliacao a" + RECEBIDAS_PELO_PROFISSIONAL + VISIVEL)
    Resumo resumoDoProfissional(
            @Param("id") Long profissionalId,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("fimDaJanela") Instant fimDaJanela);

    @Query("select d as destaque, count(a) as total from Avaliacao a join a.destaques d"
            + RECEBIDAS_PELO_PROFISSIONAL + VISIVEL + " group by d order by count(a) desc, d")
    List<ContagemDestaque> destaquesDoProfissional(
            @Param("id") Long profissionalId,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("fimDaJanela") Instant fimDaJanela);

    @Query("select avg(a.nota) as media, count(a) as total from Avaliacao a" + RECEBIDAS_PELO_PACIENTE + VISIVEL)
    Resumo resumoDoPaciente(
            @Param("id") Long pacienteId,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("fimDaJanela") Instant fimDaJanela);

    @Query("select d as destaque, count(a) as total from Avaliacao a join a.destaques d"
            + RECEBIDAS_PELO_PACIENTE + VISIVEL + " group by d order by count(a) desc, d")
    List<ContagemDestaque> destaquesDoPaciente(
            @Param("id") Long pacienteId,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("fimDaJanela") Instant fimDaJanela);

    @Query("""
            select atd from Atendimento atd
            join fetch atd.proposta p join fetch p.profissional pr join fetch pr.usuario
            join fetch atd.solicitacao s join fetch s.paciente pa join fetch pa.usuario
            where pa.id = :pacienteId
            """ + PENDENTE)
    List<Atendimento> pendentesDoPaciente(
            @Param("pacienteId") Long pacienteId,
            @Param("lado") LadoAtendimento lado,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("desde") Instant desde);

    @Query("""
            select atd from Atendimento atd
            join fetch atd.proposta p join fetch p.profissional pr join fetch pr.usuario
            join fetch atd.solicitacao s join fetch s.paciente pa join fetch pa.usuario
            where pr.id = :profissionalId
            """ + PENDENTE)
    List<Atendimento> pendentesDoProfissional(
            @Param("profissionalId") Long profissionalId,
            @Param("lado") LadoAtendimento lado,
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("desde") Instant desde);

    @Query("""
            select atd.id from Atendimento atd
            where atd.status in :realizados
              and atd.termino > :desde
              and ((atd.lembreteAvaliacao3dEm is null and atd.termino <= :ha3Dias)
                   or (atd.lembreteAvaliacao7dEm is null and atd.termino <= :ha7Dias))
            """)
    List<Long> findIdsComLembreteDeAvaliacaoDevido(
            @Param("realizados") Collection<StatusAtendimento> realizados,
            @Param("desde") Instant desde,
            @Param("ha3Dias") Instant ha3Dias,
            @Param("ha7Dias") Instant ha7Dias);

}
