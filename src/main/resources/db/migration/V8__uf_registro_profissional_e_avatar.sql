-- UF do conselho/registro profissional (ex.: CRM-SP) é independente da UF do
-- endereço do profissional, que pode residir/atuar em estado diferente do que
-- emitiu seu registro.
ALTER TABLE profissional
    ADD COLUMN registro_profissional_uf VARCHAR(2);

ALTER TABLE usuario
    ADD COLUMN avatar_storage_key VARCHAR(255);
