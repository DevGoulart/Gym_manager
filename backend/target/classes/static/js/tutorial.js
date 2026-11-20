/* =====================================================================
 * Tutorial guiado para novos usuários.
 * Passa pelas telas reais do sistema e destaca cada parte com um
 * "holofote" (o resto da tela escurece) e um balão com a explicação.
 *   - abre sozinho no primeiro acesso de cada usuário neste navegador;
 *   - pode ser reaberto em "Tutorial do sistema", no menu da conta;
 *   - teclado: → / Enter avança, ← volta, Esc fecha;
 *   - muda conforme o perfil (administrador ou recepção), se a academia
 *     é nova (passo dos "Primeiros passos") e no celular (botão de menu).
 * Uso (feito pelo app.js): Tutorial.aoEntrar(ctx) e Tutorial.iniciar(ctx), com
 *   ctx = { usuarioId, academiaId, nome, academia, admin, comecando }
 * ===================================================================== */
(function () {
  const CHAVE = (ctx) => `forcatotal.tutorial.${ctx.academiaId}.${ctx.usuarioId}`;
  const celular = () => matchMedia('(max-width: 1100px)').matches; // abaixo disso o menu vira botão (☰)
  const reduz = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
  const espera = (ms) => new Promise((r) => setTimeout(r, ms));
  const PADDING = 8;

  const jaViu = (ctx) => { try { return localStorage.getItem(CHAVE(ctx)) === 'visto'; } catch { return true; } };
  const marcarVisto = (ctx) => { try { localStorage.setItem(CHAVE(ctx), 'visto'); } catch { /* sem armazenamento: tudo bem */ } };

  /* ---------------- Passos ---------------- */
  function montarPassos(ctx) {
    const primeiro = (ctx.nome || '').trim().split(/\s+/)[0] || '';
    const passos = [
      {
        rota: null, alvo: null,
        titulo: `Bem-vindo${primeiro ? `, ${primeiro}` : ''}!`,
        texto: `Este tour rápido mostra onde fica cada coisa no sistema da <b>${ctx.academia || 'sua academia'}</b>. Leva menos de um minuto e você pode sair quando quiser.`,
        proximo: 'Começar tour', pular: 'Agora não',
      },
      celular()
        ? { rota: 'dashboard', alvo: '#hamburguer', titulo: 'Os módulos do sistema',
            texto: 'Toque neste botão para abrir o menu com <b>Alunos, Planos, Mensalidades, Professores, Treinos e Avaliações</b>.' }
        : { rota: 'dashboard', alvo: '#menu', titulo: 'Os módulos do sistema',
            texto: 'Cada parte da academia tem a sua tela: <b>alunos, planos, mensalidades, professores, treinos e avaliações</b>. Clique para trocar.' },
    ];
    if (ctx.comecando) passos.push({
      rota: 'dashboard', alvo: '.primeiros-passos', titulo: 'Comece por aqui',
      texto: 'Como a academia é nova, siga esta lista: <b>crie os planos</b>, cadastre os professores e matricule os alunos. Ela some quando você começar.',
    });
    passos.push(
      { rota: 'dashboard', alvo: '.destaques', titulo: 'Os números do dia',
        texto: 'Alunos ativos, quanto entrou no mês, mensalidades atrasadas e o total em aberto. <b>Clique num cartão</b> para ver a lista já filtrada.' },
      { rota: 'dashboard', alvo: '#acoes', titulo: 'Atalhos de cada tela',
        texto: 'Os botões de ação ficam sempre aqui no topo. No início, dá para <b>matricular um aluno</b> ou registrar uma <b>nova avaliação</b> direto.' },
      { rota: 'alunos', alvo: '#buscaAluno', titulo: 'Busca rápida',
        texto: 'Encontre um aluno pelo nome, CPF, e-mail ou telefone enquanto digita. Atalho: aperte <kbd>/</kbd> em qualquer tela com busca.' },
      { rota: 'alunos', alvo: '#listaAlunos', titulo: 'A ficha do aluno',
        texto: 'Em cada linha você vê o plano e a situação financeira. O botão <b>Ficha</b> reúne pagamentos, treinos e avaliações do aluno.' },
      ctx.admin
        ? { rota: 'planos', alvo: '#acoes', titulo: 'Planos e preços',
            texto: 'Crie os planos da academia com valor mensal e duração. Só <b>administradores</b> criam e alteram planos.' }
        : { rota: 'planos', alvo: '#conteudo', titulo: 'Planos e preços',
            texto: 'Aqui ficam os planos da academia e quantos alunos há em cada um. Quem cria e altera planos é o <b>administrador</b>.' },
      { rota: 'mensalidades', alvo: '#acoes', titulo: 'Cobrança sem esquecimento',
        texto: '<b>Gere as mensalidades</b> do mês pelo plano de cada aluno. Na lista, use <b>Registrar pagamento</b> ao receber: as atrasadas aparecem em vermelho.' },
      { rota: 'treinos', alvo: '#acoes', titulo: 'Fichas de treino',
        texto: 'Monte a ficha com exercícios, séries, repetições e carga. O sistema sugere exercícios enquanto você digita, e dá para duplicar uma ficha pronta.' },
      { rota: 'avaliacoes', alvo: '#fAluno', titulo: 'Evolução em gráficos',
        texto: 'Escolha um aluno aqui para ver a evolução de <b>peso, IMC, gordura e cintura</b>, comparando cada avaliação com a anterior.' },
      { rota: 'dashboard', alvo: '#botaoUsuario', titulo: 'Sua conta',
        texto: `Troque sua senha, ative a verificação em duas etapas${ctx.admin ? ', gerencie os usuários da academia' : ''} e <b>reveja este tutorial</b> quando quiser.` },
      { rota: null, alvo: null, titulo: 'Tudo pronto!',
        texto: ctx.comecando
          ? 'Agora é com você. Comece criando os <b>planos</b> da academia na lista de primeiros passos. Bons treinos!'
          : 'Você já conhece o caminho. Para rever o tour, abra o menu da sua conta e escolha <b>Tutorial do sistema</b>.',
        proximo: 'Concluir', pular: null },
    );
    return passos;
  }

  /* ---------------- Interface ---------------- */
  let estado = null; // { ctx, passos, i, el, foco, ... }

  function criarInterface() {
    const raiz = document.createElement('div');
    raiz.className = 'tut';
    raiz.innerHTML = `
      <div class="tut-bloqueio"></div>
      <div class="tut-holofote" aria-hidden="true"></div>
      <div class="tut-balao" role="dialog" aria-modal="true" aria-labelledby="tutTitulo" aria-describedby="tutTexto" tabindex="-1">
        <div class="tut-topo">
          <span class="tut-contador"></span>
          <button type="button" class="tut-fechar" aria-label="Fechar tutorial"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5l14 14M19 5L5 19"/></svg></button>
        </div>
        <h2 id="tutTitulo" class="tut-titulo"></h2>
        <p id="tutTexto" class="tut-texto"></p>
        <div class="tut-progresso" aria-hidden="true"><i></i></div>
        <div class="tut-botoes">
          <button type="button" class="link tut-pular"></button>
          <button type="button" class="pequeno tut-voltar">Voltar</button>
          <button type="button" class="primario tut-proximo"></button>
        </div>
      </div>`;
    document.body.append(raiz);
    raiz.querySelector('.tut-proximo').addEventListener('click', () => ir(estado.i + 1));
    raiz.querySelector('.tut-voltar').addEventListener('click', () => ir(estado.i - 1));
    raiz.querySelector('.tut-pular').addEventListener('click', () => fechar());
    raiz.querySelector('.tut-fechar').addEventListener('click', () => fechar());
    // Clique fora do balão: chama a atenção para ele em vez de deixar mexer na tela por trás
    raiz.querySelector('.tut-bloqueio').addEventListener('click', () => {
      const b = raiz.querySelector('.tut-balao');
      b.classList.remove('tut-chamar'); void b.offsetWidth; b.classList.add('tut-chamar');
    });
    return raiz;
  }

  // Espera a tela terminar de carregar e o elemento aparecer (até ~5 s)
  async function esperarAlvo(seletor, limite = 5000) {
    const fim = performance.now() + limite;
    let ultimo = '';
    while (performance.now() < fim) {
      if (!estado) return null;
      const carregando = document.querySelector('#conteudo .carregando');
      const el = seletor ? document.querySelector(seletor) : null;
      if (!carregando && !seletor) return null;
      if (!carregando && el && el.getClientRects().length) {
        // Só aceita quando o elemento parou de mudar de tamanho/posição (a tela terminou de montar)
        const r = el.getBoundingClientRect();
        const marca = [r.top, r.left, r.width, r.height].map(Math.round).join(',');
        if (marca === ultimo) return el;
        ultimo = marca;
      } else ultimo = '';
      await espera(80);
    }
    return null;
  }

  function posicionar() {
    if (!estado) return;
    const { raiz, alvoEl } = estado;
    const holofote = raiz.querySelector('.tut-holofote');
    const balao = raiz.querySelector('.tut-balao');
    const vw = innerWidth, vh = innerHeight, margem = 16, gap = 14;

    if (!alvoEl || !alvoEl.isConnected) {
      // Passo sem alvo: holofote recolhido no centro e balão no meio da tela
      raiz.classList.add('tut-centro');
      holofote.style.cssText = `top:${vh / 2}px;left:${vw / 2}px;width:0;height:0;`;
      balao.style.cssText = '';
      return;
    }
    raiz.classList.remove('tut-centro');
    const r = alvoEl.getBoundingClientRect();
    // Alvo maior que a tela (ex.: lista longa): destaca só a parte visível
    const topo = Math.max(r.top, 0), base = Math.min(r.bottom, vh);
    const caixa = { top: topo - PADDING, left: r.left - PADDING, width: r.width + PADDING * 2, height: Math.max(0, base - topo) + PADDING * 2 };
    // Não deixa o holofote passar das bordas da tela (ex.: menu colado no topo)
    if (caixa.top < 2) { caixa.height -= 2 - caixa.top; caixa.top = 2; }
    if (caixa.left < 2) { caixa.width -= 2 - caixa.left; caixa.left = 2; }
    caixa.width = Math.min(caixa.width, vw - 2 - caixa.left);
    caixa.height = Math.min(caixa.height, vh - 2 - caixa.top);
    holofote.style.cssText = `top:${caixa.top}px;left:${caixa.left}px;width:${caixa.width}px;height:${caixa.height}px;`;

    const bw = balao.offsetWidth, bh = balao.offsetHeight;
    let top, left;
    if (vw <= 600) {
      // Celular: balão preso embaixo (ou em cima, se o alvo estiver embaixo)
      left = margem;
      // Em cima só se o alvo estiver embaixo E couber acima dele; senão embaixo (alvo maior que a tela: mostra o começo dele)
      const embaixo = vh - bh - margem, acima = caixa.top - bh - gap;
      top = caixa.top + caixa.height > embaixo - margem && acima >= margem ? acima : embaixo;
    } else {
      const cabe = (y) => y >= margem && y + bh <= vh - margem;
      const abaixo = caixa.top + caixa.height + gap, acima = caixa.top - gap - bh;
      top = cabe(abaixo) ? abaixo : cabe(acima) ? acima : vh - bh - margem;
      left = Math.min(Math.max(caixa.left + caixa.width / 2 - bw / 2, margem), vw - bw - margem);
    }
    balao.style.cssText = `top:${Math.round(top)}px;left:${Math.round(left)}px;`;
  }

  let quadro = 0;
  const reposicionar = () => { if (!quadro) quadro = requestAnimationFrame(() => { quadro = 0; posicionar(); }); };

  async function ir(indice) {
    if (!estado) return;
    const { passos, raiz } = estado;
    if (indice >= passos.length) { fechar(true); return; }
    if (indice < 0) return;
    const voltando = indice < estado.i;
    const passo = passos[indice];
    const token = (estado.token = (estado.token || 0) + 1);
    raiz.classList.add('tut-trocando');

    // Troca de tela, se o passo for em outro módulo
    if (passo.rota) {
      const atual = location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard';
      if (atual !== passo.rota) { location.hash = `#/${passo.rota}`; await espera(150); }
    }
    let alvoEl = passo.alvo ? await esperarAlvo(passo.alvo) : null;
    if (!estado || token !== estado.token) return;
    // Alvo opcional que não existe nesta academia: pula para o próximo (ou anterior)
    if (passo.alvo && !alvoEl && passo.alvo === '.primeiros-passos') { passos.splice(indice, 1); ir(voltando ? indice - 1 : indice); return; }

    if (alvoEl && !alvoEl.closest('.barra-topo')) {
      const r = alvoEl.getBoundingClientRect();
      const cab = document.querySelector('.barra-topo')?.getBoundingClientRect().height || 0;
      const comportamento = reduz() ? 'auto' : 'smooth';
      let destino = null;
      if (innerWidth <= 600) {
        // Celular: o balão fica embaixo, então o alvo sobe para logo abaixo do cabeçalho
        if (r.top < cab + 8 || r.bottom > innerHeight - 300) destino = scrollY + r.top - cab - 14;
      } else if (r.top < cab + 20 || r.bottom > innerHeight - 200) {
        destino = r.height > innerHeight * 0.6 ? scrollY + r.top - cab - 20 : scrollY + r.top - (innerHeight - r.height) / 2;
      }
      if (destino !== null) {
        scrollTo({ top: Math.max(0, destino), behavior: comportamento });
        await espera(reduz() ? 30 : 420);
        if (!estado || token !== estado.token) return;
      }
    }

    estado.i = indice;
    estado.alvoEl = alvoEl;
    estado.observador?.disconnect();
    estado.observador = new ResizeObserver(reposicionar);
    estado.observador.observe(document.body);
    if (alvoEl) estado.observador.observe(alvoEl);
    const total = passos.length;
    raiz.querySelector('.tut-contador').textContent = `${String(indice + 1).padStart(2, '0')} / ${String(total).padStart(2, '0')}`;
    raiz.querySelector('.tut-titulo').textContent = passo.titulo;
    raiz.querySelector('.tut-texto').innerHTML = passo.texto;
    raiz.querySelector('.tut-progresso i').style.width = `${((indice + 1) / total) * 100}%`;
    const pular = raiz.querySelector('.tut-pular');
    const rotuloPular = passo.pular === undefined ? 'Pular tutorial' : passo.pular;
    pular.hidden = !rotuloPular; pular.textContent = rotuloPular || '';
    raiz.querySelector('.tut-voltar').hidden = indice === 0;
    raiz.querySelector('.tut-proximo').textContent = passo.proximo || 'Próximo';
    posicionar();
    raiz.classList.remove('tut-trocando');
    raiz.querySelector('.tut-proximo').focus({ preventScroll: true });
  }

  function teclado(e) {
    if (!estado) return;
    const balao = estado.raiz.querySelector('.tut-balao');
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); fechar(); return; }
    if (e.key === 'ArrowRight') { e.preventDefault(); ir(estado.i + 1); return; }
    if (e.key === 'ArrowLeft') { e.preventDefault(); ir(estado.i - 1); return; }
    if (e.key === 'Tab') {
      // Mantém o foco dentro do balão
      const focaveis = [...balao.querySelectorAll('button:not([hidden])')];
      if (!focaveis.length) return;
      const i = focaveis.indexOf(document.activeElement);
      const proximo = e.shiftKey ? (i <= 0 ? focaveis.length - 1 : i - 1) : (i === focaveis.length - 1 ? 0 : i + 1);
      e.preventDefault(); focaveis[proximo].focus();
      return;
    }
    // Enquanto o tour está aberto, atalhos da página (como "/") não disparam
    if (!balao.contains(e.target)) e.stopPropagation();
  }

  function iniciar(ctx) {
    if (estado) return;
    if (document.querySelector('dialog[open]')) return;
    const raiz = criarInterface();
    estado = { ctx, passos: montarPassos(ctx), i: 0, raiz, alvoEl: null, foco: document.activeElement };
    document.body.classList.add('tutorial-aberto');
    document.body.classList.remove('menu-aberto');
    document.addEventListener('keydown', teclado, true);
    addEventListener('resize', reposicionar);
    addEventListener('scroll', reposicionar, { passive: true });
    requestAnimationFrame(() => raiz.classList.add('tut-visivel'));
    ir(0);
  }

  function fechar(concluiu = false) {
    if (!estado) return;
    const { raiz, ctx, foco } = estado;
    estado.observador?.disconnect();
    marcarVisto(ctx); // pular também conta: o tour não volta a abrir sozinho
    estado = null;
    document.removeEventListener('keydown', teclado, true);
    removeEventListener('resize', reposicionar);
    removeEventListener('scroll', reposicionar);
    document.body.classList.remove('tutorial-aberto');
    raiz.classList.remove('tut-visivel');
    setTimeout(() => raiz.remove(), reduz() ? 0 : 220);
    if (concluiu && (location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard') !== 'dashboard') location.hash = '#/dashboard';
    if (foco && foco.isConnected) foco.focus({ preventScroll: true });
  }

  // Primeiro acesso: espera a tela inicial carregar e abre o tour (uma vez por usuário neste navegador)
  async function aoEntrar(ctx) {
    if (jaViu(ctx) || estado) return;
    await espera(400);
    const fim = performance.now() + 5000;
    while (document.querySelector('#conteudo .carregando') && performance.now() < fim) await espera(100);
    if (document.body.classList.contains('nao-logado')) return;
    iniciar(ctx);
  }

  window.Tutorial = { iniciar, aoEntrar, encerrar: () => { if (estado) { const ctx = estado.ctx; fechar(); try { localStorage.removeItem(CHAVE(ctx)); } catch { /* ignora */ } } } };
})();
