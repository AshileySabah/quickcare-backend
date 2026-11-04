-- Branch 4b: problema no atendimento ("não foi realizado"), disputa decidida
-- pelo admin, contagem de ocorrências com bloqueio e auditoria do admin.

-- 1) Novos estados do atendimento.
ALTER TABLE atendimento DROP CONSTRAINT atendimento_status_check;
ALTER TABLE atendimento ADD CONSTRAINT atendimento_status_check CHECK (status IN (
    'AGENDADO', 'AGUARDANDO_REACEITE', 'EM_ANDAMENTO', 'FINALIZADO', 'CONCLUIDO', 'CANCELADO',
    'PROBLEMA_RELATADO', 'EM_DISPUTA', 'NAO_REALIZADO'));

-- 2) O relato de que o atendimento não foi realizado, a resposta do outro lado
--    (confirmar ou contestar, em até 7 dias) e a decisão do admin.
CREATE TABLE problema_atendimento (
    id BIGSERIAL PRIMARY KEY,
    atendimento_id BIGINT NOT NULL UNIQUE REFERENCES atendimento(id) ON DELETE CASCADE,
    relatado_por VARCHAR(20) NOT NULL CHECK (relatado_por IN ('PACIENTE', 'PROFISSIONAL')),
    motivo VARCHAR(30) NOT NULL CHECK (motivo IN ('OUTRO_NAO_COMPARECEU', 'COMBINADO')),
    descricao VARCHAR(2000) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'AGUARDANDO_RESPOSTA', 'CONFIRMADO', 'SEM_RESPOSTA', 'EM_DISPUTA', 'DECIDIDO')),
    prazo_resposta TIMESTAMPTZ NOT NULL,
    -- Status do atendimento quando o problema foi relatado (para voltar, se o admin decidir que aconteceu).
    status_anterior VARCHAR(30) NOT NULL,
    respondido_em TIMESTAMPTZ,
    contestacao VARCHAR(2000),
    contestado_em TIMESTAMPTZ,
    decisao VARCHAR(20) CHECK (decisao IN ('REALIZADO', 'NAO_REALIZADO')),
    decisao_justificativa VARCHAR(2000),
    decidido_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    decidido_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_problema_status ON problema_atendimento(status, prazo_resposta);

CREATE TABLE problema_anexo (
    id BIGSERIAL PRIMARY KEY,
    problema_id BIGINT NOT NULL REFERENCES problema_atendimento(id) ON DELETE CASCADE,
    lado VARCHAR(20) NOT NULL CHECK (lado IN ('PACIENTE', 'PROFISSIONAL')),
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    enviado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_problema_anexo_problema ON problema_anexo(problema_id);

-- 3) Ocorrências: podem ser lançadas pelo admin (sem solicitação), vir de um
--    problema e ser anuladas com justificativa.
ALTER TABLE ocorrencia
    ALTER COLUMN solicitacao_id DROP NOT NULL,
    ADD COLUMN problema_id BIGINT REFERENCES problema_atendimento(id) ON DELETE SET NULL,
    ADD COLUMN justificativa VARCHAR(1000),
    ADD COLUMN criado_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN anulada_em TIMESTAMPTZ,
    ADD COLUMN anulada_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN justificativa_anulacao VARCHAR(1000);

-- 4) Bloqueio de criar solicitação / enviar proposta, por perfil.
CREATE TABLE bloqueio (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL')),
    nivel INT NOT NULL CHECK (nivel >= 1),
    inicio TIMESTAMPTZ NOT NULL,
    fim TIMESTAMPTZ NOT NULL,
    removido_em TIMESTAMPTZ,
    removido_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    justificativa_remocao VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_bloqueio_periodo CHECK (fim > inicio)
);

CREATE INDEX idx_bloqueio_usuario ON bloqueio(usuario_id, perfil, inicio DESC);

-- 5) O que o admin fez fora da validação (ocorrências, bloqueios, status de
--    solicitação, decisão de disputa), com a justificativa.
CREATE TABLE acao_admin (
    id BIGSERIAL PRIMARY KEY,
    admin_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    tipo VARCHAR(40) NOT NULL,
    alvo_tipo VARCHAR(30) NOT NULL,
    alvo_id BIGINT NOT NULL,
    detalhe VARCHAR(500),
    justificativa VARCHAR(1000) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_acao_admin_alvo ON acao_admin(alvo_tipo, alvo_id, criado_em);
