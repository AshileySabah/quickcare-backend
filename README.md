# QuickCare — Back-end

API do QuickCare, um marketplace de profissionais de saúde orientado a demanda: o paciente descreve o que precisa uma única vez e os profissionais aprovados para aquela área enviam propostas. Projeto de TCC do curso de Análise e Desenvolvimento de Sistemas (FATEC).

O front-end fica no repositório `quickcare-frontend`.

## Stack

- **Java 21** e **Spring Boot 3.4** (Web, Data JPA, Security, Validation, WebSocket, Mail, Actuator, Thymeleaf para os e-mails)
- **PostgreSQL 16 + PostGIS** (busca por distância), migrations com **Flyway**
- **JWT** (jjwt) enviado no cabeçalho `Authorization: Bearer`
- **STOMP sobre WebSocket** para notificações e chat em tempo real
- **Supabase Storage** para documentos, avatares e anexos
- E-mail por **Brevo** (API) ou SMTP
- **springdoc-openapi** (Swagger)
- Infra: Render (API), Supabase (banco e bucket), Vercel (front)

## Perfis

- **Paciente**: cria solicitações, recebe e aceita propostas, acompanha os atendimentos, avalia, denuncia e bloqueia.
- **Profissional**: tem uma ou mais atuações (categoria e especialidades, com registro profissional validado), vê as solicitações compatíveis e envia propostas.
- **Admin**: valida cadastros, decide disputas, denúncias, recursos e banimentos, e atua sobre solicitações, propostas e bloqueios.

Uma mesma conta pode ter os perfis de paciente e de profissional e alternar entre eles sem sair. O admin não tem outro perfil.

## Funcionalidades

### Cadastro e acesso
- Cadastro de paciente e de profissional com documentos (CPF, RG/CNH, CNPJ opcional, registro por atuação) e avatar. Os arquivos são enviados ao storage em paralelo e, se a gravação falhar, são apagados.
- Validações por algoritmo no cadastro (dígitos de CPF, CNPJ, CNH e RG-SP) e regra de 18 anos ou mais.
- **Confirmação de e-mail**: depois do cadastro, a conta só entra pelo link enviado por e-mail, que já faz o login. O link vale 12 horas e pode ser reenviado (intervalo mínimo de 1 minuto). Cadastros não confirmados no prazo são apagados junto com os arquivos.
- Login com token JWT de 7 dias. A troca de senha e de e-mail encerra as outras sessões.
- Redefinição de senha por e-mail.

### Validação cadastral
- O admin aprova ou reprova cada etapa (CPF, identidade, CNPJ e o registro de cada atuação), sempre com justificativa e anexo opcional.
- Pré-validação opcional no painel do admin por APIs de terceiros (Serpro para CPF, BrasilAPI para CNPJ). A API nunca decide, só ajuda o admin.
- Quem foi reprovado pode reenviar. Quem altera dados ou documentos entra em revalidação e, enquanto isso, não cria solicitação nem proposta.

### Solicitações, propostas e atendimentos
- A solicitação pede uma ou mais vagas por categoria e especialidade, com modalidade presencial ou online.
- O profissional envia proposta com valor, abordagem e período (início e término). Pode ter uma proposta ativa por atuação na mesma solicitação.
- Cada proposta aceita vira um **atendimento**, que muda de status sozinho com o tempo. Há reaceite quando a solicitação ou a proposta é alterada, remarcação combinada entre os dois e cancelamento.
- Relato de problema, contestação e disputa decidida pelo admin.
- **Ocorrências** por desistência ou falta (regra das 48h). Três ocorrências em 90 dias geram um bloqueio de criação crescente (7, 14, 21 dias...). Cabe recurso.

### Relacionamento entre usuários
- **Perfis públicos** de profissional e de paciente. Os dados do paciente só aparecem para quem tem vínculo com ele.
- **Avaliação** anônima e às cegas, com estrelas e destaques, liberada no término do atendimento e com janela de 30 dias.
- **Denúncia** com fila no admin e recurso.
- **Banimento**, permanente ou por suspensão, com recurso.
- **Bloqueio entre usuários**: funciona nos dois sentidos, encerra propostas e atendimentos vigentes entre os dois e esconde nome, foto e perfil de um para o outro.
- **Consulta pública** sem login a profissionais e a solicitações anonimizadas.

### Chat
- Uma conversa individual entre o paciente e cada profissional aceito, mais um grupo por solicitação quando há dois ou mais profissionais.
- O profissional entra quando a proposta é aceita e sai quando ela deixa de estar aceita ou quando existe um bloqueio entre ele e o paciente, qualquer que seja o autor do bloqueio. Depois de sair, ele lê o histórico até a saída e não escreve mais.
- Texto e um anexo por mensagem. A mensagem apagada continua no histórico como "mensagem apagada", sem conteúdo.
- Busca de conversas pelo nome dos participantes e pelo texto das mensagens, sem diferenciar acentos nem maiúsculas.
- Mensagens não lidas salvas por conversa, com notificação no sino e e-mail após 1 hora sem leitura.

