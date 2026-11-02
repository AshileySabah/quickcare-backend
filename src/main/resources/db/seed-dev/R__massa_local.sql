-- Massa de dados para desenvolvimento local. Só é carregada no perfil "dev"
-- (ver spring.flyway.locations em application-dev.yml); nunca roda em produção.
-- Migration repetível: roda de novo sempre que este arquivo mudar, por isso
-- tudo aqui precisa ser idempotente.
--
-- Login do paciente:     paciente@quickcare.dev / Senha@123
-- Login do profissional: profissional@quickcare.dev / Senha@123
-- Login do admin:        admin@quickcare.dev / Senha@123
-- Paciente e profissional (alternância de perfil): ambos@quickcare.dev / Senha@123
-- Propostas de exemplo: profissional@ (pendente) e ambos@ (aceita) na solicitação presencial do paciente@;
--   nutricionista@quickcare.dev (pendente) na online do paciente@; maria@quickcare.dev em andamento com o profissional@.
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

-- ---------------------------------------------------------------------------
-- Propostas na solicitação presencial do paciente de teste
-- ---------------------------------------------------------------------------
-- profissional@ (enfermeiro): pendente — vê o perfil do paciente sem a saúde.
-- ambos@ (cuidadora): aceita na vaga de cuidador — vê também saúde e contatos.
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
INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, prazo, status)
SELECT presencial.id, pr.id,
       CASE WHEN p.status = 'ACEITA'
            THEN (SELECT sp.id FROM solicitacao_profissional sp
                  WHERE sp.solicitacao_id = presencial.id AND sp.categoria = 'CUIDADOR')
       END,
       p.valor, p.abordagem, CURRENT_DATE + 10, p.status
FROM presencial
CROSS JOIN propostas p
JOIN usuario u ON u.email = p.email
JOIN profissional pr ON pr.usuario_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM proposta x
    WHERE x.solicitacao_id = presencial.id AND x.profissional_id = pr.id AND x.status <> 'CANCELADA'
);

-- A vaga de cuidador fica ocupada pela proposta aceita, e a solicitação passa a receber propostas.
UPDATE solicitacao_profissional sp
SET vagas_preenchidas = 1
FROM proposta p
WHERE p.solicitacao_profissional_id = sp.id AND p.status = 'ACEITA' AND sp.vagas_preenchidas = 0;

UPDATE solicitacao s
SET status = 'RECEBENDO_PROPOSTAS'
WHERE s.status = 'ABERTA' AND EXISTS (SELECT 1 FROM proposta p WHERE p.solicitacao_id = s.id AND p.status <> 'CANCELADA');

-- ---------------------------------------------------------------------------
-- Perfis para conferir (telas de perfil de paciente e de profissional)
-- ---------------------------------------------------------------------------
-- paciente@: dados de saúde completos e contatos de emergência.
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

-- nutricionista@: só online, com proposta pendente na solicitação online do paciente@.
INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Carla Mendes', 'nutricionista@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
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

INSERT INTO proposta (solicitacao_id, profissional_id, valor, abordagem, prazo, status)
SELECT s.id, pr.id, 120.00,
       'Avaliação inicial por vídeo e plano alimentar com retorno quinzenal pelos próximos três meses.',
       CURRENT_DATE + 20, 'PENDENTE'
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

-- maria@: paciente com saúde e contatos, solicitação presencial em andamento com
-- a proposta do profissional@ aceita — ele vê os dados de saúde dela.
INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Maria Oliveira', 'maria@quickcare.dev',
        '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq', -- BCrypt de "Senha@123"
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
INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, prazo, status)
SELECT linha.solicitacao_id, pr.id, linha.id, 200.00,
       'Curativo diário com avaliação da ferida e orientação sobre o controle da glicemia.',
       CURRENT_DATE + 7, 'ACEITA'
FROM linha
JOIN usuario u ON u.email = 'profissional@quickcare.dev'
JOIN profissional pr ON pr.usuario_id = u.id;

-- Contatos de emergência dos profissionais de teste: a Maria (atendimento em
-- andamento com o profissional@) vê os dele no perfil.
INSERT INTO profissional_contato_emergencia (profissional_id, nome, telefone)
SELECT p.id, c.nome, c.telefone
FROM profissional p
JOIN usuario u ON u.id = p.usuario_id
JOIN (VALUES ('profissional@quickcare.dev', 'Marcos Teste (cônjuge)', '11933334444'),
             ('ambos@quickcare.dev', 'Bruno Duplo (irmão)', '11922223333')) AS c (email, nome, telefone)
  ON c.email = u.email
WHERE NOT EXISTS (SELECT 1 FROM profissional_contato_emergencia x WHERE x.profissional_id = p.id);

