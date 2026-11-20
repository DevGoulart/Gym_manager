/* =====================================================================
 * Faixa de texto rolando (ticker) sem buracos, em qualquer largura de tela.
 *
 * O trilho recebe só UMA vez as frases. O script repete as frases até um grupo ficar mais largo que a
 * tela e coloca dois grupos iguais lado a lado. A animação anda exatamente um grupo (-50%) e recomeça
 * do início: como os dois grupos são idênticos, a emenda é invisível e a rolagem fica contínua.
 * A duração é calculada pela largura, então a velocidade é a mesma em qualquer tela.
 * ===================================================================== */
(function () {
  const VELOCIDADE = 70; // pixels por segundo

  function montar(trilho) {
    if (!trilho.dataset.base) trilho.dataset.base = trilho.innerHTML;
    const base = trilho.dataset.base;
    const area = trilho.parentElement.clientWidth || window.innerWidth;

    // Mede uma cópia das frases
    trilho.innerHTML = `<div class="ticker-grupo">${base}</div>`;
    const larguraBase = trilho.firstElementChild.scrollWidth || 1;
    const vezes = Math.max(1, Math.ceil(area / larguraBase) + 1);

    const grupo = `<div class="ticker-grupo">${base.repeat(vezes)}</div>`;
    trilho.innerHTML = grupo + grupo.replace('class="ticker-grupo"', 'class="ticker-grupo" aria-hidden="true"');
    const larguraGrupo = trilho.firstElementChild.scrollWidth;
    trilho.style.animationDuration = `${(larguraGrupo / VELOCIDADE).toFixed(2)}s`;
  }

  /** Monta (ou remonta) as faixas dentro de "raiz". */
  window.montarTickers = function (raiz = document) {
    raiz.querySelectorAll('.ticker-trilho').forEach(montar);
  };

  // Ao mudar o tamanho da janela, ou quando as fontes terminam de carregar, as larguras mudam
  let espera;
  window.addEventListener('resize', () => {
    clearTimeout(espera);
    espera = setTimeout(() => window.montarTickers(), 200);
  });
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => window.montarTickers());
  if (document.readyState !== 'loading') window.montarTickers();
  else document.addEventListener('DOMContentLoaded', () => window.montarTickers());
})();
