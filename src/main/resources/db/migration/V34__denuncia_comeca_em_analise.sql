UPDATE denuncia SET status = 'EM_ANALISE' WHERE status = 'ABERTA';

ALTER TABLE denuncia DROP CONSTRAINT IF EXISTS denuncia_status_check;
ALTER TABLE denuncia ADD CONSTRAINT denuncia_status_check
    CHECK (status IN ('EM_ANALISE', 'PROCEDENTE', 'IMPROCEDENTE'));
