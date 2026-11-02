-- Proposta que já tinha sido aceita e voltou a aguardar o aceite do paciente:
-- o profissional alterou a proposta ou o paciente alterou a solicitação.
-- Nulo nas demais.
ALTER TABLE proposta ADD COLUMN motivo_reaceite VARCHAR(30)
    CHECK (motivo_reaceite IN ('PROPOSTA_ALTERADA', 'SOLICITACAO_ALTERADA'));
