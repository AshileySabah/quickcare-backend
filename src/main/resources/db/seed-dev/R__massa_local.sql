-- Massa de dados para desenvolvimento local. Só é carregada no perfil "dev"
-- (ver spring.flyway.locations em application-dev.yml); nunca roda em produção.
-- Migration repetível: roda de novo sempre que este arquivo mudar, por isso
-- tudo aqui precisa ser idempotente.
--
-- Login do paciente:     paciente@quickcare.dev / Senha@123
-- Login do profissional: profissional@quickcare.dev / Senha@123
-- Login do admin:        admin@quickcare.dev / Senha@123
-- Paciente e profissional (alternância de perfil): ambos@quickcare.dev / Senha@123
--
-- Cadastros aguardando validação (mesma senha), com CPFs do ambiente de
-- demonstração da Serpro para testar a pré-validação do admin:
--   paciente.pendente@quickcare.dev      Rone Pereira (CPF regular, dados conferem)
--   profissional.pendente@quickcare.dev  Suderland Meneses (CPF regular + CNPJ ativo + COREN)
--   cuidador.pendente@quickcare.dev      Matheus Lima (CPF cancelado na Receita)
-- Os documentos desses cadastros não existem no storage: o admin vê o nome do
-- arquivo, mas sem pré-visualização.

INSERT INTO usuario (nome, email, senha_hash, cpf, telefone)
VALUES ('Paciente Teste', 'paciente@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
        '52998224725', '11987654321')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                      alergias, cep, logradouro, numero, complemento, bairro, cidade, uf)
SELECT id, DATE '1990-05-10', 'PREFIRO_NAO_INFORMAR', TRUE, TRUE,
       'Dipirona', '01310100', 'Avenida Paulista', '1000', 'Apto 12', 'Bela Vista', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'paciente@quickcare.dev'
ON CONFLICT DO NOTHING;

-- Duas solicitações de exemplo, criadas só se o paciente ainda não tiver nenhuma:
--   presencial: 2 enfermeiros que façam curativos E sondagem + 1 cuidador de idosos
--   online:     1 nutricionista
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
-- Uma linha por especialidade; linhas da mesma solicitação e categoria formam
-- um único profissional pedido.
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

-- Enfermeiro(a) que atende presencial e online, com Curativos e Sondagem:
-- compatível com a solicitação presencial do paciente de teste.
INSERT INTO usuario (nome, email, senha_hash, cpf, telefone)
VALUES ('Profissional Teste', 'profissional@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
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
-- Coordenadas dos dados de teste (no app real vêm da geocodificação do endereço).
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

-- ---------------------------------------------------------------------------
-- Validação cadastral
-- ---------------------------------------------------------------------------

-- Os usuários de teste acima continuam aprovados (sem etapas, como os
-- cadastros anteriores à validação).
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

-- Usuário com os dois perfis aprovados, para testar a alternância de perfil.
INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Ana Duplo Perfil', 'ambos@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
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

-- Admin
INSERT INTO usuario (nome, email, senha_hash, cpf)
VALUES ('Admin QuickCare', 'admin@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
        '87748248800')
ON CONFLICT DO NOTHING;

INSERT INTO administrador (usuario_id)
SELECT id FROM usuario WHERE email = 'admin@quickcare.dev'
ON CONFLICT DO NOTHING;

-- Cadastros pendentes
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

-- Documentos (fictícios) e etapas pendentes, criados só na primeira vez.
WITH pendentes (email, perfil, tipo_documento, tipo_etapa, nome_arquivo) AS (
    VALUES ('paciente.pendente@quickcare.dev', 'PACIENTE', 'VALIDACAO_CPF', 'IDENTIDADE', 'rg-rone.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CPF', 'IDENTIDADE', 'cnh-suderland.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'VALIDACAO_CNPJ', 'CNPJ', 'cartao-cnpj.pdf'),
           ('profissional.pendente@quickcare.dev', 'PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'REGISTRO_PROFISSIONAL', 'coren.pdf'),
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