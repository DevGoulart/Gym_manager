/* =====================================================================
 * CardSwap em JavaScript puro (adaptado do componente React CardSwap).
 * Uma pilha de cartões em 3D que troca sozinha: o cartão da frente sai
 * por baixo e volta para o fundo da pilha, com física de mola.
 *   - arraste o cartão da frente para jogá-lo para fora;
 *   - clique num cartão do fundo para trazê-lo para a frente;
 *   - setas do teclado avançam/voltam; mouse em cima pausa a troca.
 * Uso:
 *   <div class="card-swap" data-largura="520" data-altura="360">
 *     <div class="card-swap-card">...conteúdo...</div>  (um por cartão)
 *   </div>
 * ===================================================================== */
(function () {
  const PERSPECTIVA = 900;
  const TAU = Math.PI * 2;
  const PASSO = 1 / 240;
  const MARGEM = 32;
  const EIXOS = ['x', 'y', 'z', 'r', 'p'];
  const BAIXO = { x: 0, y: 1 };
  const limitar = (v, min, max) => Math.min(max, Math.max(min, v));

  // Caixa ocupada pela pilha inteira (para o palco caber no espaço disponível)
  function limitesDaPilha({ larg, alt, dx, dy, dz, inclinacao, visiveis, abrir }) {
    const tg = Math.tan((inclinacao * Math.PI) / 180), afast = 1 + Math.max(0, abrir);
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (let i = 0; i < visiveis; i++) {
      const escala = PERSPECTIVA / (PERSPECTIVA + i * dz);
      for (const u of [-larg / 2, larg / 2]) {
        for (const v of [-alt / 2, alt / 2]) {
          const x = (i * dx * afast + u) * escala, y = (-i * dy * afast + v + u * tg) * escala;
          minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
      }
    }
    return { larg: Math.ceil(maxX - minX + MARGEM * 2), alt: Math.ceil(maxY - minY + MARGEM * 2), ox: MARGEM - minX, oy: MARGEM - minY };
  }

  function criarCardSwap(raiz) {
    const num = (nome, padrao) => (raiz.dataset[nome] !== undefined ? Number(raiz.dataset[nome]) : padrao);
    const cartoes = [...raiz.querySelectorAll(':scope > .card-swap-card')];
    const total = cartoes.length;
    if (!total) return;
    const s = {
      larg: num('largura', 460), alt: num('altura', 340), dx: num('distancia', 60), dy: num('distanciaVertical', 70),
      dz: num('profundidade', 90), inclinacao: num('inclinacao', 6), visiveis: limitar(num('visiveis', 4), 1, total),
      atraso: num('atraso', 4000), velocidade: limitar(num('velocidade', 1), 0.1, 4), quique: limitar(num('quique', 0.35), 0, 1),
      paralaxe: limitar(num('paralaxe', 0.5), 0, 1), abrir: num('abrir', 0.12), escurecer: limitar(num('escurecer', 0.5), 0, 1),
      raio: num('raio', 16), arrastavel: raiz.dataset.arrastavel !== 'false', automatico: raiz.dataset.automatico !== 'false',
    };
    s.limites = limitesDaPilha(s);
    const reduz = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Monta a estrutura: medidor (dá a altura), palco 3D e um "slot" por cartão
    raiz.style.setProperty('--swap-w', s.larg + 'px');
    raiz.style.setProperty('--swap-h', s.alt + 'px');
    raiz.style.setProperty('--swap-ox', s.limites.ox + 'px');
    raiz.style.setProperty('--swap-oy', s.limites.oy + 'px');
    raiz.style.setProperty('--swap-stage-w', s.limites.larg + 'px');
    raiz.style.setProperty('--swap-stage-h', s.limites.alt + 'px');
    raiz.style.setProperty('--swap-ratio', `${s.limites.larg} / ${s.limites.alt}`);
    raiz.style.setProperty('--swap-radius', s.raio + 'px');
    if (!raiz.hasAttribute('tabindex')) raiz.tabIndex = 0;
    raiz.setAttribute('role', 'region');
    raiz.setAttribute('aria-roledescription', 'carrossel');
    const medidor = document.createElement('div');
    medidor.className = 'card-swap-sizer'; medidor.setAttribute('aria-hidden', 'true'); medidor.append(document.createElement('span'));
    const palco = document.createElement('div');
    palco.className = 'card-swap-stage';
    const aviso = document.createElement('p');
    aviso.className = 'card-swap-aviso'; aviso.setAttribute('aria-live', 'polite');

    const pilha = cartoes.map((cartao, indice) => {
      const slot = document.createElement('div');
      slot.className = 'card-swap-slot'; slot.dataset.swapIndex = indice;
      const borda = document.createElement('span'); borda.className = 'card-swap-card-rim'; borda.setAttribute('aria-hidden', 'true');
      cartao.append(borda);
      const sombra = document.createElement('span'); sombra.className = 'card-swap-shade'; sombra.setAttribute('aria-hidden', 'true');
      slot.append(cartao, sombra);
      palco.append(slot);
      return {
        el: slot, sombra, indice, pos: indice, de: indice, segura: 0, empurra: 0, fase: 'parado', dir: { x: 0, y: 1 }, giro: 0,
        topo: 0, embaixo: false, x: 0, y: 0, z: 0, r: 0, p: 0, v: { x: 0, y: 0, z: 0, r: 0, p: 0 },
      };
    });
    raiz.append(medidor, palco, aviso);

    const e = {
      ordem: pilha.map((c) => c.indice), raf: 0, ultimo: 0, timer: 0, fila: [], camadas: 0, escala: 1, sobre: false, sobreCartao: false,
      naTela: true, arraste: null, abrir: 0, abrirV: 0, px: 0, py: 0, pvx: 0, pvy: 0, tx: 0, ty: 0, arrastou: false, focado: false,
    };

    const posicao = (p) => {
      const q = Math.min(p, s.visiveis), af = 1 + e.abrir;
      return { x: q * s.dx * af, y: -q * s.dy * af, z: -q * s.dz, r: 0, p: q };
    };
    const folga = (dir) => Math.abs(dir.x) * s.larg + Math.abs(dir.y) * s.alt + 12;

    const alvoDe = (c, t) => {
      if (c.fase === 'sai') {
        const b = posicao(0), alcance = folga(c.dir) * 1.3;
        return { x: b.x + c.dir.x * alcance, y: b.y + c.dir.y * alcance, z: b.z, r: c.giro, p: 0 };
      }
      if (c.fase === 'entra') { const b = posicao(0); return { x: b.x, y: b.y + folga(BAIXO) * 1.3, z: b.z, r: 0, p: c.p }; }
      return posicao(Math.max(0, (t < c.segura ? c.de : c.pos) + c.empurra));
    };
    const camadaDe = (c) => (c.topo ? 1000 + c.topo : c.embaixo ? 1 : 10 + (total - c.pos) * 2);

    function escrever(c) {
      const el = c.el;
      el.style.transform = `translate3d(${c.x.toFixed(2)}px, ${c.y.toFixed(2)}px, ${c.z.toFixed(2)}px) rotate(${c.r.toFixed(3)}deg) skewY(${s.inclinacao}deg)`;
      el.style.zIndex = String(camadaDe(c));
      const some = limitar(s.visiveis - c.p, 0, 1);
      el.style.opacity = some < 1 ? some.toFixed(3) : '';
      el.style.visibility = some <= 0.001 ? 'hidden' : '';
      const fundo = limitar(c.p / Math.max(1, s.visiveis - 1), 0, 1);
      c.sombra.style.opacity = (s.escurecer * 0.62 * fundo).toFixed(3);
      el.style.cursor = c.fase === 'arrasta' ? 'grabbing' : c.pos > 0 ? 'pointer' : s.arrastavel ? 'grab' : '';
      el.toggleAttribute('data-front', c.pos === 0);
      el.setAttribute('aria-hidden', String(c.pos !== 0));
    }
    const escreverPalco = () => {
      palco.style.perspectiveOrigin = `${(s.limites.ox + e.px).toFixed(2)}px ${(s.limites.oy + e.py).toFixed(2)}px`;
    };

    function integrar(c, alvo, omega, zeta, dt) {
      const k = omega * omega, d = 2 * zeta * omega, n = Math.max(1, Math.ceil(dt / PASSO)), h = dt / n;
      for (let i = 0; i < n; i++) for (const ex of EIXOS) { c.v[ex] += (k * (alvo[ex] - c[ex]) - d * c.v[ex]) * h; c[ex] += c.v[ex] * h; }
    }
    function mola(valor, vel, alvo, omega, zeta, dt) {
      const k = omega * omega, d = 2 * zeta * omega, n = Math.max(1, Math.ceil(dt / PASSO)), h = dt / n;
      let x = valor, v = vel;
      for (let i = 0; i < n; i++) { v += (k * (alvo - x) - d * v) * h; x += v * h; }
      return [x, v];
    }

    function quadro(agora) {
      e.raf = 0;
      const dt = e.ultimo ? Math.min(0.05, (agora - e.ultimo) / 1000) : 1 / 60;
      e.ultimo = agora;
      const omegaParado = TAU * 1.7 * s.velocidade, zetaParado = reduz ? 1 : 1 - 0.72 * s.quique, omegaSai = TAU * 1.9 * s.velocidade;
      let ocupado = false;
      const alvoAbrir = e.sobre && !reduz ? s.abrir : 0;
      [e.abrir, e.abrirV] = mola(e.abrir, e.abrirV, alvoAbrir, TAU * 1.4, 0.9, dt);
      [e.px, e.pvx] = mola(e.px, e.pvx, reduz ? 0 : e.tx, TAU * 1.1, 1, dt);
      [e.py, e.pvy] = mola(e.py, e.pvy, reduz ? 0 : e.ty, TAU * 1.1, 1, dt);
      if (Math.abs(e.abrir - alvoAbrir) > 0.0005 || Math.abs(e.abrirV) > 0.0005 || Math.abs(e.px - e.tx) > 0.05 ||
        Math.abs(e.py - e.ty) > 0.05 || Math.abs(e.pvx) + Math.abs(e.pvy) > 0.05) ocupado = true;
      escreverPalco();

      for (const c of pilha) {
        if (c.fase === 'arrasta') { ocupado = true; escrever(c); continue; }
        const alvo = alvoDe(c, agora), movendo = c.fase === 'sai' || c.fase === 'entra';
        integrar(c, alvo, movendo ? omegaSai : omegaParado, movendo ? 1 : zetaParado, dt);
        if (c.fase === 'sai') {
          const b = posicao(0), andou = (c.x - b.x) * c.dir.x + (c.y - b.y) * c.dir.y;
          if (andou >= folga(c.dir)) { c.fase = 'parado'; c.topo = 0; c.segura = 0; }
        } else if (c.fase === 'entra') {
          const b = posicao(0);
          if (c.y - b.y >= folga(BAIXO)) { c.fase = 'parado'; c.embaixo = false; c.segura = 0; }
        }
        if (c.segura > agora || c.fase !== 'parado') ocupado = true;
        for (const ex of EIXOS) {
          const lim = ex === 'p' ? 0.001 : 0.02;
          if (Math.abs(alvo[ex] - c[ex]) > lim || Math.abs(c.v[ex]) > lim) ocupado = true;
        }
        escrever(c);
      }
      if (ocupado) e.raf = requestAnimationFrame(quadro);
      else { e.ultimo = 0; for (const c of pilha) if (c.topo && c.fase === 'parado') c.topo = 0; }
    }
    const acordar = () => { if (!e.raf) e.raf = requestAnimationFrame(quadro); };

    const pausado = () => !s.automatico || reduz || total < 2 || e.sobreCartao || e.focado || e.arraste || !e.naTela || document.visibilityState === 'hidden';
    function agendar() {
      clearTimeout(e.timer); e.timer = 0;
      if (pausado()) return;
      e.timer = setTimeout(() => { e.timer = 0; trocar(1); }, Math.max(400, s.atraso));
    }

    function reordenar(t, escalonar) {
      e.ordem.forEach((indice, p) => {
        const c = pilha[indice];
        if (c.pos === p) return;
        c.de = c.pos; c.pos = p; c.segura = t + Math.max(0, p) * escalonar;
      });
    }

    function anunciar(falar) {
      const frente = cartoes[e.ordem[0]];
      if (falar) aviso.textContent = frente.dataset.titulo || frente.querySelector('img')?.alt || '';
      raiz.dispatchEvent(new CustomEvent('cardswap:troca', { detail: { indice: e.ordem[0] } }));
    }

    function trocar(direcao, op = {}) {
      if (total < 2) return;
      const t = performance.now(), escalonar = (0.05 / s.velocidade) * 1000;
      if (reduz) {
        // Sem animação: só reordena
        e.ordem = direcao > 0 ? [...e.ordem.slice(1), e.ordem[0]] : [e.ordem[total - 1], ...e.ordem.slice(0, total - 1)];
        e.ordem.forEach((indice, p) => { const c = pilha[indice]; c.pos = p; c.de = p; Object.assign(c, posicao(p)); escrever(c); });
        anunciar(op.usuario); agendar(); return;
      }
      if (direcao > 0) {
        const frente = pilha[e.ordem[0]];
        e.ordem = [...e.ordem.slice(1), frente.indice];
        reordenar(t, escalonar);
        Object.assign(frente, { fase: 'sai', dir: op.dir ?? BAIXO, giro: op.giro ?? 0, topo: ++e.camadas, embaixo: false, empurra: 0, segura: 0 });
        if (op.velocidade) { frente.v.x = op.velocidade.x; frente.v.y = op.velocidade.y; }
      } else {
        const fundo = pilha[e.ordem[total - 1]];
        e.ordem = [fundo.indice, ...e.ordem.slice(0, total - 1)];
        reordenar(t, escalonar);
        Object.assign(fundo, { fase: 'entra', topo: 0, embaixo: true, empurra: 0, segura: 0 });
      }
      pilha.forEach((c) => { if (c.fase === 'parado') c.empurra = 0; });
      anunciar(op.usuario); acordar(); agendar();
    }

    function irPara(p) {
      e.fila.forEach(clearTimeout); e.fila = [];
      const intervalo = 110 / s.velocidade;
      for (let i = 0; i < p; i++) e.fila.push(setTimeout(() => trocar(1, { usuario: true }), i * intervalo));
    }

    function ajustar() {
      const w = raiz.clientWidth, h = raiz.clientHeight;
      const escala = Math.min(1, w / s.limites.larg, h > 0 ? h / s.limites.alt : 1);
      e.escala = escala > 0 ? escala : 1;
      palco.style.transform = `translate(-50%, -50%) scale(${e.escala.toFixed(4)})`;
    }

    const local = (ev) => { const b = raiz.getBoundingClientRect(); return { x: ev.clientX - b.left, y: ev.clientY - b.top, w: b.width, h: b.height }; };

    raiz.addEventListener('pointermove', (ev) => {
      if (ev.pointerType !== 'touch') {
        const p = local(ev);
        e.tx = limitar((p.x / Math.max(1, p.w)) * 2 - 1, -1, 1) * s.paralaxe * 0.35 * s.limites.larg;
        e.ty = limitar((p.y / Math.max(1, p.h)) * 2 - 1, -1, 1) * s.paralaxe * 0.35 * s.limites.alt;
        e.sobre = true;
        const sobreCartao = Boolean(ev.target.closest?.('[data-swap-index]'));
        if (sobreCartao !== e.sobreCartao) { e.sobreCartao = sobreCartao; agendar(); }
        acordar();
      }
      const a = e.arraste;
      if (!a || a.id !== ev.pointerId) return;
      const dx = (ev.clientX - a.x0) / e.escala, dy = (ev.clientY - a.y0) / e.escala;
      if (!a.ativo) {
        if (Math.hypot(dx, dy) < 5) return;
        a.ativo = true; e.arrastou = true;
        const c = pilha[a.indice];
        c.fase = 'arrasta'; c.v = { x: 0, y: 0, z: 0, r: 0, p: 0 };
        raiz.setPointerCapture?.(ev.pointerId);
        raiz.style.cursor = 'grabbing';
      }
      const c = pilha[a.indice], b = posicao(0);
      c.x = b.x + dx; c.y = b.y + dy; c.z = b.z; c.r = limitar(dx * 0.035, -9, 9); c.p = 0;
      const t = performance.now();
      a.amostras.push({ x: c.x, y: c.y, t });
      while (a.amostras.length > 2 && t - a.amostras[0].t > 90) a.amostras.shift();
      const progresso = limitar(Math.hypot(dx, dy) / (Math.min(s.larg, s.alt) * 0.3), 0, 1);
      const proximo = pilha[e.ordem[1]];
      if (proximo && proximo.fase === 'parado') proximo.empurra = -0.22 * progresso;
      acordar();
    });

    raiz.addEventListener('pointerleave', (ev) => {
      if (ev.pointerType === 'touch' || e.arraste?.ativo) return;
      e.sobre = false; e.sobreCartao = false; e.tx = 0; e.ty = 0;
      agendar(); acordar();
    });

    raiz.addEventListener('pointerdown', (ev) => {
      e.arrastou = false;
      if (!s.arrastavel || ev.button > 0 || total < 2) return;
      const alvo = ev.target.closest?.('[data-swap-index]');
      if (!alvo || !raiz.contains(alvo)) return;
      const indice = Number(alvo.dataset.swapIndex);
      if (indice !== e.ordem[0] || pilha[indice].fase !== 'parado') return;
      e.arraste = { id: ev.pointerId, indice, x0: ev.clientX, y0: ev.clientY, ativo: false, amostras: [] };
      agendar();
    });

    const soltar = (ev) => {
      const a = e.arraste;
      if (!a || a.id !== ev.pointerId) return;
      e.arraste = null; raiz.style.cursor = '';
      if (!a.ativo) { agendar(); return; }
      raiz.releasePointerCapture?.(ev.pointerId);
      const c = pilha[a.indice], b = posicao(0);
      const pri = a.amostras[0], ult = a.amostras[a.amostras.length - 1];
      const span = pri && ult ? Math.max(0.016, (ult.t - pri.t) / 1000) : 1;
      const vel = pri && ult && ult !== pri ? { x: (ult.x - pri.x) / span, y: (ult.y - pri.y) / span } : { x: 0, y: 0 };
      const off = { x: c.x - b.x, y: c.y - b.y }, dist = Math.hypot(off.x, off.y), lance = Math.hypot(vel.x, vel.y);
      const proximo = pilha[e.ordem[1]];
      if (proximo) proximo.empurra = 0;
      if (ev.type !== 'pointercancel' && (dist > Math.min(s.larg, s.alt) * 0.3 || lance > 700)) {
        const mira = { x: off.x + vel.x * 0.12, y: off.y + vel.y * 0.12 }, tam = Math.hypot(mira.x, mira.y) || 1;
        c.fase = 'parado';
        trocar(1, { dir: { x: mira.x / tam, y: mira.y / tam }, giro: c.r, velocidade: vel, usuario: true });
      } else {
        c.fase = 'parado'; c.v.x = vel.x; c.v.y = vel.y;
        acordar(); agendar();
      }
    };
    raiz.addEventListener('pointerup', soltar);
    raiz.addEventListener('pointercancel', soltar);

    raiz.addEventListener('click', (ev) => {
      if (e.arrastou) { e.arrastou = false; return; }
      const alvo = ev.target.closest?.('[data-swap-index]');
      if (!alvo || !raiz.contains(alvo)) return;
      const p = e.ordem.indexOf(Number(alvo.dataset.swapIndex));
      if (p > 0) irPara(p);
    });

    raiz.addEventListener('keydown', (ev) => {
      if (ev.key === 'ArrowRight' || ev.key === 'ArrowDown') { ev.preventDefault(); trocar(1, { usuario: true }); }
      else if (ev.key === 'ArrowLeft' || ev.key === 'ArrowUp') { ev.preventDefault(); trocar(-1, { usuario: true }); }
    });
    // Teclado dentro da pilha: pausa a troca automática enquanto tem foco
    raiz.addEventListener('focus', () => { e.focado = true; agendar(); });
    raiz.addEventListener('blur', () => { e.focado = false; agendar(); });

    // Entrada: os cartões sobem para os lugares um depois do outro
    const agora = performance.now();
    if (!reduz) {
      pilha.forEach((c) => { c.de = 0; c.segura = agora + (c.pos * 70) / s.velocidade; });
      palco.animate?.([{ opacity: 0 }, { opacity: 1 }], { duration: 420, easing: 'ease-out' });
    } else pilha.forEach((c) => Object.assign(c, posicao(c.pos)));
    ajustar(); escreverPalco(); pilha.forEach(escrever); anunciar(); acordar();

    new ResizeObserver(ajustar).observe(raiz);
    new IntersectionObserver((en) => { e.naTela = en[en.length - 1]?.isIntersecting ?? true; agendar(); }).observe(raiz);
    document.addEventListener('visibilitychange', agendar);
    agendar();
    raiz.cardSwap = { proximo: () => trocar(1), anterior: () => trocar(-1) };
  }

  window.criarCardSwaps = (raiz = document) => raiz.querySelectorAll('.card-swap').forEach((el) => { if (!el.cardSwap) criarCardSwap(el); });
  if (document.readyState !== 'loading') window.criarCardSwaps();
  else document.addEventListener('DOMContentLoaded', () => window.criarCardSwaps());
})();
