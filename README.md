# Sistema de Gestão de Academia — Força Total

Sistema completo de gestão de academia com **back-end em Java + Spring Boot** e **front-end web** (HTML, CSS e JavaScript) integrado à API REST. Também há um app Android opcional na pasta `android/`.

## Estrutura

```
GymApp/
├── backend/                          → projeto Spring Boot (abrir no IntelliJ IDEA)
│   ├── src/main/java/com/academia/
│   │   ├── model/                    → entidades JPA (Aluno, Plano, Mensalidade, Professor, FichaTreino, AvaliacaoFisica)
│   │   ├── repository/               → Spring Data JPA
│   │   ├── service/                  → regras de negócio
│   │   ├── controller/               → API REST (/api/...)
│   │   ├── dto/                      → objetos de entrada e saída da API
│   │   ├── exception/                → tratamento de erros em JSON
│   │   └── config/                   → CORS e dados de exemplo
│   └── src/main/resources/
│       ├── application.properties    → porta 8080, banco H2 em arquivo
│       └── static/                   → FRONT-END WEB (servido pelo próprio Spring Boot)
│           ├── index.html
│           ├── css/style.css
│           └── js/app.js
└── android/                          → app Android (opcional), consome a mesma API
```

## Como executar

1. Abra a pasta **`backend`** no **IntelliJ IDEA** (*File → Open*, selecione a pasta; ele reconhece o `pom.xml`).
2. Use o **JDK 21** (*File → Project Structure → SDK*).
3. Execute a classe **`AcademiaApplication`** (botão ▶ verde).
4. Acesse **http://localhost:8080**: abre a página que apresenta o produto. Para usar o sistema, clique em **Entrar** (ou acesse direto **http://localhost:8080/app**) e entre com um dos logins abaixo.

O banco H2 fica salvo em `backend/data/`. Na primeira execução (banco vazio), o sistema cria a academia padrão com dados de exemplo de todos os módulos.

## Página de apresentação do produto

