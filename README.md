# QuickCare — Back-end

## Sobre o projeto

O QuickCare é uma plataforma web que conecta pacientes a profissionais de saúde por meio de solicitações. Foi desenvolvido como Trabalho de Conclusão de Curso de Análise e Desenvolvimento de Sistemas da FATEC.

### O problema

A contratação de profissionais de saúde, principalmente para atendimento domiciliar, costuma depender de indicações, grupos de mensagens ou listas de profissionais. O paciente precisa falar com cada um para saber disponibilidade e preço, e nem sempre consegue confirmar se o profissional é habilitado.

### Como o QuickCare resolve

O paciente publica uma única solicitação com as especialidades, a quantidade de profissionais, a modalidade (presencial ou online) e o endereço. Os profissionais habilitados naquela área, e dentro do seu raio de atendimento, enviam propostas com valor e abordagem. Cada proposta aceita vira um atendimento acompanhado pela plataforma.

O cadastro do profissional passa por validação em etapas (CPF, documento de identidade, CNPJ e registro no conselho de classe) antes de ele poder enviar propostas.

### Público-alvo

Pacientes e familiares que precisam de atendimento em saúde, como enfermagem, cuidador, fisioterapia ou consulta, e profissionais de saúde que prestam esses serviços.

### Este repositório

API do QuickCare, com endpoints REST e WebSocket. As regras de negócio ficam aqui, e o front-end apenas reflete o que a API devolve.

## Links

| O quê                    | Endereço                                                          |
| ------------------------ | ----------------------------------------------------------------- |
| Sistema em produção      | https://quickcare-frontend.vercel.app                             |
| API em produção          | https://quickcare-backend-b4a4.onrender.com                       |
| Swagger em produção      | https://quickcare-backend-b4a4.onrender.com/swagger-ui/index.html |
| Repositório do front-end | https://github.com/AshileySabah/quickcare-frontend                |

A API roda no plano gratuito do Render. A primeira chamada depois de um tempo parado pode demorar.

## Integrantes

- Ashiley Sabrina Santiago Rodrigues
- Yasmin Feliciano Pereira Serafim de Lima
- Vinicius Santana Barcellos

Orientador: Carlos Henrique Verissimo Pereira.

## Perfis de usuário

- **Paciente**: cria solicitações, escolhe entre as propostas recebidas e acompanha os atendimentos.
- **Profissional**: cadastra uma ou mais atuações (categoria, especialidades e registro profissional) e envia propostas para as solicitações compatíveis.
- **Admin**: valida cadastros e modera a plataforma (disputas, denúncias, ocorrências, recursos e banimentos).

Uma mesma conta pode ter os perfis de paciente e de profissional e alternar entre eles sem sair. O admin não pode ter outro perfil.

## Funcionalidades

### Cadastro e acesso

- Cadastro de paciente e de profissional com documentos (CPF, RG ou CNH, CNPJ opcional e registro por atuação) e avatar.
- Validações no cadastro: dígitos de CPF, CNPJ, CNH e RG de SP, senha forte e idade mínima de 18 anos.
- Confirmação de e-mail: a conta só entra pelo link enviado, que já faz o login. O link vale 12 horas e pode ser reenviado a cada 1 minuto. Cadastros não confirmados no prazo são apagados junto com os arquivos.
- Redefinição de senha por e-mail, com link de 30 minutos.
- Segurança da conta: troca de e-mail com código de 10 minutos e link "Não fui eu" no e-mail antigo (válido por 7 dias), troca de senha e encerramento de perfil ou conta. O encerramento tem 30 dias para recuperação. Depois disso, os dados pessoais são anonimizados.

### Validação cadastral

- Cada etapa (CPF, identidade, CNPJ e registro de cada atuação) é aprovada ou reprovada pelo admin, com justificativa e anexo opcional.
- Pré-validação opcional no painel do admin: CPF pela Serpro e CNPJ pela BrasilAPI. A consulta só sugere um parecer; quem decide é o admin.
- Quem foi reprovado pode reenviar a etapa. Quem altera dados ou documentos entra em revalidação e, enquanto isso, não cria solicitação nem proposta.

