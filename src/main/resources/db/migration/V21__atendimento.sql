-- Branch 4a: cada proposta aceita vira um atendimento, com data e hora de
-- início e término (no lugar do prazo da proposta).

-- 1) A proposta passa a ter início e término. O prazo antigo (só a data)
--    vira das 08:00 às 18:00 desse dia, no horário de São Paulo.
ALTER TABLE proposta
    ADD COLUMN inicio TIMESTAMPTZ,
    ADD COLUMN termino TIMESTAMPTZ;

UPDATE proposta
SET inicio = (prazo + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
    termino = (prazo + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE proposta
    ALTER COLUMN inicio SET NOT NULL,
    ALTER COLUMN termino SET NOT NULL,
    ADD CONSTRAINT ck_proposta_periodo CHECK (termino > inicio),
    DROP COLUMN prazo;

-- 2) Vagas reservadas: o paciente editou a solicitação e o profissional
--    aceito ainda não reaceitou. A vaga conta como preenchida, mas aceita
--    propostas (o paciente pode escolher outro no lugar).
ALTER TABLE solicitacao_profissional
    ADD COLUMN vagas_reservadas INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_solicitacao_profissional_reservadas
        CHECK (vagas_reservadas >= 0 AND vagas_reservadas <= vagas_preenchidas);

-- 3) O atendimento de uma proposta aceita. Uma proposta pode ter vários ao
--    longo do tempo (aceita, editada, aceita de novo), mas só um ativo.
CREATE TABLE atendimento (
    id BIGSERIAL PRIMARY KEY,
    proposta_id BIGINT NOT NULL REFERENCES proposta(id) ON DELETE CASCADE,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'AGENDADO', 'AGUARDANDO_REACEITE', 'EM_ANDAMENTO', 'FINALIZADO', 'CONCLUIDO', 'CANCELADO')),
    inicio TIMESTAMPTZ NOT NULL,
    termino TIMESTAMPTZ NOT NULL,
    -- Última aceitação (aceite, reaceite ou remarcação aceita): base da regra das 48h.
    aceito_em TIMESTAMPTZ NOT NULL,
    reaceite_solicitado_em TIMESTAMPTZ,
    remarcacao_inicio TIMESTAMPTZ,
    remarcacao_termino TIMESTAMPTZ,
    remarcacao_por VARCHAR(20) CHECK (remarcacao_por IN ('PACIENTE', 'PROFISSIONAL')),
    remarcacao_solicitada_em TIMESTAMPTZ,
    cancelado_por VARCHAR(20) CHECK (cancelado_por IN ('PACIENTE', 'PROFISSIONAL', 'SISTEMA')),
    motivo_cancelamento VARCHAR(40),
    cancelado_em TIMESTAMPTZ,
    lembrete_24h_em TIMESTAMPTZ,
    lembrete_30min_em TIMESTAMPTZ,
    lembrete_inicio_em TIMESTAMPTZ,
    -- Trava otimista: o job e uma ação do usuário podem mexer no mesmo atendimento.
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_atendimento_periodo CHECK (termino > inicio),
    CONSTRAINT ck_atendimento_remarcacao CHECK (
        (remarcacao_inicio IS NULL AND remarcacao_termino IS NULL AND remarcacao_por IS NULL)
        OR (remarcacao_inicio IS NOT NULL AND remarcacao_termino > remarcacao_inicio AND remarcacao_por IS NOT NULL))
);

CREATE UNIQUE INDEX uq_atendimento_ativo_por_proposta ON atendimento(proposta_id)
    WHERE status IN ('AGENDADO', 'AGUARDANDO_REACEITE', 'EM_ANDAMENTO');
CREATE INDEX idx_atendimento_solicitacao ON atendimento(solicitacao_id);
CREATE INDEX idx_atendimento_status_inicio ON atendimento(status, inicio);

-- 4) Auditoria: tudo o que acontece com o atendimento, com o período vigente.
CREATE TABLE atendimento_evento (
    id BIGSERIAL PRIMARY KEY,
    atendimento_id BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    tipo VARCHAR(40) NOT NULL,
    autor_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    inicio TIMESTAMPTZ NOT NULL,
    termino TIMESTAMPTZ NOT NULL,
    detalhe VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_atendimento_evento_atendimento ON atendimento_evento(atendimento_id, criado_em);

-- 5) Ocorrências (desistências que contam). A contagem e o bloqueio vêm na
--    branch 4b; aqui só o registro.
CREATE TABLE ocorrencia (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('PACIENTE', 'PROFISSIONAL')),
    motivo VARCHAR(40) NOT NULL,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao(id) ON DELETE CASCADE,
    atendimento_id BIGINT REFERENCES atendimento(id) ON DELETE SET NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- O paciente leva no máximo uma ocorrência por alteração tardia em cada solicitação.
CREATE UNIQUE INDEX uq_ocorrencia_alteracao_tardia ON ocorrencia(usuario_id, solicitacao_id)
    WHERE motivo = 'ALTERACAO_TARDIA_SOLICITACAO';
CREATE INDEX idx_ocorrencia_usuario ON ocorrencia(usuario_id, perfil, criado_em);

-- 6) As propostas já aceitas viram atendimentos, no estado que as datas indicam.
INSERT INTO atendimento (proposta_id, solicitacao_id, status, inicio, termino, aceito_em, criado_em)
SELECT p.id, p.solicitacao_id,
       CASE
           WHEN s.status = 'CANCELADA' THEN 'CANCELADO'
           WHEN now() < p.inicio THEN 'AGENDADO'
           WHEN now() < p.termino THEN 'EM_ANDAMENTO'
           WHEN now() < p.termino + INTERVAL '72 hours' THEN 'FINALIZADO'
           ELSE 'CONCLUIDO'
       END,
       p.inicio, p.termino, p.atualizado_em, p.atualizado_em
FROM proposta p
JOIN solicitacao s ON s.id = p.solicitacao_id
WHERE p.status = 'ACEITA';
