INSERT INTO usuario (nome, email, senha_hash, cpf, telefone,
                     documento_identidade_tipo, documento_identidade_numero, documento_identidade_uf)
VALUES ('Helena Prado', 'chat.paciente@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '14725836982', '11962220001', 'RG', '246781312', 'SP'),
       ('Rafaela Nunes', 'chat.enfermeira@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '25836914737', '11962220002', 'RG', '246781312', 'SP'),
       ('Otávio Reis', 'chat.cuidador@quickcare.dev', '$2a$10$ZgmQejKpnZ53yyHq49/Ey.D/MF1qIcHNerLZC7U1L2sh.ZS6xV3aq',
        '36914725837', '11962220003', 'RG', '246781312', 'SP')
ON CONFLICT DO NOTHING;

INSERT INTO paciente (usuario_id, status_validacao, data_nascimento, genero, confirmacao_informacoes_verdadeiras,
                      consentimento_lgpd, condicoes_saude, cep, logradouro, numero, complemento, bairro, cidade, uf)
SELECT id, 'APROVADO', DATE '1952-11-03', 'FEMININO', TRUE, TRUE, 'Recuperação de cirurgia no quadril',
       '01310100', 'Avenida Paulista', '2000', 'Apto 81', 'Bela Vista', 'São Paulo', 'SP'
FROM usuario
WHERE email = 'chat.paciente@quickcare.dev'
ON CONFLICT DO NOTHING;

INSERT INTO profissional (usuario_id, status_validacao,
                          data_nascimento, genero, confirmacao_informacoes_verdadeiras, consentimento_lgpd,
                          possui_seguro_responsabilidade_civil, modalidade_atendimento, raio_atendimento_km,
                          cep, logradouro, numero, bairro, cidade, uf, latitude, longitude)
SELECT u.id, 'APROVADO', p.nascimento, p.genero, TRUE, TRUE, TRUE, 'AMBOS', 20,
       '01311000', 'Rua Augusta', p.numero, 'Consolação', 'São Paulo', 'SP', -23.555000, -46.655000
FROM (VALUES ('chat.enfermeira@quickcare.dev', DATE '1987-04-12', 'FEMININO', '2100'),
             ('chat.cuidador@quickcare.dev', DATE '1981-08-27', 'MASCULINO', '2200')) AS p (email, nascimento, genero, numero)
JOIN usuario u ON u.email = p.email
ON CONFLICT DO NOTHING;

INSERT INTO profissional_atuacao (profissional_id, categoria, registro_profissional, registro_profissional_uf)
SELECT pr.id, a.categoria, a.registro, a.uf
FROM (VALUES ('chat.enfermeira@quickcare.dev', 'ENFERMEIRO', '588888', 'SP'),
             ('chat.cuidador@quickcare.dev', 'CUIDADOR', NULL, NULL)) AS a (email, categoria, registro, uf)
JOIN usuario u ON u.email = a.email
JOIN profissional pr ON pr.usuario_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM profissional_atuacao x WHERE x.profissional_id = pr.id AND x.categoria = a.categoria);

INSERT INTO profissional_atuacao_especialidade (atuacao_id, especialidade_id)
SELECT pa.id, e.id
FROM (VALUES ('chat.enfermeira@quickcare.dev', 'ENFERMEIRO', 'Curativos'),
             ('chat.cuidador@quickcare.dev', 'CUIDADOR', 'Cuidador de Idosos')) AS a (email, categoria, especialidade)
JOIN usuario u ON u.email = a.email
JOIN profissional pr ON pr.usuario_id = u.id
JOIN profissional_atuacao pa ON pa.profissional_id = pr.id AND pa.categoria = a.categoria
JOIN especialidade e ON e.nome = a.especialidade AND e.categoria = a.categoria
WHERE NOT EXISTS (SELECT 1 FROM profissional_atuacao_especialidade x WHERE x.atuacao_id = pa.id AND x.especialidade_id = e.id);

INSERT INTO profissional_atuacao_evento (atuacao_id, tipo, detalhe)
SELECT pa.id, 'INCLUIDA', 'Massa do chat.'
FROM profissional_atuacao pa
JOIN profissional pr ON pr.id = pa.profissional_id
JOIN usuario u ON u.id = pr.usuario_id
WHERE u.email IN ('chat.enfermeira@quickcare.dev', 'chat.cuidador@quickcare.dev')
  AND NOT EXISTS (SELECT 1 FROM profissional_atuacao_evento x WHERE x.atuacao_id = pa.id);

DELETE FROM notificacao
WHERE usuario_id IN (SELECT id FROM usuario WHERE email IN
    ('chat.paciente@quickcare.dev', 'chat.enfermeira@quickcare.dev', 'chat.cuidador@quickcare.dev'));

DELETE FROM proposta
WHERE profissional_id IN (SELECT pr.id FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id
                          WHERE u.email IN ('chat.enfermeira@quickcare.dev', 'chat.cuidador@quickcare.dev'));

DELETE FROM solicitacao
WHERE paciente_id IN (SELECT pa.id FROM paciente pa JOIN usuario u ON u.id = pa.usuario_id
                      WHERE u.email = 'chat.paciente@quickcare.dev');

DO $$
DECLARE
    v_paciente BIGINT;
    v_usuario_paciente BIGINT;
    v_enfermeira BIGINT;
    v_usuario_enfermeira BIGINT;
    v_cuidador BIGINT;
    v_usuario_cuidador BIGINT;
    v_solicitacao BIGINT;
    v_linha_enfermeiro BIGINT;
    v_linha_cuidador BIGINT;
BEGIN
    SELECT pa.id, u.id INTO v_paciente, v_usuario_paciente
    FROM paciente pa JOIN usuario u ON u.id = pa.usuario_id WHERE u.email = 'chat.paciente@quickcare.dev';
    SELECT pr.id, u.id INTO v_enfermeira, v_usuario_enfermeira
    FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = 'chat.enfermeira@quickcare.dev';
    SELECT pr.id, u.id INTO v_cuidador, v_usuario_cuidador
    FROM profissional pr JOIN usuario u ON u.id = pr.usuario_id WHERE u.email = 'chat.cuidador@quickcare.dev';

    INSERT INTO solicitacao (paciente_id, descricao, modalidade, cep, logradouro, numero, complemento, bairro,
                             cidade, uf, latitude, longitude, prazo_desejado, status)
    VALUES (v_paciente, 'Pós-operatório do quadril: curativo da incisão e um cuidador para ajudar durante o dia.',
            'PRESENCIAL', '01310100', 'Avenida Paulista', '2000', 'Apto 81', 'Bela Vista', 'São Paulo', 'SP',
            -23.557000, -46.659000, CURRENT_DATE + 5, 'EM_ANDAMENTO')
    RETURNING id INTO v_solicitacao;

    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade, vagas_preenchidas)
    VALUES (v_solicitacao, 'ENFERMEIRO', 1, 1) RETURNING id INTO v_linha_enfermeiro;
    INSERT INTO solicitacao_profissional (solicitacao_id, categoria, quantidade, vagas_preenchidas)
    VALUES (v_solicitacao, 'CUIDADOR', 1, 1) RETURNING id INTO v_linha_cuidador;

    INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
    SELECT v_linha_enfermeiro, id FROM especialidade WHERE nome = 'Curativos' AND categoria = 'ENFERMEIRO';
    INSERT INTO solicitacao_profissional_especialidade (solicitacao_profissional_id, especialidade_id)
    SELECT v_linha_cuidador, id FROM especialidade WHERE nome = 'Cuidador de Idosos' AND categoria = 'CUIDADOR';

    INSERT INTO proposta (solicitacao_id, profissional_id, atuacao_id, solicitacao_profissional_id, valor, abordagem,
                          inicio, termino, status)
    VALUES (v_solicitacao, v_enfermeira,
            (SELECT id FROM profissional_atuacao WHERE profissional_id = v_enfermeira AND categoria = 'ENFERMEIRO' ORDER BY id LIMIT 1),
            v_linha_enfermeiro, 180.00, 'Troca do curativo da incisão todas as manhãs e avaliação da cicatrização.',
            (CURRENT_DATE + 5 + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
            (CURRENT_DATE + 5 + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo', 'ACEITA'),
           (v_solicitacao, v_cuidador,
            (SELECT id FROM profissional_atuacao WHERE profissional_id = v_cuidador AND categoria = 'CUIDADOR' ORDER BY id LIMIT 1),
            v_linha_cuidador, 150.00, 'Acompanhamento durante o dia: banho, alimentação e apoio para caminhar.',
            (CURRENT_DATE + 5 + TIME '08:00') AT TIME ZONE 'America/Sao_Paulo',
            (CURRENT_DATE + 5 + TIME '18:00') AT TIME ZONE 'America/Sao_Paulo', 'ACEITA');

    INSERT INTO atendimento (proposta_id, solicitacao_id, status, inicio, termino, aceito_em, criado_em)
    SELECT p.id, p.solicitacao_id, 'AGENDADO', p.inicio, p.termino, now() - INTERVAL '2 days', now() - INTERVAL '2 days'
    FROM proposta p
    WHERE p.solicitacao_id = v_solicitacao AND p.status = 'ACEITA';

    INSERT INTO conversa_profissional (solicitacao_id, usuario_id, entrou_em)
    VALUES (v_solicitacao, v_usuario_enfermeira, now() - INTERVAL '2 days'),
           (v_solicitacao, v_usuario_cuidador, now() - INTERVAL '2 days');

    INSERT INTO mensagem (solicitacao_id, remetente_id, profissional_usuario_id, conteudo, data_envio)
    VALUES (v_solicitacao, v_usuario_paciente, NULL,
            'Bom dia aos dois! Criei este grupo para combinarmos os horários do pós-operatório.', now() - INTERVAL '1 day 3 hours'),
           (v_solicitacao, v_usuario_enfermeira, NULL,
            'Bom dia, Helena! Chego às 8h para o curativo e deixo as orientações por escrito.', now() - INTERVAL '1 day 2 hours'),
           (v_solicitacao, v_usuario_cuidador, NULL,
            'Combinado. Chego logo depois da Rafaela e fico até as 18h.', now() - INTERVAL '1 day 1 hour'),
           (v_solicitacao, v_usuario_paciente, v_usuario_enfermeira,
            'Rafaela, a incisão coçou bastante ontem à noite. É normal?', now() - INTERVAL '20 hours'),
           (v_solicitacao, v_usuario_enfermeira, v_usuario_enfermeira,
            'É comum na cicatrização. Amanhã eu avalio; se aparecer vermelhidão, me avise antes.', now() - INTERVAL '19 hours'),
           (v_solicitacao, v_usuario_paciente, v_usuario_cuidador,
            'Otávio, consegue trazer um andador emprestado da clínica?', now() - INTERVAL '3 hours'),
           (v_solicitacao, v_usuario_cuidador, v_usuario_cuidador,
            'Consigo sim, levo amanhã cedo.', now() - INTERVAL '2 hours');
END $$;

