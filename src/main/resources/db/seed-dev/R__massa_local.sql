CREATE OR REPLACE FUNCTION pg_temp.seed_atuacao(
    p_email TEXT, p_categoria TEXT, p_registro TEXT, p_uf TEXT, p_especialidades TEXT[]
) RETURNS BIGINT AS $$
DECLARE
    v_profissional BIGINT;
    v_atuacao BIGINT;
BEGIN
    SELECT pr.id INTO v_profissional FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = p_email;
    IF v_profissional IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT a.id INTO v_atuacao
    FROM profissional_atuacao a
    WHERE a.profissional_id = v_profissional AND a.categoria = p_categoria
      AND (p_categoria <> 'OUTRO' OR EXISTS (
          SELECT 1 FROM profissional_atuacao_especialidade ae
          JOIN especialidade e ON e.id = ae.especialidade_id
          WHERE ae.atuacao_id = a.id AND e.nome = ANY (p_especialidades)))
    ORDER BY a.id
    LIMIT 1;

    IF v_atuacao IS NOT NULL THEN
        RETURN (SELECT id FROM profissional_atuacao WHERE id = v_atuacao AND removido_em IS NULL);
    END IF;

    INSERT INTO profissional_atuacao (profissional_id, categoria, registro_profissional, registro_profissional_uf)
    VALUES (v_profissional, p_categoria, p_registro, p_uf)
    RETURNING id INTO v_atuacao;
    INSERT INTO profissional_atuacao_evento (atuacao_id, tipo, detalhe) VALUES (v_atuacao, 'INCLUIDA', 'Massa local.');
    INSERT INTO profissional_atuacao_especialidade (atuacao_id, especialidade_id)
    SELECT v_atuacao, e.id FROM especialidade e WHERE e.nome = ANY (p_especialidades) AND e.categoria = p_categoria;
    RETURN v_atuacao;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.atuacao_de(p_profissional BIGINT, p_categoria TEXT) RETURNS BIGINT AS $$
    SELECT a.id
    FROM profissional_atuacao a
    WHERE a.profissional_id = p_profissional AND a.removido_em IS NULL
      AND (p_categoria IS NULL OR a.categoria = p_categoria)
    ORDER BY a.id
    LIMIT 1;
$$ LANGUAGE sql;

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone)
VALUES ('Paciente Teste', 'paciente@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '52998224725', '11987654321')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                      alergias, cep, logradouro, numero, complemento, bairro, cidade, uf)
SELECT id, DATE '1990-05-10', 'PREFIRO_NAO_INFORMAR', TRUE, TRUE,
       'Dipirona', '01310100', 'Avenida Paulista', '1000', 'Apto 12', 'Bela Vista', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'paciente@quickcare.dev'
ON CONFLICT DO NOTHING;

WITH paciente_teste AS (
    SELECT p.id
    FROM paciente p
    JOIN usuario u ON u.id = p.usuario_id
    WHERE u.email = 'paciente@quickcare.dev'
      AND NOT EXISTS (SELECT 1 FROM solicitacao s WHERE s.paciente_id = p.id)
),
novas AS (
    INSERT INTO solicitacao (paciente_id, descricao, modalidade, cep, logradouro, numero, complemento, bairro,
                             cidade, uf, prazo_desejado, status)
    SELECT id, 'Minha mãe precisa de curativos diários e de um cuidador durante o dia.', 'PRESENCIAL',
           '01310100', 'Avenida Paulista', '1000', 'Apto 12', 'Bela Vista', 'São Paulo', 'SP',
           CURRENT_DATE + 15, 'ABERTA'
    FROM paciente_teste
    UNION ALL
    SELECT id, 'Busco acompanhamento nutricional para reeducação alimentar.', 'ONLINE',
           NULL, NULL, NULL, NULL, NULL, NULL, NULL,
           CURRENT_DATE + 30, 'ABERTA'
    FROM paciente_teste
    RETURNING id, modalidade
),
pedidos (modalidade, categoria, quantidade, especialidade) AS (
    VALUES ('PRESENCIAL', 'ENFERMEIRO', 2, 'Curativos'),
           ('PRESENCIAL', 'ENFERMEIRO', 2, 'Sondagem'),
           ('PRESENCIAL', 'CUIDADOR', 1, 'Cuidador de Idosos'),
           ('ONLINE', 'OUTRO', 1, 'Nutrição')
),
profissionais AS (
    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade)
    SELECT DISTINCT n.id, p.categoria, p.quantidade
    FROM novas n
    JOIN pedidos p ON p.modalidade = n.modalidade
    RETURNING id, solicitacao_id, categoria
)
INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
SELECT sp.id, e.id
FROM profissionais sp
JOIN novas n ON n.id = sp.solicitacao_id
JOIN pedidos p ON p.modalidade = n.modalidade AND p.categoria = sp.categoria
JOIN especialidade e ON e.nome = p.especialidade;

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone)
VALUES ('Profissional Teste', 'profissional@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '11144477735', '11912345678')
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, data_nascimento,
                          genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, DATE '1988-03-20', 'PREFIRO_NAO_INFORMAR', TRUE, TRUE, TRUE, 'AMBOS', 15,
       '01311000', 'Rua Augusta', '500', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'profissional@quickcare.dev'
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('profissional@quickcare.dev', 'ENFERMEIRO', '123456', 'SP', ARRAY['Curativos', 'Sondagem']);
UPDATE profissional p
SET latitude = -23.552300, longitude = -46.647000
FROM usuario u
WHERE u.id = p.usuario_id AND u.email = 'profissional@quickcare.dev' AND p.latitude IS NULL;

UPDATE solicitacao s
SET latitude = -23.564887, longitude = -46.651918
FROM paciente pa
JOIN usuario u ON u.id = pa.usuario_id
WHERE s.paciente_id = pa.id AND u.email = 'paciente@quickcare.dev'
  AND s.modalidade = 'PRESENCIAL' AND s.latitude IS NULL;

UPDATE usuario
SET documento_identidade_tipo = 'RG', documento_identidade_numero = '246781312', documento_identidade_uf = 'SP'
WHERE email IN ('paciente@quickcare.dev', 'profissional@quickcare.dev') AND documento_identidade_numero IS NULL;

UPDATE paciente p
SET status_validacao = 'APROVADO'
FROM usuario u
WHERE u.id = p.usuario_id AND u.email = 'paciente@quickcare.dev'
  AND NOT EXISTS (SELECT 1 FROM etapa_validacao e WHERE e.paciente_id = p.id);

UPDATE profissional p
SET status_validacao = 'APROVADO'
FROM usuario u
WHERE u.id = p.usuario_id AND u.email = 'profissional@quickcare.dev'
  AND NOT EXISTS (SELECT 1 FROM etapa_validacao e WHERE e.profissional_id = p.id);

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Ana Duplo Perfil', 'ambos@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '39053344705', '11977776666', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'APROVADO', DATE '1985-07-02', 'FEMININO', TRUE, TRUE,
       '01310100', 'Avenida Paulista', '1500', 'Bela Vista', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'ambos@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao, data_nascimento, genero,
                          confirmacao_informacoes_verdadeiras, consentimento_lgpd, possui_seguro_responsabilidade_civil,
                          modalidade_atendimento, raio_atendimento_km, cep, logradouro, numero, bairro, cidade, uf,
                          latitude, longitude)
SELECT id, 'APROVADO', DATE '1985-07-02', 'FEMININO', TRUE, TRUE, FALSE,
       'PRESENCIAL', 10, '01310100', 'Avenida Paulista', '1500', 'Bela Vista', 'São Paulo', 'SP',
       -23.561400, -46.655900
FROM usuario
WHERE email = 'ambos@quickcare.dev'
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('ambos@quickcare.dev', 'CUIDADOR', NULL, NULL, ARRAY['Cuidador de Idosos']);

