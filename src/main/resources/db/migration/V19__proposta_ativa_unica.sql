-- O profissional pode enviar uma nova proposta depois de ter a anterior
-- recusada ou cancelada: só não pode ter duas ativas (pendente ou aceita) na
-- mesma solicitação.
DROP INDEX uq_proposta_ativa_por_profissional;

CREATE UNIQUE INDEX uq_proposta_ativa_por_profissional
    ON proposta(solicitacao_id, profissional_id)
    WHERE status IN ('PENDENTE', 'ACEITA');
