CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    telefone VARCHAR(20),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE paciente (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario(id),
    banido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profissional (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario(id),
    banido BOOLEAN NOT NULL DEFAULT FALSE,
    modalidade_atendimento VARCHAR(20) NOT NULL CHECK (modalidade_atendimento IN ('PRESENCIAL', 'REMOTO', 'AMBOS')),
    raio_atendimento_km NUMERIC(6,2) NOT NULL,
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
