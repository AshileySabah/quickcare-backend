-- Massa de dados para desenvolvimento local. Só é carregada no perfil "dev"
-- (ver spring.flyway.locations em application-dev.yml); nunca roda em produção.
-- Migration repetível: roda de novo sempre que este arquivo mudar, por isso
-- tudo aqui precisa ser idempotente.
--
-- Login do paciente:     paciente@quickcare.dev / Senha@123
-- Login do profissional: profissional@quickcare.dev / Senha@123

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