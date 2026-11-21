/*
 * Sons dos cliques — Força Total
 *
 * Toca um som curto ao clicar em botões, itens de menu, abas, caixas de
 * seleção e perguntas abertas. Os sons são sintetizados na hora pelo
 * Web Audio (nada para baixar), em quatro tipos:
 *   clique    botões comuns, links do menu e da navegação
 *   primario  botões principais e de envio de formulário
 *   perigo    excluir, sair e outras ações destrutivas
 *   alternar  caixas de seleção, opções, abas e perguntas (<summary>)
 * Para o botão de segurar (HoldButton), Sons.segurar() dá um zumbido que sobe
 * conforme o botão enche, e há ainda "confirmado", "excluido" e "erro".
 *
 * A preferência fica no navegador (localStorage "forcatotal.sons") e vale
 * para a página de apresentação e para o sistema. Qualquer elemento com
 * [data-sons-alternar] liga e desliga os sons; [data-sem-som] fica mudo.
 *
 * Mouse e caneta tocam ao apertar (mais imediato); toque na tela e teclado
 * tocam no clique, para não soar quando a pessoa só está rolando a página.
 */
(function () {
  'use strict';

  const CHAVE = 'forcatotal.sons';
  const VOLUME = 0.32;

  const ALVOS = [
    'button', '.btn', '[role="button"]', '[role="menuitem"]', '[role="menuitemcheckbox"]',
    '[role="tab"]', '[role="radio"]', '[role="switch"]', 'summary',
    'input[type="checkbox"]', 'input[type="radio"]', 'input[type="submit"]', 'input[type="button"]',
    'nav a', 'a.link', 'label.check',
  ].join(',');

  let ligado = lerPreferencia();
  let ctx = null, saida = null, ruidoBuf = null;
  let ultimoPonteiro = 0, ultimoClique = 0, ultimoSom = 0;

  function lerPreferencia() {
    try { return localStorage.getItem(CHAVE) !== 'desligado'; } catch { return true; }
  }
  function gravarPreferencia() {
    try { localStorage.setItem(CHAVE, ligado ? 'ligado' : 'desligado'); } catch { /* navegação privada */ }
  }

  function audio() {
    if (!ctx) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return null;
      ctx = new Ctx();
      const compressor = ctx.createDynamicsCompressor();
      saida = ctx.createGain();
      saida.gain.value = VOLUME;
      saida.connect(compressor).connect(ctx.destination);
      ruidoBuf = ctx.createBuffer(1, Math.ceil(ctx.sampleRate * 0.05), ctx.sampleRate);
      const d = ruidoBuf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }

  /** Um tom com envelope curto (subida rápida, queda exponencial). */
  function tom(t0, { freq, ate, dur, forma = 'sine', vol }) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = forma;
    o.frequency.setValueAtTime(freq, t0);
    if (ate) o.frequency.exponentialRampToValueAtTime(ate, t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(vol, t0 + 0.003);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g).connect(saida);
    o.start(t0); o.stop(t0 + dur + 0.02);
  }

  /** Um estalo de ruído filtrado — dá o "toque" físico do clique. */
  function estalo(t0, { freq, dur, vol }) {
    const s = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
    s.buffer = ruidoBuf;
    f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = 1.2;
    g.gain.setValueAtTime(vol, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    s.connect(f).connect(g).connect(saida);
    s.start(t0); s.stop(t0 + dur + 0.01);
  }

  const SONS = {
    clique(t) {
      estalo(t, { freq: 3200, dur: 0.018, vol: 0.5 });
      tom(t, { freq: 1500, ate: 950, dur: 0.035, forma: 'triangle', vol: 0.16 });
    },
    primario(t) {
      estalo(t, { freq: 2400, dur: 0.02, vol: 0.45 });
      tom(t, { freq: 190, ate: 95, dur: 0.09, vol: 0.55 });
      tom(t, { freq: 950, ate: 620, dur: 0.045, forma: 'triangle', vol: 0.12 });
    },
    perigo(t) {
      estalo(t, { freq: 1600, dur: 0.02, vol: 0.35 });
      tom(t, { freq: 150, ate: 70, dur: 0.13, vol: 0.5 });
      tom(t + 0.012, { freq: 330, ate: 230, dur: 0.07, forma: 'triangle', vol: 0.08 });
    },
    alternar(t) {
      estalo(t, { freq: 5200, dur: 0.012, vol: 0.32 });
      tom(t, { freq: 2300, ate: 1800, dur: 0.026, forma: 'triangle', vol: 0.14 });
    },
    // Fim do "segurar" (botão cheio): uma trava curta, enquanto o servidor responde
    confirmado(t) {
      estalo(t, { freq: 2600, dur: 0.02, vol: 0.4 });
      tom(t, { freq: 620, ate: 940, dur: 0.07, forma: 'triangle', vol: 0.2 });
      tom(t + 0.06, { freq: 940, ate: 900, dur: 0.09, forma: 'sine', vol: 0.12 });
    },
    // Exclusão concluída: impacto grave com um sopro de ruído que desce
    excluido(t) {
      estalo(t, { freq: 900, dur: 0.05, vol: 0.7 });
      tom(t, { freq: 130, ate: 38, dur: 0.42, vol: 0.75 });
      tom(t, { freq: 1100, ate: 220, dur: 0.16, forma: 'triangle', vol: 0.1 });
      tom(t + 0.05, { freq: 260, ate: 90, dur: 0.3, forma: 'triangle', vol: 0.12 });
    },
    // Algo deu errado (senha ou código incorretos): dois toques secos descendo
    erro(t) {
      tom(t, { freq: 330, ate: 300, dur: 0.08, forma: 'square', vol: 0.07 });
      tom(t + 0.11, { freq: 250, ate: 220, dur: 0.12, forma: 'square', vol: 0.07 });
    },
  };

  /** Congela um parâmetro no valor atual, para mudar de rumo sem salto. */
  function congelar(param, t) {
    if (param.cancelAndHoldAtTime) param.cancelAndHoldAtTime(t);
    else { const v = param.value; param.cancelScheduledValues(t); param.setValueAtTime(v, t); }
  }

  /**
   * Som contínuo de "pressione e segure" (usado pelo HoldButton): um zumbido grave
   * que sobe de tom, abre o filtro e treme mais rápido conforme o botão enche,
   * acompanhando a onda do preenchimento.
   *   const s = Sons.segurar(); s.progresso(0..1); s.cancelar() | s.concluir();
   * Cancelar faz o som "murchar" (desce e some em ~0,2 s, como o preenchimento
   * voltando); concluir corta o zumbido e toca a trava "confirmado".
   */
  function segurar() {
    const nada = { progresso() {}, cancelar() {}, concluir() {} };
    if (!ligado) return nada;
    try { if (!audio()) return nada; } catch { return nada; }
    const t0 = ctx.currentTime;
    const volume = ctx.createGain(), filtro = ctx.createBiquadFilter();
    const base = ctx.createOscillator(), sub = ctx.createOscillator(), lfo = ctx.createOscillator(), lfoGanho = ctx.createGain();
    volume.gain.setValueAtTime(0.0001, t0);
    volume.gain.exponentialRampToValueAtTime(0.16, t0 + 0.06);
    filtro.type = 'lowpass'; filtro.frequency.setValueAtTime(480, t0); filtro.Q.value = 5;
    base.type = 'triangle'; base.frequency.setValueAtTime(90, t0);
    sub.type = 'sine'; sub.frequency.setValueAtTime(45, t0);
    lfo.frequency.setValueAtTime(5, t0); lfoGanho.gain.setValueAtTime(5, t0);
    lfo.connect(lfoGanho).connect(base.frequency);
    base.connect(filtro); sub.connect(filtro); filtro.connect(volume).connect(saida);
    [base, sub, lfo].forEach((o) => o.start(t0));
    let ativo = true;
    const parar = (t) => [base, sub, lfo].forEach((o) => { try { o.stop(t); } catch { /* já parado */ } });
    return {
      progresso(p) {
        if (!ativo) return;
        const t = ctx.currentTime, f = 90 * Math.pow(4, p); // 90 Hz → 360 Hz (duas oitavas)
        base.frequency.setTargetAtTime(f, t, 0.03);
        sub.frequency.setTargetAtTime(f / 2, t, 0.03);
        filtro.frequency.setTargetAtTime(480 + 2600 * p, t, 0.03);
        lfo.frequency.setTargetAtTime(5 + 9 * p, t, 0.05);
        lfoGanho.gain.setTargetAtTime(5 + 12 * p, t, 0.05);
        volume.gain.setTargetAtTime(0.16 + 0.1 * p, t, 0.05);
      },
      cancelar() {
        if (!ativo) return;
        ativo = false;
        const t = ctx.currentTime;
        [base.frequency, sub.frequency, volume.gain, filtro.frequency].forEach((x) => congelar(x, t));
        base.frequency.setTargetAtTime(55, t, 0.06);
        sub.frequency.setTargetAtTime(28, t, 0.06);
        filtro.frequency.setTargetAtTime(300, t, 0.06);
        volume.gain.setTargetAtTime(0.0001, t, 0.06);
        parar(t + 0.4);
      },
      concluir() {
        if (!ativo) return;
        ativo = false;
        const t = ctx.currentTime;
        congelar(volume.gain, t);
        volume.gain.setTargetAtTime(0.0001, t, 0.012);
        parar(t + 0.15);
        ultimoSom = 0; // a trava toca mesmo logo depois de outro som
        tocar('confirmado');
      },
    };
  }

  function tocar(tipo = 'clique') {
    if (!ligado || !SONS[tipo]) return;
    const agora = performance.now();
    if (agora - ultimoSom < 35) return; // dois disparos do mesmo gesto
    ultimoSom = agora;
    try {
      if (!audio()) return;
      SONS[tipo](ctx.currentTime + 0.005);
    } catch { /* áudio indisponível: segue sem som */ }
  }

  /** Qual elemento foi clicado (ou null se não é um controle). */
  function alvoDe(el) {
    const alvo = el instanceof Element ? el.closest(ALVOS) : null;
    if (!alvo || alvo.closest('[data-sem-som]')) return null;
    if (alvo.disabled || alvo.getAttribute('aria-disabled') === 'true') return null;
    if (alvo.matches('label.check') && !alvo.querySelector('input')) return null;
    return alvo;
  }

  function tipoDe(alvo) {
    if (alvo.matches('.perigo, .sair, [data-som="perigo"]')) return 'perigo';
    if (alvo.dataset.som && SONS[alvo.dataset.som]) return alvo.dataset.som;
    if (alvo.matches('input[type="checkbox"], input[type="radio"], label.check, [role="radio"], [role="switch"], [role="tab"], [role="menuitemcheckbox"], summary')) return 'alternar';
    if (alvo.matches('.primario, [type="submit"]')) return 'primario';
    return 'clique';
  }

  document.addEventListener('pointerdown', (e) => {
    if (e.pointerType === 'touch' || e.button !== 0) return;
    const alvo = alvoDe(e.target);
    if (!alvo) return;
    ultimoPonteiro = performance.now();
    tocar(tipoDe(alvo));
  }, true);

  document.addEventListener('click', (e) => {
    if (!e.isTrusted) return; // cliques feitos pelo próprio código não tocam
    const agora = performance.now();
    // o mesmo gesto do mouse já tocou no pointerdown; um rótulo dispara um
    // segundo clique na caixa de seleção — nenhum dos dois toca de novo
    if (agora - ultimoPonteiro < 1000 || agora - ultimoClique < 80) { ultimoClique = agora; return; }
    ultimoClique = agora;
    const alvo = alvoDe(e.target);
    if (alvo) tocar(tipoDe(alvo));
  }, true);

  // ---- Liga/desliga -------------------------------------------------------
  function atualizarControles() {
    document.querySelectorAll('[data-sons-alternar]').forEach((b) => {
      if (/^menuitem(checkbox|radio)$|^switch$/.test(b.getAttribute('role') || '')) b.setAttribute('aria-checked', String(ligado));
      else b.setAttribute('aria-pressed', String(ligado));
      b.title = ligado ? 'Desligar os sons dos cliques' : 'Ligar os sons dos cliques';
    });
    document.querySelectorAll('[data-sons-rotulo]').forEach((r) => { r.textContent = ligado ? 'Ligados' : 'Desligados'; });
  }

  function definir(valor) {
    ligado = !!valor;
    gravarPreferencia();
    atualizarControles();
    document.dispatchEvent(new CustomEvent('sons:mudou', { detail: { ligado } }));
  }

  document.addEventListener('click', (e) => {
    const b = e.target instanceof Element && e.target.closest('[data-sons-alternar]');
    if (!b) return;
    e.preventDefault();
    const religando = !ligado;
    definir(!ligado);
    if (religando) tocar('alternar'); // confirma que voltou
  });

  // outra aba mudou a preferência
  window.addEventListener('storage', (e) => { if (e.key === CHAVE) { ligado = lerPreferencia(); atualizarControles(); } });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', atualizarControles);
  else atualizarControles();

  window.Sons = { tocar, segurar, ligado: () => ligado, definir, alternar: () => definir(!ligado) };
})();
