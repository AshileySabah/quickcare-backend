-- Proposta de um profissional para uma solicitação. Cancelamento é lógico
-- (status CANCELADA); recusa acontece quando o paciente aceita outra proposta
-- ou cancela a solicitação.
CREATE TABLE proposta (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    valor NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    abordagem VARCHAR(1000) NOT NULL,
    prazo DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'ACEITA', 'RECUSADA', 'CANCELADA')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Uma proposta não cancelada por profissional em cada solicitação; depois de
-- cancelar, ele pode enviar outra.
CREATE UNIQUE INDEX uq_proposta_ativa_por_profissional
    ON proposta(solicitacao_id, profissional_id)
    WHERE status <> 'CANCELADA';

CREATE INDEX idx_proposta_profissional ON proposta(profissional_id);
