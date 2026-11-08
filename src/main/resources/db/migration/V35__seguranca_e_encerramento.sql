ALTER TABLE usuario ALTER COLUMN cpf DROP NOT NULL;

ALTER TABLE usuario
    ADD COLUMN cpf_encerrado VARCHAR(11),
    ADD COLUMN lapide BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN titular_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN encerrado_em TIMESTAMPTZ,
    ADD COLUMN excluido_em TIMESTAMPTZ;

CREATE INDEX idx_usuario_cpf_encerrado ON usuario(cpf_encerrado) WHERE cpf_encerrado IS NOT NULL;
CREATE INDEX idx_usuario_titular ON usuario(titular_id) WHERE titular_id IS NOT NULL;
CREATE INDEX idx_usuario_encerrado ON usuario(encerrado_em) WHERE encerrado_em IS NOT NULL AND excluido_em IS NULL;

ALTER TABLE paciente
    ADD COLUMN encerrado_em TIMESTAMPTZ,
    ADD COLUMN excluido_em TIMESTAMPTZ;

ALTER TABLE profissional
    ADD COLUMN encerrado_em TIMESTAMPTZ,
    ADD COLUMN excluido_em TIMESTAMPTZ;

CREATE INDEX idx_paciente_encerrado ON paciente(encerrado_em) WHERE encerrado_em IS NOT NULL AND excluido_em IS NULL;
CREATE INDEX idx_profissional_encerrado ON profissional(encerrado_em) WHERE encerrado_em IS NOT NULL AND excluido_em IS NULL;

CREATE TABLE codigo_seguranca (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    finalidade VARCHAR(30) NOT NULL CHECK (finalidade IN ('TROCAR_EMAIL', 'ENCERRAR', 'RECUPERAR_CONTA')),
    codigo_hash VARCHAR(64) NOT NULL,
    dado VARCHAR(255),
    tentativas INTEGER NOT NULL DEFAULT 0,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_codigo_seguranca UNIQUE (usuario_id, finalidade)
);

CREATE INDEX idx_codigo_seguranca_hash ON codigo_seguranca(codigo_hash);