### Solicitações e propostas

- A solicitação pede uma ou mais vagas por categoria e especialidade, na modalidade presencial ou online.
- O profissional só vê as solicitações compatíveis com as atuações aprovadas dele e, no presencial, dentro do seu raio de atendimento.
- O profissional envia proposta com valor, abordagem e período (início e término). Ele pode ter uma proposta ativa por atuação na mesma solicitação.
- Edição com reaceite: se a solicitação ou a proposta aceita muda, a outra parte precisa aceitar de novo.
- Recusa, cancelamento, reabertura de solicitação e reenvio de proposta.
- Consulta pública, sem login, a profissionais e a solicitações anonimizadas.

### Atendimentos

- Cada proposta aceita vira um atendimento.
- Um job roda a cada minuto e muda o status pelo horário: agendado, em andamento, finalizado e concluído. A conclusão acontece 72 horas depois do término.
- Remarcação combinada entre os dois e cancelamento.
- Relato de problema com prazo de resposta de 7 dias, contestação e disputa decidida pelo admin.
- Ocorrências por desistência ou falta, com a regra das 48 horas. Três ocorrências em 90 dias geram um bloqueio de criação de 7 dias por nível (7, 14, 21...). Sessenta dias sem ocorrência zeram o nível. Cabe recurso.

### Chat e notificações

- Uma conversa individual entre o paciente e cada profissional aceito, mais um grupo por solicitação quando há dois ou mais profissionais.
- O profissional entra quando a proposta é aceita. Ele sai quando a proposta deixa de estar aceita ou quando há bloqueio entre ele e o paciente. Depois de sair, lê o histórico até a saída e não escreve mais.
- Texto e um anexo por mensagem. A mensagem apagada continua no histórico como "mensagem apagada", sem o conteúdo.
- Busca de conversas pelo nome dos participantes e pelo texto das mensagens.
- Mensagens não lidas por conversa, com aviso por e-mail depois de 1 hora sem leitura.
- Notificações in-app separadas por perfil, entregues em tempo real pelo WebSocket. Os eventos importantes também geram e-mail.
- Lembretes na véspera e 30 minutos antes do atendimento, e pedidos de avaliação 3 e 7 dias depois do término.

### Avaliação e moderação

- Avaliação anônima e às cegas, com estrelas e destaques. Ela é liberada no término do atendimento e fica aberta por 30 dias.
- Perfis públicos de profissional e de paciente. Os dados do paciente só aparecem para quem tem vínculo com ele.
- Denúncia, com fila no admin e recurso.
- Banimento permanente ou suspensão de 1 a 365 dias, com recurso.
- Bloqueio entre usuários nos dois sentidos. Ele encerra propostas e atendimentos vigentes entre os dois e esconde nome, foto e perfil de um para o outro.
- Ações gerais do admin sobre perfis, solicitações, propostas e bloqueios, com registro de auditoria.

## Tecnologias

### Stack

| Tecnologia            | Uso                                                             |
| --------------------- | --------------------------------------------------------------- |
| Java 21               | linguagem                                                       |
| Spring Boot 3.4       | Web, Data JPA, Security, Validation, WebSocket, Mail e Actuator |
| PostgreSQL 16         | banco de dados                                                  |
| Flyway                | migrations versionadas e massa de teste local                   |
| jjwt 0.12             | geração e validação do JWT                                      |
| STOMP sobre WebSocket | tempo real no chat e nas notificações                           |
| Thymeleaf             | templates HTML dos e-mails                                      |
| springdoc-openapi 2.7 | Swagger                                                         |
| Lombok                | redução de código repetitivo nas entidades e nos services       |
| JUnit 5 e Mockito     | testes                                                          |

