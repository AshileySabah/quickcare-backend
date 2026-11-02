-- Solicitações concluídas à mão (antes da branch 4a) podiam ter a proposta
-- aceita com data futura; o atendimento delas já aconteceu, então fica
-- concluído (e não agendado), sem lembretes.
UPDATE atendimento a
SET status = 'CONCLUIDO',
    lembrete_24h_em = COALESCE(a.lembrete_24h_em, now()),
    lembrete_30min_em = COALESCE(a.lembrete_30min_em, now()),
    lembrete_inicio_em = COALESCE(a.lembrete_inicio_em, now())
FROM solicitacao s
WHERE s.id = a.solicitacao_id
  AND s.status = 'CONCLUIDA'
  AND a.status IN ('AGENDADO', 'AGUARDANDO_REACEITE', 'EM_ANDAMENTO', 'FINALIZADO');
