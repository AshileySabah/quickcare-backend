-- Chat entre o paciente e os profissionais com proposta aceita em uma solicitacao.
-- A conversa e identificada pela propria solicitacao; quem participa e derivado das propostas ACEITAS.
CREATE TABLE mensagem (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    remetente_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    conteudo TEXT NOT NULL,
    data_envio TIMESTAMPTZ NOT NULL DEFAULT now(),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    anexo_storage_key VARCHAR(255),
    anexo_nome_original VARCHAR(255),
    anexo_tipo VARCHAR(100)
);

CREATE INDEX idx_mensagem_solicitacao_data_envio ON mensagem (solicitacao_id, data_envio);
CREATE INDEX idx_mensagem_remetente ON mensagem (remetente_id);
