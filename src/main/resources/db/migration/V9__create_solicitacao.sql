-- Solicitação de atendimento aberta por um paciente para uma especialidade.
-- Endereço só é exigido no atendimento presencial; cancelamento é lógico
-- (status CANCELADA) para preservar o histórico do paciente.
CREATE TABLE solicitacao (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade(id) ON DELETE RESTRICT,
    descricao VARCHAR(1000) NOT NULL,
    modalidade VARCHAR(20) NOT NULL CHECK (modalidade IN ('PRESENCIAL', 'ONLINE')),
    logradouro VARCHAR(150),
    cidade VARCHAR(100),
    uf VARCHAR(2),
    prazo_desejado DATE NOT NULL,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('ABERTA', 'RECEBENDO_PROPOSTAS', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT solicitacao_endereco_presencial_check CHECK (
        modalidade = 'ONLINE' OR (logradouro IS NOT NULL AND cidade IS NOT NULL AND uf IS NOT NULL)
    )
);

CREATE INDEX idx_solicitacao_paciente ON solicitacao(paciente_id);
CREATE INDEX idx_solicitacao_especialidade_status ON solicitacao(especialidade_id, status);
