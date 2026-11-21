/*
 * Área de notificações — Força Total
 *
 * O sino do topo lista os avisos do servidor (GET /api/notificacoes): eventos gravados
 * (pagamento aprovado, senha alterada...) e avisos calculados na hora (mensalidades
 * atrasadas, aniversariantes, assinatura, rede, segurança). Cada tipo tem cor, ícone e som:
 *   FINANCEIRO  "plim" de moeda      ALUNOS  arpejo alegre      ASSINATURA  sino suave
 *   REDE        marimba              SEGURANCA  dois bipes firmes
 * O som toca quando chega uma notificação nova (uma vez por aviso, neste navegador), só o do
 * aviso mais importante quando chegam vários juntos. Cada tipo pode ser silenciado no painel
 * (engrenagem), e o "Sons dos cliques" do menu da conta desliga tudo.
 * Atualiza ao entrar, a cada 45 segundos, ao trocar de tela e quando a aba volta a ficar visível.
 */
(function () {
  'use strict';

  const TIPOS = {
    ASSINATURA: { nome: 'Assinatura', desc: 'Pagamentos, teste e vencimento', som: 'notif-assinatura',
      icone: '<path d="M3 7h18v10H3z"/><path d="M3 10h18M7 14h4"/>' },
    SEGURANCA: { nome: 'Segurança', desc: 'Senha, duas etapas e sessões', som: 'notif-seguranca',
      icone: '<path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z"/><path d="M9 12l2 2 4-4"/>' },
    FINANCEIRO: { nome: 'Financeiro', desc: 'Mensalidades atrasadas e vencendo', som: 'notif-financeiro',
      icone: '<circle cx="12" cy="12" r="9"/><path d="M15 9.5c-.5-1-1.6-1.5-3-1.5-1.7 0-3 .9-3 2.2 0 3 6 1.6 6 4.6 0 1.3-1.3 2.2-3 2.2-1.4 0-2.6-.6-3.1-1.6M12 6.5v11"/>' },
    REDE: { nome: 'Rede', desc: 'Unidades e vagas', som: 'notif-rede',
      icone: '<path d="M4 21V9l5-3v15M9 21V4l11 4v13M3 21h18M13 11h3M13 15h3"/>' },
    ALUNOS: { nome: 'Alunos', desc: 'Aniversariantes do dia', som: 'notif-alunos',
      icone: '<path d="M4 21v-7h16v7M4 17h16M12 14V9M12 6.5c-1-1.2-.3-2.5 0-3.5.3 1 1 2.3 0 3.5z"/>' },
  };
  const PRIORIDADE = ['ASSINATURA', 'SEGURANCA', 'FINANCEIRO', 'REDE', 'ALUNOS'];
  const CHAVE_SONS = 'forcatotal.notificacoes.sons';
  const INTERVALO = 45000;

  let itens = [], relogio = null, carregando = false, atrasoTela = null;
  const $ = (s, el = document) => el.querySelector(s);
  const escH = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const logado = () => typeof sessao !== 'undefined' && !!sessao;

  /* ---- Preferências e avisos já tocados ---- */
  function lerJson(chave, padrao) { try { return JSON.parse(localStorage.getItem(chave)) ?? padrao; } catch { return padrao; } }
  function gravarJson(chave, v) { try { localStorage.setItem(chave, JSON.stringify(v)); } catch { /* sem armazenamento */ } }
  const prefs = () => lerJson(CHAVE_SONS, {});
  const somLigado = (tipo) => prefs()[tipo] !== false;
  const chaveAvisadas = () => 'forcatotal.notificacoes.avisadas.' + (logado() ? sessao.usuario.id : '0');

  /* ---- Tempo relativo ---- */
  function quando(iso) {
    const d = new Date(iso), agora = new Date();
    const min = Math.round((agora - d) / 60000);
    const hoje = new Date(agora.getFullYear(), agora.getMonth(), agora.getDate());
    if (d >= hoje) {
      if (d.getHours() === 0 && d.getMinutes() === 0) return 'hoje';
      return min < 1 ? 'agora' : min < 60 ? `há ${min} min` : `hoje, ${d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`;
    }
    const ontem = new Date(hoje); ontem.setDate(ontem.getDate() - 1);
    if (d >= ontem) return 'ontem';
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  }

  /* ---- Desenho ---- */
  function desenhar() {
    const naoLidas = itens.filter((n) => !n.lida).length;
    const cont = $('#contadorNotificacoes');
    cont.textContent = naoLidas > 9 ? '9+' : String(naoLidas);
    cont.hidden = naoLidas === 0;
    $('#botaoNotificacoes').setAttribute('aria-label', naoLidas ? `Notificações: ${naoLidas} não ${naoLidas === 1 ? 'lida' : 'lidas'}` : 'Notificações');
    $('#marcarTodasNotificacoes').disabled = naoLidas === 0;
    const lista = $('#listaNotificacoes');
    if (!itens.length) {
      lista.innerHTML = '<div class="notif-vazio"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12.5l5 5L20 6.5"/></svg><b>Tudo em dia</b><span>Nenhuma notificação por aqui.</span></div>';
      return;
    }
    lista.innerHTML = itens.map((n) => {
      const t = TIPOS[n.tipo] || TIPOS.ASSINATURA;
      return `<button type="button" class="notif-item tipo-${n.tipo.toLowerCase()} nivel-${n.nivel.toLowerCase()} ${n.lida ? 'lida' : ''}" role="listitem" data-chave="${escH(n.chave)}" ${n.link ? `data-link="${escH(n.link)}"` : ''}>
          <span class="notif-icone" aria-hidden="true"><svg viewBox="0 0 24 24">${t.icone}</svg></span>
          <span class="notif-corpo">
            <span class="notif-titulo">${escH(n.titulo)}</span>
            ${n.texto ? `<span class="notif-texto">${escH(n.texto)}</span>` : ''}
            <span class="notif-meta">${t.nome} · ${quando(n.data)}${n.nivel === 'URGENTE' ? ' · <b>urgente</b>' : ''}</span>
          </span>
          ${n.lida ? '' : '<span class="notif-ponto" aria-label="não lida"></span>'}
        </button>`;
    }).join('');
  }

  function desenharSons() {
    const p = prefs();
    const mudo = window.Sons && !window.Sons.ligado();
    $('#sonsNotificacoes').innerHTML = `
      <p class="notif-sons-dica">${mudo ? 'Os sons estão <b>desligados</b> em “Sons dos cliques”, no menu da conta.' : 'Cada tipo tem o seu som. Toque ▶ para ouvir.'}</p>
      ${PRIORIDADE.map((tipo) => {
        const t = TIPOS[tipo];
        return `<div class="notif-som tipo-${tipo.toLowerCase()}">
            <span class="notif-icone" aria-hidden="true"><svg viewBox="0 0 24 24">${t.icone}</svg></span>
            <span class="notif-corpo"><span class="notif-titulo">${t.nome}</span><span class="notif-texto">${t.desc}</span></span>
            <button type="button" class="notif-ouvir" data-ouvir="${tipo}" aria-label="Ouvir o som de ${t.nome}">▶</button>
            <label class="notif-chave" title="${p[tipo] === false ? 'Ligar' : 'Desligar'} o som de ${t.nome}">
              <input type="checkbox" data-som-tipo="${tipo}" ${p[tipo] === false ? '' : 'checked'} aria-label="Som das notificações de ${t.nome}">
              <span aria-hidden="true"></span>
            </label>
          </div>`;
      }).join('')}`;
  }

  /* ---- Som das novas ---- */
  function tocarNovas() {
    const avisadas = new Set(lerJson(chaveAvisadas(), []));
    const novas = itens.filter((n) => !n.lida && !avisadas.has(n.chave));
    if (!novas.length) return;
    // Sem um clique ou tecla na página o navegador não deixa tocar: espera a próxima atualização
    if (navigator.userActivation && !navigator.userActivation.hasBeenActive) return;
    const ordem = (n) => (n.nivel === 'URGENTE' ? -10 : 0) + PRIORIDADE.indexOf(n.tipo);
    const principal = novas.slice().sort((a, b) => ordem(a) - ordem(b))[0];
    if (somLigado(principal.tipo)) window.Sons?.tocar(TIPOS[principal.tipo]?.som);
    const botao = $('#botaoNotificacoes');
    botao.classList.remove('balancando'); void botao.offsetWidth; botao.classList.add('balancando');
    novas.forEach((n) => avisadas.add(n.chave));
    gravarJson(chaveAvisadas(), [...avisadas].slice(-300));
  }

  /* ---- Servidor ---- */
  // Busca própria, sem o tratamento de 401 do requisitar(): o sino nunca encerra a sessão
  // (ex.: logo depois de excluir a conta, a resposta 401 é esperada e a tela de saída é outra).
  async function buscar(caminho, method = 'GET', corpo) {
    const token = typeof lerToken === 'function' ? lerToken() : null;
    if (!token) return null;
    const headers = { Authorization: 'Bearer ' + token };
    if (corpo !== undefined) headers['Content-Type'] = 'application/json';
    const r = await fetch((typeof API !== 'undefined' ? API : '/api') + caminho, { method, headers, body: corpo !== undefined ? JSON.stringify(corpo) : undefined });
    return r.ok ? r.json() : null;
  }

  async function carregar() {
    if (!logado() || carregando) return;
    carregando = true;
    try {
      const r = await buscar('/notificacoes');
      if (!logado() || !r) return;
      itens = r.itens || [];
      desenhar();
      tocarNovas();
    } catch { /* servidor sem notificações ou fora do ar: tenta na próxima */ }
    finally { carregando = false; }
  }

  async function marcar(chave) {
    try {
      const r = await buscar('/notificacoes/lidas', 'POST', { chave });
      if (r) itens = r.itens || itens;
      desenhar();
    } catch { /* fica para a próxima */ }
  }

  /* ---- Painel ---- */
  function abrir() {
    if (typeof fecharMenuUsuario === 'function') fecharMenuUsuario();
    $('#painelNotificacoes').hidden = false;
    $('#botaoNotificacoes').setAttribute('aria-expanded', 'true');
    mostrarSons(false);
    carregar();
  }
  function fechar() {
    $('#painelNotificacoes').hidden = true;
    $('#botaoNotificacoes').setAttribute('aria-expanded', 'false');
  }
  function mostrarSons(sim) {
    $('#sonsNotificacoes').hidden = !sim;
    $('#listaNotificacoes').hidden = sim;
    $('#botaoSonsNotificacoes').setAttribute('aria-pressed', String(sim));
    $('#tituloNotificacoes').textContent = sim ? 'Sons das notificações' : 'Notificações';
    $('#marcarTodasNotificacoes').hidden = sim;
    if (sim) desenharSons();
  }

  function ligar() {
    const botao = $('#botaoNotificacoes'), painel = $('#painelNotificacoes');
    if (!botao || botao.dataset.ligado) return;
    botao.dataset.ligado = '1';
    botao.addEventListener('click', (e) => { e.stopPropagation(); painel.hidden ? abrir() : fechar(); });
    $('#botaoUsuario')?.addEventListener('click', fechar);
    document.addEventListener('click', (e) => { if (!e.target.closest('.notif-area')) fechar(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !painel.hidden) { fechar(); botao.focus(); } });
    $('#marcarTodasNotificacoes').addEventListener('click', async () => {
      try { const r = await buscar('/notificacoes/lidas/todas', 'POST'); if (r) { itens = r.itens || itens; desenhar(); } } catch { /* fica para a próxima */ }
    });
    $('#botaoSonsNotificacoes').addEventListener('click', () => mostrarSons($('#sonsNotificacoes').hidden));
    $('#listaNotificacoes').addEventListener('click', (e) => {
      const item = e.target.closest('.notif-item');
      if (!item) return;
      if (!item.classList.contains('lida')) marcar(item.dataset.chave);
      if (item.dataset.link) { fechar(); location.hash = item.dataset.link; }
    });
    $('#sonsNotificacoes').addEventListener('click', (e) => {
      const b = e.target.closest('[data-ouvir]');
      if (b) window.Sons?.tocar(TIPOS[b.dataset.ouvir].som);
    });
    $('#sonsNotificacoes').addEventListener('change', (e) => {
      const tipo = e.target.dataset?.somTipo;
      if (!tipo) return;
      const p = prefs();
      p[tipo] = e.target.checked;
      gravarJson(CHAVE_SONS, p);
      if (e.target.checked) window.Sons?.tocar(TIPOS[tipo].som);
      desenharSons();
    });
    // Atualizações: troca de tela, aba visível de novo e o primeiro clique (que libera o áudio)
    window.addEventListener('hashchange', () => { clearTimeout(atrasoTela); atrasoTela = setTimeout(carregar, 700); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) carregar(); });
    document.addEventListener('pointerdown', () => setTimeout(carregar, 300), { once: true, capture: true });
  }

  window.Notificacoes = {
    iniciar() {
      ligar();
      clearInterval(relogio);
      relogio = setInterval(() => { if (!document.hidden) carregar(); }, INTERVALO);
      carregar();
    },
    parar() {
      clearInterval(relogio);
      relogio = null;
      itens = [];
      if ($('#painelNotificacoes')) { fechar(); desenhar(); }
    },
    atualizar: carregar,
    tipos: TIPOS,
  };
})();