INSERT INTO usuario (nome, email, senha_hash, cpf)
VALUES ('Admin QuickCare', 'admin@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '87748248800')
ON CONFLICT DO NOTHING;

INSERT INTO administrador (usuario_id)
SELECT id FROM usuario WHERE email = 'admin@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Rone Pereira', 'paciente.pendente@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '40442820135', '11955554444', 'RG', '123456782', 'SP'),
       ('Suderland Meneses', 'profissional.pendente@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '63017285995', '11944443333', 'CNH', '02650306461', NULL),
       ('Matheus Lima', 'cuidador.pendente@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '01648527949', '11933332222', 'RG', '1234567', 'MG')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'PENDENTE', DATE '1970-11-14', 'MASCULINO', TRUE, TRUE,
       '01310100', 'Avenida Paulista', '2000', 'Bela Vista', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'paciente.pendente@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km, cnpj,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'PENDENTE', DATE '1964-12-26', 'MASCULINO', TRUE, TRUE, TRUE,
       'AMBOS', 10, '00000000000191', '01311000', 'Rua Augusta', '900', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'profissional.pendente@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao, data_nascimento, genero,
                          confirmacao_informacoes_verdadeiras, consentimento_lgpd, possui_seguro_responsabilidade_civil,
                          modalidade_atendimento, raio_atendimento_km, cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'PENDENTE', DATE '1996-02-18', 'MASCULINO', TRUE, TRUE, FALSE,
       'PRESENCIAL', 5, '01311000', 'Rua Augusta', '1200', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'cuidador.pendente@quickcare.dev'
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('profissional.pendente@quickcare.dev', 'ENFERMEIRO', '654321', 'SP', ARRAY['Curativos']);
SELECT pg_temp.seed_atuacao('cuidador.pendente@quickcare.dev', 'CUIDADOR', NULL, NULL, ARRAY['Cuidador de Idosos']);

WITH pendentes (email, perfil, tipo_documento, tipo_etapa, nome_arquivo) AS (
    VALUES ('paciente.pendente@quickcare.dev', 'PACIENTE', 'COMPROVANTE_CPF', 'CPF', 'cpf-rone.pdf'),
           ('paciente.pendente@quickcare.dev', 'PACIENTE', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-rone.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'COMPROVANTE_CPF', 'CPF', 'cpf-suderland.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CPF', 'IDENTIDADE', 'cnh-suderland.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CNPJ', 'CNPJ', 'cartao-cnpj.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'coren.pdf'),
           ('cuidador.pendente@quickcare.dev', 'PROFISSIONAL', 'COMPROVANTE_CPF', 'CPF', 'cpf-matheus.pdf'),
           ('cuidador.pendente@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-matheus.pdf')
),
donos AS (
    SELECT pe.*, pa.id AS paciente_id, pr.id AS profissional_id
    FROM pendentes pe
    JOIN usuario u ON u.email = pe.email
    LEFT JOIN paciente pa ON pa.usuario_id = u.id AND pe.perfil = 'PACIENTE'
    LEFT JOIN profissional pr ON pr.usuario_id = u.id AND pe.perfil = 'PROFISSIONAL'
    WHERE NOT EXISTS (
        SELECT 1 FROM etapa_validacao e
        WHERE e.paciente_id IS NOT DISTINCT FROM pa.id
          AND e.profissional_id IS NOT DISTINCT FROM pr.id
          AND e.tipo = pe.tipo_etapa
    )
),
documentos AS (
    INSERT INTO documento (paciente_id, profissional_id, tipo_documento, nome_arquivo, content_type, tamanho_bytes,
                           storage_key)
    SELECT paciente_id, profissional_id, tipo_documento, nome_arquivo, 'application/pdf', 102400,
           'seed-dev/' || nome_arquivo
    FROM donos
    RETURNING id, paciente_id, profissional_id, tipo_documento
)
INSERT INTO etapa_validacao (paciente_id, profissional_id, atuacao_id, tipo, status, documento_id)
SELECT d.paciente_id, d.profissional_id,
       CASE WHEN dn.tipo_etapa = 'REGISTRO_PROFISSIONAL' THEN pg_temp.atuacao_de(d.profissional_id, NULL) END,
       dn.tipo_etapa, 'PENDENTE', d.id
FROM documentos d
JOIN donos dn ON dn.paciente_id IS NOT DISTINCT FROM d.paciente_id
             AND dn.profissional_id IS NOT DISTINCT FROM d.profissional_id
             AND dn.tipo_documento = d.tipo_documento;

WITH presencial AS (
    SELECT s.id
    FROM solicitacao s
    JOIN paciente pa ON pa.id = s.paciente_id
    JOIN usuario u ON u.id = pa.usuario_id
    WHERE u.email = 'paciente@quickcare.dev' AND s.modalidade = 'PRESENCIAL'
    ORDER BY s.id
    LIMIT 1
),
propostas (email, status, valor, abordagem) AS (
    VALUES ('profissional@quickcare.dev', 'PENDENTE', 180.00,
            'Curativos diários com troca de cobertura e registro da evolução da ferida.'),
           ('ambos@quickcare.dev', 'ACEITA', 150.00,
            'Acompanhamento durante o dia, com apoio na alimentação, higiene e medicação.')
)
INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, solicitacao_profissional_id, valor, abordagem,
                      inicio, termino, status)
SELECT presencial.id, pr.id, pg_temp.atuacao_de(pr.id, NULL),
       CASE WHEN p.status = 'ACEITA'
            THEN (SELECT sp.id FROM solicitacao_profissional sp
                  WHERE sp.solicitacao_id = presencial.id AND sp.categoria = 'CUIDADOR')
       END,
       p.valor, p.abordagem, (CURRENT_DATE + 10 + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo', (CURRENT_DATE + 10 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo', p.status
FROM presencial
CROSS JOIN propostas p
JOIN usuario u ON u.email = p.email
JOIN profissional pr ON pr.usuario_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM proposta x
    WHERE x.solicitacao_id = presencial.id AND x.profissional_id = pr.id AND x.status <> 'CANCELADA'
);

UPDATE solicitacao_profissional sp
SET vagas_preenchidas = 1
FROM proposta p
WHERE p.solicitacao_profissional_id = sp.id AND p.status = 'ACEITA' AND sp.vagas_preenchidas = 0;

UPDATE solicitacao s
SET status = 'RECEBENDO_PROPOSTAS'
WHERE s.status = 'ABERTA' AND EXISTS (SELECT 1 FROM proposta p WHERE p.solicitacao_id = s.id AND p.status <> 'CANCELADA');

UPDATE paciente p
SET condicoes_saude = 'Hipertensão controlada',
    medicamentos_em_uso = 'Losartana 50 mg, 1x ao dia'
FROM usuario u
WHERE u.id = p.usuario_id AND u.email = 'paciente@quickcare.dev' AND p.condicoes_saude IS NULL;

INSERT INTO paciente_contato_emergencia (paciente_id, nome, telefone)
SELECT p.id, c.nome, c.telefone
FROM paciente p
JOIN usuario u ON u.id = p.usuario_id
CROSS JOIN (VALUES ('Lúcia Teste (filha)', '11988887777'), ('Pedro Teste (irmão)', '11977776655')) AS c (nome, telefone)
WHERE u.email = 'paciente@quickcare.dev'
  AND NOT EXISTS (SELECT 1 FROM paciente_contato_emergencia x WHERE x.paciente_id = p.id);

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Carla Mendes', 'nutricionista@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '71460238001', '19966665544', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'APROVADO', DATE '1991-09-15', 'FEMININO', TRUE, TRUE, TRUE, 'REMOTO',
       '13010001', 'Rua Barão de Jaguara', '700', 'Centro', 'Campinas', 'SP'
FROM usuario
WHERE email = 'nutricionista@quickcare.dev'
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('nutricionista@quickcare.dev', 'OUTRO', 'CRN-3 54321', 'SP', ARRAY['Nutrição']);

INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, valor, abordagem, inicio, termino, status)
SELECT s.id, pr.id, pg_temp.atuacao_de(pr.id, 'OUTRO'), 120.00,
       'Avaliação inicial por vídeo e plano alimentar com retorno quinzenal pelos próximos três meses.',
       (CURRENT_DATE + 20 + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo', (CURRENT_DATE + 20 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo', 'PENDENTE'
FROM solicitacao s
JOIN paciente pa ON pa.id = s.paciente_id
JOIN usuario up ON up.id = pa.usuario_id AND up.email = 'paciente@quickcare.dev'
JOIN usuario un ON un.email = 'nutricionista@quickcare.dev'
JOIN profissional pr ON pr.usuario_id = un.id
WHERE s.modalidade = 'ONLINE'
  AND NOT EXISTS (SELECT 1 FROM proposta x WHERE x.solicitacao_id = s.id AND x.profissional_id = pr.id);

UPDATE solicitacao s
SET status = 'RECEBENDO_PROPOSTAS'
WHERE s.status = 'ABERTA' AND EXISTS (SELECT 1 FROM proposta p WHERE p.solicitacao_id = s.id AND p.status <> 'CANCELADA');

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Maria Oliveira', 'maria@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '38522147698', '11955553322', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, alergias, condicoes_saude, medicamentos_em_uso,
                      cep, logradouro, numero, complemento, bairro, cidade, uf)
SELECT id, 'APROVADO', DATE '1948-03-08', 'FEMININO', TRUE, TRUE,
       'Penicilina', 'Diabetes tipo 2; ferida no pé direito em cicatrização', 'Metformina 850 mg, 2x ao dia',
       '01311200', 'Rua Haddock Lobo', '300', 'Apto 41', 'Cerqueira César', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'maria@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO paciente_contato_emergencia (paciente_id, nome, telefone)
SELECT p.id, 'Ricardo Oliveira (filho)', '11944441234'
FROM paciente p
JOIN usuario u ON u.id = p.usuario_id
WHERE u.email = 'maria@quickcare.dev'
  AND NOT EXISTS (SELECT 1 FROM paciente_contato_emergencia x WHERE x.paciente_id = p.id);

WITH maria AS (
    SELECT p.id
    FROM paciente p
    JOIN usuario u ON u.id = p.usuario_id
    WHERE u.email = 'maria@quickcare.dev'
      AND NOT EXISTS (SELECT 1 FROM solicitacao s WHERE s.paciente_id = p.id)
),
nova AS (
    INSERT INTO solicitacao (paciente_id, descricao, modalidade, cep, logradouro, numero, complemento, bairro,
                             cidade, uf, latitude, longitude, prazo_desejado, status)
    SELECT id, 'Curativo diário da ferida no pé, com acompanhamento da glicemia.', 'PRESENCIAL',
           '01311200', 'Rua Haddock Lobo', '300', 'Apto 41', 'Cerqueira César', 'São Paulo', 'SP',
           -23.558000, -46.660000, CURRENT_DATE + 7, 'EM_ANDAMENTO'
    FROM maria
    RETURNING id
),
linha AS (
    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade, vagas_preenchidas)
    SELECT id, 'ENFERMEIRO', 1, 1 FROM nova
    RETURNING id, solicitacao_id
),
especialidade_da_linha AS (
    INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
    SELECT linha.id, e.id FROM linha JOIN especialidade e ON e.nome = 'Curativos'
)
INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, solicitacao_profissional_id, valor, abordagem,
                      inicio, termino, status)
