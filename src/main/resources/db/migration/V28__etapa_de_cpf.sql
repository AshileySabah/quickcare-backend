INSERT INTO etapa_validacao (paciente_id, profissional_id, tipo, status, documento_id, justificativa,
                             avaliado_por_id, avaliado_em, criado_em)
SELECT DISTINCT ON (e.paciente_id, e.profissional_id)
       e.paciente_id, e.profissional_id, 'CPF', e.status, e.documento_id, e.justificativa,
       e.avaliado_por_id, e.avaliado_em, e.criado_em
FROM etapa_validacao e
WHERE e.tipo = 'IDENTIDADE'
  AND NOT EXISTS (
      SELECT 1 FROM etapa_validacao c
      WHERE c.tipo = 'CPF'
        AND c.paciente_id IS NOT DISTINCT FROM e.paciente_id
        AND c.profissional_id IS NOT DISTINCT FROM e.profissional_id
  )
ORDER BY e.paciente_id, e.profissional_id, e.criado_em DESC, e.id DESC;
