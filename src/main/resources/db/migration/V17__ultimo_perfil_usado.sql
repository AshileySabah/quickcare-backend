-- Perfil (paciente ou profissional) em que o usuário estava por último, para
-- abrir nele no próximo login. Nulo para quem nunca alternou.
ALTER TABLE usuario ADD COLUMN ultimo_perfil VARCHAR(20);