SELECT linha.solicitacao_id, pr.id, pg_temp.atuacao_de(pr.id, 'ENFERMEIRO'), linha.id, 200.00,
       'Curativo diário com avaliação da ferida e orientação sobre o controle da glicemia.',
       (CURRENT_DATE + 7 + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo', (CURRENT_DATE + 7 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo', 'ACEITA'
FROM linha
JOIN usuario u ON u.email = 'profissional@quickcare.dev'
JOIN profissional pr ON pr.usuario_id = u.id;

INSERT INTO profissional_contato_emergencia (profissional_id, nome, telefone)
SELECT p.id, c.nome, c.telefone
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN (VALUES ('profissional@quickcare.dev', 'Marcos Teste (cônjuge)', '11933334444'),
             ('ambos@quickcare.dev', 'Bruno Duplo (irmão)', '11922223333')) AS c (email, nome, telefone)
  ON c.email = u.email
WHERE NOT EXISTS (SELECT 1 FROM profissional_contato_emergencia x WHERE x.profissional_id = p.id);

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Lúcia Alves', 'lucia@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '61328045790', '11951112233', 'RG', '246781312', 'SP'),
       ('Roberto Dias', 'roberto@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '74219536809', '11952223344', 'RG', '246781312', 'SP'),
       ('Sônia Ramos', 'sonia@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '80637125444', '11953334455', 'RG', '246781312', 'SP'),
       ('Fernanda Rocha', 'fernanda@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '93514862737', '11954445566', 'RG', '246781312', 'SP'),
       ('Gustavo Prado', 'gustavo@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '46802731940', '11955556677', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, cep, logradouro, numero, bairro, cidade, uf)
SELECT u.id, 'APROVADO', p.nascimento, p.genero, TRUE, TRUE,
       '01311200', 'Rua Haddock Lobo', p.numero, 'Cerqueira César', 'São Paulo', 'SP'
FROM (VALUES ('lucia@quickcare.dev', DATE '1960-05-12', 'FEMININO', '120'),
             ('roberto@quickcare.dev', DATE '1955-11-02', 'MASCULINO', '140'),
             ('sonia@quickcare.dev', DATE '1970-01-25', 'FEMININO', '160')) AS p (email, nascimento, genero, numero)
JOIN usuario u ON u.email = p.email
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf, latitude, longitude)
SELECT u.id, 'APROVADO', p.nascimento, p.genero, TRUE, TRUE, TRUE, 'AMBOS', 20,
       '01311000', 'Rua Augusta', p.numero, 'Consolação', 'São Paulo', 'SP', -23.555000, -46.655000
FROM (VALUES ('fernanda@quickcare.dev', DATE '1990-07-18', 'FEMININO', '1000'),
             ('gustavo@quickcare.dev', DATE '1986-02-09', 'MASCULINO', '1100')) AS p (email, nascimento, genero, numero)
JOIN usuario u ON u.email = p.email
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('fernanda@quickcare.dev', 'ENFERMEIRO', '234567', 'SP', ARRAY['Curativos']);
SELECT pg_temp.seed_atuacao('gustavo@quickcare.dev', 'ENFERMEIRO', '345678', 'SP', ARRAY['Curativos']);

WITH avaliadas (email, perfil, tipo_documento, tipo_etapa, nome_arquivo, status, justificativa, dias_atras) AS (
    VALUES ('paciente@quickcare.dev', 'PACIENTE', 'COMPROVANTE_CPF', 'CPF', 'cpf-paciente.pdf', 'APROVADA',
            'CPF regular na Receita e igual ao do documento.', 30),
           ('paciente@quickcare.dev', 'PACIENTE', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-paciente.pdf', 'APROVADA',
            'RG legível, dados conferem com o cadastro.', 30),
           ('profissional@quickcare.dev', 'PROFISSIONAL', 'COMPROVANTE_CPF', 'CPF', 'cpf-profissional.pdf', 'APROVADA',
            'CPF regular na Receita.', 30),
           ('profissional@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CPF', 'IDENTIDADE', 'cnh-profissional.pdf', 'APROVADA',
            'CNH válida, dados conferem.', 30),
           ('profissional@quickcare.dev', 'PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'coren-profissional.pdf',
            'APROVADA', 'COREN ativo no portal do conselho.', 30),
           ('fernanda@quickcare.dev', 'PROFISSIONAL', 'COMPROVANTE_CPF', 'CPF', 'cpf-fernanda.pdf', 'APROVADA',
            'CPF regular na Receita.', 20),
           ('fernanda@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-fernanda.pdf', 'APROVADA',
            'RG legível, dados conferem.', 20),
           ('fernanda@quickcare.dev', 'PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'coren-fernanda.pdf',
            'APROVADA', 'COREN ativo no portal do conselho.', 20),
           ('roberto@quickcare.dev', 'PACIENTE', 'COMPROVANTE_CPF', 'CPF', 'cpf-roberto.pdf', 'APROVADA',
            'CPF regular na Receita.', 40),
           ('roberto@quickcare.dev', 'PACIENTE', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-roberto-novo.pdf', 'REPROVADA',
            'A foto do RG novo está cortada e não dá para ler a data de nascimento. Envie de novo, inteiro e legível.', 1)
),
donos AS (
    SELECT av.*, pa.id AS paciente_id, pr.id AS profissional_id
    FROM avaliadas av
    JOIN usuario u ON u.email = av.email
    LEFT JOIN paciente pa ON pa.usuario_id = u.id AND av.perfil = 'PACIENTE'
    LEFT JOIN profissional pr ON pr.usuario_id = u.id AND av.perfil = 'PROFISSIONAL'
    WHERE NOT EXISTS (
        SELECT 1 FROM etapa_validacao e
        WHERE e.paciente_id IS NOT DISTINCT FROM pa.id
          AND e.profissional_id IS NOT DISTINCT FROM pr.id
          AND e.tipo = av.tipo_etapa
    )
),
documentos AS (
    INSERT INTO documento (paciente_id, profissional_id, tipo_documento, nome_arquivo, content_type, tamanho_bytes,
                           storage_key, enviado_em)
    SELECT paciente_id, profissional_id, tipo_documento, nome_arquivo, 'application/pdf', 102400,
           'seed-dev/' || nome_arquivo, now() - make_interval(days => dias_atras + 1)
    FROM donos
    RETURNING id, paciente_id, profissional_id, tipo_documento
)
INSERT INTO etapa_validacao (paciente_id, profissional_id, atuacao_id, tipo, status, documento_id, justificativa,
                             avaliado_por_id, avaliado_em, criado_em)
SELECT d.paciente_id, d.profissional_id,
       CASE WHEN dn.tipo_etapa = 'REGISTRO_PROFISSIONAL' THEN pg_temp.atuacao_de(d.profissional_id, 'ENFERMEIRO') END,
       dn.tipo_etapa, dn.status, d.id, dn.justificativa,
       adm.id, now() - make_interval(days => dn.dias_atras), now() - make_interval(days => dn.dias_atras + 1)
FROM documentos d
JOIN donos dn ON dn.paciente_id IS NOT DISTINCT FROM d.paciente_id
             AND dn.profissional_id IS NOT DISTINCT FROM d.profissional_id
             AND dn.tipo_documento = d.tipo_documento
CROSS JOIN (SELECT usuario_id AS id FROM administrador ORDER BY id LIMIT 1) adm;

WITH nova_atuacao AS (
    SELECT pr.id AS profissional_id,
           pg_temp.seed_atuacao('profissional@quickcare.dev', 'MEDICO', '98765', 'SP', ARRAY['Clínica Médica', 'Geriatria'])
               AS atuacao_id
    FROM profissional pr
    JOIN usuario u ON u.id = pr.usuario_id
    WHERE u.email = 'profissional@quickcare.dev'
),
sem_etapa AS (
    SELECT n.* FROM nova_atuacao n
    WHERE n.atuacao_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM etapa_validacao e WHERE e.atuacao_id = n.atuacao_id)
),
documento_crm AS (
    INSERT INTO documento (profissional_id, tipo_documento, nome_arquivo, content_type, tamanho_bytes, storage_key)
    SELECT profissional_id, 'REGISTRO_PROFISSIONAL', 'crm-profissional.pdf', 'application/pdf', 102400,
           'seed-dev/crm-profissional.pdf'
    FROM sem_etapa
    RETURNING id, profissional_id
)
INSERT INTO etapa_validacao (profissional_id, atuacao_id, tipo, status, documento_id)
SELECT d.profissional_id, s.atuacao_id, 'REGISTRO_PROFISSIONAL', 'PENDENTE', d.id
FROM documento_crm d
JOIN sem_etapa s ON s.profissional_id = d.profissional_id;

CREATE OR REPLACE FUNCTION pg_temp.seed_solicitacao(
    p_email TEXT, p_descricao TEXT, p_modalidade TEXT, p_categoria TEXT, p_especialidade TEXT,
    p_status TEXT, p_prazo_dias INT, p_dias_atras INT
) RETURNS BIGINT AS $$
DECLARE
    v_paciente paciente%ROWTYPE;
    v_id BIGINT;
    v_linha BIGINT;
BEGIN
    SELECT pa.* INTO v_paciente FROM paciente pa JOIN usuario u ON u.id = pa.usuario_id WHERE u.email = p_email;
    SELECT id INTO v_id FROM solicitacao WHERE paciente_id = v_paciente.id AND descricao = p_descricao;
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;

    INSERT INTO solicitacao (paciente_id, descricao, modalidade, cep, logradouro, numero, complemento, bairro, cidade,
                             uf, latitude, longitude, prazo_desejado, status, criado_em, atualizado_em)
    VALUES (v_paciente.id, p_descricao, p_modalidade,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.cep END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.logradouro END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.numero END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.complemento END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.bairro END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.cidade END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN v_paciente.uf END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN -23.560000 END,
            CASE WHEN p_modalidade = 'PRESENCIAL' THEN -46.656000 END,
            CURRENT_DATE + p_prazo_dias, p_status, now() - make_interval(days => p_dias_atras), now())
    RETURNING id INTO v_id;

    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade) VALUES (v_id, p_categoria, 1)
    RETURNING id INTO v_linha;
    INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
    SELECT v_linha, e.id FROM especialidade e WHERE e.nome = p_especialidade;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.seed_proposta(
    p_solicitacao BIGINT, p_email TEXT, p_valor NUMERIC, p_abordagem TEXT, p_status TEXT, p_dias_atras INT
) RETURNS VOID AS $$
DECLARE
    v_profissional BIGINT;
    v_linha BIGINT;
BEGIN
    SELECT pr.id INTO v_profissional FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = p_email;
    IF EXISTS (SELECT 1 FROM proposta WHERE solicitacao_id = p_solicitacao AND profissional_id = v_profissional) THEN
        RETURN;
    END IF;

    IF p_status = 'ACEITA' THEN
        SELECT id INTO v_linha FROM solicitacao_profissional WHERE solicitacao_id = p_solicitacao LIMIT 1;
        UPDATE solicitacao_profissional SET vagas_preenchidas = vagas_preenchidas + 1 WHERE id = v_linha;
    END IF;

    INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, solicitacao_profissional_id, valor, abordagem,
                          inicio, termino, status, criado_em, atualizado_em)
    SELECT p_solicitacao, v_profissional,
           pg_temp.atuacao_de(v_profissional,
                              (SELECT sp.categoria FROM solicitacao_profissional sp
                               WHERE sp.solicitacao_id = p_solicitacao ORDER BY sp.id LIMIT 1)),
           v_linha, p_valor, p_abordagem,
           (s.prazo_desejado + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
           (s.prazo_desejado + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo',
           p_status, now() - make_interval(days => p_dias_atras), now()
    FROM solicitacao s WHERE s.id = p_solicitacao;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('ambos@quickcare.dev',
           'Acompanhamento nutricional para controle do colesterol.', 'ONLINE', 'OUTRO', 'Nutrição',
           'RECEBENDO_PROPOSTAS', 20, 2),
       'nutricionista@quickcare.dev', 110.00, 'Consulta por vídeo com plano alimentar e retorno mensal.', 'PENDENTE', 1);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('ambos@quickcare.dev',
           'Curativo pós-cirúrgico no joelho, três vezes por semana.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'RECEBENDO_PROPOSTAS', 10, 4),
       'profissional@quickcare.dev', 160.00, 'Curativo com técnica estéril e acompanhamento da cicatrização.', 'PENDENTE', 3);
SELECT pg_temp.seed_solicitacao('ambos@quickcare.dev',
       'Sondagem vesical para a minha mãe, uma vez por semana.', 'PRESENCIAL', 'ENFERMEIRO', 'Sondagem',
       'ABERTA', 15, 1);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('ambos@quickcare.dev',
           'Curativo de uma queimadura leve na mão.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'CONCLUIDA', -20, 40),
       'profissional@quickcare.dev', 140.00, 'Curativos diários até a cicatrização completa.', 'ACEITA', 38);
SELECT pg_temp.seed_solicitacao('ambos@quickcare.dev',
       'Avaliação nutricional para ganho de massa.', 'ONLINE', 'OUTRO', 'Nutrição',
       'CANCELADA', -5, 25);

SELECT pg_temp.seed_solicitacao('maria@quickcare.dev',
       'Cuidadora para acompanhar minha mãe nas tardes de semana.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos',
       'ABERTA', 12, 1);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('maria@quickcare.dev',
           'Acompanhante para consultas médicas e exames.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos',
           'RECEBENDO_PROPOSTAS', 9, 3),
       'ambos@quickcare.dev', 90.00, 'Acompanho nas consultas, ajudo com documentos e oriento sobre as receitas.', 'PENDENTE', 2);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('maria@quickcare.dev',
           'Cuidadora para os fins de semana, das 8h às 18h.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos',
           'EM_ANDAMENTO', 5, 8),
       'ambos@quickcare.dev', 180.00, 'Cuidado nos fins de semana: alimentação, higiene, medicação e companhia.', 'ACEITA', 7);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('paciente@quickcare.dev',
           'Cuidadora para o pós-operatório do meu pai por duas semanas.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos',
           'CONCLUIDA', -10, 30),
       'ambos@quickcare.dev', 1500.00, 'Acompanhamento integral no pós-operatório, com relatório diário.', 'ACEITA', 29);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('paciente@quickcare.dev',
           'Cuidadora noturna por um mês.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos',
           'CANCELADA', -2, 20),
       'ambos@quickcare.dev', 2400.00, 'Plantão noturno de 12h, cinco noites por semana.', 'RECUSADA', 19);

SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('paciente@quickcare.dev',
           'Curativo na perna depois de uma queda, uma visita.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', -1, 6),
       'profissional@quickcare.dev', 150.00, 'Limpeza e curativo da ferida com orientação para os próximos dias.', 'ACEITA', 5);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('maria@quickcare.dev',
           'Curativo semanal na perna, com relatório para a médica.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', -2, 8),
       'profissional@quickcare.dev', 170.00, 'Curativo com registro fotográfico e relatório para a médica.', 'ACEITA', 7);

SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('lucia@quickcare.dev',
           'Teste de bloqueio: curativo no braço daqui a 10 dias.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 10, 6),
       'fernanda@quickcare.dev', 150.00, 'Curativo no braço com orientação de cuidados em casa.', 'ACEITA', 5);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('lucia@quickcare.dev',
           'Teste de bloqueio: curativo na perna em menos de 48h.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 2, 8),
       'gustavo@quickcare.dev', 160.00, 'Curativo na perna e troca do curativo oclusivo.', 'ACEITA', 7);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('roberto@quickcare.dev',
           'Teste de bloqueio: curativo no pé daqui a 10 dias.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 10, 6),
       'fernanda@quickcare.dev', 170.00, 'Curativo no pé diabético com registro fotográfico.', 'ACEITA', 5);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('roberto@quickcare.dev',
           'Teste de bloqueio: curativo nas costas em menos de 48h.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 2, 8),
       'gustavo@quickcare.dev', 180.00, 'Curativo de escara com mudança de decúbito orientada.', 'ACEITA', 7);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('sonia@quickcare.dev',
           'Teste de bloqueio: curativo na mão, aceito em cima da hora.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 2, 1),
       'fernanda@quickcare.dev', 140.00, 'Curativo na mão com retorno no dia seguinte.', 'ACEITA', 0);
