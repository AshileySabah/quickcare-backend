ALTER TABLE usuario ADD COLUMN confirmacao_email_pendente BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_usuario_confirmacao_pendente ON usuario(criado_em) WHERE confirmacao_email_pendente;

CREATE TABLE token_confirmacao_email (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
