/* =====================================================================
 * ScrollExpand em JavaScript puro (adaptado do componente React
 * ScrollExpand, no modo useWindowScroll). Uma imagem num quadro de cantos
 * arredondados cresce até ocupar a tela inteira conforme a página rola:
 *   - o palco fica "preso" (sticky) enquanto a trilha passa;
 *   - o quadro abre de 42% × 58% até 100% (clip-path com cantos 24px → 0);
 *   - a imagem sai do zoom 1,35 para 1 e escurece aos poucos (scrim);
 *   - o título some subindo; no fim, o texto por cima aparece.
 * Mesmos valores padrão e mesmas curvas (smoothstep) do original.
 * Diferença para o site: o palco começa abaixo do cabeçalho fixo
 * (data-cabecalho), para o quadro aberto não ficar escondido atrás dele.
 * Estrutura esperada (classes do original):
 *   .scroll-expand[data-scroll-expand] > .scroll-expand__track > .scroll-expand__stage >
 *     .scroll-expand__frame (> .scroll-expand__media, __scrim, __overlay), __title, __hint
 * ===================================================================== */
(function () {
  const limitar = (v, a, b) => (v < a ? a : v > b ? b : v);
  const suave = (e0, e1, x) => { const t = limitar((x - e0) / (e1 - e0 || 1e-6), 0, 1); return t * t * (3 - 2 * t); };

  function criarScrollExpand(raiz) {
    const d = raiz.dataset;
    const num = (v, padrao) => (v !== undefined && v !== '' ? Number(v) : padrao);
    const c = {
      largInicial: num(d.larguraInicial, 42), altInicial: num(d.alturaInicial, 58),
      raioInicial: num(d.raioInicial, 24), raioFinal: num(d.raioFinal, 0),
      zoom: num(d.zoom, 1.35), distancia: num(d.distancia, 1.2), pausa: num(d.pausa, 0.35),
      suavidade: num(d.suavidade, 0.1), escurecer: num(d.escurecer, 0.45),
    };
    const $ = (sel) => raiz.querySelector(sel);
    const trilha = $('.scroll-expand__track'), palco = $('.scroll-expand__stage'), quadro = $('.scroll-expand__frame');
    const midia = $('.scroll-expand__media'), titulo = $('.scroll-expand__title'), sobre = $('.scroll-expand__overlay');
    const veu = $('.scroll-expand__scrim'), dica = $('.scroll-expand__hint');
    const cabecalho = d.cabecalho ? document.querySelector(d.cabecalho) : null;
    if (!trilha || !palco || !quadro || !midia) return;
    const reduz = matchMedia('(prefers-reduced-motion: reduce)').matches;

    function aplicar(p) {
      const e = suave(0, 1, p);
      const w = c.largInicial + (100 - c.largInicial) * e;
      const h = c.altInicial + (100 - c.altInicial) * e;
      const ix = Math.max(0, (100 - w) / 2), iy = Math.max(0, (100 - h) / 2);
      const r = c.raioInicial + (c.raioFinal - c.raioInicial) * e;
      quadro.style.clipPath = `inset(${iy}% ${ix}% ${iy}% ${ix}% round ${r}px)`;
      midia.style.transform = `scale(${c.zoom + (1 - c.zoom) * e})`;
      if (veu) veu.style.opacity = `${c.escurecer * e}`;
      if (titulo) {
        const sai = suave(0.4, 0.88, p);
        titulo.style.opacity = `${1 - sai}`;
        titulo.style.transform = `translate3d(0, ${-28 * sai}px, 0) scale(${1 + 0.06 * sai})`;
      }
      if (dica) {
        const some = suave(0, 0.12, p);
        dica.style.opacity = `${1 - some}`;
        dica.style.transform = `translate3d(0, ${8 * some}px, 0)`;
      }
      if (sobre) {
        const entra = suave(0.68, 1, p);
        sobre.style.opacity = `${entra}`;
        sobre.style.transform = `translate3d(0, ${18 * (1 - entra)}px, 0)`;
        // Enquanto invisível, o texto não recebe clique nem foco (o link não fica "fantasma")
        sobre.style.visibility = entra < 0.02 ? 'hidden' : '';
      }
    }

    let raf = 0, atual = 0, alvo = 0, altPalco = 0, topo = 0, rodando = false;

    function medir() {
      topo = cabecalho ? cabecalho.getBoundingClientRect().height : 0;
      altPalco = innerHeight - topo;
      if (altPalco <= 0) return;
      palco.style.top = `${topo}px`;
      palco.style.height = `${altPalco}px`;
      trilha.style.height = `${altPalco * (1 + Math.max(0, c.distancia) + Math.max(0, c.pausa))}px`;
      const w = raiz.clientWidth || altPalco;
      palco.style.setProperty('--se-title-size', `${limitar(w * 0.075, 20, 84)}px`);
    }

    const lerProgresso = () => {
      const vao = altPalco * Math.max(0.01, c.distancia);
      return limitar(-(trilha.getBoundingClientRect().top - topo) / vao, 0, 1);
    };

    function quadroAnim() {
      const k = c.suavidade <= 0 ? 1 : 1 - Math.exp(-1 / (60 * c.suavidade));
      atual += (alvo - atual) * k;
      if (Math.abs(alvo - atual) < 0.0004) { atual = alvo; rodando = false; }
      aplicar(atual);
      raf = rodando ? requestAnimationFrame(quadroAnim) : 0;
    }
    const chutar = () => { if (rodando) return; rodando = true; if (!raf) raf = requestAnimationFrame(quadroAnim); };

    const aoRolar = () => {
      alvo = lerProgresso();
      if (c.suavidade <= 0 || reduz) { atual = alvo; aplicar(atual); return; }
      chutar();
    };
    const aoRedimensionar = () => { medir(); alvo = lerProgresso(); atual = alvo; aplicar(atual); };

    medir();
    alvo = lerProgresso(); atual = alvo; aplicar(atual);
    addEventListener('scroll', aoRolar, { passive: true });
    addEventListener('resize', aoRedimensionar);
    new ResizeObserver(aoRedimensionar).observe(raiz);
  }

  const iniciar = () => document.querySelectorAll('[data-scroll-expand]').forEach((el) => { if (!el.dataset.pronto) { el.dataset.pronto = '1'; criarScrollExpand(el); } });
  if (document.readyState !== 'loading') iniciar(); else document.addEventListener('DOMContentLoaded', iniciar);
})();
