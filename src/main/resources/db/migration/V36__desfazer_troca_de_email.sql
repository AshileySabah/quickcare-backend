ALTER TABLE codigo_seguranca DROP CONSTRAINT IF EXISTS codigo_seguranca_finalidade_check;

ALTER TABLE codigo_seguranca
    ADD CONSTRAINT codigo_seguranca_finalidade_check
        CHECK (finalidade IN ('TROCAR_EMAIL', 'ENCERRAR', 'RECUPERAR_CONTA', 'DESFAZER_EMAIL'));

ALTER TABLE codigo_seguranca ADD COLUMN senha_hash_anterior VARCHAR(100);