### Autenticação

A API não guarda sessão (stateless) e usa um único JWT, sem refresh token.

- O `POST /auth/login` devolve o token no cabeçalho `X-Auth-Token` da resposta.
- O cliente envia o token em `Authorization: Bearer <token>` nas chamadas HTTP e no `CONNECT` do STOMP.
- O token vale 7 dias (`quickcare.jwt.token-expiration-days`). Quando passa da metade da validade, a resposta seguinte traz um token novo no mesmo `X-Auth-Token`.
- A troca de senha e o encerramento da conta invalidam os tokens emitidos antes.

### Infraestrutura

| Serviço  | Uso                                                                                |
| -------- | ---------------------------------------------------------------------------------- |
| Render   | hospeda a API (imagem do `Dockerfile`)                                             |
| Supabase | Postgres de produção e Supabase Storage para documentos, avatares e anexos do chat |
| Vercel   | hospeda o front-end                                                                |

### Serviços de terceiros

| Serviço                   | Para que serve                                                           | Chamada |
| ------------------------- | ------------------------------------------------------------------------ | ------- |
| Supabase Storage          | upload, exclusão e URL assinada dos arquivos                             | HTTP    |
| Nominatim (OpenStreetMap) | converte o endereço do cadastro e da solicitação em latitude e longitude | HTTP    |
| Serpro (Consulta CPF)     | pré-validação do CPF no painel do admin                                  | HTTP    |
| BrasilAPI                 | pré-validação do CNPJ no painel do admin                                 | HTTP    |
| Brevo                     | envio de e-mail pela API, quando `BREVO_API_KEY` está definida           | HTTP    |
| SMTP do Gmail             | envio de e-mail quando não há chave do Brevo                             | SMTP    |

O ViaCEP e o Feature Flags da Vercel são usados pelo front-end, não por esta API.

### Tempo real

O endpoint STOMP fica em `/ws`, sobre WebSocket nativo (sem SockJS). A conexão exige o token no `CONNECT`. O cliente só pode assinar destinos `/user/queue/...` e não envia mensagens pelo socket: ações como enviar mensagem no chat são feitas por HTTP, e o servidor publica o resultado para os participantes. O chat e as notificações usam esse canal.

### Geolocalização

O endereço informado no cadastro e na solicitação é convertido em latitude e longitude pelo Nominatim. A distância entre paciente e profissional é calculada na própria API, pela fórmula de Haversine (`geolocalizacao/Coordenadas.java`), e comparada com o raio de atendimento do profissional.

## Arquitetura e organização do código

<!-- Inserir aqui a imagem do diagrama de arquitetura (front na Vercel, API no Render, Postgres e Storage no Supabase, serviços de terceiros). -->

O código fica em `src/main/java/com/quickcare/backend`, com um pacote por domínio. Cada pacote reúne:

