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

INSERT INTO profissional (usuario_id, categoria, registro_profissional, registro_profissional_uf, data_nascimento,
                          genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'ENFERMEIRO', '123456', 'SP', DATE '1988-03-20', 'PREFIRO_NAO_INFORMAR', TRUE, TRUE, TRUE, 'AMBOS', 15,
       '01311000', 'Rua Augusta', '500', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'profissional@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional_especialidade (profissional_id, especialidade_id)
SELECT p.id, e.id
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN especialidade e ON e.nome IN ('Curativos', 'Sondagem')
WHERE u.email = 'profissional@quickcare.dev'
ON CONFLICT DO NOTHING;
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

INSERT INTO profissional (usuario_id, status_validacao, categoria, data_nascimento, genero,
                          confirmacao_informacoes_verdadeiras, consentimento_lgpd, possui_seguro_responsabilidade_civil,
                          modalidade_atendimento, raio_atendimento_km, cep, logradouro, numero, bairro, cidade, uf,
                          latitude, longitude)
SELECT id, 'APROVADO', 'CUIDADOR', DATE '1985-07-02', 'FEMININO', TRUE, TRUE, FALSE,
       'PRESENCIAL', 10, '01310100', 'Avenida Paulista', '1500', 'Bela Vista', 'São Paulo', 'SP',
       -23.561400, -46.655900
FROM usuario
WHERE email = 'ambos@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional_especialidade (profissional_id, especialidade_id)
SELECT p.id, e.id
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN especialidade e ON e.nome = 'Cuidador de Idosos'
WHERE u.email = 'ambos@quickcare.dev'
ON CONFLICT DO NOTHING;

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

INSERT INTO profissional (usuario_id, status_validacao, categoria, registro_profissional, registro_profissional_uf,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km, cnpj,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'PENDENTE', 'ENFERMEIRO', '654321', 'SP', DATE '1964-12-26', 'MASCULINO', TRUE, TRUE, TRUE,
       'AMBOS', 10, '00000000000191', '01311000', 'Rua Augusta', '900', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'profissional.pendente@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao, categoria, data_nascimento, genero,
                          confirmacao_informacoes_verdadeiras, consentimento_lgpd, possui_seguro_responsabilidade_civil,
                          modalidade_atendimento, raio_atendimento_km, cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'PENDENTE', 'CUIDADOR', DATE '1996-02-18', 'MASCULINO', TRUE, TRUE, FALSE,
       'PRESENCIAL', 5, '01311000', 'Rua Augusta', '1200', 'Consolação', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'cuidador.pendente@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional_especialidade (profissional_id, especialidade_id)
SELECT p.id, e.id
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN especialidade e ON (u.email = 'profissional.pendente@quickcare.dev' AND e.nome = 'Curativos')
                     OR (u.email = 'cuidador.pendente@quickcare.dev' AND e.nome = 'Cuidador de Idosos')
ON CONFLICT DO NOTHING;

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
INSERT INTO etapa_validacao (paciente_id, profissional_id, tipo, status, documento_id)
SELECT d.paciente_id, d.profissional_id, dn.tipo_etapa, 'PENDENTE', d.id
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
INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, inicio, termino, status)
SELECT presencial.id, pr.id,
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

INSERT INTO profissional (usuario_id, status_validacao, categoria, registro_profissional, registro_profissional_uf,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento,
                          cep, logradouro, numero, bairro, cidade, uf)
SELECT id, 'APROVADO', 'OUTRO', 'CRN-3 54321', 'SP', DATE '1991-09-15', 'FEMININO', TRUE, TRUE, TRUE, 'REMOTO',
       '13010001', 'Rua Barão de Jaguara', '700', 'Centro', 'Campinas', 'SP'
FROM usuario
WHERE email = 'nutricionista@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional_especialidade (profissional_id, especialidade_id)
SELECT p.id, e.id
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN especialidade e ON e.nome = 'Nutrição'
WHERE u.email = 'nutricionista@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO proposta (solicitacao_id, profissional_id, valor, abordagem, inicio, termino, status)
SELECT s.id, pr.id, 120.00,
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
INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, inicio, termino, status)
SELECT linha.solicitacao_id, pr.id, linha.id, 200.00,
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

INSERT INTO profissional (usuario_id, status_validacao, categoria, registro_profissional, registro_profissional_uf,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf, latitude, longitude)
SELECT u.id, 'APROVADO', 'ENFERMEIRO', p.registro, 'SP', p.nascimento, p.genero, TRUE, TRUE, TRUE, 'AMBOS', 20,
       '01311000', 'Rua Augusta', p.numero, 'Consolação', 'São Paulo', 'SP', -23.555000, -46.655000
FROM (VALUES ('fernanda@quickcare.dev', '234567', DATE '1990-07-18', 'FEMININO', '1000'),
             ('gustavo@quickcare.dev', '345678', DATE '1986-02-09', 'MASCULINO', '1100')) AS p (email, registro, nascimento, genero, numero)
JOIN usuario u ON u.email = p.email
ON CONFLICT DO NOTHING;

INSERT INTO profissional_especialidade (profissional_id, especialidade_id)
SELECT pr.id, e.id
FROM profissional pr
JOIN usuario u ON u.id = pr.usuario_id AND u.email IN ('fernanda@quickcare.dev', 'gustavo@quickcare.dev')
JOIN especialidade e ON e.nome = 'Curativos'
ON CONFLICT DO NOTHING;

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

    INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, inicio, termino,
                          status, criado_em, atualizado_em)
    SELECT p_solicitacao, v_profissional, v_linha, p_valor, p_abordagem,
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
