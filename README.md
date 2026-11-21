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
- **Meu perfil** (menu do usuário → *Meu perfil*, rota `#/perfil`): página com tudo da própria conta. Mostra os dados (nome, e-mail, perfil, academia/unidade, rede e desde quando), permite trocar o próprio nome e a senha, traz a verificação em duas etapas (ativar, desativar, códigos restantes e dispositivos lembrados) e as **sessões abertas**, com *Encerrar as outras sessões* (este navegador continua conectado). Também tem o botão para rever o tutorial. O e-mail, que é o login, só o administrador troca.
- **Excluir a conta** (fim de *Meu perfil*): o aviso diz exatamente o que some antes de qualquer coisa.
  - **Funcionário, ou administrador com outro administrador ativo, ou administrador de uma unidade de rede:** apaga só a própria conta. Os dados da academia ficam, e numa rede o dono segue cuidando da unidade. A confirmação é digitar `EXCLUIR`.
  - **Último administrador de uma academia avulsa:** apaga a **academia inteira**: alunos, mensalidades, fichas de treino, avaliações, professores, planos e os outros usuários. A confirmação é digitar o **nome da academia**.
  - **Dono da rede:** apaga a **rede com todas as unidades** e tudo o que há nelas. A confirmação é digitar o **nome da rede**.
  - Além do texto (maiúsculas, acentos e espaços extras não importam), pede a senha e, com a verificação em duas etapas ativa, o código do aplicativo ou de recuperação. Por fim, é preciso **segurar o botão por 2,5 segundos** (`static/js/hold-button.js` e `static/css/hold-button.css`, porte em JS puro do HoldButton do React Bits). Soltar antes, arrastar para fora ou apertar Esc cancela. No teclado, segure Enter ou Espaço.
  - Todas as sessões e dispositivos lembrados das contas apagadas caem na hora. Não há como desfazer.
- **Verificação em duas etapas (opcional, por usuário):** em *Meu perfil* → *Verificação em duas etapas*. O usuário lê um QR Code com um aplicativo autenticador (Google Authenticator, Microsoft Authenticator ou Authy) e, a partir daí, o login pede também o código de 6 dígitos que muda a cada 30 segundos. Detalhes:
  - na ativação aparecem 8 **códigos de recuperação**, de uso único, para quem perder o celular. Eles não são mostrados de novo;
  - um código já usado não vale outra vez, e códigos errados contam para o bloqueio de tentativas;
  - para desligar, é preciso a senha e um código;
  - se alguém perder o celular e os códigos, o administrador usa *Redefinir 2 etapas* na tela de usuários;
  - **Lembrar deste dispositivo por 30 dias:** opção na tela do código (desmarcada por padrão; não use em computadores compartilhados). Naquele navegador, o login pede só a senha até a data de validade, que não se renova sozinha. O navegador guarda um token aleatório e o banco guarda só o hash dele (tabela `dispositivos_confiaveis`). O token só vale para a mesma pessoa e nunca dispensa a senha. Na tela *Verificação em duas etapas* aparece a lista de dispositivos lembrados (navegador, sistema, desde quando, válido até), com *Esquecer* em cada um ou em todos. Trocar a senha, desligar as duas etapas, a redefinição pelo administrador e a exclusão do usuário esquecem todos os dispositivos dele. Cada pessoa tem no máximo 10 dispositivos lembrados. Código em `service/DispositivosConfiaveis.java`;
  - os códigos seguem o padrão TOTP (RFC 6238), implementado em `security/Totp.java`. O QR Code é gerado com a biblioteca ZXing.
- **Bancos antigos:** ao subir esta versão, os dados que já existiam passam para a academia padrão. Um backup do banco anterior ficou em `backend/data/backup-antes-do-login/`.
- **App Android:** como a API agora exige login, o app da pasta `android` deixa de funcionar até ganhar uma tela de login.

### Plano Rede: dono com várias unidades

Para quem tem mais de uma academia. Cada unidade continua isolada, com os próprios dados e o seu administrador, mas existe um cargo acima delas: o **dono da rede**.

| Perfil | Vê | Pode fazer |
|---|---|---|
| Dono da rede | Todas as unidades da rede, cada uma separada | Criar unidades (até 5), cadastrar o administrador de cada uma, abrir qualquer unidade e gerenciá-la com os poderes de administrador, excluir unidades (menos a principal) |
| Administrador | Só a própria unidade | Tudo dentro da unidade (alunos, planos, usuários…), sem ver as outras nem o dono |
| Funcionário | Só a própria unidade | O dia a dia da unidade |

