CREATE TABLE banimento (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL')),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('PERMANENTE', 'SUSPENSAO')),
    inicio TIMESTAMPTZ NOT NULL,
    fim TIMESTAMPTZ,
    justificativa VARCHAR(1000) NOT NULL,
    aplicado_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    denuncia_id BIGINT REFERENCES denuncia(id) ON DELETE SET NULL,
    encerrado_em TIMESTAMPTZ,
    encerramento VARCHAR(20) CHECK (encerramento IN ('REMOVIDO', 'EXPIROU', 'RECURSO_ACEITO')),
    encerrado_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    justificativa_encerramento VARCHAR(1000),
    recurso_texto VARCHAR(1000),
    recurso_status VARCHAR(20) CHECK (recurso_status IN ('EM_ANALISE', 'ACEITO', 'NEGADO')),
    recurso_criado_em TIMESTAMPTZ,
    recurso_decidido_em TIMESTAMPTZ,
    recurso_decidido_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    recurso_justificativa VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_banimento_fim CHECK ((tipo = 'PERMANENTE' AND fim IS NULL) OR (tipo = 'SUSPENSAO' AND fim > inicio))
);

CREATE INDEX idx_banimento_usuario ON banimento(usuario_id, perfil, inicio DESC);
CREATE INDEX idx_banimento_ativo ON banimento(fim) WHERE encerrado_em IS NULL;
CREATE INDEX idx_banimento_recurso ON banimento(recurso_status);
