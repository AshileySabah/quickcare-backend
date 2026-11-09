-- Canais do chat: NULL = grupo (paciente + todos os profissionais); preenchido = conversa
-- individual entre o paciente e esse profissional (usuario.id).
ALTER TABLE mensagem
    ADD COLUMN profissional_usuario_id BIGINT REFERENCES usuario(id) ON DELETE CASCADE;

CREATE INDEX idx_mensagem_canal
    ON mensagem (solicitacao_id, profissional_usuario_id, data_envio);

-- Quem esteve na conversa como profissional e ate quando. Quando a proposta deixa de estar
-- ACEITA, saiu_em e preenchido: o profissional so le o historico ate essa data.
CREATE TABLE conversa_profissional (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    entrou_em TIMESTAMPTZ NOT NULL,
    saiu_em TIMESTAMPTZ,
    CONSTRAINT uk_conversa_profissional UNIQUE (solicitacao_id, usuario_id)
);

CREATE INDEX idx_conversa_profissional_usuario ON conversa_profissional (usuario_id);
