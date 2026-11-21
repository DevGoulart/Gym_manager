/*
 * HoldButton — botão de "pressione e segure para confirmar"
 *
 * Porte em JavaScript puro do componente HoldButton (React Bits, versão JS-CSS),
 * com a mesma lógica: o preenchimento "líquido" sobe enquanto o botão está
 * pressionado e volta se a pessoa soltar antes do tempo. Funciona com mouse,
 * toque e teclado (Enter ou Espaço segurados; Esc cancela).
 *
 * Uso:
 *   const hb = HoldButton.montar(botao, { rotulo, rotuloFeito, tempo, aoSegurar, ... });
 *   hb.desativar(true|false); hb.reiniciar();
 *
 * Opções (equivalentes às props do original):
 *   rotulo / rotuloFeito         children / doneLabel
 *   icone / iconeFeito           icon / doneIcon (HTML)
 *   corFundo, corPreenchimento,  backgroundColor, fillColor,
 *   corTexto, corTextoPreenchido textColor, fillTextColor
 *   tamanho ('sm'|'md'|'lg'), raio, direcao ('right'|'up'), tempo (ms), tempoSoltar (ms),
 *   escalaPressao, onda, amplitudeOnda, brilho, reiniciarApos (ms, 0 = não volta sozinho),
 *   desativado, aoSegurar, aoTocar
 *   som (extra deste site): true toca o som de "segurar" de js/sons.js enquanto enche
 */
