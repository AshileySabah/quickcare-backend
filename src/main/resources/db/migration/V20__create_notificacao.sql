-- Notificações in-app, separadas pelo perfil (paciente ou profissional) a que
-- se referem. O tipo não tem CHECK: cresce a cada feature e é validado no enum.
CREATE TABLE notificacao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL')),
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    link VARCHAR(255),
    lida_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notificacao_usuario_perfil ON notificacao(usuario_id, perfil, criado_em DESC);
CREATE INDEX idx_notificacao_nao_lida ON notificacao(usuario_id, perfil) WHERE lida_em IS NULL;