### Notificações e segurança
- Notificações in-app por perfil, em tempo real, e por e-mail nos eventos importantes. Há lembretes antes do atendimento e pedidos de avaliação.
- Tela de segurança para trocar e-mail (com código e link "Não fui eu" no e-mail antigo), trocar senha e encerrar perfil ou conta. O encerramento tem 30 dias para recuperação e, depois disso, os dados pessoais são anonimizados.

## Organização do código

Um pacote por domínio em `src/main/java/com/quickcare/backend`. Cada um tem entidade, repositório, service com as regras de negócio, controller e DTOs:

```
administrador   admin inicial, auditoria das ações do admin
atendimento     atendimentos, transições automáticas, job de 1 em 1 minuto
atuacao         categorias/especialidades do profissional
auth            login, sessão, redefinição de senha
avaliacao       avaliações e destaques
banimento       banimentos e recursos
bloqueiousuario bloqueio entre paciente e profissional
cadastro        cadastro, novo perfil, confirmação de e-mail
chat            conversas, mensagens, não lidas
consulta        consulta pública
denuncia        denúncias
documento       documentos e storage
jwt / config    segurança, CORS, WebSocket
notificacao     notificações in-app e e-mail
ocorrencia      ocorrências, bloqueio de criação, recursos, perfis no admin
problema        problemas de atendimento e disputas
proposta / solicitacao
seguranca       troca de e-mail/senha, encerramento e recuperação
validacao       etapas de validação e pré-validação
```

As regras de negócio ficam nos services, e o front só reflete o que a API devolve. As migrations ficam em `src/main/resources/db/migration` (V1 a V40). Migrations já aplicadas não são alteradas: toda mudança de esquema entra numa migration nova.

## Rodando localmente

Pré-requisitos: Docker. Para rodar fora do Docker, também é preciso ter Java 21.

1. Crie um `.env` na raiz do projeto com as variáveis abaixo. **Nunca suba esse arquivo para o repositório.**

   | Variável | Uso |
   | --- | --- |
   | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | banco local (padrão `quickcare`) |
   | `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `SUPABASE_BUCKET` | storage. Sem elas a API sobe, mas os uploads falham |
   | `EMAIL_APP`, `EMAIL_APP_PASSWORD` ou `BREVO_API_KEY` | envio de e-mail |
   | `FRONTEND_URL` | links dos e-mails (padrão `http://localhost:4200`) |
   | `ADMIN_EMAIL`, `ADMIN_SENHA`, `ADMIN_NOME`, `ADMIN_CPF` | admin criado ao subir, opcional |

2. Suba o Postgres e a API:

   ```bash
   ./dev.sh          # Linux/macOS (./dev.sh stop para derrubar)
   .\dev.ps1         # Windows   (.\dev.ps1 -Stop para derrubar)
   ```

   Ou rode só o banco com `docker compose up -d postgres` e a API com `./mvnw spring-boot:run`.

3. Endereços:
   - API: `http://localhost:8080`
   - Swagger: `http://localhost:8080/swagger-ui/index.html`
   - Health: `http://localhost:8080/actuator/health`

### Massa de teste

No perfil `dev`, o Flyway também roda `db/seed-dev/R__massa_local.sql`, que cria contas `*@quickcare.dev` com a senha `Senha@123`. Por exemplo:

| Conta | Situação |
| --- | --- |
| `admin@quickcare.dev` | admin |
| `paciente@quickcare.dev` | paciente aprovado |
| `profissional@quickcare.dev` | profissional aprovado |
| `ambos@quickcare.dev` | conta com os dois perfis |
| `paciente.pendente@quickcare.dev`, `profissional.pendente@quickcare.dev` | aguardando validação |

Para testar o chat com dois profissionais, o perfil `dev` também roda `db/seed-dev/R__massa_local_chat.sql`. Ele cria uma paciente, uma enfermeira e um cuidador (`chat.paciente@`, `chat.enfermeira@` e `chat.cuidador@quickcare.dev`, mesma senha), com atendimentos agendados, um grupo e conversas individuais já iniciadas. Como é uma migration repetível, o Flyway recria esse cenário sempre que o arquivo muda.

## Testes

```bash
./mvnw test
```

Os testes são de service, com Mockito, e cobrem as regras de negócio de cada domínio.

## Produção

O perfil `prod` lê o banco de `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` e liga o keep-alive: um GET no próprio health a cada 14 minutos, para o Render não hibernar, e um `SELECT 1` diário, para o Supabase não pausar. A imagem é gerada pelo `Dockerfile`.