(function () {
  'use strict';

  const TAP_MS = 250;
  const HIT_PAD = 10;
  const LINEAR = (t) => t;
  const EASE_OUT = (t) => 1 - Math.pow(1 - t, 3);
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  let sequencia = 0;

  function montar(botao, opcoes = {}) {
    const o = {
      rotulo: 'Segure para excluir', rotuloFeito: 'Excluído', icone: null, iconeFeito: null,
      corFundo: '#27272a', corPreenchimento: '#5227FF', corTexto: '#f5f5f5', corTextoPreenchido: '#ffffff',
      tamanho: 'md', raio: 14, direcao: 'right', tempo: 2000, tempoSoltar: 200, escalaPressao: 0.97,
      onda: true, amplitudeOnda: 6, brilho: true, reiniciarApos: 1200, desativado: false,
      aoSegurar: null, aoTocar: null, classe: '', som: false,
      ...opcoes,
    };
    const holdTime = o.tempo, releaseTime = o.tempoSoltar;
    const hintId = 'hb-dica-' + (++sequencia);
    const direction = o.direcao === 'up' ? 'up' : 'right';

    // ---- Marcação (igual à do original)
    const rotulos = () => `
      <span class="hold-button__idle">${o.icone ? `<span class="hold-button__icon">${o.icone}</span>` : ''}${esc(o.rotulo)}</span>
      <span class="hold-button__done" aria-hidden="true">${o.iconeFeito ? `<span class="hold-button__icon">${o.iconeFeito}</span>` : ''}${esc(o.rotuloFeito)}</span>`;
    botao.type = 'button';
    botao.disabled = !!o.desativado;
    botao.className = `hold-button hold-button--${o.tamanho}${o.classe ? ' ' + o.classe : ''}`;
    botao.dataset.phase = 'idle';
    botao.dataset.direction = direction;
    if (o.brilho) botao.dataset.glow = 'true';
    botao.setAttribute('aria-describedby', hintId);
    const estilo = {
      '--hb-radius': typeof o.raio === 'number' ? `${o.raio}px` : o.raio,
      '--hb-bg': o.corFundo, '--hb-fill': o.corPreenchimento, '--hb-text': o.corTexto, '--hb-fill-text': o.corTextoPreenchido,
      '--hb-hold': `${holdTime}ms`, '--hb-cycles': holdTime / 1100, '--hb-release': `${releaseTime}ms`,
      '--hb-press': o.escalaPressao, '--hb-wave': `${o.onda ? o.amplitudeOnda : 0}px`,
    };
    for (const [k, v] of Object.entries(estilo)) botao.style.setProperty(k, v);
    const segundos = Math.round(holdTime / 100) / 10;
    botao.innerHTML = `
      <span class="hold-button__pulse" aria-hidden="true"></span>
      <span class="hold-button__label">${rotulos()}</span>
      <span class="hold-button__clip" aria-hidden="true">
        <span class="hold-button__fill"><span class="hold-button__label hold-button__label--fill">${rotulos()}</span></span>
        <span class="hold-button__crest" aria-hidden="true"><span class="hold-button__label hold-button__label--fill">${rotulos()}</span></span>
      </span>
      <span id="${hintId}" class="hold-button__sr">Pressione e segure por ${String(segundos).replace('.', ',')} segundo${segundos === 1 ? '' : 's'} para confirmar</span>`;
    const principal = botao.querySelector('.hold-button__label');

    // ---- Estado
    let phase = 'idle', input = null;
    const gesture = { pointerId: null, start: 0, rect: null };
    const timers = { complete: 0, reset: 0 };
    const motion = { raf: 0, p: 0, from: 0, to: 0, start: 0 };
    let som = null; // som contínuo do "segurar" (js/sons.js), se ligado

    const go = (next, kind = null) => {
      phase = next; input = kind;
      botao.dataset.phase = next;
      if (kind) botao.dataset.input = kind; else delete botao.dataset.input;
      principal.querySelector('.hold-button__idle').setAttribute('aria-hidden', String(next === 'done'));
      principal.querySelector('.hold-button__done').setAttribute('aria-hidden', String(next !== 'done'));
      if (next === 'holding') ligarCancelamento(); else desligarCancelamento();
    };

    const clearTimers = () => { clearTimeout(timers.complete); clearTimeout(timers.reset); };

    const drive = (to, duration, ease) => {
      cancelAnimationFrame(motion.raf);
      motion.from = motion.p; motion.to = to; motion.start = performance.now();
      const step = (now) => {
        const t = duration > 0 ? Math.min(1, (now - motion.start) / duration) : 1;
        motion.p = motion.from + (motion.to - motion.from) * ease(t);
        botao.style.setProperty('--hb-p', motion.p.toFixed(4));
        if (phase === 'holding') som?.progresso(motion.p);
        if (t < 1) { motion.raf = requestAnimationFrame(step); return; }
        motion.raf = 0;
        if (motion.to === 1) complete();
      };
      motion.raf = requestAnimationFrame(step);
    };

    const voltar = () => { go('idle'); drive(0, releaseTime, EASE_OUT); };

    function complete() {
      if (phase !== 'holding') return;
      if (performance.now() - gesture.start < holdTime - 50) return;
      clearTimers();
      som?.concluir(); som = null;
      go('done', input);
      o.aoSegurar?.();
      if (o.reiniciarApos > 0) timers.reset = setTimeout(voltar, o.reiniciarApos);
    }

    const begin = (kind) => {
      if (botao.disabled || phase !== 'idle') return false;
      gesture.start = performance.now();
      gesture.rect = botao.getBoundingClientRect();
      go('holding', kind);
      if (o.som) som = window.Sons?.segurar?.() || null;
      drive(1, holdTime, LINEAR);
      timers.complete = setTimeout(complete, holdTime + 100);
      return true;
    };

    const release = ({ drifted = false } = {}) => {
      if (phase !== 'holding') return;
      clearTimers();
      const held = performance.now() - gesture.start;
      som?.cancelar(); som = null;
      voltar();
      if (!drifted && held < TAP_MS) o.aoTocar?.();
    };

    // Cancela se a janela perder o foco ou a aba ficar escondida durante o "segurar"
    const cancel = () => release({ drifted: true });
    const onVisibility = () => { if (document.hidden) cancel(); };
    function ligarCancelamento() { window.addEventListener('blur', cancel); document.addEventListener('visibilitychange', onVisibility); }
    function desligarCancelamento() { window.removeEventListener('blur', cancel); document.removeEventListener('visibilitychange', onVisibility); }

    // ---- Ponteiro
    const endPointer = (e, options) => {
      if (e.pointerId !== gesture.pointerId) return;
      gesture.pointerId = null;
      try { if (botao.hasPointerCapture(e.pointerId)) botao.releasePointerCapture(e.pointerId); } catch { /* já liberado */ }
      release(options);
    };
    botao.addEventListener('pointerdown', (e) => {
      if (e.button !== 0 || !e.isPrimary || gesture.pointerId !== null) return;
      if (!begin('pointer')) return;
      gesture.pointerId = e.pointerId;
      try { botao.setPointerCapture(e.pointerId); } catch { /* sem captura */ }
    });
    botao.addEventListener('pointermove', (e) => {
      if (e.pointerId !== gesture.pointerId || !gesture.rect) return;
      const r = gesture.rect;
      const out = e.clientX < r.left - HIT_PAD || e.clientX > r.right + HIT_PAD || e.clientY < r.top - HIT_PAD || e.clientY > r.bottom + HIT_PAD;
      if (out) endPointer(e, { drifted: true });
    });
    botao.addEventListener('pointerup', (e) => endPointer(e));
    botao.addEventListener('pointercancel', (e) => endPointer(e, { drifted: true }));
    botao.addEventListener('lostpointercapture', (e) => endPointer(e, { drifted: true }));
    botao.addEventListener('pointerleave', (e) => { if (e.pointerType !== 'touch') endPointer(e, { drifted: true }); });
    botao.addEventListener('contextmenu', (e) => e.preventDefault());

    // ---- Teclado
    botao.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { if (input === 'key') release({ drifted: true }); return; }
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); if (!e.repeat) begin('key'); }
    });
    botao.addEventListener('keyup', (e) => {
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); if (input === 'key') release(); }
    });

    // ---- Medidas para a onda
    const measure = () => {
      botao.style.setProperty('--hb-w', `${botao.offsetWidth}px`);
      botao.style.setProperty('--hb-h', `${botao.offsetHeight}px`);
    };
    measure();
    if ('ResizeObserver' in window) new ResizeObserver(measure).observe(botao);

    const api = {
      /** Liga ou desliga o botão (desligar no meio do "segurar" cancela). */
      desativar(valor) {
        if (valor && phase === 'holding') release({ drifted: true });
        botao.disabled = !!valor;
      },
      /** Volta ao estado inicial (por exemplo, depois de um erro na ação). */
      reiniciar() { clearTimers(); if (phase !== 'idle') voltar(); },
      get fase() { return phase; },
    };
    botao.holdButton = api;
    return api;
  }

  window.HoldButton = { montar };
})();
