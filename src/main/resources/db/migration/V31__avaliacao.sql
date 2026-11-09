CREATE TABLE avaliacao (
    id BIGSERIAL PRIMARY KEY,
    atendimento_id BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    avaliador VARCHAR(20) NOT NULL CHECK (avaliador IN ('PACIENTE', 'PROFISSIONAL')),
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    nota INTEGER NOT NULL CHECK (nota BETWEEN 1 AND 5),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_avaliacao_atendimento_avaliador UNIQUE (atendimento_id, avaliador)
);

CREATE INDEX idx_avaliacao_profissional ON avaliacao(profissional_id, avaliador);
CREATE INDEX idx_avaliacao_paciente ON avaliacao(paciente_id, avaliador);

CREATE TABLE avaliacao_destaque (
    avaliacao_id BIGINT NOT NULL REFERENCES avaliacao(id) ON DELETE CASCADE,
    destaque VARCHAR(40) NOT NULL,
    PRIMARY KEY (avaliacao_id, destaque)
);

ALTER TABLE atendimento
    ADD COLUMN lembrete_avaliacao_3d_em TIMESTAMPTZ,
    ADD COLUMN lembrete_avaliacao_7d_em TIMESTAMPTZ;
