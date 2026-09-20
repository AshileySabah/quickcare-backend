ALTER TABLE profissional
    ALTER COLUMN latitude DROP NOT NULL,
    ALTER COLUMN longitude DROP NOT NULL;

ALTER TABLE profissional
    ADD COLUMN cnpj VARCHAR(14),
    ADD COLUMN registro_profissional VARCHAR(50),
    ADD COLUMN cep VARCHAR(8),
    ADD COLUMN logradouro VARCHAR(150),
    ADD COLUMN numero VARCHAR(20),
    ADD COLUMN complemento VARCHAR(100),
    ADD COLUMN bairro VARCHAR(100),
    ADD COLUMN cidade VARCHAR(100),
    ADD COLUMN uf VARCHAR(2);

ALTER TABLE profissional
    ADD CONSTRAINT profissional_cnpj_unique UNIQUE (cnpj);

ALTER TABLE paciente
    ADD COLUMN cep VARCHAR(8),
    ADD COLUMN logradouro VARCHAR(150),
    ADD COLUMN numero VARCHAR(20),
    ADD COLUMN complemento VARCHAR(100),
    ADD COLUMN bairro VARCHAR(100),
    ADD COLUMN cidade VARCHAR(100),
    ADD COLUMN uf VARCHAR(2);

CREATE TABLE especialidade (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO especialidade (nome) VALUES
    ('Enfermagem'),
    ('Fisioterapia'),
    ('Fonoaudiologia'),
    ('Nutrição'),
    ('Psicologia'),
    ('Terapia Ocupacional'),
    ('Cuidador de Idosos'),
    ('Medicina'),
    ('Odontologia'),
    ('Educação Física');

CREATE TABLE profissional_especialidade (
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade(id) ON DELETE RESTRICT,
    PRIMARY KEY (profissional_id, especialidade_id)
);
