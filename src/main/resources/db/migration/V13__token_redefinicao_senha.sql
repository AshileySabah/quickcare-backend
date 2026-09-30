ALTER TABLE usuario DROP COLUMN token_recuperacao;
ALTER TABLE usuario DROP COLUMN token_recuperacao_expiracao;
ALTER TABLE usuario DROP COLUMN tentativas_recuperacao;

ALTER TABLE usuario ADD COLUMN senha_alterada_em TIMESTAMPTZ;

CREATE TABLE token_redefinicao_senha (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
