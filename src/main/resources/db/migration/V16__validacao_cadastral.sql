-- Validação cadastral feita pelo admin: cada perfil (paciente/profissional)
-- passa por etapas (identidade, CNPJ, registro profissional) aprovadas ou
-- reprovadas com justificativa e anexo opcional.

CREATE TABLE administrador (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documento de identidade com foto (RG ou CNH) cujo arquivo já era enviado
-- como VALIDACAO_CPF; agora o número também é informado. Fica no usuário porque
-- é compartilhado entre os perfis de paciente e profissional.
ALTER TABLE usuario
    ADD COLUMN documento_identidade_tipo VARCHAR(10),
    ADD COLUMN documento_identidade_numero VARCHAR(20),
    ADD COLUMN documento_identidade_uf VARCHAR(2);

-- Cadastros anteriores a esta feature entram como já aprovados; os novos
-- nascem pendentes.
ALTER TABLE paciente
    ADD COLUMN status_validacao VARCHAR(20) NOT NULL DEFAULT 'APROVADO';
ALTER TABLE paciente
    ALTER COLUMN status_validacao SET DEFAULT 'PENDENTE';

ALTER TABLE profissional
    ADD COLUMN status_validacao VARCHAR(20) NOT NULL DEFAULT 'APROVADO';
ALTER TABLE profissional
    ALTER COLUMN status_validacao SET DEFAULT 'PENDENTE';

-- Uma linha por avaliação. No reenvio após reprovação nasce uma nova linha
-- PENDENTE do mesmo tipo; as anteriores ficam como histórico.
CREATE TABLE etapa_validacao (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT REFERENCES paciente(id) ON DELETE CASCADE,
    profissional_id BIGINT REFERENCES profissional(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- Arquivo enviado pelo usuário que foi conferido nesta avaliação.
    documento_id BIGINT REFERENCES documento(id) ON DELETE SET NULL,
    justificativa VARCHAR(1000),
    anexo_storage_key VARCHAR(500),
    anexo_nome_arquivo VARCHAR(255),
    anexo_content_type VARCHAR(100),
    avaliado_por_id BIGINT REFERENCES usuario(id),
    avaliado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT etapa_validacao_dono_check CHECK (
        (paciente_id IS NOT NULL AND profissional_id IS NULL) OR
        (paciente_id IS NULL AND profissional_id IS NOT NULL)
    )
);

CREATE INDEX idx_etapa_validacao_paciente ON etapa_validacao(paciente_id);
CREATE INDEX idx_etapa_validacao_profissional ON etapa_validacao(profissional_id);
