-- Uma solicitação passa a pedir vários profissionais: cada linha diz qual
-- especialidade (e, por ela, a categoria) e quantos profissionais dela.
CREATE TABLE solicitacao_profissional (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL CHECK (quantidade BETWEEN 1 AND 10),
    CONSTRAINT solicitacao_profissional_unique UNIQUE (solicitacao_id, especialidade_id)
);

CREATE INDEX idx_solicitacao_profissional_especialidade ON solicitacao_profissional(especialidade_id);

INSERT INTO solicitacao_profissional (solicitacao_id, especialidade_id, quantidade)
SELECT id, especialidade_id, 1 FROM solicitacao;

DROP INDEX idx_solicitacao_especialidade_status;
ALTER TABLE solicitacao DROP COLUMN especialidade_id;

CREATE INDEX idx_solicitacao_status ON solicitacao(status);

-- Endereço completo, no mesmo formato do cadastro (componente de endereço do front).
ALTER TABLE solicitacao
    ADD COLUMN cep VARCHAR(8),
    ADD COLUMN numero VARCHAR(20),
    ADD COLUMN complemento VARCHAR(100),
    ADD COLUMN bairro VARCHAR(100);

ALTER TABLE solicitacao DROP CONSTRAINT solicitacao_endereco_presencial_check;

-- NOT VALID: solicitações presenciais antigas não têm CEP/número/bairro; a regra
-- vale para toda linha inserida ou alterada daqui em diante.
ALTER TABLE solicitacao
    ADD CONSTRAINT solicitacao_endereco_presencial_check CHECK (
        modalidade = 'ONLINE' OR (
            cep IS NOT NULL AND logradouro IS NOT NULL AND numero IS NOT NULL
            AND bairro IS NOT NULL AND cidade IS NOT NULL AND uf IS NOT NULL
        )
    ) NOT VALID;
