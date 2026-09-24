-- The registration number belongs to the professional's own council (one CRM
-- covers every specialty a doctor practices), not to each specialty link, so it
-- moves back onto profissional. It's nullable here: cuidadores have no council
-- and carry no registration at all.
ALTER TABLE profissional
    ADD COLUMN registro_profissional VARCHAR(50);

UPDATE profissional p
    SET registro_profissional = pe.registro_profissional
    FROM (
        SELECT DISTINCT ON (profissional_id) profissional_id, registro_profissional
        FROM profissional_especialidade
        ORDER BY profissional_id, id
    ) pe
    WHERE pe.profissional_id = p.id;

ALTER TABLE profissional_especialidade
    DROP COLUMN registro_profissional;

ALTER TABLE profissional_especialidade
    DROP CONSTRAINT profissional_especialidade_unique;

ALTER TABLE profissional_especialidade
    DROP COLUMN id;

ALTER TABLE profissional_especialidade
    ADD PRIMARY KEY (profissional_id, especialidade_id);

-- Each specialty now belongs to exactly one professional category, so the
-- catalog is categorized and re-seeded to reflect real specialty names per
-- category instead of the generic placeholder list from V3.
ALTER TABLE profissional
    ADD COLUMN categoria VARCHAR(20);

DELETE FROM profissional_especialidade;
DELETE FROM especialidade;

ALTER TABLE especialidade
    ADD COLUMN categoria VARCHAR(20) NOT NULL DEFAULT 'OUTRO';

ALTER TABLE especialidade
    ALTER COLUMN categoria DROP DEFAULT;

INSERT INTO especialidade (nome, categoria) VALUES
    ('Clínica Médica', 'MEDICO'),
    ('Pediatria', 'MEDICO'),
    ('Nefrologia', 'MEDICO'),
    ('Cardiologia', 'MEDICO'),
    ('Geriatria', 'MEDICO'),
    ('Ortopedia', 'MEDICO'),
    ('Ginecologia e Obstetrícia', 'MEDICO'),
    ('Psiquiatria', 'MEDICO'),
    ('Acompanhamento 12h', 'ENFERMEIRO'),
    ('Acompanhamento 24h', 'ENFERMEIRO'),
    ('Cuidados com Ostomia', 'ENFERMEIRO'),
    ('Aplicação de Injeção', 'ENFERMEIRO'),
    ('Curativos', 'ENFERMEIRO'),
    ('Sondagem', 'ENFERMEIRO'),
    ('Cuidador de Idosos', 'CUIDADOR'),
    ('Acompanhante Hospitalar', 'CUIDADOR'),
    ('Cuidados Paliativos', 'CUIDADOR'),
    ('Psicologia', 'OUTRO'),
    ('Fisioterapia', 'OUTRO'),
    ('Fonoaudiologia', 'OUTRO'),
    ('Nutrição', 'OUTRO'),
    ('Terapia Ocupacional', 'OUTRO'),
    ('Odontologia', 'OUTRO'),
    ('Educação Física', 'OUTRO');

-- Best-effort default so the new NOT NULL constraint can be applied even if a
-- deployment already has professional rows; every real signup from here on
-- always supplies its own categoria explicitly.
UPDATE profissional SET categoria = 'OUTRO' WHERE categoria IS NULL;

ALTER TABLE profissional
    ALTER COLUMN categoria SET NOT NULL;
