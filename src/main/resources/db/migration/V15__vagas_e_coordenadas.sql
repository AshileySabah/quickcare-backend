-- Coordenadas do endereço da solicitação presencial, obtidas por geocodificação
-- no back-end; usadas para comparar com o raio de atendimento do profissional.
ALTER TABLE solicitacao
    ADD COLUMN latitude NUMERIC(9,6),
    ADD COLUMN longitude NUMERIC(9,6);

-- Cada linha de profissionais pedidos tem "quantidade" vagas; cada proposta
-- aceita ocupa uma. A solicitação só entra em andamento com todas preenchidas.
ALTER TABLE solicitacao_profissional
    ADD COLUMN vagas_preenchidas INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT solicitacao_profissional_vagas_check
        CHECK (vagas_preenchidas >= 0 AND vagas_preenchidas <= quantidade);

-- Vaga ocupada pela proposta aceita.
ALTER TABLE proposta
    ADD COLUMN solicitacao_profissional_id BIGINT REFERENCES solicitacao_profissional(id) ON DELETE SET NULL;
