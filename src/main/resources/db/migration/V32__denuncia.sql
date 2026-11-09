CREATE TABLE denuncia (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    denunciante VARCHAR(20) NOT NULL CHECK (denunciante IN ('PACIENTE', 'PROFISSIONAL')),
    motivo VARCHAR(40) NOT NULL,
    descricao VARCHAR(2000) NOT NULL,
    anexo_nome VARCHAR(255),
    anexo_content_type VARCHAR(100),
    anexo_tamanho_bytes BIGINT,
    anexo_storage_key VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ABERTA', 'EM_ANALISE', 'PROCEDENTE', 'IMPROCEDENTE')),
    consequencia VARCHAR(20) CHECK (consequencia IN ('OCORRENCIA', 'BANIMENTO')),
    justificativa_decisao VARCHAR(1000),
    analise_iniciada_em TIMESTAMPTZ,
    analisada_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    decidida_em TIMESTAMPTZ,
    decidida_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ocorrencia_id BIGINT REFERENCES ocorrencia(id) ON DELETE SET NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_denuncia_status ON denuncia(status, criado_em);
CREATE INDEX idx_denuncia_partes ON denuncia(paciente_id, profissional_id);
