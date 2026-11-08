CREATE TABLE profissional_atuacao (
    id BIGSERIAL PRIMARY KEY,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    categoria VARCHAR(20) NOT NULL,
    registro_profissional VARCHAR(50),
    registro_profissional_uf VARCHAR(2),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    removido_em TIMESTAMPTZ
);

CREATE INDEX idx_atuacao_profissional ON profissional_atuacao(profissional_id);

CREATE UNIQUE INDEX uq_atuacao_ativa_por_categoria
    ON profissional_atuacao(profissional_id, categoria)
    WHERE removido_em IS NULL AND categoria <> 'OUTRO';

CREATE TABLE profissional_atuacao_especialidade (
    atuacao_id BIGINT NOT NULL REFERENCES profissional_atuacao(id) ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade(id),
    PRIMARY KEY (atuacao_id, especialidade_id)
);

CREATE TABLE profissional_atuacao_evento (
    id BIGSERIAL PRIMARY KEY,
    atuacao_id BIGINT NOT NULL REFERENCES profissional_atuacao(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    detalhe VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_atuacao_evento_atuacao ON profissional_atuacao_evento(atuacao_id);

INSERT INTO profissional_atuacao (profissional_id, categoria, registro_profissional, registro_profissional_uf, criado_em)
SELECT p.id,
       p.categoria,
       CASE WHEN p.categoria = 'CUIDADOR' THEN NULL ELSE p.registro_profissional END,
       CASE WHEN p.categoria = 'CUIDADOR' THEN NULL ELSE p.registro_profissional_uf END,
       p.criado_em
FROM profissional p;

INSERT INTO profissional_atuacao_especialidade (atuacao_id, especialidade_id)
SELECT a.id, pe.especialidade_id
FROM profissional_atuacao a
JOIN profissional_especialidade pe ON pe.profissional_id = a.profissional_id
JOIN especialidade e ON e.id = pe.especialidade_id AND e.categoria = a.categoria;

INSERT INTO profissional_atuacao_evento (atuacao_id, tipo, criado_em)
SELECT id, 'INCLUIDA', criado_em FROM profissional_atuacao;

ALTER TABLE etapa_validacao
    ADD COLUMN atuacao_id BIGINT REFERENCES profissional_atuacao(id);

UPDATE etapa_validacao e
SET atuacao_id = a.id
FROM profissional_atuacao a
WHERE e.tipo = 'REGISTRO_PROFISSIONAL'
  AND a.profissional_id = e.profissional_id;

DELETE FROM etapa_validacao WHERE tipo = 'REGISTRO_PROFISSIONAL' AND atuacao_id IS NULL;

CREATE INDEX idx_etapa_validacao_atuacao ON etapa_validacao(atuacao_id);

ALTER TABLE proposta
    ADD COLUMN atuacao_id BIGINT REFERENCES profissional_atuacao(id);

UPDATE proposta p
SET atuacao_id = a.id
FROM profissional_atuacao a
WHERE a.profissional_id = p.profissional_id;

ALTER TABLE proposta
    ALTER COLUMN atuacao_id SET NOT NULL;

DROP INDEX uq_proposta_ativa_por_profissional;

CREATE UNIQUE INDEX uq_proposta_ativa_por_atuacao
    ON proposta(solicitacao_id, atuacao_id)
    WHERE status IN ('PENDENTE', 'ACEITA');

ALTER TABLE profissional
    DROP COLUMN categoria,
    DROP COLUMN registro_profissional,
    DROP COLUMN registro_profissional_uf;

DROP TABLE profissional_especialidade;
