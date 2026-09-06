# SaltoUrl — Encurtador de Links

API REST para encurtamento de URLs, com autenticação JWT, rastreamento de cliques via mensageria assíncrona e containerização completa com Docker.

## Tecnologias

| Categoria | Tecnologia |
|---|---|
| Linguagem | Java 26 |
| Framework | Spring Boot 4.1.1 |
| Banco de Dados | PostgreSQL 15 |
| Mensageria | RabbitMQ 3 |
| Autenticação | JWT (JJWT 0.11.5) |
| Migrações | Flyway |
| Containerização | Docker / Docker Compose |
| Build | Maven |

## Funcionalidades

- **Cadastro e autenticação de usuários** com JWT via cookie HttpOnly
- **Criação de links curtos** com código alfanumérico de 6 caracteres gerado de forma segura
- **Redirecionamento** HTTP 302 para a URL original
- **Rastreamento de cliques** de forma assíncrona via RabbitMQ (IP anonimizado com SHA-256, User-Agent)
- **Expiração de links** com suporte a campo `expires_at`
- **Refresh tokens** para revalidação de sessão

## Modelo de Dados

```
users
 └── links (1:N)
      └── click_events (1:N)
 └── refresh_tokens (1:N)
```

## Endpoints

### Autenticação — `/auth`

| Método | Rota | Descrição | Auth |
|---|---|---|---|
| `POST` | `/auth/register` | Cria um novo usuário | ❌ |
| `POST` | `/auth/login` | Realiza login e define o cookie JWT | ❌ |

### Links

| Método | Rota | Descrição | Auth |
|---|---|---|---|
| `POST` | `/links` | Cria um link curto | ✅ |
| `GET` | `/{code}` | Redireciona para a URL original | ❌ |

### Exemplos de Payload

**Registro:**
```json
POST /auth/register
{
  "email": "usuario@email.com",
  "password": "senha123"
}
```

**Login:**
```json
POST /auth/login
{
  "email": "usuario@email.com",
  "password": "senha123"
}
```
> O token JWT é retornado como cookie `jwt` (HttpOnly).

**Criar link:**
```json
POST /links
Authorization: Cookie jwt=<token>

{
  "originalUrl": "https://www.exemplo.com/pagina-muito-longa"
}
```

**Resposta:**
```json
{
  "code": "aB3xYz",
  "originalUrl": "https://www.exemplo.com/pagina-muito-longa",
  "shortUrl": "http://localhost:8080/aB3xYz",
  "createdAt": "2026-09-06T11:00:00"
}
```

## Como executar

### Pré-requisitos

- [Docker](https://docs.docker.com/get-docker/) e [Docker Compose](https://docs.docker.com/compose/)

### 1. Configure as variáveis de ambiente

Crie um arquivo `.env` na raiz do projeto com base no modelo abaixo:

```env
DB_NAME=saltourl_db
DB_USER=postgres
DB_PASSWORD=sua_senha

RABBITMQ_USER=admin
RABBITMQ_PASSWORD=sua_senha
RABBITMQ_COOKIE=cookie_aleatorio_seguro

JWT_SECRET=chave_secreta_base64_de_pelo_menos_256bits
```

### 2. Suba os containers

```bash
docker compose up --build
```

A API estará disponível em `http://localhost:8080`.

O painel de gerenciamento do RabbitMQ estará em `http://localhost:15672`.

### 3. Executar localmente (sem Docker)

Certifique-se de ter PostgreSQL e RabbitMQ rodando localmente com as configurações padrão, depois:

```bash
./mvnw spring-boot:run
```

## Testes

O projeto utiliza **Testcontainers** para testes de integração, subindo instâncias reais de PostgreSQL e RabbitMQ automaticamente. Necessário ter o Docker instalado.

```bash
./mvnw test
```

## Arquitetura

```
┌──────────────┐     HTTP      ┌──────────────────┐
│    Client    │ ───────────►  │  Spring Boot API │
└──────────────┘               └───────┬──────────┘
                                       │
                        ┌──────────────┼──────────────┐
                        ▼              ▼              ▼
                  ┌──────────┐  ┌──────────┐  ┌───────────┐
                  │PostgreSQL│  │ RabbitMQ │  │  Flyway   │
                  │          │  │          │  │(Migrations│
                  └──────────┘  └──────────┘  └───────────┘
```

**Fluxo de rastreamento de cliques:**
1. Requisição chega em `GET /{code}`
2. API faz o redirect e publica um evento no RabbitMQ
3. O listener consome o evento de forma assíncrona e persiste o `ClickEvent` no banco

Esse design desacopla o redirecionamento (crítico para latência) do registro de analytics.


## Segurança

- Senhas armazenadas com hash via **BCrypt**
- IPs dos clientes anonimizados com **SHA-256** antes de persistir
- Autenticação via **JWT** entregue em cookie **HttpOnly** (proteção contra XSS)
- Sessões gerenciadas por **refresh tokens** com suporte a revogação