-- ---------------------------------------------------------------------------
-- Mais solicitações e propostas para a ambos@ (paciente e cuidadora), para
-- testar os filtros de Minhas solicitações, Histórico e Minhas propostas.
-- ---------------------------------------------------------------------------
-- Cria a solicitação (uma linha de profissional) se o paciente ainda não tem
-- uma com a mesma descrição; devolve o id de qualquer forma.
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

-- Cria a proposta se o profissional ainda não tem uma nessa solicitação; a
-- aceita ocupa a vaga da linha.
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

    INSERT INTO proposta (solicitacao_id, profissional_id, solicitacao_profissional_id, valor, abordagem, prazo, status,
                          criado_em, atualizado_em)
    VALUES (p_solicitacao, v_profissional, v_linha, p_valor, p_abordagem, CURRENT_DATE + 10, p_status,
            now() - make_interval(days => p_dias_atras), now());
END;
$$ LANGUAGE plpgsql;

-- ambos@ como paciente: ativas e encerradas, com propostas de outros profissionais.
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

-- ambos@ como cuidadora: abertas para ela, pendente, aceita e encerradas.
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

-- Notificações de exemplo (sino): ambos@ tem não lidas nos dois perfis, para
-- ver o indicador do outro perfil no seletor; paciente@ e profissional@ têm
-- lidas e não lidas. Os links apontam para solicitações e propostas reais da
-- massa, com o item a destacar (?destaque=), como o back gera.

-- Versão anterior desta massa: links sem destaque (só dev).
DELETE FROM notificacao
WHERE link IN ('/patient', '/professional/propostas', '/professional/historico') AND tipo <> 'CADASTRO_APROVADO';

-- p_descricao identifica a solicitação; p_email_profissional, a proposta (a
-- ativa dele, ou a mais recente). p_destaque: PROPOSTA, SOLICITACAO ou nulo
-- (sem solicitação: p_link_fixo).
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

-- ambos@ como paciente
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Profissional Teste enviou uma proposta para a sua solicitação de Curativos.',
       'Curativo pós-cirúrgico no joelho', 'profissional@quickcare.dev', 'PROPOSTA', NULL, 3, FALSE);
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Carla Mendes enviou uma proposta para a sua solicitação de Nutrição.',
       'Acompanhamento nutricional para controle do colesterol', 'nutricionista@quickcare.dev', 'PROPOSTA', NULL, 26, TRUE);

-- ambos@ como cuidadora
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PROFISSIONAL', 'PROPOSTA_ACEITA', 'Proposta aceita',
       'O paciente aceitou a sua proposta para Cuidador de Idosos.',
       'Cuidadora para os fins de semana', 'ambos@quickcare.dev', 'PROPOSTA', NULL, 5, FALSE);
SELECT pg_temp.seed_notificacao('ambos@quickcare.dev', 'PROFISSIONAL', 'SOLICITACAO_CANCELADA', 'Solicitação cancelada',
       'O paciente cancelou a solicitação de Cuidador de Idosos.',
       'Cuidadora noturna por um mês', 'ambos@quickcare.dev', 'SOLICITACAO', NULL, 48, FALSE);

-- paciente@
SELECT pg_temp.seed_notificacao('paciente@quickcare.dev', 'PACIENTE', 'CADASTRO_APROVADO', 'Cadastro aprovado',
       'Seu cadastro de paciente foi aprovado. Você já pode criar solicitações.',
       NULL, NULL, NULL, '/patient', 240, TRUE);
SELECT pg_temp.seed_notificacao('paciente@quickcare.dev', 'PACIENTE', 'PROPOSTA_RECEBIDA', 'Nova proposta',
       'Profissional Teste enviou uma proposta para a sua solicitação de Curativos.',
       'Minha mãe precisa de curativos diários', 'profissional@quickcare.dev', 'PROPOSTA', NULL, 2, FALSE);

-- profissional@
SELECT pg_temp.seed_notificacao('profissional@quickcare.dev', 'PROFISSIONAL', 'CADASTRO_APROVADO', 'Cadastro aprovado',
       'Seu cadastro de profissional foi aprovado. Você já pode enviar propostas.',
       NULL, NULL, NULL, '/professional', 240, TRUE);
SELECT pg_temp.seed_notificacao('profissional@quickcare.dev', 'PROFISSIONAL', 'SOLICITACAO_ALTERADA', 'Solicitação alterada',
       'O paciente alterou a solicitação de Curativos. Confira se a sua proposta ainda atende.',
       'Minha mãe precisa de curativos diários', 'profissional@quickcare.dev', 'SOLICITACAO', NULL, 1, FALSE);