- **Como criar uma rede:**
  - *Cadastro novo:* na tela de cadastro, escolha **Rede de academias**, informe o nome da rede e o da primeira unidade. Quem se cadastra vira o dono.
  - *Academia que já existe:* o administrador abre o menu da conta → **Transformar em rede**. A academia vira a primeira unidade, com todos os dados, e ele vira o dono.
- **Painel da rede** (menu **Rede**, só para o dono): os totais da rede e um cartão por unidade, com alunos ativos, atrasadas, professores, usuários, recebido no mês (com barra para comparar as unidades), valor em aberto e os administradores. Dali o dono cria unidades (sempre com o administrador), cadastra mais administradores, renomeia e **abre** uma unidade.
- **Excluir uma unidade** (botão **Excluir** no cartão da unidade): usa o mesmo sistema da exclusão da conta, num modal. O aviso diz quantos alunos e usuários somem. A confirmação pede o **nome da unidade** digitado, a senha do dono e, se a verificação em duas etapas estiver ativa, o código. Por fim, é preciso segurar o botão por 2,5 segundos, com o mesmo som.
  - Apaga tudo o que há na unidade: alunos, mensalidades, treinos, avaliações, professores, planos e os usuários, que perdem o acesso na hora. As outras unidades não mudam.
  - A **unidade principal** do dono (a do cadastro dele) não tem o botão e o servidor recusa. Para apagar a rede inteira, o dono exclui a própria conta em *Meu perfil*.
  - Se o dono estava com a unidade aberta, o sistema volta para a unidade principal.
  - Só o dono, e só nas unidades da própria rede (administradores recebem 403, e outra rede recebe 404). A remoção dos dados fica em `service/RemocaoDeAcademias.java`, a mesma usada pela exclusão da conta.
- **Abrir uma unidade** troca o sistema inteiro para ela: todas as telas passam a mostrar os dados dessa unidade. Uma faixa amarela no topo mostra em qual unidade o dono está, com o link *Trocar unidade*. Cada login do dono começa na unidade do cadastro dele.
- **Isolamento:**
  - o servidor confere a cada requisição se a unidade aberta é mesmo da rede do dono;
  - administradores e funcionários nunca acessam o painel da rede (403), não veem o dono na lista de usuários e não podem criar alguém com perfil de dono;
  - o dono de uma rede não enxerga nem altera unidades de outra rede (404).
- **Onde está no código:** `model/Rede.java`, perfil `DONO` em `model/Perfil.java`, a unidade aberta fica na sessão (`Sessao.academiaAtiva`) e é aplicada em `security/TokenFilter.java`, regras em `service/RedeService.java`, rotas em `controller/RedeController.java`.
- **Bancos antigos:** ao subir esta versão, a coluna de perfis passa a aceitar o dono (`config/DadosIniciais.java`). Backup do banco anterior: `backend/data/backup-antes-da-rede/`.

### Planos, checkout e vagas de unidade

A assinatura do sistema usa os planos da página de apresentação. O pagamento é de **demonstração**: nenhum valor é cobrado.

| Plano | Mensal | Anual | Limites |
|---|---|---|---|
| Essencial | R$ 99 | R$ 990 | 150 alunos ativos e 2 usuários |
| Profissional | R$ 189 | R$ 1.890 | 600 alunos ativos, usuários ilimitados |
| Rede | R$ 349 | R$ 3.490 | ilimitados, 5 unidades incluídas |
| Vaga extra de unidade (só Rede) | R$ 59 cada | R$ 590 cada | até 20 unidades no total |

- **Teste grátis:** contas novas, e as que já existiam, começam com **14 dias** com tudo liberado. A situação do teste aparece só em **Plano e cobrança** (e na seção de planos da apresentação). Nos 3 últimos dias, o sino de notificações avisa o administrador.
- **Onde assinar:**
  - Na apresentação, os botões dos planos levam a escolha (plano, mensal ou anual e, no Rede, quantas unidades) para o cadastro e, depois dele, direto ao checkout.
  - No sistema: menu da conta → **Plano e cobrança** (só administradores). Essa tela mostra a situação, o uso comparado aos limites, as vagas de unidade, a comparação dos planos e o histórico de pagamentos.
- **Comparar unidades:**
  - O cartão do Rede na apresentação tem um seletor de 5 a 20 unidades, com o preço e quanto sai cada unidade.
  - No sistema, uma tabela compara 5, 6, 8, 10, 15 e 20 unidades: total por mês e preço por unidade.