A **entrada padrão do site** (**http://localhost:8080/**) é a página pública de venda do produto: os 6 módulos, benefícios, simulador, planos de assinatura e perguntas frequentes, com botões para entrar ou cadastrar uma academia.

- **Telas do sistema:** no topo, uma pilha 3D com prints reais das telas internas (`static/img/telas/`), em `static/js/card-swap.js`, adaptado do componente CardSwap nas cores do site. Os cartões trocam sozinhos a cada 3,8 s; dá para arrastar o da frente, clicar num do fundo ou usar as setas do teclado. A troca pausa com o mouse em cima, com o foco do teclado ou com a aba em segundo plano.
- **Atualizar os prints:** se alguma tela mudar, rode, com o servidor ligado e a partir da pasta do projeto:

  ```powershell
  node ferramentas/capturar-telas.mjs
  ```

  O script abre o Edge (ou o Chrome) sem janela e entra com o administrador de exemplo. Ele fotografa Painel, Alunos, Planos, Mensalidades, Treinos e a evolução da Ana Souza em 1440×900, além do painel em alta resolução e no layout de celular, salva os `.webp` em `static/img/telas/` (e em `target/classes`, para o servidor ligado já mostrar) e encerra a sessão no fim. Precisa do Node 22 ou mais novo. Para usar outro usuário, defina `EMAIL` e `SENHA` antes de rodar.
- **Painel em tela cheia:** entre os números e os módulos, um print do painel começa num quadro de cantos arredondados e abre até ocupar a tela inteira conforme a página rola. O título some e, no fim, aparece o texto com o botão para os módulos (`static/js/scroll-expand.js`, adaptado do componente ScrollExpand). Usa as mesmas curvas e valores do original. No site, o palco começa abaixo do cabeçalho fixo e o véu escuro é mais forte, para o texto ficar legível sobre o print. No celular é usado um print do painel no layout de celular (`painel-celular.webp`).
- **Chamada final:** no título "Pronto para começar?", as letras sobem esticadas de baixo da linha e assentam conforme a página rola (`static/js/scroll-float.js`, o efeito *float* do componente ScrollFloat). Usa os mesmos valores do original, sem GSAP. Quem prefere menos movimento vê o título parado.
- **Módulos:** abas clicáveis (também pelas setas do teclado) com uma maquete de cada tela. Elas trocam sozinhas enquanto estão na tela e param quando a pessoa clica ou passa o mouse.
- **Simulador:** controles deslizantes de alunos, mensalidade média e percentual de atraso. A página calcula o faturamento do mês e o valor em aberto, e indica um plano pelo número de alunos, que fica destacado nos cartões.
- **Planos:** Essencial (R$ 99), Profissional (R$ 189) e Rede (R$ 349), com chave mensal/anual (anual = 10 mensalidades). O plano indicado pelo simulador ganha a etiqueta "Indicado para você". **Os planos e preços existem só na página:** o sistema não cobra assinatura nem limita alunos ou usuários por plano.

- **Animações:** os elementos aparecem conforme a rolagem, no modelo da biblioteca AOS (*Animate On Scroll*). Elas usam os mesmos atributos `data-aos`, `data-aos-delay` e `data-aos-duration`, mas com um script próprio e sem dependências (`static/js/aos.js`). Assim a página funciona mesmo sem internet.
- **Acessibilidade:** quem configurou o sistema para reduzir movimento vê a página sem animações. Sem JavaScript, todo o conteúdo continua visível.
- **Arquivos:** `static/apresentacao.html`, `static/css/apresentacao.css`, `static/js/apresentacao.js` (abas, simulador e cobrança), `static/js/card-swap.js`, `static/js/scroll-float.js`, `static/js/scroll-expand.js`, `static/js/aos.js` e as imagens em `static/img/telas/`. As rotas ficam em `config/PaginasConfig.java`: `/` mostra esta página, `/app` abre o sistema e o endereço antigo `/apresentacao` redireciona para `/`.

## Login e várias academias

O sistema atende **várias academias ao mesmo tempo**. Cada academia tem os próprios alunos, planos, mensalidades, professores, treinos, avaliações e usuários, e **nunca enxerga os dados de outra**.

**Logins de exemplo** da academia padrão (*Academia Força Total*), com a senha `forca2026`:

| Perfil | E-mail | Pode fazer |
|---|---|---|
| Administrador | `admin@forcatotal.com` | Tudo, inclusive planos e usuários |
| Funcionário | `recepcao@forcatotal.com` | Alunos, mensalidades, professores, treinos e avaliações; consulta planos |

- **Nova academia:** na tela de login, clique em *Cadastrar academia*. O sistema cria a academia e o primeiro administrador, e já entra mostrando um guia de primeiros passos.
- **Equipe:** o administrador cria os logins dos funcionários no menu do usuário (canto superior direito) → *Usuários da academia*. Ali também desativa um acesso ou troca a senha de alguém.
- **Segurança:**
  - as senhas são guardadas só como hash BCrypt;
  - cada login gera um token aleatório que vale por 12 horas, e o banco guarda apenas o hash SHA-256 desse token;
  - depois de 5 senhas erradas em 15 minutos, o login daquele e-mail fica bloqueado;
  - toda consulta é filtrada pela academia de quem está logado. Um id de outra academia responde 404, como se não existisse.
- **Verificação em duas etapas (opcional, por usuário):** no menu do usuário → *Verificação em duas etapas*. O usuário lê um QR Code com um aplicativo autenticador (Google Authenticator, Microsoft Authenticator ou Authy) e, a partir daí, o login pede também o código de 6 dígitos que muda a cada 30 segundos. Detalhes:
  - na ativação aparecem 8 **códigos de recuperação**, de uso único, para quem perder o celular. Eles não são mostrados de novo;
  - um código já usado não vale outra vez, e códigos errados contam para o bloqueio de tentativas;
  - para desligar, é preciso a senha e um código;
  - se alguém perder o celular e os códigos, o administrador usa *Redefinir 2 etapas* na tela de usuários;
  - os códigos seguem o padrão TOTP (RFC 6238), implementado em `security/Totp.java`. O QR Code é gerado com a biblioteca ZXing.
- **Bancos antigos:** ao subir esta versão, os dados que já existiam passam para a academia padrão. Um backup do banco anterior ficou em `backend/data/backup-antes-do-login/`.
- **App Android:** como a API agora exige login, o app da pasta `android` deixa de funcionar até ganhar uma tela de login.

> Console do banco: http://localhost:8080/h2-console — JDBC URL `jdbc:h2:file:./data/academia`, usuário `sa`, sem senha.

> Se você editar arquivos do front-end com o servidor rodando, use *Build → Rebuild Project* (ou reinicie) e recarregue a página com **Ctrl+F5**.

## Tutorial para novos usuários

No primeiro acesso de cada usuário, um tour guiado passa pelas telas reais do sistema. Cada parte fica destacada (o resto da tela escurece) com um balão explicando o que faz: menu de módulos, números do painel, atalhos, busca de alunos, ficha do aluno, planos, cobrança das mensalidades, fichas de treino, gráficos de evolução e menu da conta.

- **Quando aparece:** sozinho, uma vez por usuário em cada navegador. "Agora não" ou "Pular tutorial" também contam como visto.
- **Rever depois:** menu da conta (canto superior direito), opção **Tutorial do sistema**.
- **Adapta-se:** ao perfil (para a recepção, explica que planos são do administrador), à academia recém-cadastrada (passo extra mostrando a lista "Primeiros passos") e ao celular (aponta o botão de menu e põe o balão na parte de baixo da tela).
- **Teclado:** → ou Enter avança, ← volta, Esc fecha. O foco fica dentro do balão enquanto o tour está aberto.
- **Arquivos:** `static/js/tutorial.js` (passos e lógica), estilos no fim de `static/css/style.css`. Os passos ficam na função `montarPassos`: para incluir um, informe a rota (`alunos`, `planos`…), o seletor do elemento a destacar, o título e o texto.
- **Para ver de novo como novo usuário:** apague a chave `forcatotal.tutorial.<academia>.<usuário>` do armazenamento do navegador (DevTools → Application → Local Storage) e recarregue.

## Funcionalidades

| Módulo | O que faz |
|---|---|
| **Início (dashboard)** | Indicadores: alunos ativos, valor recebido no mês, mensalidades atrasadas e a vencer, total em aberto, e atalho para receber pagamentos. |
| **Alunos** | Matrícula em seções (dados pessoais, contato, matrícula), com plano escolhido em cartões e resumo da 1ª mensalidade. **Busca instantânea** por nome, CPF, e-mail ou telefone, filtro por situação, edição e **exclusão**. Ficha do aluno com mensalidades, treinos e avaliações. |
| **Planos** | Cadastro, edição, ativar/desativar e exclusão. Impede excluir plano com alunos vinculados. |
| **Mensalidades** | Lançamento manual, **geração automática** de N meses pelo valor do plano, **registro de pagamento** (forma e data), **estorno**, filtros por aluno e situação (paga, pendente, atrasada), totais recebido/em aberto. |
| **Professores** | Cadastro, edição, consulta e exclusão (especialidade, CREF, contatos). |
| **Treinos** | Criação de **fichas de treino** com lista de exercícios (séries, repetições, carga), professor responsável, objetivo e período; editar, duplicar e excluir. |
| **Avaliações físicas** | Registro de peso, altura, % de gordura e medidas; **IMC e classificação calculados**; variação em relação à avaliação anterior e **gráficos de evolução** por aluno. |

### Recursos de usabilidade

- **Validação em todos os cadastros**, com a mensagem embaixo de cada campo: CPF (dígitos verificadores), e-mail, telefone, datas no futuro, limites de peso, altura e medidas, formato do CREF e da referência (MM/AAAA). Erros do servidor, como CPF duplicado, também aparecem no campo certo.
  - CPF válido para testes: `529.982.247-25`. Os CPFs dos dados de exemplo são fictícios e não são revalidados ao editar.
- **Proteção contra perda de dados**: fechar um formulário preenchido (Cancelar, X, Esc ou clique fora) pergunta antes de descartar.
- **Atalhos**: `Ctrl+Enter` salva o formulário; `/` vai para a busca; `Enter` no campo de carga cria um novo exercício.
- **Tabelas**: clique no título de uma coluna para ordenar. No celular, cada linha vira um cartão.
- **Desempenho**: compressão gzip (o JavaScript cai de 82 KB para 23 KB), cache com validação (HTTP 304) e cache de dados no navegador, que é limpo a cada gravação.

## API REST (resumo)

| Recurso | Endpoints |
|---|---|
| Alunos | `GET/POST /api/alunos` (`?busca=`), `GET/PUT/DELETE /api/alunos/{id}` |
| Planos | `GET/POST /api/planos`, `GET/PUT/DELETE /api/planos/{id}` |
| Mensalidades | `GET/POST /api/mensalidades` (`?alunoId=&status=`), `GET/PUT/DELETE /api/mensalidades/{id}`, `POST /api/mensalidades/{id}/pagar`, `POST /api/mensalidades/{id}/estornar`, `POST /api/mensalidades/gerar` |
| Professores | `GET/POST /api/professores` (`?busca=`), `GET/PUT/DELETE /api/professores/{id}` |
| Treinos | `GET/POST /api/treinos` (`?alunoId=`), `GET/PUT/DELETE /api/treinos/{id}` |
| Avaliações | `GET/POST /api/avaliacoes` (`?alunoId=`), `GET/PUT/DELETE /api/avaliacoes/{id}` |
| Dashboard | `GET /api/dashboard` |
| Login (público) | `POST /api/auth/login`, `POST /api/auth/cadastro` (nova academia) |
| Sessão | `GET /api/auth/eu`, `POST /api/auth/logout`, `POST /api/auth/senha` |
| Duas etapas | `POST /api/auth/login/2fa` (público: desafio + código), `GET /api/auth/2fa`, `POST /api/auth/2fa/iniciar`, `POST /api/auth/2fa/ativar`, `POST /api/auth/2fa/desativar`, `POST /api/usuarios/{id}/2fa/redefinir` (admin) |
| Usuários (só admin) | `GET/POST /api/usuarios`, `PUT/DELETE /api/usuarios/{id}` |

Fora o login e o cadastro, toda chamada precisa do cabeçalho `Authorization: Bearer <token>`, com o token devolvido pelo login. Sem ele, a API responde 401; sem permissão para a ação, responde 403.

Erros são devolvidos como `{"erro": "mensagem"}` e aparecem para o usuário no site.

## Tecnologias

Java 21 · Spring Boot 3.5 (Web, Data JPA, Validation, Security) · H2 Database · Maven · HTML5, CSS3 e JavaScript (Fetch API), sem bibliotecas externas · IntelliJ IDEA


# INICIALIZAÇÃO:

| & "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd" spring-boot:run
