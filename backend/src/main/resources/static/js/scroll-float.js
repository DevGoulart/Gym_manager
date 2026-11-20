/* =====================================================================
 * ScrollFloat em JavaScript puro (efeito "float" do componente React
 * ScrollFloat). As letras sobem esticadas de baixo da linha e assentam
 * conforme a página rola — o movimento acompanha a rolagem (scrub), em
 * vez de tocar sozinho. Mesmos valores padrão do original:
 *   início "center bottom+=50%", fim "clamp(bottom bottom-=40%)",
 *   duração 1 por letra, atraso 0,03 entre letras, ease back.inOut(2),
 *   distância 120% e esticamento 2,3.
 * Uso: <h2 data-scroll-float>Texto</h2>
 *   Pode ter <span>s dentro (cada um com seu estilo); com
 *   data-scroll-float-linhas, cada filho direto vira uma linha que recorta
 *   as próprias letras.
 * Quem prefere menos movimento vê o texto parado, como no original.
 * Sem GSAP: a linha do tempo e a curva back.inOut são calculadas aqui,
 * assim a página continua funcionando sem internet.
 * ===================================================================== */
(function () {
  const limitar01 = (v) => Math.min(Math.max(v, 0), 1);

  // back.inOut(força) igual ao do GSAP
  const backInOut = (forca) => {
    const entrada = (p) => (p ? p * p * ((forca + 1) * p - forca) : 0);
    return (p) => (p < 0.5 ? entrada(p * 2) / 2 : 1 - entrada((1 - p) * 2) / 2);
  };

  // Separa o texto em palavras (que não quebram no meio) e letras, mantendo os elementos originais
  function separar(raiz) {
    const letras = [];
    const andar = (no) => {
      [...no.childNodes].forEach((filho) => {
        if (filho.nodeType === Node.ELEMENT_NODE) { andar(filho); return; }
        if (filho.nodeType !== Node.TEXT_NODE || !filho.textContent) return;
        const frag = document.createDocumentFragment();
        filho.textContent.split(/(\s+)/).forEach((parte) => {
          if (!parte) return;
          if (!parte.trim()) { frag.append(parte); return; }
          const palavra = document.createElement('span');
          palavra.className = 'scroll-float-word';
          Array.from(parte).forEach((ch) => {
            const letra = document.createElement('span');
            letra.className = 'scroll-float-char';
            letra.textContent = ch;
            palavra.append(letra);
            letras.push(letra);
          });
          frag.append(palavra);
        });
        filho.replaceWith(frag);
      });
    };
    andar(raiz);
    return letras;
  }

  function criarScrollFloat(el) {
    const d = el.dataset;
    const cfg = {
      duracao: Number(d.duracao || 1),
      atraso: Number(d.atraso || 0.03),
      distancia: Number(d.distancia || 120),
      esticar: Number(d.esticar || 2.3),
      inicioExtra: Number(d.inicio || 0.5), // "center bottom+=50%" → 50% da altura da janela
      fimRecuo: Number(d.fim || 0.4),       // "bottom bottom-=40%"
      direcao: d.direcao === 'baixo' ? -1 : 1,
    };
    const ease = backInOut(Number(d.forca || 2));
    // Em linhas separadas, junta cada linha com espaço ("Pronto para" + "começar?"), mantendo maiúsculas/minúsculas do HTML
    const texto = (d.scrollFloatLinhas !== undefined && el.children.length
      ? [...el.children].map((c) => c.textContent).join(' ')
      : el.textContent).replace(/\s+/g, ' ').trim();

    // Leitores de tela leem o texto inteiro uma vez (as letras soltas ficam escondidas)
    const leitor = document.createElement('span');
    leitor.className = 'scroll-float-sr';
    leitor.textContent = texto;
    const visual = document.createElement('span');
    visual.className = 'scroll-float-text';
    visual.setAttribute('aria-hidden', 'true');
    visual.append(...el.childNodes);
    el.append(leitor, visual);
    el.classList.add('scroll-float');
    if (d.scrollFloatLinhas !== undefined) [...visual.children].forEach((linha) => linha.classList.add('scroll-float-linha'));

    const letras = separar(visual);
    const n = letras.length;
    const total = cfg.duracao + cfg.atraso * Math.max(0, n - 1);
    const alcance = Math.max(0, cfg.distancia) / 120;
    const apertar = cfg.esticar > 0 ? cfg.esticar ** -0.43 : 1;
    const origem = cfg.direcao > 0 ? '50% 0%' : '50% 100%';
    letras.forEach((l) => { l.style.transformOrigin = origem; l.style.willChange = 'opacity, transform'; });

    let inicio = 0, fim = 1, ultimoTempo = -1, pedido = 0;

    // Equivalente ao refresh do ScrollTrigger: posições em pixels de rolagem
    function medir() {
      const r = el.getBoundingClientRect();
      const topo = r.top + scrollY, vh = innerHeight;
      const maxRolagem = Math.max(0, document.documentElement.scrollHeight - vh);
      inicio = topo + r.height / 2 - vh * (1 + cfg.inicioExtra);
      fim = Math.min(Math.max(topo + r.height - vh * (1 - cfg.fimRecuo), 0), maxRolagem); // clamp()
      if (fim <= inicio) fim = inicio + 1;
      ultimoTempo = -1;
    }

    function aplicar() {
      pedido = 0;
      const progresso = limitar01((scrollY - inicio) / (fim - inicio));
      const tempo = progresso * total;
      if (tempo === ultimoTempo) return;
      ultimoTempo = tempo;
      for (let i = 0; i < n; i++) {
        const t = limitar01((tempo - i * cfg.atraso) / cfg.duracao);
        const e = ease(t);
        const s = letras[i].style;
        // de: opacidade 0, 120% abaixo, esticada → para: tudo no lugar (o back.inOut passa um pouco do ponto)
        s.opacity = String(e);
        s.transform = `translateY(${(120 * alcance * cfg.direcao * (1 - e)).toFixed(3)}%) scale(${(apertar + (1 - apertar) * e).toFixed(4)}, ${(cfg.esticar + (1 - cfg.esticar) * e).toFixed(4)})`;
      }
    }
    const agendar = () => { if (!pedido) pedido = requestAnimationFrame(aplicar); };

    medir(); aplicar();
    addEventListener('scroll', agendar, { passive: true });
    let espera = 0;
    const remedir = () => { clearTimeout(espera); espera = setTimeout(() => { medir(); aplicar(); }, 120); };
    addEventListener('resize', remedir);
    addEventListener('load', remedir);
    document.fonts?.ready.then(remedir);
    new ResizeObserver(remedir).observe(document.body);
  }

  function iniciar() {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return; // texto parado, como no original
    document.querySelectorAll('[data-scroll-float]').forEach((el) => { if (!el.classList.contains('scroll-float')) criarScrollFloat(el); });
  }
  if (document.readyState !== 'loading') iniciar(); else document.addEventListener('DOMContentLoaded', iniciar);
})();
