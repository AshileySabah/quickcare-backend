-- 1) Bloqueio entre um paciente e um profissional. Vale nos dois sentidos
--    (nenhum dos dois vê ou recebe nada do outro), não importa quem bloqueou;
--    cada lado pode ter o seu, e só quem bloqueou desfaz o dele.
CREATE TABLE bloqueio_usuario (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    bloqueado_por VARCHAR(20) NOT NULL CHECK (bloqueado_por IN ('PACIENTE', 'PROFISSIONAL')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_bloqueio_usuario UNIQUE (paciente_id, profissional_id, bloqueado_por)
);

CREATE INDEX idx_bloqueio_usuario_profissional ON bloqueio_usuario(profissional_id);

-- 2) Recurso contra uma ocorrência: o admin mantém ou anula. Um por ocorrência.
CREATE TABLE recurso_ocorrencia (
    id BIGSERIAL PRIMARY KEY,
    ocorrencia_id BIGINT NOT NULL UNIQUE REFERENCES ocorrencia(id) ON DELETE CASCADE,
    texto VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('EM_ANALISE', 'MANTIDA', 'ANULADA')),
    decidido_em TIMESTAMPTZ,
    decidido_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    justificativa_decisao VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurso_ocorrencia_status ON recurso_ocorrencia(status);
