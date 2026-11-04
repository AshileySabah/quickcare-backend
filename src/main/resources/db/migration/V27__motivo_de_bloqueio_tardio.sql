CREATE TABLE motivo_bloqueio_tardio (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL')),
    atendimento_id BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    texto VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('EM_ANALISE', 'ACEITO', 'NEGADO')),
    ocorrencia_id BIGINT REFERENCES ocorrencia(id) ON DELETE SET NULL,
    decidido_em TIMESTAMPTZ,
    decidido_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    justificativa_decisao VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_motivo_bloqueio_tardio_usuario ON motivo_bloqueio_tardio(usuario_id, perfil);
CREATE INDEX idx_motivo_bloqueio_tardio_status ON motivo_bloqueio_tardio(status);
