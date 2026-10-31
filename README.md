# GM Elcons Catalog

Sistema corporativo de cadastro técnico e preparação de anúncios para comércio eletrônico.

> **Status:** versão inicial (0.1.0). O código foi escrito por assistente de IA a partir de requisitos do projeto e **ainda precisa ser compilado, testado e revisado pelo autor** (ver [Estado atual e pendências](#estado-atual-e-pendências)). Nenhum resultado de teste ou ganho de produtividade está registrado aqui: esses dados devem ser medidos e documentados depois.

## 1. Descrição e objetivo

A GM Elcons comercializa produtos elétricos pelo Mercado Livre. Hoje, cadastrar um produto exige um processo manual: pesquisar dados técnicos, consultar anúncios de concorrentes e catálogos de fabricantes/fornecedores, reunir as características, gerar título/descrição/palavras-chave no ChatGPT e preencher o anúncio à mão. A empresa estima cerca de 30 minutos por produto (estimativa informada pela empresa, **não medida por este sistema**).

O **GM Elcons Catalog** centraliza esse trabalho:

- guarda os dados técnicos **verificados** de cada produto, organizados por categoria;
- registra a **fonte** de cada informação, diferenciando fabricante, fornecedor e anúncio de concorrente;
- valida os atributos obrigatórios e exige **revisão explícita** antes de liberar o produto;
- gera um **briefing** organizado para ser copiado no ChatGPT (sem integração com IA nesta versão);
- guarda o resultado final (título, descrição, palavras-chave, data e responsável);
- registra **auditoria** das operações críticas.

**Fora do escopo:** controle de estoque, pedidos e financeiro. Esses itens continuam no ERP Olist; o sistema apenas complementa o processo atual.

O projeto é parte de um Projeto Integrado de Análise e Desenvolvimento de Sistemas e se relaciona com o **ODS 9 (Indústria, Inovação e Infraestrutura)** da Agenda 2030.

## 2. Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem / build | Java 21, Maven |
| Backend | Spring Boot 3.3, Spring Web, Spring Security, Spring Data JPA, Bean Validation |
| Banco de dados | PostgreSQL 16 (produção/Docker); H2 em memória apenas nos testes |
| Frontend | HTML, CSS e JavaScript puro (sem dependências externas), servido pelo próprio Spring Boot |
| Documentação da API | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, Spring Security Test, MockMvc |
| Execução | Docker e Docker Compose |

## 3. Arquitetura

Aplicação monolítica em camadas, em um único projeto Maven:

```
src/main/java/br/com/gmelcons/catalog
├── web/         Controllers REST e tratamento global de erros
├── service/     Regras de negócio, validação, fluxo de status, auditoria
├── repository/  Spring Data JPA
├── entity/      Entidades JPA
├── dto/         Objetos de entrada/saída da API (records)
├── domain/      Enums (papéis, tipos, status, operações de auditoria)
├── exception/   Exceções de negócio
└── config/      Segurança, OpenAPI, carga de dados iniciais
src/main/resources/static   Interface web (login.html, index.html, css/, js/)
```

Decisões principais:

- **Sessão com cookie** (HttpOnly, SameSite=Lax) em vez de JWT: mais simples para uma aplicação web única e de equipe pequena.
- **CSRF ativo**: o cookie `XSRF-TOKEN` é lido pelo JavaScript e devolvido no cabeçalho `X-XSRF-TOKEN`.
- **Autorização em duas camadas**: regras por URL no `SecurityConfig` e `@PreAuthorize` nos serviços sensíveis. A interface só esconde botões; quem decide é o backend.
- **Atributos flexíveis por categoria**: tabelas `attribute_definitions` (o que a categoria exige) e `product_attribute_values` (o valor de cada produto), sem alterar o esquema para novas categorias.
- **Auditoria na mesma transação** da operação: se a operação falhar, o registro correspondente não é gravado.
- **Esquema do banco** gerado pelo Hibernate (`ddl-auto=update`). Para produção, o ideal é adotar migrações versionadas (Flyway/Liquibase) — ver melhorias.

## 4. Funcionalidades

- **Login e usuários:** tela de login; ADMIN cria e edita usuários (papéis `ADMIN` e `OPERADOR`); senhas com BCrypt.
- **Produtos:** cadastro, edição, consulta, exclusão (somente ADMIN, com confirmação digitando o SKU) e pesquisa por nome, código, marca, categoria e status.
- **Categorias e atributos:** nome, tipo (texto, número, sim/não), obrigatoriedade e unidade. Somente ADMIN altera; OPERADOR consulta.
- **Fontes de informação:** tipo (fabricante, fornecedor, anúncio de concorrente, outra), nome, URL, data da consulta e observações. Fonte de fabricante é marcada como oficial; atributos apoiados só em anúncio de concorrente geram aviso ao revisor.
- **Duplicação:** copia dados, atributos e fontes, mas o novo produto nasce `INCOMPLETO`, registra o produto de origem e **todos os atributos copiados ficam marcados "copiado: revisar"** até alguém confirmá-los.
- **Status:** `INCOMPLETO` → `AGUARDANDO_REVISAO` → `PRONTO_PARA_ANUNCIO`. Para avançar é preciso: ao menos uma fonte, atributos obrigatórios preenchidos e com fonte, nenhum atributo pendente de revisão e, no final, **confirmação explícita** do revisor. Editar um produto pronto o devolve para revisão; esvaziar um atributo obrigatório o devolve a incompleto.
- **Gerador de briefing:** disponível só para produtos prontos. Reúne nome, marca, categoria, SKU, atributos revisados (com a fonte de cada um), fontes consultadas, observações e orientações para título, descrição e palavras-chave. O texto é copiado e usado no ChatGPT manualmente.
- **Anúncios preparados:** guarda título, descrição, palavras-chave, data da preparação e responsável pela revisão.
- **Dashboard:** totais por status, anúncios recentes e atividades recentes, todos calculados a partir do banco.
- **Auditoria (somente ADMIN):** login/falha de login/logout, produtos, atributos, status, fontes, anúncios, categorias, usuários e papéis, com usuário, data/hora, operação, entidade e valores anteriores/novos.

## 5. Estrutura do banco de dados

| Tabela | Conteúdo |
|---|---|
| `app_users` | login, nome, hash BCrypt, papel, ativo |
| `categories` | nome, descrição |
| `attribute_definitions` | atributos de cada categoria: nome, tipo, obrigatório, unidade, ordem |
| `products` | nome, SKU (único), marca, categoria, status, responsável, datas, produto de origem (duplicação), revisor |
| `product_attribute_values` | valor de cada atributo por produto, fonte e marca "precisa de revisão" |
| `information_sources` | fontes do produto: tipo, nome, URL, data da consulta, observações |
| `ad_listings` | anúncio preparado (1 por produto): título, descrição, palavras-chave, datas, revisor |
| `audit_logs` | data/hora, usuário, operação, entidade, id, valor anterior e novo (JSON) |

Relações principais: `categories 1—N attribute_definitions`, `categories 1—N products`, `products 1—N product_attribute_values`, `products 1—N information_sources`, `products 1—1 ad_listings`, `app_users 1—N products` (responsável).

## 6. Como executar

### 6.1 Com Docker Compose (recomendado)

Pré-requisito: Docker Desktop.

```powershell
copy .env.example .env      # Windows (no Linux/macOS: cp .env.example .env)
# edite o .env e troque DB_PASSWORD
docker compose up --build
```

Acesse `http://localhost:8080`. O banco PostgreSQL **não é publicado** na máquina; só a aplicação o acessa. Dados ficam no volume `pgdata`.

### 6.2 Localmente (sem Docker para a aplicação)

Pré-requisitos: JDK 21, Maven 3.9+ e um PostgreSQL acessível. Para subir só o banco com Docker:

```powershell
docker run --name gmelcons-db -e POSTGRES_DB=gmelcons -e POSTGRES_USER=gmelcons -e POSTGRES_PASSWORD=gmelcons -p 5432:5432 -d postgres:16-alpine
mvn spring-boot:run
```

(Essa senha simples é só para desenvolvimento local.)

### 6.3 Variáveis de ambiente

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/gmelcons` | conexão com o banco |
| `DB_USER` / `DB_PASSWORD` | `gmelcons` / `gmelcons` | credenciais do banco (**troque em qualquer ambiente real**) |
| `APP_SEED_DEMO` | `true` | `true` carrega dados fictícios e usuários de demonstração |
| `APP_ADMIN_USERNAME` | `admin` | login do primeiro administrador |
| `APP_ADMIN_PASSWORD` | vazio | senha do primeiro administrador. **Obrigatória** quando `APP_SEED_DEMO=false` |
| `APP_DEMO_OPERATOR_PASSWORD` | vazio | senha do operador de demonstração (se vazia, usa a senha de demonstração abaixo) |
| `SESSION_COOKIE_SECURE` | `false` | use `true` quando houver HTTPS |
| `JPA_DDL_AUTO` | `update` | estratégia de esquema do Hibernate |

Credenciais nunca devem ser versionadas: o `.env` está no `.gitignore`; use o `.env.example` como modelo.

### 6.4 Usuários de demonstração

Criados **apenas** quando `APP_SEED_DEMO=true` e o banco está vazio. São credenciais públicas, de demonstração, e **não devem existir em ambiente real**.

| Usuário | Senha | Papel |
|---|---|---|
| `admin` | `Admin@Demo2026` | ADMIN |
| `operador` | `Operador@Demo2026` | OPERADOR |

Para uso real: defina `APP_SEED_DEMO=false` e `APP_ADMIN_PASSWORD` com uma senha forte (ou troque as senhas pela tela de Usuários).

### 6.5 Dados de demonstração

Categorias: Tomadas, Kits de condulete com tomadas, Interruptores e Sensores de alarme Margirius, com atributos de exemplo (nomes dos campos). Produtos de demonstração em quatro situações (incompleto, aguardando revisão, duplicado pendente de revisão e com fonte apenas de concorrente). **Nenhuma especificação técnica real foi cadastrada**: os valores vêm marcados com `[EXEMPLO]` e os nomes de produto/marca/fonte com `[EXEMPLO]` ou `(fictício)`.

### 6.6 API (Swagger/OpenAPI)

Com a aplicação rodando e **logado na interface**, abra `http://localhost:8080/swagger-ui.html` (JSON em `/v3/api-docs`). A API usa a sessão por cookie; requisições que alteram dados precisam do cabeçalho `X-XSRF-TOKEN` (valor do cookie `XSRF-TOKEN`).

### 6.7 Testes

```powershell
mvn test
```

Os testes de integração usam H2 em memória (perfil `test`), sem PostgreSQL. Cobrem: autenticação (login, falha, CSRF, usuário inativo, BCrypt), autorização por papel, cadastro e edição de produtos, atributos obrigatórios, duplicação com revisão, mudanças de status, briefing/anúncio, dashboard e registros de auditoria. `ProductValidationServiceTest` é um teste unitário com Mockito.

## 7. Segurança e LGPD

Medidas implementadas:

- senhas com **BCrypt**; a senha nunca é devolvida pela API nem gravada na auditoria;
- autenticação por sessão, cookie `HttpOnly` e `SameSite=Lax` (`Secure` configurável), expiração por inatividade de 30 min;
- **CSRF** habilitado;
- **RBAC** no backend (URL + `@PreAuthorize`); menor privilégio: OPERADOR não gerencia usuários, categorias, auditoria nem exclui produtos;
- validação de entrada (Bean Validation) e validação por tipo de atributo; consultas via JPA (sem concatenação de SQL);
- interface escapa todo dado exibido (proteção contra XSS) e o servidor envia cabeçalhos de segurança (CSP restritiva, `X-Frame-Options`, `Referrer-Policy`);
- mensagens de erro genéricas ao cliente, sem stack trace;
- credenciais fora do código, por variáveis de ambiente; banco sem porta publicada no Compose; contêiner da aplicação roda sem root;
- **auditoria** de operações críticas, consultável apenas por ADMIN.

LGPD (Lei 13.709/2018) como referência:

- **minimização:** o sistema guarda apenas dados do catálogo e dos usuários internos (login, nome, papel). **Não armazena dados de clientes do Mercado Livre** e não há campos para isso;
- **finalidade e necessidade:** dados pessoais limitados a identificar quem opera e quem fez cada alteração (rastreabilidade);
- **segurança:** controles acima; **responsabilização:** auditoria por usuário;
- direitos do titular, base legal, prazo de retenção dos registros de auditoria e eventual encarregado (DPO) são **decisões da empresa** e não foram definidos aqui.

### Limitações da auditoria

Os registros são protegidos por controle de acesso no sistema, mas **não são invioláveis nem à prova de adulteração**: quem tiver acesso direto ao banco (por exemplo, o administrador do PostgreSQL) pode alterá-los ou apagá-los, e a aplicação não usa assinatura nem encadeamento de registros. Ações fora da aplicação não são registradas. Para maior garantia seria preciso enviar os registros a um repositório externo com acesso restrito.

### Backup e recuperação (orientações)

- Faça **backup periódico do PostgreSQL** (ex.: `pg_dump`) e guarde cópias fora da máquina do servidor, com acesso restrito (os dumps contêm hashes de senha e a auditoria).
- Exemplo (Docker Compose): `docker compose exec db pg_dump -U gmelcons gmelcons > backup.sql`.
- **Teste a restauração** em ambiente separado; um backup nunca testado não é garantia.
- Defina e documente a frequência e o tempo de retenção (decisão da empresa).
- Proteja o arquivo `.env` e troque as senhas se houver suspeita de vazamento.

## 8. Diagnóstico de riscos e melhorias propostas

Riscos identificados (qualitativo; não foi feita análise formal):

| Risco | Situação atual | Melhoria sugerida |
|---|---|---|
| Dado técnico incorreto vindo de concorrente | aviso ao revisor + exigência de fonte + revisão explícita | exigir fonte oficial para atributos obrigatórios |
| Senhas fracas ou compartilhadas | mínimo de 8 caracteres | política de complexidade, troca de senha pelo próprio usuário, bloqueio após tentativas |
| Força bruta no login | falhas são auditadas, sem bloqueio | limite de tentativas / atraso progressivo |
| Adulteração da auditoria | acesso restrito; sem proteção contra acesso direto ao banco | log externo imutável, assinatura ou encadeamento |
| Perda de dados | depende de backup manual | rotina automatizada de backup e teste de restauração |
| Tráfego sem criptografia | HTTP por padrão | publicar atrás de proxy HTTPS e ativar `SESSION_COOKIE_SECURE=true` |
| Evolução do esquema | `ddl-auto=update` | migrações versionadas (Flyway/Liquibase) |
| Erro de revisão humana | revisão explícita, sem segundo revisor | revisão por pessoa diferente de quem cadastrou (opcional) |

Ideias de evolução: integração com IA para gerar o anúncio, importação em lote, histórico de versões de anúncio, exportação do briefing, recuperação de senha, paginação/ordenação avançadas.

## 9. Estado atual e pendências

- O código foi gerado sem acesso a compilador/Maven neste ambiente de desenvolvimento: **é necessário rodar `mvn test` e corrigir eventuais erros de compilação ou de teste**.
- A interface **ainda não foi aberta em navegador**: validar visualmente e funcionalmente (a sintaxe do JavaScript foi verificada, o comportamento não).
- Não foram executados testes de segurança (varredura de vulnerabilidades, teste de invasão).
- Resultados de testes, medições de tempo e ganhos de produtividade devem ser registrados depois pelo autor, com dados reais.
- Docker/Compose não foi executado.