- **Checkout** (`#/checkout`): o pedido (plano, cobrança e unidades) fica à esquerda, e o pagamento por **cartão, Pix ou boleto** fica à direita. Os valores sempre vêm do servidor.
  - **Cartão:** validado como num gateway real (algoritmo de Luhn, validade e CVV de 3 ou 4 dígitos). Só a bandeira e os 4 últimos dígitos são gravados. Cartões de teste: `4242 4242 4242 4242` é aprovado e `4000 0000 0000 0002` é recusado.
  - **Pix:** QR Code e copia e cola no formato do Banco Central, com CRC válido. Vale 30 minutos.
  - **Boleto:** linha digitável, vence em 3 dias.
  - Pix e boleto ficam aguardando até o botão **Simular pagamento**, que faz o papel do banco. Recarregar a página volta ao mesmo pedido, e um pedido novo substitui o pendente.
- **Regras de cobrança:**
  - Trocar de plano ou de ciclo começa um período novo hoje, descontando o que sobrou do período pago (crédito proporcional).
  - Pagar o mesmo plano e ciclo é renovação: o período novo emenda no atual.
  - Vagas extras compradas no meio do período custam o proporcional aos dias que faltam e entram na renovação.
  - Uma academia que assina o Rede vira rede na aprovação, e o administrador vira o dono. Fora do teste, *Transformar em rede* leva a esse checkout.
- **Limites:**
  - Com a assinatura ativa, valem os do plano. O 151º aluno ativo no Essencial é recusado, assim como reativar um aluno com o limite cheio. O mesmo vale para o 3º usuário no Essencial e para a unidade além das vagas.
  - Com o teste ou o período pago **vencido**, novos alunos, usuários e unidades ficam bloqueados (com a mensagem de como resolver). O resto do sistema continua funcionando.
  - Para descer de plano, o uso precisa caber no plano menor.
- **Quem paga:** o administrador da academia avulsa ou o dono da rede. O administrador de uma unidade vê a situação da rede, mas não paga, e funcionários não acessam (403).
- **Onde está no código:**
  - Servidor:
    - `model/PlanoSistema.java`: preços e limites.
    - `model/Assinatura.java` e `model/Pagamento.java`.
    - `service/AssinaturaService.java`: regras, crédito, limites e aprovação.
    - `service/GatewayDemonstracao.java`: troque por um gateway real (Mercado Pago, Stripe…) para cobrar de verdade.
    - `controller/AssinaturaController.java`.
  - Telas: `static/js/assinatura.js`.
  - Comparador da apresentação: `static/js/apresentacao.js`.
- **Exclusões:** excluir a conta (academia ou rede) também apaga a assinatura e o histórico de pagamentos.

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
- **Sons dos cliques**: botões, itens de menu, abas, caixas de seleção e perguntas tocam um som curto, gerado na hora pelo navegador (Web Audio, sem arquivos de áudio). São quatro tipos: clique comum, botão principal (salvar/enviar), ação de risco (excluir, sair) e alternar (caixas, opções, abas). Com mouse, o som toca ao apertar o botão. No toque e no teclado, toca no clique, para não soar enquanto a página rola.
  - **Ligar ou desligar:** no sistema, pela opção **Sons dos cliques** do menu da conta. Na apresentação, pelo botão de alto-falante no topo. A escolha vale para as duas páginas, no mesmo navegador.
  - **Botão de excluir a conta:** enquanto o botão é segurado, um zumbido grave sobe de tom e treme mais rápido conforme o preenchimento enche. Se a pessoa soltar antes, o som "murcha" junto com o botão. Quando enche, toca uma trava curta. Depois vem um impacto grave se a exclusão deu certo, ou dois toques secos se a senha ou o código estavam errados. Outros botões de segurar podem usar o mesmo som com a opção `som: true` do `HoldButton.montar`.
  - **Arquivo:** `static/js/sons.js`. Para deixar um elemento sem som, use `data-sem-som`. Para forçar um tipo, use `data-som="primario|perigo|alternar|clique"`.
