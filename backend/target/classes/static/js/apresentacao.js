/* =====================================================================
 * Interatividade da página de apresentação:
 *   - abas dos módulos (clique, setas do teclado e troca automática);
 *   - simulador de faturamento com plano indicado;
 *   - cobrança mensal/anual nos planos.
 * ===================================================================== */
(function () {
  const reduz = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const reais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
  const $ = (sel, raiz = document) => raiz.querySelector(sel);
  const $$ = (sel, raiz = document) => [...raiz.querySelectorAll(sel)];

  /* ---------------- Abas dos módulos ---------------- */
  function iniciarAbas() {
    const explorador = $('.explorador');
    if (!explorador) return;
    const abas = $$('[role="tab"]', explorador);
    const barra = $('.abas-progresso i', explorador);
    const INTERVALO = 6500;
    let atual = 0, timer = 0, automatico = !reduz, visivel = false, pausado = false;

    function mostrar(indice, focar = false) {
      atual = (indice + abas.length) % abas.length;
      abas.forEach((aba, i) => {
        const ativa = i === atual;
        aba.setAttribute('aria-selected', String(ativa));
        aba.tabIndex = ativa ? 0 : -1;
        const painel = document.getElementById(aba.getAttribute('aria-controls'));
        painel.hidden = !ativa;
        if (ativa) { painel.classList.remove('entrando'); void painel.offsetWidth; painel.classList.add('entrando'); }
      });
      if (focar) abas[atual].focus();
      // Em telas estreitas as abas rolam para o lado: mantém a aba ativa visível
      const lista = abas[atual].parentElement;
      const a = abas[atual];
      if (lista.scrollWidth > lista.clientWidth) lista.scrollTo({ left: a.offsetLeft - (lista.clientWidth - a.offsetWidth) / 2, behavior: reduz ? 'auto' : 'smooth' });
      reiniciarRelogio();
    }

    function reiniciarRelogio() {
      clearTimeout(timer);
      if (barra) { barra.style.animation = 'none'; void barra.offsetWidth; barra.style.animation = ''; }
      const rodando = automatico && visivel && !pausado && !document.hidden;
      explorador.classList.toggle('rodando', rodando);
      if (rodando) timer = setTimeout(() => mostrar(atual + 1), INTERVALO);
    }

    // Quem clica assume o controle: a troca automática para de vez
    const pararAutomatico = () => { automatico = false; reiniciarRelogio(); };

    abas.forEach((aba, i) => {
      aba.addEventListener('click', () => { pararAutomatico(); mostrar(i); });
      aba.addEventListener('keydown', (ev) => {
        const mapa = { ArrowRight: atual + 1, ArrowDown: atual + 1, ArrowLeft: atual - 1, ArrowUp: atual - 1, Home: 0, End: abas.length - 1 };
        if (!(ev.key in mapa)) return;
        ev.preventDefault(); pararAutomatico(); mostrar(mapa[ev.key], true);
      });
    });
    // Mouse em cima ou foco dentro: pausa enquanto a pessoa lê
    explorador.addEventListener('pointerenter', (ev) => { if (ev.pointerType === 'mouse') { pausado = true; reiniciarRelogio(); } });
    explorador.addEventListener('pointerleave', () => { pausado = false; reiniciarRelogio(); });
    explorador.addEventListener('focusin', () => { pausado = true; reiniciarRelogio(); });
    explorador.addEventListener('focusout', (ev) => { if (!explorador.contains(ev.relatedTarget)) { pausado = false; reiniciarRelogio(); } });
    // Só troca sozinho quando o explorador está na tela
    new IntersectionObserver(([en]) => { visivel = en.isIntersecting; reiniciarRelogio(); }, { threshold: 0.35 }).observe(explorador);
    if (barra) barra.style.animationDuration = INTERVALO + 'ms';
    document.addEventListener('visibilitychange', reiniciarRelogio);
  }

  /* ---------------- Número que "corre" até o novo valor ---------------- */
  const animacoes = new WeakMap();
  function animarNumero(el, alvo, formatar) {
    const de = animacoes.get(el)?.valor ?? alvo;
    cancelAnimationFrame(animacoes.get(el)?.raf);
    if (reduz || de === alvo) { el.textContent = formatar(alvo); animacoes.set(el, { valor: alvo }); return; }
    const inicio = performance.now(), duracao = 420;
    const estado = { valor: de, raf: 0 };
    animacoes.set(el, estado);
    const passo = (agora) => {
      const t = Math.min(1, (agora - inicio) / duracao), e = 1 - Math.pow(1 - t, 3);
      estado.valor = de + (alvo - de) * e;
      el.textContent = formatar(estado.valor);
      if (t < 1) estado.raf = requestAnimationFrame(passo); else estado.valor = alvo;
    };
    estado.raf = requestAnimationFrame(passo);
  }

  /* ---------------- Simulador ---------------- */
  const PLANOS = [
    { id: 'essencial', nome: 'Essencial', ate: 150 },
    { id: 'profissional', nome: 'Profissional', ate: 600 },
    { id: 'rede', nome: 'Rede', ate: Infinity },
  ];

  function iniciarSimulador() {
    const alunos = $('#simAlunos'), mensalidade = $('#simMensalidade'), atraso = $('#simAtraso');
    if (!alunos) return;
    const formatar = (v) => reais.format(Math.round(v));
    let planoAnterior = '';

    function pintarTrilho(input) {
      const p = ((input.value - input.min) / (input.max - input.min)) * 100;
      input.style.setProperty('--p', p + '%');
    }

    function calcular() {
      const a = Number(alunos.value), m = Number(mensalidade.value), t = Number(atraso.value) / 100;
      [alunos, mensalidade, atraso].forEach(pintarTrilho);
      $('#saidaAlunos').textContent = a.toLocaleString('pt-BR');
      $('#saidaMensalidade').textContent = reais.format(m);
      $('#saidaAtraso').textContent = Math.round(t * 100) + '%';
      const faturamento = a * m, aberto = Math.round(a * t) * m;
      animarNumero($('#resFaturamento'), faturamento, formatar);
      animarNumero($('#resAberto'), aberto, formatar);
      $('#resAno').textContent = `${formatar(aberto * 12)} por ano`;

      const plano = PLANOS.find((p) => a <= p.ate);
      $('#resPlano').textContent = plano.nome;
      if (plano.id !== planoAnterior) {
        planoAnterior = plano.id;
        $$('.plano-card').forEach((card) => {
          const indicado = card.dataset.plano === plano.id;
          card.classList.toggle('indicado', indicado);
          $('.plano-selo', card).hidden = !indicado;
        });
        const res = $('.sim-recomendacao');
        res.classList.remove('pulsa'); void res.offsetWidth; res.classList.add('pulsa');
      }
    }

    [alunos, mensalidade, atraso].forEach((input) => input.addEventListener('input', calcular));
    // "Ver plano": rola até os planos
    $('#verPlanoIndicado').addEventListener('click', (ev) => {
      ev.preventDefault();
      $('#planos').scrollIntoView({ behavior: reduz ? 'auto' : 'smooth', block: 'start' });
    });
    calcular();
  }

  /* ---------------- Cobrança mensal / anual ---------------- */
  function iniciarCobranca() {
    const grupo = $('.cobranca');
    if (!grupo) return;
    const botoes = $$('[data-cobranca]', grupo);

    function escolher(modo) {
      grupo.dataset.modo = modo;
      botoes.forEach((b) => { const sim = b.dataset.cobranca === modo; b.setAttribute('aria-checked', String(sim)); b.tabIndex = sim ? 0 : -1; });
      $$('.plano-preco b').forEach((b) => {
        const novo = b.dataset[modo];
        if (b.textContent === novo) return;
        if (reduz) { b.textContent = novo; return; }
        b.classList.add('saindo');
        setTimeout(() => { b.textContent = novo; b.classList.remove('saindo'); b.classList.add('entrando'); setTimeout(() => b.classList.remove('entrando'), 260); }, 160);
      });
      $$('.plano-cobrado').forEach((p) => { p.textContent = p.dataset[modo]; });
    }

    botoes.forEach((b) => {
      b.addEventListener('click', () => escolher(b.dataset.cobranca));
      b.addEventListener('keydown', (ev) => {
        if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(ev.key)) return;
        ev.preventDefault();
        const outro = botoes.find((x) => x !== b);
        escolher(outro.dataset.cobranca); outro.focus();
      });
    });
    escolher('mensal');
  }

  /* ---------------- Perguntas: só uma aberta por vez ---------------- */
  function iniciarPerguntas() {
    const itens = $$('.faq details');
    itens.forEach((d) => d.addEventListener('toggle', () => { if (d.open) itens.forEach((o) => { if (o !== d) o.open = false; }); }));
  }

  /* ---------------- Quem já entrou no sistema vê "Abrir o sistema" ---------------- */
  function iniciarAcesso() {
    let logado = false;
    try { logado = !!localStorage.getItem('forcatotal.token'); } catch { /* sem armazenamento: mantém "Entrar" */ }
    if (!logado) return;
    const entrar = $('#linkEntrar'), jaTenho = $('#linkJaTenho');
    if (entrar) entrar.textContent = 'Abrir o sistema';
    if (jaTenho) jaTenho.textContent = 'Abrir o sistema';
  }

  /* ---------------- Plano Rede: comparar quantas unidades ---------------- */
  // O preço do cartão do Rede muda com a quantidade (5 incluídas + vagas extras) e mostra quanto sai cada unidade.
  let extrasRede = 0;
  function iniciarUnidades() {
    const caixa = $('[data-unidades]');
    if (!caixa) return;
    const card = caixa.closest('.plano-card');
    const precoEl = $('.plano-preco b', card), cobradoEl = $('.plano-cobrado', card);
    const d = caixa.dataset, base = +d.base, max = +d.max;
    const br = (v) => v.toLocaleString('pt-BR', { minimumFractionDigits: Number.isInteger(v) ? 0 : 2, maximumFractionDigits: 2 });
    const modo = () => ($('.cobranca')?.dataset.modo === 'anual' ? 'anual' : 'mensal');
    const [menos, mais] = $$('[data-unid]', caixa);

    function atualizar(soPorUnidade = false) {
      const unidades = base + extrasRede;
      const mensal = +d.precoMensal + +d.extraMensal * extrasRede;
      const anual = +d.precoAnual + +d.extraAnual * extrasRede;
      precoEl.dataset.mensal = br(mensal);
      precoEl.dataset.anual = br(Math.round((anual / 12) * 100) / 100);
      cobradoEl.dataset.anual = `R$ ${br(anual)} cobrados por ano`;
      if (!soPorUnidade) { // na troca mensal/anual, o preço anima pelo iniciarCobranca
        precoEl.textContent = precoEl.dataset[modo()];
        cobradoEl.textContent = cobradoEl.dataset[modo()];
      }
      const porMes = modo() === 'anual' ? anual / 12 : mensal;
      $('output', caixa).textContent = `${unidades} unidades`;
      $('[data-por-unidade]', caixa).textContent = `R$ ${br(Math.round((porMes / unidades) * 100) / 100)} por unidade`;
      menos.disabled = extrasRede <= 0;
      mais.disabled = unidades >= max;
    }
    [menos, mais].forEach((b) => b.addEventListener('click', () => {
      extrasRede = Math.min(max - base, Math.max(0, extrasRede + Number(b.dataset.unid)));
      atualizar();
    }));
    $$('[data-cobranca]').forEach((b) => b.addEventListener('click', () => setTimeout(() => atualizar(true), 0)));
    atualizar();
  }

  /* ---------------- Botões dos planos: levam ao checkout com a escolha ---------------- */
  // Sem login: guarda a escolha e abre o cadastro (depois do cadastro, o sistema vai direto ao checkout).
  function iniciarCheckout() {
    let logado = false;
    try { logado = !!localStorage.getItem('forcatotal.token'); } catch { /* sem armazenamento */ }
    $$('.plano-card').forEach((card) => {
      const botao = $('.plano-botao', card);
      if (!botao) return;
      botao.addEventListener('click', () => {
        const plano = String(card.dataset.plano || '').toUpperCase();
        const ciclo = $('.cobranca')?.dataset.modo === 'anual' ? 'ANUAL' : 'MENSAL';
        const extras = plano === 'REDE' ? extrasRede : 0;
        if (logado) {
          botao.href = `/app#/checkout?plano=${plano}&ciclo=${ciclo}${extras ? `&extras=${extras}` : ''}`;
        } else {
          try { sessionStorage.setItem('forcatotal.planoEscolhido', JSON.stringify({ plano, ciclo, extras })); } catch { /* sem armazenamento: só abre o cadastro */ }
          botao.href = '/app#cadastro';
        }
      });
    });
  }

  function iniciar() { iniciarAbas(); iniciarSimulador(); iniciarCobranca(); iniciarUnidades(); iniciarCheckout(); iniciarPerguntas(); iniciarAcesso(); }
  if (document.readyState !== 'loading') iniciar(); else document.addEventListener('DOMContentLoaded', iniciar);
})();
