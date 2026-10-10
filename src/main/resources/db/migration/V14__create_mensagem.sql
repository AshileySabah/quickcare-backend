CREATE TABLE mensagem (
    id BIGSERIAL PRIMARY KEY,
    remetente_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    destinatario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    conteudo TEXT NOT NULL,
    data_envio TIMESTAMPTZ NOT NULL DEFAULT now(),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_mensagem_remetente ON mensagem (remetente_id);
CREATE INDEX idx_mensagem_destinatario ON mensagem (destinatario_id);
