ALTER TABLE profissional_especialidade
    DROP CONSTRAINT profissional_especialidade_pkey;

ALTER TABLE profissional_especialidade
    ADD COLUMN id BIGSERIAL PRIMARY KEY;

ALTER TABLE profissional_especialidade
    ADD CONSTRAINT profissional_especialidade_unique UNIQUE (profissional_id, especialidade_id);

ALTER TABLE profissional_especialidade
    ADD COLUMN registro_profissional VARCHAR(50);

UPDATE profissional_especialidade pe
    SET registro_profissional = p.registro_profissional
    FROM profissional p
    WHERE p.id = pe.profissional_id;

ALTER TABLE profissional_especialidade
    ALTER COLUMN registro_profissional SET NOT NULL;

ALTER TABLE profissional
    DROP COLUMN registro_profissional;
