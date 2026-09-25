-- Dados pessoais, declarações e consentimento LGPD que faltavam em ambos os
-- cadastros. registro_profissional continua sendo o único documento vinculado
-- ao profissional-level; a partir daqui documentos de verdade (arquivos) vivem
-- na tabela `documento`, enviados para armazenamento de objetos (MinIO em dev,
-- S3-compatível em prod).
ALTER TABLE paciente
    ADD COLUMN data_nascimento DATE,
    ADD COLUMN genero VARCHAR(20),
    ADD COLUMN confirmacao_informacoes_verdadeiras BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN consentimento_lgpd BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN alergias VARCHAR(500),
    ADD COLUMN condicoes_saude VARCHAR(500),
    ADD COLUMN medicamentos_em_uso VARCHAR(500);

UPDATE paciente SET data_nascimento = DATE '1990-01-01' WHERE data_nascimento IS NULL;
UPDATE paciente SET genero = 'PREFIRO_NAO_INFORMAR' WHERE genero IS NULL;

ALTER TABLE paciente
    ALTER COLUMN data_nascimento SET NOT NULL,
    ALTER COLUMN genero SET NOT NULL;

CREATE TABLE paciente_contato_emergencia (
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    nome VARCHAR(150) NOT NULL,
    telefone VARCHAR(20) NOT NULL
);

ALTER TABLE profissional
    ADD COLUMN data_nascimento DATE,
    ADD COLUMN genero VARCHAR(20),
    ADD COLUMN confirmacao_informacoes_verdadeiras BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN consentimento_lgpd BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN possui_seguro_responsabilidade_civil BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE profissional SET data_nascimento = DATE '1990-01-01' WHERE data_nascimento IS NULL;
UPDATE profissional SET genero = 'PREFIRO_NAO_INFORMAR' WHERE genero IS NULL;

ALTER TABLE profissional
    ALTER COLUMN data_nascimento SET NOT NULL,
    ALTER COLUMN genero SET NOT NULL;

CREATE TABLE profissional_contato_emergencia (
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    nome VARCHAR(150) NOT NULL,
    telefone VARCHAR(20) NOT NULL
);

CREATE TABLE documento (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT REFERENCES paciente(id) ON DELETE CASCADE,
    profissional_id BIGINT REFERENCES profissional(id) ON DELETE CASCADE,
    tipo_documento VARCHAR(30) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    enviado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documento_dono_check CHECK (
        (paciente_id IS NOT NULL AND profissional_id IS NULL) OR
        (paciente_id IS NULL AND profissional_id IS NOT NULL)
    )
);

CREATE INDEX idx_documento_paciente ON documento(paciente_id);
CREATE INDEX idx_documento_profissional ON documento(profissional_id);
