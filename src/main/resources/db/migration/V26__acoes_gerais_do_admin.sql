-- Ações que o admin faz por iniciativa própria (menu "Gerais").

-- 1) Bloqueio de ação aplicado direto pelo admin (sem as 3 ocorrências):
--    segue o nível da pessoa, tem justificativa e não é desfeito por anular
--    ocorrência.
ALTER TABLE bloqueio
    ADD COLUMN criado_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN justificativa VARCHAR(1000);

-- 2) Bloqueio entre usuários feito pelo admin: mão dupla como os outros, sem
--    ocorrência; não aparece na lista dos dois e só o admin desfaz.
ALTER TABLE bloqueio_usuario DROP CONSTRAINT IF EXISTS bloqueio_usuario_bloqueado_por_check;
ALTER TABLE bloqueio_usuario
    ADD CONSTRAINT bloqueio_usuario_bloqueado_por_check CHECK (bloqueado_por IN ('PACIENTE', 'PROFISSIONAL', 'ADMIN')),
    ADD COLUMN criado_por_usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN justificativa VARCHAR(1000);
