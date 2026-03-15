# 🏦 Banco EJB — Do Zero ao Deploy com Jakarta EE

> *"Porque aprender EJB não precisa ser chato como fila de banco!"*

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java)
![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-10-blue?style=for-the-badge)
![WildFly](https://img.shields.io/badge/WildFly-39-red?style=for-the-badge)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-🐳-blue?style=for-the-badge&logo=docker)

---

## 📖 O que é esse projeto?

Um sistema bancário simplificado construído do zero para aprender **Enterprise JavaBeans (EJB)** na prática. Nada de teoria seca — aqui você vai ver boleto sendo processado em background, filas JMS consumindo mensagens e agendamentos rodando sozinhos!

### O que você vai aprender aqui:
- ✅ `@Stateless` — CRUD bancário com transações automáticas
- ✅ `@Asynchronous` — Processar boleto sem travar a API
- ✅ `@Singleton` + `@Schedule` — Tarefas agendadas periódicas
- ✅ `@MessageDriven` — Consumir filas JMS com Artemis
- ✅ **JAX-RS** — API REST no estilo Jakarta EE
- ✅ **JPA + Hibernate** — Persistência com PostgreSQL

---

## 🏗️ Arquitetura do projeto

```
┌─────────────────────────────────────────────────────────┐
│                      Cliente (Postman)                   │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  ContaResource (JAX-RS)                  │
│           /api/contas   /api/contas/{id}/boletos          │
└──────┬──────────────┬──────────────────┬────────────────┘
       │              │                  │
       ▼              ▼                  ▼
┌──────────┐  ┌─────────────┐  ┌─────────────────┐
│ContaBean │  │ BoletoBean  │  │ JmsProducerBean │
│@Stateless│  │ @Stateless  │  │   @Stateless    │
│  (CRUD)  │  │@Asynchronous│  │  publica fila   │
└──────┬───┘  └──────┬──────┘  └────────┬────────┘
       │             │                   │
       │             │            ┌──────▼──────┐
       │             │            │ BoletoQueue │
       │             │            │    (JMS)    │
       │             │            └──────┬──────┘
       │             │                   │
       │             │         ┌─────────▼────────┐
       │             │         │BoletoListenerBean│
       │             │         │  @MessageDriven  │
       │             │         └─────────┬────────┘
       │             │                   │
       ▼             ▼                   ▼
┌─────────────────────────────────────────────────────────┐
│                    PostgreSQL (Docker)                    │
│                  tabelas: conta, boleto                   │
└─────────────────────────────────────────────────────────┘
       ▲
       │ a cada 5 minutos
┌──────┴───────────┐
│ AgendamentoBean  │
│    @Singleton    │
│    @Schedule     │
└──────────────────┘
```

---

## 🗂️ Estrutura de arquivos

```
banco-ejb/
├── src/main/java/br/com/banco/
│   ├── config/
│   │   └── JaxRsConfig.java          ← Ativa o REST em /api
│   ├── entity/
│   │   ├── Conta.java                ← Tabela conta no PostgreSQL
│   │   └── Boleto.java               ← Tabela boleto no PostgreSQL
│   ├── ejb/
│   │   ├── ContaBean.java            ← @Stateless: CRUD bancário
│   │   ├── BoletoBean.java           ← @Stateless + @Asynchronous
│   │   ├── AgendamentoBean.java      ← @Singleton + @Schedule
│   │   ├── BoletoListenerBean.java   ← @MessageDriven (MDB)
│   │   └── JmsProducerBean.java      ← Publica na fila JMS
│   └── rest/
│       └── ContaResource.java        ← Endpoints REST
├── src/main/resources/
│   └── META-INF/
│       └── persistence.xml           ← Configuração JPA
├── src/main/webapp/
│   └── WEB-INF/
│       └── web.xml
└── pom.xml
```

---

## 🚀 Como rodar o projeto

### Pré-requisitos

- Java 17+
- Maven 3.8+
- WildFly 39 (via Homebrew no Mac)
- Docker (para o PostgreSQL)

### 1️⃣ Sobe o PostgreSQL

```bash
mkdir -p init-sql
docker compose up -d
docker compose ps
```

### 2️⃣ Inicia o WildFly com suporte a JMS

```bash
# SEMPRE use standalone-full.xml para ter JMS disponível!
/opt/homebrew/opt/wildfly-as/libexec/bin/standalone.sh \
  --server-config=standalone-full.xml
```

> 💡 **Dica:** Crie um alias para não esquecer:
> ```bash
> echo 'alias wildfly="/opt/homebrew/opt/wildfly-as/libexec/bin/standalone.sh --server-config=standalone-full.xml"' >> ~/.zshrc
> source ~/.zshrc
> ```
> Agora basta digitar `wildfly` para subir o servidor!

### 3️⃣ Configura o WildFly (só na primeira vez)

```bash
# Baixa o driver PostgreSQL
curl -L https://jdbc.postgresql.org/download/postgresql-42.7.3.jar \
  -o ~/postgresql-42.7.3.jar

# Deploy do driver JDBC
/opt/homebrew/opt/wildfly-as/libexec/bin/jboss-cli.sh --connect \
  --command="deploy /Users/SEU_USUARIO/postgresql-42.7.3.jar"

# Cria o DataSource
/opt/homebrew/opt/wildfly-as/libexec/bin/jboss-cli.sh --connect \
  --command="/subsystem=datasources/data-source=BancoDS:add(\
jndi-name=java:/BancoDS,\
driver-name=postgresql-42.7.3.jar,\
connection-url=\"jdbc:postgresql://localhost:5432/ejbdb\",\
user-name=ejbuser,\
password=ejbpass,\
min-pool-size=5,\
max-pool-size=20)"

# Habilita o DataSource
/opt/homebrew/opt/wildfly-as/libexec/bin/jboss-cli.sh --connect \
  --command="/subsystem=datasources/data-source=BancoDS:enable"

# Cria a fila JMS
/opt/homebrew/opt/wildfly-as/libexec/bin/jboss-cli.sh --connect \
  --command="/subsystem=messaging-activemq/server=default/jms-queue=BoletoQueue:add(entries=[\"java:/jms/queue/BoletoQueue\"])"

# Recarrega o servidor
/opt/homebrew/opt/wildfly-as/libexec/bin/jboss-cli.sh --connect \
  --command=":reload"
```

### 4️⃣ Build e Deploy

```bash
mvn clean package

cp target/banco-ejb.war \
   /opt/homebrew/Cellar/wildfly-as/39.0.1/libexec/standalone/deployments/
```

Aguarde no log do WildFly:
```
WFLYSRV0016: Replaced deployment "banco-ejb.war"
[AgendamentoBean] HEALTH CHECK | Contas: X | Pendentes: X
```

---

## 🧪 Testando a API

### Criar uma conta

```bash
curl -X POST http://localhost:8080/banco-ejb/api/contas \
  -H "Content-Type: application/json" \
  -d '{
    "titular": "João Silva",
    "numeroConta": "12345-6",
    "agencia": "0001",
    "tipo": "CORRENTE"
  }'
```

**Resposta (201 Created):**
```json
{
  "id": 1,
  "titular": "João Silva",
  "saldo": 0,
  "tipo": "CORRENTE",
  "ativa": true
}
```

### Creditar saldo

```bash
curl -X PUT "http://localhost:8080/banco-ejb/api/contas/1/creditar?valor=1000"
```

### Emitir boleto

```bash
curl -X POST http://localhost:8080/banco-ejb/api/contas/1/boletos \
  -H "Content-Type: application/json" \
  -d '{
    "valor": "150.00",
    "vencimento": "2026-12-31",
    "beneficiario": "Empresa ABC"
  }'
```

### Processar via @Asynchronous

```bash
curl -X POST http://localhost:8080/banco-ejb/api/contas/1/boletos/1/processar
```

**Resposta imediata (202 Accepted):**
```json
{
  "mensagem": "Boleto enviado para processamento",
  "boletoId": 1,
  "status": "PROCESSANDO"
}
```

> ⚡ O `202 Accepted` retorna **na hora**! O processamento ocorre em background numa thread separada. Observe o log do WildFly para ver o `@Asynchronous` em ação!

### Publicar na fila JMS

```bash
curl -X POST http://localhost:8080/banco-ejb/api/contas/1/boletos/1/publicar
```

**Observe no log do WildFly as 3 threads trabalhando:**
```
[JmsProducerBean] Mensagem publicada na fila: 1
[BoletoMDB] [Thread-28 (activemq-client-global)] Mensagem recebida  ← Thread do MDB
[BoletoMDB] Processando boleto ID=1 valor=R$ 150,00
[BoletoBean] [Thread: EJB default-2] Iniciando boleto 1             ← Thread @Async
[BoletoBean] [Thread: EJB default-2] Boleto 1 finalizado: PAGO
```

> 🎯 **3 threads diferentes!**
> `REST (task-2)` → publica → `MDB (activemq)` → delega → `@Async (EJB default-2)`

---

## 📚 Conceitos EJB — Guia Rápido

### Os 4 tipos de EJB usados aqui

```
@Stateless          → Pool de instâncias, sem estado entre chamadas
                      Ideal para CRUD e regras de negócio
                      Equivale ao @Service do Spring

@Singleton          → Uma única instância na aplicação
+ @Startup          → Criado quando o servidor sobe
+ @Schedule         → Timer periódico automático
                      Equivale ao @Scheduled do Spring

@Asynchronous       → Executa em thread separada, retorna imediato
                      Equivale ao @Async do Spring

@MessageDriven      → Escuta fila JMS, chamado automaticamente
                      Equivale ao @KafkaListener do Spring
```

### @TransactionAttribute — Controle de transações

| Tipo | Quando usar |
|------|-------------|
| `REQUIRED` | Padrão — usa transação existente ou cria nova |
| `REQUIRES_NEW` | Sempre cria transação nova (ex: @Asynchronous) |
| `SUPPORTS` | Usa se houver, senão executa sem (ex: leituras) |
| `NOT_SUPPORTED` | Sempre executa sem transação |

### EJB vs Spring Boot — Comparativo

| EJB | Spring Boot | O que faz |
|-----|------------|-----------|
| `@Stateless` | `@Service` | Bean de serviço |
| `@Singleton` + `@Startup` | `@Component` | Bean singleton |
| `@Asynchronous` | `@Async` | Execução assíncrona |
| `@Schedule` | `@Scheduled` | Agendamento |
| `@MessageDriven` | `@KafkaListener` | Consumidor de fila |
| `@TransactionAttribute` | `@Transactional` | Controle de transação |
| `@PersistenceContext` | `@Autowired EntityManager` | Injeção JPA |
| `@Inject` | `@Autowired` | Injeção de dependência |

---

## 🔗 Endpoints disponíveis

| Método | URL | Descrição |
|--------|-----|-----------|
| `GET` | `/api/contas` | Lista todas as contas |
| `GET` | `/api/contas/{id}` | Busca conta por ID |
| `POST` | `/api/contas` | Cria nova conta |
| `DELETE` | `/api/contas/{id}` | Desativa conta |
| `PUT` | `/api/contas/{id}/creditar?valor=X` | Credita valor |
| `PUT` | `/api/contas/{id}/debitar?valor=X` | Debita valor |
| `POST` | `/api/contas/{id}/boletos` | Emite boleto |
| `GET` | `/api/contas/{id}/boletos` | Lista boletos da conta |
| `GET` | `/api/contas/{id}/boletos/{bid}` | Busca boleto |
| `POST` | `/api/contas/{id}/boletos/{bid}/processar` | Processa via @Async |
| `POST` | `/api/contas/{id}/boletos/{bid}/publicar` | Publica na fila JMS |

---

## 🐳 Docker Compose

```bash
docker compose up -d    # Sobe o PostgreSQL
docker compose down     # Para (mantém dados)
docker compose down -v  # Para e apaga os dados
```

**Conexão com o banco:**
```
Host:     localhost  |  Porta:  5432
Banco:    ejbdb      |  Senha:  ejbpass
Usuário:  ejbuser    |  URL:    jdbc:postgresql://localhost:5432/ejbdb
```

---

## 🤓 Por que essas decisões técnicas?

**Por que `BigDecimal` e não `double`?**
Porque `double` tem erros de arredondamento. Tente `0.1 + 0.2` no Java — vai dar `0.30000000000000004`. Para dinheiro, **sempre** `BigDecimal`!

**Por que `FetchType.LAZY` no relacionamento?**
Para não carregar a `Conta` inteira toda vez que buscar um `Boleto`. Economiza memória e evita consultas desnecessárias ao banco.

**Por que `standalone-full.xml`?**
O perfil padrão do WildFly não inclui o subsystem de mensageria (Artemis/ActiveMQ). O `full` vem com tudo configurado.

**Por que `@JsonbTransient` e não `@JsonIgnore`?**
O `@JsonIgnore` é do Jackson (biblioteca externa). O `@JsonbTransient` é do Jakarta JSON-B, que já vem no WildFly. Sem dependência extra no `pom.xml`!

**Por que `REQUIRES_NEW` no método `@Asynchronous`?**
O método assíncrono roda em thread diferente — a transação original já não existe nessa thread. Precisamos criar uma transação nova e independente.

---

## 📝 Licença

MIT — use à vontade para estudar, modificar e compartilhar!

---

<div align="center">

**Feito com ☕ muito café e vontade de aprender Jakarta EE**

*Se esse projeto te ajudou, deixa uma ⭐ no repositório!*

</div>