- **controller**: endpoints REST, documentados com `@Tag` e `@Operation`;
- **service**: regras de negócio e transações;
- **repository**: acesso ao banco com Spring Data JPA;
- **entidades e enums** do domínio (status, motivos e tipos);
- **dto/**: requests e responses da API;
- **exception/**: exceções do domínio, quando há.

```
administrador    admin inicial, auditoria das ações do admin
atendimento      atendimentos e job de transição de status
atuacao          categorias e especialidades do profissional
auth             login, sessão, perfil ativo, redefinição de senha
avaliacao        avaliações e destaques
banimento        banimentos e recursos
bloqueiousuario  bloqueio entre paciente e profissional
cadastro         cadastro, novo perfil, confirmação de e-mail
chat             conversas, mensagens e não lidas
common           tratamento de erros, e-mail, validadores de CPF, CNPJ, senha e idade
config           segurança, CORS, WebSocket, OpenAPI
consulta         consulta pública
denuncia         denúncias
documento        documentos e envio ao storage
especialidade    categorias e especialidades
geolocalizacao   geocodificação e cálculo de distância
jwt              geração e validação do token
keepalive        ping do Render e consulta ao banco em produção
localizacao      UFs
notificacao      notificações in-app e por e-mail
ocorrencia       ocorrências, bloqueio de criação, recursos
paciente         entidade e repositório do paciente
perfil           perfil próprio e perfis públicos
problema         problemas de atendimento e disputas
profissional     entidade e repositório do profissional
proposta         propostas
seguranca        troca de e-mail e senha, encerramento e recuperação
solicitacao      solicitações e vagas
storage          configuração do Supabase Storage
usuario          usuário, contato de emergência e enums comuns
validacao        etapas de validação e pré-validação (externa/)
```

Os erros saem num formato único (`ApiError`), montado pelo `GlobalExceptionHandler`.

As migrations ficam em `src/main/resources/db/migration` (V1 a V40). Migration já aplicada não é alterada: toda mudança de esquema entra numa nova.

Os documentos e anexos sobem ao Supabase Storage em paralelo. Se a gravação no banco falhar, os arquivos enviados são apagados.

Os templates de e-mail ficam em `src/main/resources/templates/email`.

## Como rodar localmente

### Pré-requisitos

- Docker com Docker Compose.
- Java 21, só para rodar a API fora do Docker. O Maven vem pelo wrapper (`mvnw`).

### Variáveis de ambiente

Crie um `.env` na raiz do projeto. O `docker-compose.yml` lê esse arquivo. Não suba o `.env` para o repositório.

| Variável                                                                       | Para que serve                                                   | Obrigatória                                                            |
| ------------------------------------------------------------------------------ | ---------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`                            | banco local do perfil `dev`                                      | não (padrão `quickcare`)                                               |
| `SUPABASE_URL`, `SUPABASE_SECRET_KEY`                                          | acesso ao Supabase Storage                                       | sim fora do Docker; no Docker há valores fictícios e os uploads falham |
| `SUPABASE_BUCKET`                                                              | nome do bucket                                                   | não                                                                    |
| `BREVO_API_KEY`                                                                | envio de e-mail pela API do Brevo                                | não                                                                    |
| `EMAIL_APP`, `EMAIL_APP_PASSWORD`                                              | conta e senha de app do Gmail para SMTP, usadas sem Brevo        | não                                                                    |
| `FRONTEND_URL`                                                                 | base dos links enviados por e-mail                               | não (padrão `http://localhost:4200`)                                   |
| `ADMIN_EMAIL`, `ADMIN_SENHA`, `ADMIN_NOME`, `ADMIN_CPF`                        | cria um admin ao subir a API                                     | não                                                                    |
| `JWT_SECRET`                                                                   | chave de assinatura do JWT                                       | sim em produção; em dev há uma chave padrão                            |
| `SPRING_PROFILES_ACTIVE`                                                       | perfil do Spring (`dev` ou `prod`)                               | não (padrão `dev`)                                                     |
| `PORT`                                                                         | porta HTTP                                                       | não (padrão `8080`)                                                    |
| `GEOCODIFICACAO_HABILITADA`, `GEOCODIFICACAO_URL`, `GEOCODIFICACAO_USER_AGENT` | liga e configura o Nominatim                                     | não                                                                    |
| `SERPRO_CPF_URL`, `SERPRO_CPF_TOKEN`                                           | endereço e token da consulta de CPF                              | não (padrão: ambiente de demonstração da Serpro)                       |
| `BRASILAPI_URL`                                                                | endereço da BrasilAPI                                            | não                                                                    |
| `VALIDACAO_EXTERNA_TIMEOUT_SEGUNDOS`                                           | tempo limite das consultas da pré-validação                      | não (padrão `8`)                                                       |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`                                         | banco do perfil `prod`                                           | sim em produção                                                        |
| `KEEP_ALIVE_HABILITADO`                                                        | liga o keep-alive no perfil `prod`                               | não (padrão `true` em `prod`)                                          |
| `RENDER_EXTERNAL_URL`                                                          | URL pública usada no ping do keep-alive; o Render define sozinho | não                                                                    |

Sem nenhuma configuração de e-mail, a API sobe, mas os envios falham e aparecem no log.

### Subindo

Com o Docker, o script sobe o Postgres e a API e espera o health responder:

```bash
./dev.sh          # Linux/macOS (./dev.sh stop para derrubar)
```

```powershell
.\dev.ps1         # Windows (.\dev.ps1 -Stop para derrubar)
```

Sem os scripts:

```bash
docker compose up -d --build   # sobe banco e API
docker compose down            # derruba
```

Para rodar a API pela IDE ou pelo Maven, suba só o banco e defina `SUPABASE_URL` e `SUPABASE_SECRET_KEY` no ambiente:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

### Endereços locais

| O quê    | Endereço                                    |
| -------- | ------------------------------------------- |
| API      | http://localhost:8080                       |
| Swagger  | http://localhost:8080/swagger-ui/index.html |
| Health   | http://localhost:8080/actuator/health       |
| Postgres | `localhost:5432`                            |

No Swagger, faça `POST /auth/login`, copie o valor do cabeçalho `X-Auth-Token` da resposta e cole em **Authorize**.

### Massa de teste

No perfil `dev`, o Flyway também roda `src/main/resources/db/seed-dev/R__massa_local.sql`. Todas as contas usam a senha **`Senha@123`**.

| Conta                                 | Situação                                                     |
| ------------------------------------- | ------------------------------------------------------------ |
| `admin@quickcare.dev`                 | admin                                                        |
| `paciente@quickcare.dev`              | paciente aprovado, com solicitações e propostas              |
| `profissional@quickcare.dev`          | profissional aprovado, com atuações de enfermagem e medicina |
| `fernanda@quickcare.dev`              | profissional aprovada (enfermagem)                           |
| `ambos@quickcare.dev`                 | conta com os perfis de paciente e de profissional            |
| `maria@quickcare.dev`                 | paciente aprovada                                            |
| `nutricionista@quickcare.dev`         | profissional aprovada (nutrição), atende online              |
| `paciente.pendente@quickcare.dev`     | paciente aguardando validação                                |
| `profissional.pendente@quickcare.dev` | profissional aguardando validação, com CNPJ                  |
| `cuidador.pendente@quickcare.dev`     | cuidador aguardando validação                                |

A massa também cria outras contas `*@quickcare.dev` para cenários de ocorrências, bloqueios, avaliações, denúncias e banimentos. O script é repetível: o Flyway roda de novo quando o arquivo muda.

## Testes

```bash
./mvnw test
```

São testes unitários com JUnit 5 e Mockito, um arquivo por service ou regra. Eles cobrem:

- cadastro, confirmação de e-mail, login, token JWT e redefinição de senha;
- validadores de CPF, CNPJ, CNH, RG e idade mínima;
- validação cadastral, atuações e avaliação das consultas externas (Serpro e BrasilAPI);
- solicitações, propostas, ações do admin sobre propostas, atendimentos e problemas;
- regras de ocorrência e de bloqueio de criação;
- avaliações, denúncias, banimentos, perfis públicos e consulta pública;
- chat (conversas, mensagens e não lidas) e notificações;
- segurança da conta, código de verificação e encerramento;
- admin inicial e keep-alive.

O `QuickcareBackendApplicationTests` sobe o contexto completo do Spring, então precisa do Postgres local no ar.

## Produção

O perfil `prod` lê o banco de `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Ele também liga o keep-alive: um GET no próprio health a cada 14 minutos, para o Render não hibernar, e um `SELECT 1` diário às 3h, para o Supabase não pausar. A imagem é gerada pelo `Dockerfile` em duas etapas: build com Maven e execução com o JRE 21.
