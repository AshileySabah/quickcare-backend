CREATE TABLE mensagem_nao_lida (
    id BIGSERIAL PRIMARY KEY,
    mensagem_id BIGINT NOT NULL REFERENCES mensagem(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    profissional_usuario_id BIGINT REFERENCES usuario(id) ON DELETE CASCADE,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    email_enviado_em TIMESTAMPTZ,
    CONSTRAINT uk_mensagem_nao_lida UNIQUE (mensagem_id, usuario_id)
);

CREATE INDEX idx_mensagem_nao_lida_usuario_canal
    ON mensagem_nao_lida (usuario_id, solicitacao_id, profissional_usuario_id);

CREATE INDEX idx_mensagem_nao_lida_aviso_email
    ON mensagem_nao_lida (criada_em) WHERE email_enviado_em IS NULL;
