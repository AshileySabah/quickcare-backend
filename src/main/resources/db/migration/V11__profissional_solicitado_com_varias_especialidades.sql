-- Cada linha de profissionais pedidos passa a ter uma categoria e VÁRIAS
-- especialidades: o profissional precisa atender todas elas.
-- Ex.: 2 enfermeiros que façam curativos E sondagem.
ALTER TABLE solicitacao_profissional ADD COLUMN categoria VARCHAR(20);

UPDATE solicitacao_profissional sp
SET categoria = e.categoria
FROM especialidade e
WHERE e.id = sp.especialidade_id;

ALTER TABLE solicitacao_profissional ALTER COLUMN categoria SET NOT NULL;

CREATE TABLE solicitacao_profissional_especialidade (
    solicitacao_profissional_id BIGINT NOT NULL REFERENCES solicitacao_profissional(id) ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade(id) ON DELETE RESTRICT,
    PRIMARY KEY (solicitacao_profissional_id, especialidade_id)
);

CREATE INDEX idx_solicitacao_profissional_especialidade_esp
    ON solicitacao_profissional_especialidade(especialidade_id);

INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
SELECT id, especialidade_id FROM solicitacao_profissional;

ALTER TABLE solicitacao_profissional DROP CONSTRAINT solicitacao_profissional_unique;
DROP INDEX idx_solicitacao_profissional_especialidade;
ALTER TABLE solicitacao_profissional DROP COLUMN especialidade_id;