SELECT pg_temp.seed_proposta(pg_temp.seed_solicitacao('sonia@quickcare.dev',
           'Teste de bloqueio: curativo no ombro em menos de 48h.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos',
           'EM_ANDAMENTO', 2, 8),
       'gustavo@quickcare.dev', 155.00, 'Curativo no ombro com avaliação da cicatrização.', 'ACEITA', 6);

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Helena Souza', 'helena@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '28571936455', '11961110001', 'RG', '246781312', 'SP'),
       ('Bruno Teixeira', 'bruno@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '31946825719', '11961110002', 'RG', '246781312', 'SP'),
       ('Camila Duarte', 'camila@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '42735198600', '11961110003', 'RG', '246781312', 'SP'),
       ('Diego Martins', 'diego@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '53816472982', '11961110004', 'RG', '246781312', 'SP'),
       ('Eduardo Nunes', 'eduardo@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '64927351882', '11961110005', 'RG', '246781312', 'SP'),
       ('Fábio Castro', 'fabio@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '75138269437', '11961110006', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'APROVADO', DATE '1958-04-21', 'FEMININO', TRUE, TRUE,
       '01311200', 'Rua Haddock Lobo', '420', 'Cerqueira César', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'helena@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf, latitude, longitude)
SELECT u.id, 'APROVADO', p.nascimento, p.genero, TRUE, TRUE, TRUE, 'AMBOS', 20,
       '01311000', 'Rua Augusta', p.numero, 'Consolação', 'São Paulo', 'SP', -23.555000, -46.655000
FROM (VALUES ('bruno@quickcare.dev', DATE '1989-06-03', 'MASCULINO', '1300'),
             ('camila@quickcare.dev', DATE '1992-10-27', 'FEMININO', '1400'),
             ('diego@quickcare.dev', DATE '1984-01-15', 'MASCULINO', '1500'),
             ('eduardo@quickcare.dev', DATE '1981-08-30', 'MASCULINO', '1600'),
             ('fabio@quickcare.dev', DATE '1987-12-09', 'MASCULINO', '1700')) AS p (email, nascimento, genero, numero)
JOIN usuario u ON u.email = p.email
ON CONFLICT DO NOTHING;

SELECT pg_temp.seed_atuacao('bruno@quickcare.dev', 'ENFERMEIRO', '411111', 'SP', ARRAY['Acompanhamento 12h']);
SELECT pg_temp.seed_atuacao('camila@quickcare.dev', 'ENFERMEIRO', '422222', 'SP', ARRAY['Acompanhamento 24h']);
SELECT pg_temp.seed_atuacao('camila@quickcare.dev', 'CUIDADOR', NULL, NULL, ARRAY['Acompanhante Hospitalar']);
SELECT pg_temp.seed_atuacao('diego@quickcare.dev', 'ENFERMEIRO', '433333', 'SP', ARRAY['Cuidados com Ostomia']);
SELECT pg_temp.seed_atuacao('diego@quickcare.dev', 'OUTRO', 'CRP 06/123456', 'SP', ARRAY['Psicologia']);
SELECT pg_temp.seed_atuacao('eduardo@quickcare.dev', 'ENFERMEIRO', '444444', 'SP', ARRAY['Aplicação de Injeção', 'Sondagem']);
SELECT pg_temp.seed_atuacao('eduardo@quickcare.dev', 'CUIDADOR', NULL, NULL, ARRAY['Cuidador de Idosos']);
SELECT pg_temp.seed_atuacao('eduardo@quickcare.dev', 'OUTRO', 'CREFITO-3 112233', 'SP', ARRAY['Fisioterapia']);
SELECT pg_temp.seed_atuacao('fabio@quickcare.dev', 'ENFERMEIRO', '455555', 'SP', ARRAY['Curativos']);
SELECT pg_temp.seed_atuacao('fabio@quickcare.dev', 'CUIDADOR', NULL, NULL, ARRAY['Cuidador de Idosos']);
SELECT pg_temp.seed_atuacao('fabio@quickcare.dev', 'OUTRO', 'CRN-3 77889', 'SP', ARRAY['Nutrição']);

CREATE OR REPLACE FUNCTION pg_temp.seed_etapa_profissional(
    p_email TEXT, p_tipo TEXT, p_categoria TEXT, p_status TEXT, p_dias_atras INT, p_justificativa TEXT, p_arquivo TEXT
) RETURNS VOID AS $$
DECLARE
    v_profissional BIGINT;
    v_atuacao BIGINT;
    v_documento BIGINT;
BEGIN
    SELECT pr.id INTO v_profissional FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = p_email;
    IF v_profissional IS NULL
       OR EXISTS (SELECT 1 FROM documento d WHERE d.profissional_id = v_profissional AND d.storage_key = 'seed-dev/' || p_arquivo) THEN
        RETURN;
    END IF;

    IF p_tipo = 'REGISTRO_PROFISSIONAL' THEN
        v_atuacao := pg_temp.atuacao_de(v_profissional, p_categoria);
        IF v_atuacao IS NULL THEN
            RETURN;
        END IF;
    END IF;

    INSERT INTO documento (profissional_id, tipo_documento, nome_arquivo, content_type, tamanho_bytes, storage_key, enviado_em)
    VALUES (v_profissional,
            CASE p_tipo WHEN 'CPF' THEN 'COMPROVANTE_CPF' WHEN 'IDENTIDADE' THEN 'VALIDACAO_CPF' ELSE p_tipo END,
            p_arquivo, 'application/pdf', 102400, 'seed-dev/' || p_arquivo, now() - make_interval(days => p_dias_atras + 1))
    RETURNING id INTO v_documento;

    INSERT INTO etapa_validacao (profissional_id, atuacao_id, tipo, status, documento_id, justificativa,
                                 avaliado_por_id, avaliado_em, criado_em)
    SELECT v_profissional, v_atuacao, p_tipo, p_status, v_documento, p_justificativa,
           CASE WHEN p_status <> 'PENDENTE' THEN adm.id END,
           CASE WHEN p_status <> 'PENDENTE' THEN now() - make_interval(days => p_dias_atras) END,
           now() - make_interval(days => p_dias_atras + 1)
    FROM (SELECT usuario_id AS id FROM administrador ORDER BY id LIMIT 1) adm;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_etapa_profissional(e.email, e.tipo, e.categoria, 'APROVADA', 30, e.justificativa, e.arquivo)
FROM (VALUES ('bruno@quickcare.dev', 'CPF', NULL, 'CPF regular na Receita.', 'cpf-bruno.pdf'),
             ('bruno@quickcare.dev', 'IDENTIDADE', NULL, 'RG legível, dados conferem.', 'rg-bruno.pdf'),
             ('bruno@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'COREN ativo no portal do conselho.', 'coren-bruno.pdf'),
             ('camila@quickcare.dev', 'CPF', NULL, 'CPF regular na Receita.', 'cpf-camila.pdf'),
             ('camila@quickcare.dev', 'IDENTIDADE', NULL, 'RG legível, dados conferem.', 'rg-camila.pdf'),
             ('camila@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'COREN ativo no portal do conselho.', 'coren-camila.pdf'),
             ('diego@quickcare.dev', 'CPF', NULL, 'CPF regular na Receita.', 'cpf-diego.pdf'),
             ('diego@quickcare.dev', 'IDENTIDADE', NULL, 'RG legível, dados conferem.', 'rg-diego.pdf'),
             ('diego@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'COREN ativo no portal do conselho.', 'coren-diego-antigo.pdf'),
             ('diego@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'OUTRO', 'CRP ativo no portal do conselho.', 'crp-diego.pdf'),
             ('eduardo@quickcare.dev', 'CPF', NULL, 'CPF regular na Receita.', 'cpf-eduardo.pdf'),
             ('eduardo@quickcare.dev', 'IDENTIDADE', NULL, 'RG legível, dados conferem.', 'rg-eduardo.pdf'),
             ('eduardo@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'COREN ativo no portal do conselho.', 'coren-eduardo.pdf'),
             ('eduardo@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'OUTRO', 'CREFITO ativo no portal do conselho.', 'crefito-eduardo.pdf'),
             ('fabio@quickcare.dev', 'CPF', NULL, 'CPF regular na Receita.', 'cpf-fabio.pdf'),
             ('fabio@quickcare.dev', 'IDENTIDADE', NULL, 'RG legível, dados conferem.', 'rg-fabio.pdf'),
             ('fabio@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'COREN ativo no portal do conselho.', 'coren-fabio.pdf'),
             ('fabio@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'OUTRO', 'CRN ativo no portal do conselho.', 'crn-fabio.pdf'))
     AS e (email, tipo, categoria, justificativa, arquivo);

SELECT pg_temp.seed_etapa_profissional('diego@quickcare.dev', 'REGISTRO_PROFISSIONAL', 'ENFERMEIRO', 'PENDENTE', 1, NULL,
       'coren-diego-novo.pdf');

UPDATE profissional_atuacao a
SET registro_profissional = '433399'
FROM profissional pr
JOIN usuario u ON u.id = pr.usuario_id
WHERE a.profissional_id = pr.id AND u.email = 'diego@quickcare.dev' AND a.categoria = 'ENFERMEIRO'
  AND a.registro_profissional = '433333';

INSERT INTO profissional_atuacao_evento (atuacao_id, tipo, detalhe)
SELECT a.id, 'REGISTRO_ALTERADO', 'Registro alterado de 433333/SP para 433399/SP (massa local).'
FROM profissional_atuacao a
JOIN profissional pr ON pr.id = a.profissional_id
JOIN usuario u ON u.id = pr.usuario_id
WHERE u.email = 'diego@quickcare.dev' AND a.categoria = 'ENFERMEIRO' AND a.removido_em IS NULL
  AND NOT EXISTS (SELECT 1 FROM profissional_atuacao_evento x WHERE x.atuacao_id = a.id AND x.tipo = 'REGISTRO_ALTERADO');

CREATE OR REPLACE FUNCTION pg_temp.solicitacao_id(p_descricao TEXT) RETURNS BIGINT AS $$
    SELECT id FROM solicitacao WHERE descricao = p_descricao ORDER BY id LIMIT 1;
$$ LANGUAGE sql;

CREATE OR REPLACE FUNCTION pg_temp.seed_vaga(
    p_solicitacao BIGINT, p_categoria TEXT, p_especialidade TEXT, p_quantidade INT
) RETURNS BIGINT AS $$
DECLARE
    v_linha BIGINT;
BEGIN
    SELECT id INTO v_linha FROM solicitacao_profissional WHERE solicitacao_id = p_solicitacao AND categoria = p_categoria;
    IF v_linha IS NOT NULL THEN
        UPDATE solicitacao_profissional SET quantidade = p_quantidade WHERE id = v_linha AND quantidade < p_quantidade;
        RETURN p_solicitacao;
    END IF;

    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade)
    VALUES (p_solicitacao, p_categoria, p_quantidade)
    RETURNING id INTO v_linha;
    INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
    SELECT v_linha, e.id FROM especialidade e WHERE e.nome = p_especialidade AND e.categoria = p_categoria;
    RETURN p_solicitacao;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.seed_proposta_da_atuacao(
    p_descricao TEXT, p_email TEXT, p_categoria TEXT, p_valor NUMERIC, p_abordagem TEXT, p_status TEXT, p_dias_atras INT
) RETURNS VOID AS $$
DECLARE
    v_solicitacao BIGINT := pg_temp.solicitacao_id(p_descricao);
    v_profissional BIGINT;
    v_atuacao BIGINT;
    v_linha BIGINT;
BEGIN
    SELECT pr.id INTO v_profissional FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = p_email;
    v_atuacao := pg_temp.atuacao_de(v_profissional, p_categoria);
    IF v_solicitacao IS NULL OR v_atuacao IS NULL
       OR EXISTS (SELECT 1 FROM proposta WHERE solicitacao_id = v_solicitacao AND atuacao_id = v_atuacao) THEN
        RETURN;
    END IF;

    SELECT id INTO v_linha FROM solicitacao_profissional
    WHERE solicitacao_id = v_solicitacao AND categoria = p_categoria ORDER BY id LIMIT 1;
    IF p_status = 'ACEITA' THEN
        UPDATE solicitacao_profissional SET vagas_preenchidas = vagas_preenchidas + 1 WHERE id = v_linha;
    END IF;

    INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, solicitacao_profissional_id, valor, abordagem,
                          inicio, termino, status, criado_em, atualizado_em)
    SELECT v_solicitacao, v_profissional, v_atuacao, CASE WHEN p_status = 'ACEITA' THEN v_linha END, p_valor, p_abordagem,
           (s.prazo_desejado + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
           (s.prazo_desejado + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo',
           p_status, now() - make_interval(days => p_dias_atras), now()
    FROM solicitacao s WHERE s.id = v_solicitacao;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_solicitacao('helena@quickcare.dev', s.descricao, s.modalidade, s.categoria, s.especialidade,
                                s.status, s.prazo, s.dias_atras)
FROM (VALUES
    ('Teste atuação: enfermagem 12h no pós-operatório, já com proposta do Bruno.', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 12h', 'RECEBENDO_PROPOSTAS', 14, 3),
    ('Teste atuação: enfermeira 12h para a noite, por uma semana.', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 12h', 'ABERTA', 16, 2),
    ('Teste atuação: consulta com cardiologista em casa.', 'PRESENCIAL', 'MEDICO', 'Cardiologia', 'ABERTA', 18, 2),
    ('Teste atuação: cuidadora para cuidados paliativos do meu pai.', 'PRESENCIAL', 'CUIDADOR', 'Cuidados Paliativos', 'ABERTA', 20, 2),
    ('Teste atuação: enfermeira 24h e acompanhante no hospital (Camila já mandou para a vaga de enfermagem).', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 24h', 'RECEBENDO_PROPOSTAS', 15, 3),
    ('Teste atuação: plantão 24h e acompanhante para a alta hospitalar (sem propostas).', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 24h', 'ABERTA', 17, 2),
    ('Teste atuação: enfermagem 24h e acompanhante na internação (Camila já mandou para as duas vagas).', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 24h', 'RECEBENDO_PROPOSTAS', 19, 3),
    ('Teste atuação: duas enfermeiras 24h em revezamento.', 'PRESENCIAL', 'ENFERMEIRO', 'Acompanhamento 24h', 'RECEBENDO_PROPOSTAS', 21, 3),
    ('Teste atuação: troca da bolsa de colostomia duas vezes por semana.', 'PRESENCIAL', 'ENFERMEIRO', 'Cuidados com Ostomia', 'RECEBENDO_PROPOSTAS', 13, 4),
    ('Teste atuação: orientação sobre cuidados com a ostomia.', 'PRESENCIAL', 'ENFERMEIRO', 'Cuidados com Ostomia', 'ABERTA', 15, 1),
    ('Teste atuação: terapia online para ansiedade.', 'ONLINE', 'OUTRO', 'Psicologia', 'ABERTA', 12, 1),
    ('Teste atuação: aplicação de insulina, proposta ainda pendente.', 'PRESENCIAL', 'ENFERMEIRO', 'Aplicação de Injeção', 'RECEBENDO_PROPOSTAS', 11, 3),
    ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 5 dias.', 'PRESENCIAL', 'ENFERMEIRO', 'Aplicação de Injeção', 'EM_ANDAMENTO', 10, 7),
    ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 2 horas.', 'PRESENCIAL', 'ENFERMEIRO', 'Aplicação de Injeção', 'EM_ANDAMENTO', 10, 2),
    ('Teste atuação: sondagem daqui a 12 dias, aceita há 5 dias.', 'PRESENCIAL', 'ENFERMEIRO', 'Sondagem', 'EM_ANDAMENTO', 12, 7),
    ('Teste atuação: aplicação de injeção já concluída.', 'PRESENCIAL', 'ENFERMEIRO', 'Aplicação de Injeção', 'CONCLUIDA', -10, 20),
    ('Teste atuação: curativo em andamento agora.', 'PRESENCIAL', 'ENFERMEIRO', 'Curativos', 'EM_ANDAMENTO', 0, 6),
    ('Teste atuação: cuidadora que terminou ontem.', 'PRESENCIAL', 'CUIDADOR', 'Cuidador de Idosos', 'EM_ANDAMENTO', -1, 6),
    ('Teste atuação: consulta de nutrição com problema relatado.', 'ONLINE', 'OUTRO', 'Nutrição', 'EM_ANDAMENTO', -3, 8))
    AS s (descricao, modalidade, categoria, especialidade, status, prazo, dias_atras);

SELECT pg_temp.seed_vaga(pg_temp.solicitacao_id(d), 'CUIDADOR', 'Acompanhante Hospitalar', 1)
FROM (VALUES ('Teste atuação: enfermeira 24h e acompanhante no hospital (Camila já mandou para a vaga de enfermagem).'),
             ('Teste atuação: plantão 24h e acompanhante para a alta hospitalar (sem propostas).'),
             ('Teste atuação: enfermagem 24h e acompanhante na internação (Camila já mandou para as duas vagas).')) AS v (d);
SELECT pg_temp.seed_vaga(pg_temp.solicitacao_id('Teste atuação: duas enfermeiras 24h em revezamento.'),
                         'ENFERMEIRO', 'Acompanhamento 24h', 2);

SELECT pg_temp.seed_proposta_da_atuacao(p.descricao, p.email, p.categoria, p.valor, p.abordagem, p.status, p.dias_atras)
FROM (VALUES
    ('Teste atuação: enfermagem 12h no pós-operatório, já com proposta do Bruno.', 'bruno@quickcare.dev', 'ENFERMEIRO', 320.00,
     'Plantão de 12h com controle de sinais vitais, medicação e curativo da incisão.', 'PENDENTE', 2),
    ('Teste atuação: enfermeira 24h e acompanhante no hospital (Camila já mandou para a vaga de enfermagem).', 'camila@quickcare.dev', 'ENFERMEIRO', 600.00,
     'Plantão de 24h no hospital com passagem de plantão para a equipe e para a família.', 'PENDENTE', 2),
    ('Teste atuação: enfermagem 24h e acompanhante na internação (Camila já mandou para as duas vagas).', 'camila@quickcare.dev', 'ENFERMEIRO', 620.00,
     'Plantão de enfermagem de 24h durante a internação, com registro diário.', 'PENDENTE', 2),
    ('Teste atuação: enfermagem 24h e acompanhante na internação (Camila já mandou para as duas vagas).', 'camila@quickcare.dev', 'CUIDADOR', 180.00,
     'Acompanho durante o dia no hospital, ajudo nas refeições e aviso a família de qualquer mudança.', 'PENDENTE', 2),
    ('Teste atuação: duas enfermeiras 24h em revezamento.', 'camila@quickcare.dev', 'ENFERMEIRO', 590.00,
     'Assumo um dos turnos de 24h do revezamento, com relatório para a outra enfermeira.', 'PENDENTE', 2),
    ('Teste atuação: troca da bolsa de colostomia duas vezes por semana.', 'diego@quickcare.dev', 'ENFERMEIRO', 140.00,
     'Troca da bolsa, limpeza do estoma e orientação para a família fazer entre as visitas.', 'PENDENTE', 3),
    ('Teste atuação: aplicação de insulina, proposta ainda pendente.', 'eduardo@quickcare.dev', 'ENFERMEIRO', 90.00,
     'Aplicação de insulina com controle da glicemia antes de cada dose.', 'PENDENTE', 2),
    ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 5 dias.', 'eduardo@quickcare.dev', 'ENFERMEIRO', 80.00,
     'Aplicação intramuscular conforme receita, com orientação sobre reações.', 'ACEITA', 6),
    ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 2 horas.', 'eduardo@quickcare.dev', 'ENFERMEIRO', 85.00,
     'Aplicação de anticoagulante subcutâneo com rodízio dos locais.', 'ACEITA', 1),
    ('Teste atuação: sondagem daqui a 12 dias, aceita há 5 dias.', 'eduardo@quickcare.dev', 'ENFERMEIRO', 150.00,
     'Troca da sonda vesical com técnica estéril e orientação sobre a higiene.', 'ACEITA', 6),
    ('Teste atuação: aplicação de injeção já concluída.', 'eduardo@quickcare.dev', 'ENFERMEIRO', 75.00,
     'Aplicação de vitamina B12 em dose única.', 'ACEITA', 19),
    ('Teste atuação: curativo em andamento agora.', 'fabio@quickcare.dev', 'ENFERMEIRO', 160.00,
     'Curativo com troca de cobertura e registro da evolução.', 'ACEITA', 5),
    ('Teste atuação: cuidadora que terminou ontem.', 'fabio@quickcare.dev', 'CUIDADOR', 200.00,
     'Acompanhamento durante o dia com apoio na higiene e na alimentação.', 'ACEITA', 5),
    ('Teste atuação: consulta de nutrição com problema relatado.', 'fabio@quickcare.dev', 'OUTRO', 130.00,
     'Consulta por vídeo com avaliação dos hábitos e plano alimentar.', 'ACEITA', 7))
    AS p (descricao, email, categoria, valor, abordagem, status, dias_atras);

INSERT INTO atendimento (proposta_id, solicitacao_id, status, inicio, termino, aceito_em, criado_em)
SELECT p.id, p.solicitacao_id,
       CASE
           WHEN s.status = 'CANCELADA' THEN 'CANCELADO'
           WHEN now() < p.inicio THEN 'AGENDADO'
           WHEN now() < p.termino THEN 'EM_ANDAMENTO'
           WHEN now() < p.termino + INTERVAL '72 hours' THEN 'FINALIZADO'
           ELSE 'CONCLUIDO'
       END,
       p.inicio, p.termino, p.atualizado_em - INTERVAL '3 days', p.atualizado_em
FROM proposta p
JOIN solicitacao s ON s.id = p.solicitacao_id
WHERE p.status = 'ACEITA'
  AND NOT EXISTS (SELECT 1 FROM atendimento a WHERE a.proposta_id = p.id);

UPDATE proposta p
SET inicio = (s.prazo_desejado + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
    termino = (s.prazo_desejado + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo'
FROM solicitacao s
WHERE s.id = p.solicitacao_id AND s.status = 'CONCLUIDA' AND p.status = 'ACEITA'
  AND s.prazo_desejado < CURRENT_DATE AND p.inicio > now();

UPDATE atendimento a
SET inicio = p.inicio, termino = p.termino, status = 'CONCLUIDO',
    aceito_em = LEAST(a.aceito_em, p.inicio - INTERVAL '3 days'),
    lembrete_24h_em = p.inicio, lembrete_30min_em = p.inicio, lembrete_inicio_em = p.inicio
FROM proposta p, solicitacao s
WHERE a.proposta_id = p.id AND s.id = p.solicitacao_id AND s.status = 'CONCLUIDA'
  AND a.inicio <> p.inicio;

UPDATE atendimento a
SET status = 'EM_ANDAMENTO',
    inicio = date_trunc('hour', now()) - INTERVAL '2 hours',
    termino = date_trunc('hour', now()) + INTERVAL '8 hours',
    aceito_em = now() - INTERVAL '5 days',
    lembrete_24h_em = now(), lembrete_30min_em = now(), lembrete_inicio_em = now()
FROM proposta p, solicitacao s
WHERE a.proposta_id = p.id AND s.id = p.solicitacao_id
  AND s.descricao LIKE 'Cuidadora para os fins de semana%'
  AND a.status IN ('AGENDADO', 'EM_ANDAMENTO', 'FINALIZADO', 'CONCLUIDO');

UPDATE atendimento a
SET remarcacao_inicio = a.inicio - INTERVAL '1 day',
    remarcacao_termino = a.termino - INTERVAL '1 day',
    remarcacao_por = 'PACIENTE',
    remarcacao_solicitada_em = now() - INTERVAL '2 hours'
FROM proposta p, solicitacao s, profissional pr, usuario u
WHERE a.proposta_id = p.id AND s.id = p.solicitacao_id AND pr.id = p.profissional_id AND u.id = pr.usuario_id
  AND s.descricao LIKE 'Minha mãe precisa de curativos diários%' AND u.email = 'ambos@quickcare.dev'
  AND a.status = 'AGENDADO' AND a.remarcacao_por IS NULL;

WITH horarios AS (
    SELECT s.id AS solicitacao_id, h.inicio, h.aceito_em
    FROM (VALUES ('Teste de bloqueio: curativo na perna em menos de 48h.', date_trunc('hour', now()) + INTERVAL '30 hours', now() - INTERVAL '5 days'),
                 ('Teste de bloqueio: curativo nas costas em menos de 48h.', date_trunc('hour', now()) + INTERVAL '30 hours', now() - INTERVAL '4 days'),
                 ('Teste de bloqueio: curativo no ombro em menos de 48h.', date_trunc('hour', now()) + INTERVAL '30 hours', now() - INTERVAL '3 days'),
                 ('Teste de bloqueio: curativo na mão, aceito em cima da hora.', date_trunc('hour', now()) + INTERVAL '40 hours', now() - INTERVAL '2 hours'))
         AS h (descricao, inicio, aceito_em)
    JOIN solicitacao s ON s.descricao = h.descricao
),
propostas AS (
    UPDATE proposta p
    SET inicio = h.inicio, termino = h.inicio + INTERVAL '8 hours'
    FROM horarios h
    WHERE p.solicitacao_id = h.solicitacao_id AND p.status = 'ACEITA'
    RETURNING p.id
)
UPDATE atendimento a
SET inicio = h.inicio, termino = h.inicio + INTERVAL '8 hours', aceito_em = h.aceito_em,
    lembrete_24h_em = NULL, lembrete_30min_em = NULL, lembrete_inicio_em = NULL
FROM horarios h
WHERE a.solicitacao_id = h.solicitacao_id AND a.status = 'AGENDADO';

CREATE OR REPLACE FUNCTION pg_temp.seed_problema(
    p_descricao TEXT, p_relatado_por TEXT, p_relato TEXT, p_horas_atras INT, p_contestacao TEXT
) RETURNS VOID AS $$
DECLARE
    v_atendimento atendimento%ROWTYPE;
BEGIN
    SELECT a.* INTO v_atendimento
    FROM atendimento a JOIN solicitacao s ON s.id = a.solicitacao_id
    WHERE s.descricao = p_descricao
    ORDER BY a.id DESC LIMIT 1;
    IF v_atendimento.id IS NULL OR EXISTS (SELECT 1 FROM problema_atendimento WHERE atendimento_id = v_atendimento.id) THEN
        RETURN;
    END IF;

    INSERT INTO problema_atendimento (atendimento_id, relatado_por, motivo, descricao, status, prazo_resposta,
                                      status_anterior, respondido_em, contestacao, contestado_em, criado_em, atualizado_em)
    VALUES (v_atendimento.id, p_relatado_por, 'OUTRO_NAO_COMPARECEU', p_relato,
            CASE WHEN p_contestacao IS NULL THEN 'AGUARDANDO_RESPOSTA' ELSE 'EM_DISPUTA' END,
            now() - make_interval(hours => p_horas_atras) + INTERVAL '7 days',
            'FINALIZADO',
            CASE WHEN p_contestacao IS NOT NULL THEN now() - make_interval(hours => p_horas_atras / 2) END,
            p_contestacao,
            CASE WHEN p_contestacao IS NOT NULL THEN now() - make_interval(hours => p_horas_atras / 2) END,
            now() - make_interval(hours => p_horas_atras), now());

    UPDATE atendimento
    SET status = CASE WHEN p_contestacao IS NULL THEN 'PROBLEMA_RELATADO' ELSE 'EM_DISPUTA' END,
        lembrete_24h_em = inicio, lembrete_30min_em = inicio, lembrete_inicio_em = inicio
    WHERE id = v_atendimento.id;
    UPDATE solicitacao SET status = 'EM_ANDAMENTO' WHERE id = v_atendimento.solicitacao_id;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_problema('Curativo na perna depois de uma queda, uma visita.', 'PACIENTE',
       'Esperei a tarde toda e o profissional não apareceu nem avisou.', 48, NULL);
SELECT pg_temp.seed_problema('Curativo semanal na perna, com relatório para a médica.', 'PROFISSIONAL',
       'Cheguei no horário, toquei várias vezes e ninguém atendeu. Esperei 40 minutos.', 30,
       'Fiquei em casa o dia todo e a campainha não tocou. Minha vizinha pode confirmar.');

UPDATE atendimento a
SET aceito_em = now() - h.aceito_ha
FROM (VALUES ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 5 dias.', INTERVAL '5 days'),
             ('Teste atuação: aplicação de injeção daqui a 10 dias, aceita há 2 horas.', INTERVAL '2 hours'),
             ('Teste atuação: sondagem daqui a 12 dias, aceita há 5 dias.', INTERVAL '5 days')) AS h (descricao, aceito_ha),
     solicitacao s
WHERE s.descricao = h.descricao AND a.solicitacao_id = s.id AND a.status = 'AGENDADO';

WITH horarios AS (
    SELECT s.id AS solicitacao_id, h.status, now() + h.inicio AS inicio, now() + h.termino AS termino
    FROM (VALUES ('Teste atuação: curativo em andamento agora.', 'EM_ANDAMENTO', INTERVAL '-2 hours', INTERVAL '8 hours'),
                 ('Teste atuação: cuidadora que terminou ontem.', 'FINALIZADO', INTERVAL '-30 hours', INTERVAL '-20 hours'),
                 ('Teste atuação: consulta de nutrição com problema relatado.', 'FINALIZADO', INTERVAL '-60 hours', INTERVAL '-50 hours'))
         AS h (descricao, status, inicio, termino)
    JOIN solicitacao s ON s.descricao = h.descricao
),
propostas AS (
    UPDATE proposta p
    SET inicio = h.inicio, termino = h.termino
    FROM horarios h
    WHERE p.solicitacao_id = h.solicitacao_id AND p.status = 'ACEITA'
    RETURNING p.id
)
UPDATE atendimento a
SET status = h.status, inicio = h.inicio, termino = h.termino, aceito_em = now() - INTERVAL '5 days',
    lembrete_24h_em = h.inicio, lembrete_30min_em = h.inicio, lembrete_inicio_em = h.inicio
FROM horarios h
WHERE a.solicitacao_id = h.solicitacao_id AND a.status IN ('AGENDADO', 'EM_ANDAMENTO', 'FINALIZADO', 'CONCLUIDO');

SELECT pg_temp.seed_problema('Teste atuação: consulta de nutrição com problema relatado.', 'PACIENTE',
       'A consulta por vídeo não aconteceu: o link não abriu e a profissional não respondeu as mensagens.', 40, NULL);

INSERT INTO ocorrencia (usuario_id, perfil, motivo, justificativa, criado_por_usuario_id, criado_em,
                        anulada_em, anulada_por_usuario_id, justificativa_anulacao)
SELECT u.id, 'PROFISSIONAL', o.motivo, o.justificativa,
       CASE WHEN o.motivo = 'MANUAL' THEN adm.id END,
       now() - make_interval(days => o.dias_atras),
       CASE WHEN o.anulada THEN now() - make_interval(days => o.dias_atras - 1) END,
       CASE WHEN o.anulada THEN adm.id END,
       CASE WHEN o.anulada THEN 'Cancelamento por motivo de saúde comprovado.' END
FROM (VALUES ('profissional@quickcare.dev', 'CANCELAMENTO_ATENDIMENTO', NULL, 20, FALSE),
             ('nutricionista@quickcare.dev', 'CANCELAMENTO_ATENDIMENTO', NULL, 40, TRUE),
             ('nutricionista@quickcare.dev', 'CANCELAMENTO_ATENDIMENTO', NULL, 25, FALSE),
             ('nutricionista@quickcare.dev', 'NAO_COMPARECIMENTO', NULL, 9, FALSE),
             ('nutricionista@quickcare.dev', 'MANUAL', 'Não compareceu a duas consultas por vídeo seguidas, confirmado com o paciente.', 2, FALSE))
     AS o (email, motivo, justificativa, dias_atras, anulada)
JOIN usuario u ON u.email = o.email
CROSS JOIN (SELECT usuario_id AS id FROM administrador ORDER BY id LIMIT 1) adm
WHERE NOT EXISTS (SELECT 1 FROM ocorrencia x WHERE x.usuario_id = u.id AND x.perfil = 'PROFISSIONAL');

INSERT INTO bloqueio (usuario_id, perfil, nivel, inicio, fim)
SELECT o.usuario_id, 'PROFISSIONAL', 1, o.criado_em, o.criado_em + INTERVAL '7 days'
FROM ocorrencia o JOIN usuario u ON u.id = o.usuario_id
WHERE u.email = 'nutricionista@quickcare.dev' AND o.motivo = 'MANUAL'
  AND NOT EXISTS (SELECT 1 FROM bloqueio b WHERE b.usuario_id = o.usuario_id);

INSERT INTO recurso_ocorrencia (ocorrencia_id, texto, status, criado_em)
SELECT o.id, 'Cancelei porque o paciente pediu por mensagem para trocar o dia; tenho o print da conversa.',
       'EM_ANALISE', now() - INTERVAL '1 day'
FROM ocorrencia o JOIN usuario u ON u.id = o.usuario_id
WHERE u.email = 'nutricionista@quickcare.dev' AND o.motivo = 'CANCELAMENTO_ATENDIMENTO' AND o.anulada_em IS NULL
  AND NOT EXISTS (SELECT 1 FROM recurso_ocorrencia r JOIN ocorrencia x ON x.id = r.ocorrencia_id WHERE x.usuario_id = u.id);

DELETE FROM notificacao
WHERE link IN ('/patient', '/professional/propostas', '/professional/historico') AND tipo <> 'CADASTRO_APROVADO';

CREATE OR REPLACE FUNCTION pg_temp.seed_notificacao(
    p_email TEXT, p_perfil TEXT, p_tipo TEXT, p_titulo TEXT, p_mensagem TEXT,
    p_descricao TEXT, p_email_profissional TEXT, p_destaque TEXT, p_link_fixo TEXT,
    p_horas_atras INT, p_lida BOOLEAN
) RETURNS VOID AS $$
DECLARE
    v_usuario_id BIGINT;
    v_solicitacao_id BIGINT;
    v_proposta_id BIGINT;
    v_link TEXT := p_link_fixo;
BEGIN
    SELECT id INTO v_usuario_id FROM usuario WHERE email = p_email;
    IF v_usuario_id IS NULL THEN
        RETURN;
    END IF;

    IF p_descricao IS NOT NULL THEN
        SELECT id INTO v_solicitacao_id FROM solicitacao WHERE descricao LIKE p_descricao || '%' ORDER BY id LIMIT 1;
        SELECT p.id INTO v_proposta_id
        FROM proposta p
        JOIN profissional pr ON pr.id = p.profissional_id
        JOIN usuario u ON u.id = pr.usuario_id
        WHERE p.solicitacao_id = v_solicitacao_id AND u.email = p_email_profissional
        ORDER BY (p.status IN ('PENDENTE', 'ACEITA')) DESC, p.id DESC
        LIMIT 1;
        IF v_solicitacao_id IS NULL OR v_proposta_id IS NULL THEN
            RETURN;
        END IF;

        v_link := CASE p_perfil WHEN 'PACIENTE' THEN '/patient' ELSE '/professional' END
                  || '/solicitacoes/' || v_solicitacao_id || '?destaque='
                  || CASE p_destaque WHEN 'PROPOSTA' THEN 'proposta-' || v_proposta_id
                                     ELSE 'solicitacao-' || v_solicitacao_id END;
    END IF;

    IF EXISTS (SELECT 1 FROM notificacao
               WHERE usuario_id = v_usuario_id AND perfil = p_perfil AND titulo = p_titulo AND mensagem = p_mensagem) THEN
        UPDATE notificacao SET link = v_link
        WHERE usuario_id = v_usuario_id AND perfil = p_perfil AND titulo = p_titulo AND mensagem = p_mensagem;
        RETURN;
    END IF;

    INSERT INTO notificacao (usuario_id, perfil, tipo, titulo, mensagem, link, lida_em, criado_em)
    VALUES (v_usuario_id, p_perfil, p_tipo, p_titulo, p_mensagem, v_link,
            CASE WHEN p_lida THEN now() - make_interval(hours => p_horas_atras) + INTERVAL '10 minutes' END,
            now() - make_interval(hours => p_horas_atras));
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Profissional Teste enviou uma proposta para a sua solicitação de Curativos.',
       'Curativo pós-cirúrgico no joelho', 'profissional@quickcare.dev', 'PROPOSTA', NULL, 3, FALSE);
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Carla Mendes enviou uma proposta para a sua solicitação de Nutrição.',
       'Acompanhamento nutricional para controle do colesterol', 'nutricionista@quickcare.dev', 'PROPOSTA', NULL, 26, TRUE);

SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PROFISSIONAL', 'PROPOSTA_ACEITA', 'Proposta aceita',
       'O paciente aceitou a sua proposta para Cuidador de Idosos.',
       'Cuidadora para os fins de semana', 'ambos@quickcare.dev', 'PROPOSTA', NULL, 5, FALSE);
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PROFISSIONAL', 'SOLICITACAO_CANCELADA', 'Solicitação cancelada',
       'O paciente cancelou a solicitação de Cuidador de Idosos.',
       'Cuidadora noturna por um mês', 'ambos@quickcare.dev', 'SOLICITACAO', NULL, 48, FALSE);

SELECT pg_temp.seed_notificacao('paciente@quickcare.dev', 'PACIENTE', 'CADASTRO_APROVADO', 'Cadastro aprovado',
       'Seu cadastro de paciente foi aprovado. Você já pode criar solicitações.',
       NULL, NULL, NULL, '/patient', 240, TRUE);
SELECT pg_temp.seed_notificacao('paciente@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Profissional Teste enviou uma proposta para a sua solicitação de Curativos.',
       'Minha mãe precisa de curativos diários', 'profissional@quickcare.dev', 'PROPOSTA', NULL, 2, FALSE);

SELECT pg_temp.seed_notificacao('profissional@quickcare.dev', 'PROFISSIONAL', 'CADASTRO_APROVADO', 'Cadastro aprovado',
       'Seu cadastro de profissional foi aprovado. Você já pode enviar propostas.',
       NULL, NULL, NULL, '/professional', 240, TRUE);
SELECT pg_temp.seed_notificacao('profissional@quickcare.dev', 'PROFISSIONAL', 'SOLICITACAO_ALTERADA', 'Solicitação alterada',
       'O paciente alterou a solicitação de Curativos. Confira se a sua proposta ainda atende.',
       'Minha mãe precisa de curativos diários', 'profissional@quickcare.dev', 'SOLICITACAO', NULL, 1, FALSE);