- **Notificações** (sino no topo, para todos os perfis): o contador mostra quantas não foram lidas. Clicar num aviso marca como lido e abre a tela certa (ex.: mensalidades já filtradas nas atrasadas). Há também "Marcar todas como lidas".

  | Tipo | Quando aparece | Som |
  |---|---|---|
  | Financeiro | Mensalidades atrasadas (com o total) e mensalidades que vencem hoje | "plim" de moeda |
  | Alunos | Aniversariantes do dia | arpejo alegre |
  | Assinatura (administradores) | Pagamento aprovado, teste nos 3 últimos dias, assinatura vencendo em até 7 dias ou vencida (urgente), Pix ou boleto aguardando, perto do limite de alunos do plano | sino suave |
  | Rede (dono) | Unidade sem administrador ativo, todas as vagas de unidade em uso | marimba |
  | Segurança (cada pessoa) | Senha alterada (por você ou por um administrador), duas etapas ativadas, desligadas ou redefinidas, outras sessões encerradas, e o lembrete para ativar as duas etapas | dois bipes |

  - **Quando toca:** quando chega uma notificação nova, uma vez por aviso neste navegador. Se chegarem várias juntas, toca só o som da mais importante (urgente primeiro), e o sino balança.
  - **Atualização:** ao entrar, a cada 45 segundos, ao trocar de tela, depois de cada gravação e quando a aba volta a ficar visível. Antes do primeiro clique na página, o navegador não deixa tocar som; o aviso toca na próxima atualização.
  - **Controle do som:** o botão de alto-falante do painel mostra os 5 tipos, com ▶ para ouvir cada som e uma chave para silenciar o tipo (a escolha fica no navegador). "Sons dos cliques", no menu da conta, desliga todos os sons.
  - **No servidor:**
    - `service/NotificacaoService.java`: monta a lista com os avisos calculados na hora e os eventos gravados.
    - `service/Notificador.java`: grava os eventos.
    - Tabelas `notificacoes` e `notificacoes_lidas`: os avisos calculados têm uma chave com a data, para voltarem no dia seguinte se o problema continuar.
    - Excluir uma pessoa apaga as notificações dela.
  - **No navegador:** `static/js/notificacoes.js`.
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
| Sessão e perfil | `GET /api/auth/eu`, `PUT /api/auth/eu` (próprio nome), `POST /api/auth/logout`, `POST /api/auth/senha`, `GET /api/auth/sessoes`, `DELETE /api/auth/sessoes/outras`, `GET /api/auth/conta/exclusao` (o que a exclusão apaga), `POST /api/auth/conta/excluir` |
| Duas etapas | `POST /api/auth/login/2fa` (público: desafio + código), `GET /api/auth/2fa`, `POST /api/auth/2fa/iniciar`, `POST /api/auth/2fa/ativar`, `POST /api/auth/2fa/desativar`, `DELETE /api/auth/2fa/dispositivos/{id}` e `DELETE /api/auth/2fa/dispositivos` (esquecer dispositivos lembrados), `POST /api/usuarios/{id}/2fa/redefinir` (admin) |
| Usuários (só admin) | `GET/POST /api/usuarios`, `PUT/DELETE /api/usuarios/{id}` |
| Rede (só dono) | `GET /api/rede` (painel), `POST /api/rede/unidades` (unidade + administrador), `PUT /api/rede/unidades/{id}`, `POST /api/rede/unidades/{id}/admins`, `POST /api/rede/unidades/{id}/abrir`, `GET /api/rede/unidades/{id}/exclusao` (o que a exclusão apaga), `POST /api/rede/unidades/{id}/excluir` |
| Rede (admin de academia avulsa) | `POST /api/rede/converter` (transforma a academia em rede e vira o dono; fora do teste grátis, pelo checkout do plano Rede) |
| Assinatura (só admin) | `GET /api/assinatura` (situação, uso, limites, catálogo e histórico), `GET /api/assinatura/orcamento?plano=&ciclo=&extras=`, `GET /api/assinatura/orcamento-unidades?quantidade=`, `POST /api/assinatura/checkout`, `POST /api/assinatura/unidades`, `GET /api/assinatura/pagamentos/{id}`, `POST /api/assinatura/pagamentos/{id}/simular` (demonstração: confirma Pix/boleto), `POST /api/assinatura/pagamentos/{id}/cancelar` |
| Notificações (todos) | `GET /api/notificacoes` (lista e não lidas), `POST /api/notificacoes/lidas` (`{ "chave": "..." }`), `POST /api/notificacoes/lidas/todas` |

Fora o login e o cadastro, toda chamada precisa do cabeçalho `Authorization: Bearer <token>`, com o token devolvido pelo login. Sem ele, a API responde 401; sem permissão para a ação, responde 403.

Erros são devolvidos como `{"erro": "mensagem"}` e aparecem para o usuário no site.

## Tecnologias

Java 21 · Spring Boot 3.5 (Web, Data JPA, Validation, Security) · H2 Database · Maven · HTML5, CSS3 e JavaScript (Fetch API), sem bibliotecas externas · IntelliJ IDEA


# INICIALIZAÇÃO:

| & "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd" spring-boot:run |
