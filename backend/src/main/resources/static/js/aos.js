/* =====================================================================
 * Animate On Scroll — mesmo modelo da biblioteca AOS (data-aos="fade-up", data-aos-delay="200"...),
 * implementado aqui sem dependências externas: a página funciona mesmo sem internet.
 *
 *   data-aos           fade-up | fade-down | fade-left | fade-right | zoom-in | zoom-out | flip-up
 *   data-aos-delay     atraso em ms (ex.: 150)
 *   data-aos-duration  duração em ms (padrão 800)
 *   data-aos-once      "false" = anima de novo sempre que o elemento volta à tela
 *   data-contador      número que conta de 0 até o valor quando aparece (ex.: data-contador="128")
 *
 * Quem prefere menos movimento (configuração do sistema) vê tudo sem animação.
 * Sem JavaScript, nada fica escondido: o CSS só esconde depois que este script marca a página.
 * ===================================================================== */
(function () {
  const elementos = document.querySelectorAll('[data-aos]');
  const contadores = document.querySelectorAll('[data-contador]');
  const semMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function contar(el) {
    if (el.dataset.contou) return;
    el.dataset.contou = '1';
    const alvo = Number(el.dataset.contador);
    const prefixo = el.dataset.prefixo || '';
    const sufixo = el.dataset.sufixo || '';
    if (semMovimento) { el.textContent = prefixo + alvo.toLocaleString('pt-BR') + sufixo; return; }
    const inicio = performance.now();
    const duracao = Number(el.dataset.duracao || 1400);
    const passo = (agora) => {
      const t = Math.min(1, (agora - inicio) / duracao);
      const suave = 1 - Math.pow(1 - t, 3);
      el.textContent = prefixo + Math.round(alvo * suave).toLocaleString('pt-BR') + sufixo;
      if (t < 1) requestAnimationFrame(passo);
    };
    requestAnimationFrame(passo);
  }

  if (semMovimento || !('IntersectionObserver' in window)) {
    elementos.forEach((el) => el.classList.add('aos-animate'));
    contadores.forEach(contar);
    return;
  }

  document.documentElement.classList.add('aos-pronto');
  elementos.forEach((el) => {
    if (el.dataset.aosDelay) el.style.transitionDelay = el.dataset.aosDelay + 'ms';
    if (el.dataset.aosDuration) el.style.transitionDuration = el.dataset.aosDuration + 'ms';
  });

  const observador = new IntersectionObserver((entradas) => {
    entradas.forEach((entrada) => {
      const el = entrada.target;
      if (entrada.isIntersecting) {
        el.classList.add('aos-animate');
        el.querySelectorAll('[data-contador]').forEach(contar);
        if (el.matches('[data-contador]')) contar(el);
        if (el.dataset.aosOnce !== 'false') observador.unobserve(el);
      } else if (el.dataset.aosOnce === 'false') {
        el.classList.remove('aos-animate');
      }
    });
  }, { threshold: 0.15, rootMargin: '0px 0px -60px 0px' });

  elementos.forEach((el) => observador.observe(el));
  // Contadores que não estão dentro de um elemento animado
  contadores.forEach((el) => { if (!el.closest('[data-aos]')) observador.observe(el); });

  // Barra de progresso da leitura e topo que escurece ao rolar
  const barra = document.getElementById('progresso');
  const topo = document.querySelector('.ap-topo');
  const aoRolar = () => {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    if (barra) barra.style.transform = `scaleX(${max > 0 ? window.scrollY / max : 0})`;
    if (topo) topo.classList.toggle('rolou', window.scrollY > 20);
  };
  window.addEventListener('scroll', aoRolar, { passive: true });
  aoRolar();
})();
