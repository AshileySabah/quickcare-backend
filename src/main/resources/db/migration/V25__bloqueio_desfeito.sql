-- Bloqueio desfeito: anularam uma das ocorrências que o causaram e as que
-- sobraram não chegam ao limite. Fica no histórico, mas conta como se não
-- tivesse acontecido (não zera o contador nem sobe o nível do próximo).
ALTER TABLE bloqueio ADD COLUMN desfeito BOOLEAN NOT NULL DEFAULT FALSE;
