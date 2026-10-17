ALTER TABLE mensagem
    ADD COLUMN anexo_storage_key VARCHAR(255),
    ADD COLUMN anexo_nome_original VARCHAR(255),
    ADD COLUMN anexo_tipo VARCHAR(100);
