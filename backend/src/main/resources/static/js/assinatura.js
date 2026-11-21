/*
 * Plano e cobrança + checkout — Força Total
 *
 * Telas da assinatura do sistema (carregado depois do app.js, usa os mesmos ajudantes):
 *   #/assinatura   situação do plano, uso x limites, vagas de unidade da rede,
 *                  comparação dos planos e histórico de pagamentos
 *   #/checkout     pedido (plano, cobrança, vagas) + pagamento por cartão, Pix ou boleto
 *                  ?plano=REDE&ciclo=ANUAL&extras=2 | ?tipo=unidades&quantidade=1 | ?pagamento=ID
 * O gateway é de DEMONSTRAÇÃO (nenhum valor é cobrado): o cartão é validado como num gateway real,
 * e Pix e boleto são "pagos" pelo botão de simular. Os preços sempre vêm do servidor.
 */

const RECURSOS_PLANO = {
  ESSENCIAL: ['Os 6 módulos completos', 'Até <b>150</b> alunos ativos', '<b>2</b> usuários (gerência e recepção)', 'Suporte por e-mail'],
  PROFISSIONAL: ['Tudo do Essencial', 'Até <b>600</b> alunos ativos', 'Usuários <b>ilimitados</b>', 'Suporte por WhatsApp'],
  REDE: ['Tudo do Profissional', 'Alunos <b>ilimitados</b>', '<b>5</b> unidades incluídas (até <b>20</b> com vagas extras)', '<b>Painel do dono</b> com todas as unidades'],
};
const SITUACOES_ASSINATURA = { TESTE: ['Teste grátis', 'b-info'], ATIVA: ['Ativa', 'b-ok'], VENCIDA: ['Vencida', 'b-danger'] };
const STATUS_PAGAMENTO = { APROVADO: ['Aprovado', 'b-ok'], PENDENTE: ['Aguardando', 'b-warn'], CANCELADO: ['Cancelado', 'b-mute'] };
const CHAVE_PLANO_ESCOLHIDO = 'forcatotal.planoEscolhido';
const cicloTexto = (c) => (c === 'ANUAL' ? 'anual' : 'mensal');
const unidadesTexto = (n) => `${n} ${n === 1 ? 'unidade' : 'unidades'}`;

/* ---------------------------------------------------------------------
 * Plano escolhido na página de apresentação (antes do cadastro/login)
 * ------------------------------------------------------------------- */
function lerPlanoEscolhido() {
  try { return JSON.parse(sessionStorage.getItem(CHAVE_PLANO_ESCOLHIDO) || 'null'); } catch { return null; }
}

/** Depois de entrar ou se cadastrar: se a pessoa escolheu um plano na apresentação, vai para o checkout. */
function irParaPlanoEscolhido() {
  const e = lerPlanoEscolhido();
  if (!e || !sessao || !ehAdmin()) return false;
  try { sessionStorage.removeItem(CHAVE_PLANO_ESCOLHIDO); } catch { /* sem armazenamento */ }
  irPara('checkout', { plano: e.plano, ciclo: e.ciclo, extras: e.extras || null });
  return true;
}

/* ---------------------------------------------------------------------
 * Peças compartilhadas
 * ------------------------------------------------------------------- */
const planoDoCatalogo = (st, id) => st.catalogo.find((p) => p.id === id);

/** Preço mostrado "por mês" (no anual, o total dividido por 12). */
const porMes = (valor, ciclo) => (ciclo === 'ANUAL' ? Number(valor) / 12 : Number(valor));

/** Total da rede com N unidades no ciclo (plano Rede + vagas extras). */
function totalRede(st, unidades, ciclo) {
  const rede = planoDoCatalogo(st, 'REDE');
  const extras = Math.max(0, unidades - rede.unidades);
  const base = Number(ciclo === 'ANUAL' ? rede.anual : rede.mensal);
  const vaga = Number(ciclo === 'ANUAL' ? st.unidadeExtraAnual : st.unidadeExtraMensal);
  return base + vaga * extras;
}

/**
 * Comparativo de vagas: quanto a rede paga com 5, 6, 8, 10, 15 ou 20 unidades,
 * e quanto sai cada unidade (fica mais barato por unidade quanto mais vagas).
 */
function tabelaVagas(st, ciclo, destaque) {
  const base = planoDoCatalogo(st, 'REDE').unidades;
  const opcoes = [...new Set([base, base + 1, 8, 10, 15, st.maxUnidades, destaque])].filter((n) => n >= base && n <= st.maxUnidades).sort((a, b) => a - b);
  return `<div class="vagas-wrap"><table class="vagas-tabela">
    <caption class="sr-only">Comparativo de preço por quantidade de unidades</caption>
    <thead><tr><th>Unidades</th><th class="num col-extras">Vagas extras</th><th class="num">Total por mês</th><th class="num">Por unidade</th></tr></thead>
    <tbody>${opcoes.map((n) => {
      const mes = porMes(totalRede(st, n, ciclo), ciclo);
      return `<tr class="${n === destaque ? 'destacada' : ''}"${n === destaque ? ' aria-current="true"' : ''}><td><b>${n}</b>${n === destaque ? ' <span class="badge b-info">sua escolha</span>' : ''}</td><td class="num col-extras">${n - base}</td><td class="num">${moeda(mes)}</td><td class="num">${moeda(mes / n)}</td></tr>`;
    }).join('')}</tbody></table></div>
    <p class="dica">${ciclo === 'ANUAL' ? 'No anual, o total do ano dividido por 12 (o plano anual sai pelo preço de 10 meses).' : 'Valores do plano mensal.'} Cada vaga extra: ${moeda(st.unidadeExtraMensal)}/mês ou ${moeda(st.unidadeExtraAnual)}/ano.</p>`;
}

function medidor(rotulo, usado, limite, notaIlimitado = 'ilimitado') {
  const pct = limite == null ? 0 : Math.min(100, (usado / Math.max(1, limite)) * 100);
  const classe = limite == null ? 'livre' : pct >= 100 ? 'cheio' : pct >= 90 ? 'quase' : '';
  return `<div class="medidor ${classe}">
      <div class="medidor-topo"><span>${rotulo}</span><b>${usado}<small> / ${limite == null ? notaIlimitado : limite}</small></b></div>
      <div class="medidor-barra" aria-hidden="true"><i style="width:${limite == null ? 100 : pct}%"></i></div>
    </div>`;
}

const formaPagamento = (p) => ({ CARTAO: `${esc(p.cartaoBandeira || 'Cartão')} •••• ${esc(p.cartaoFinal || '')}`, PIX: 'Pix', BOLETO: 'Boleto', CREDITO: 'Crédito do período' }[p.metodo] || p.metodo);

/** Por que um plano não pode ser escolhido agora (ou null). */
function motivoIndisponivel(st, p) {
  if (st.escopo === 'REDE' && p.id !== 'REDE') return 'Uma rede só pode assinar o plano Rede.';
  if (p.maxAlunos != null && st.uso.alunosAtivos > p.maxAlunos) return `A academia tem ${st.uso.alunosAtivos} alunos ativos (o limite é ${p.maxAlunos}).`;
  if (p.maxUsuarios != null && st.uso.usuarios > p.maxUsuarios) return `A academia tem ${st.uso.usuarios} usuários (o limite é ${p.maxUsuarios}).`;
  return null;
}

function alternadorCiclo(ciclo, nome) {
  return `<div class="ciclo-alternar" role="radiogroup" aria-label="Forma de cobrança">
      <label><input type="radio" name="${nome}" value="MENSAL" ${ciclo === 'MENSAL' ? 'checked' : ''}><span>Mensal</span></label>
      <label><input type="radio" name="${nome}" value="ANUAL" ${ciclo === 'ANUAL' ? 'checked' : ''}><span>Anual <em>2 meses grátis</em></span></label>
    </div>`;
}

/* ---------------------------------------------------------------------
 * #/assinatura
 * ------------------------------------------------------------------- */
TELAS.assinatura = async () => {
  if (!ehAdmin()) {
    cabecalho('Plano e|Cobrança', 'A assinatura do sistema é cuidada pelo administrador.', '', 'Plano e cobrança');
    conteudo.innerHTML = '<div class="painel vazio"><strong>Acesso restrito</strong>Somente administradores veem o plano e a cobrança.</div>';
    return;
  }
  const st = await requisitar('/assinatura', 'GET');
  cabecalho('Plano e|Cobrança', `Assinatura do Força Total ${st.escopo === 'REDE' ? 'da rede' : 'da academia'} <b>${esc(st.conta)}</b>.`, '', 'Plano e cobrança');
  let ciclo = st.ciclo || 'MENSAL';
  const gerencia = st.podeGerenciar;
  const linkCheckout = (params) => montarHash('checkout', params);
  const extrasAtuais = st.escopo === 'REDE' ? st.unidadesExtras : 0;

  const situacao = () => {
    const titulo = st.situacao === 'TESTE' ? 'Teste grátis' : `Plano ${esc(st.planoNome)}`;
    const linha = {
      TESTE: `Tudo liberado até <b>${data(st.testeAte)}</b> (${st.diasRestantes <= 1 ? 'último dia' : `faltam ${st.diasRestantes} dias`}). Depois, escolha um plano para continuar cadastrando alunos, usuários e unidades.`,
      ATIVA: `Cobrança <b>${cicloTexto(st.ciclo)}</b> · válido até <b>${data(st.vigenteAte)}</b> (${st.diasRestantes === 1 ? '1 dia' : st.diasRestantes + ' dias'}).${st.valorRenovacao ? ` A renovação custa <b>${moeda(st.valorRenovacao)}</b>.` : ''}`,
      VENCIDA: st.plano
        ? `Venceu em <b>${data(st.vigenteAte)}</b>. Novos alunos, usuários e unidades estão bloqueados até renovar; o resto continua funcionando.`
        : `O teste terminou em <b>${data(st.testeAte)}</b>. Escolha um plano para voltar a cadastrar alunos, usuários e unidades.`,
    }[st.situacao];
    const renovar = gerencia && st.plano
      ? `<a class="btn primario" href="${linkCheckout({ plano: st.plano, ciclo: st.ciclo, extras: extrasAtuais || null })}">Renovar${st.valorRenovacao ? ' por ' + moeda(st.valorRenovacao) : ''}</a>` : '';
    const escolher = gerencia && !st.plano ? '<a class="btn primario" href="#planosSistema" data-rolar>Escolher um plano</a>' : '';
    return `<section class="painel ass-cartao ass-situacao" aria-labelledby="assSituacao">
        <header class="ass-cartao-topo"><span class="kicker-mini">Sua assinatura</span>${badge(SITUACOES_ASSINATURA[st.situacao])}</header>
        <h2 id="assSituacao">${titulo}</h2>
        <p>${linha}</p>
        ${gerencia ? `<div class="ass-acoes">${renovar}${escolher}</div>` : '<p class="dica">Quem gerencia a assinatura da rede é o dono da rede.</p>'}
      </section>`;
  };

  const uso = () => {
    const teste = st.situacao === 'TESTE';
    return `<section class="painel ass-cartao" aria-labelledby="assUso">
        <header class="ass-cartao-topo"><span class="kicker-mini" id="assUso">Uso do plano</span></header>
        ${medidor(st.escopo === 'REDE' ? 'Alunos ativos (rede)' : 'Alunos ativos', st.uso.alunosAtivos, st.limites.alunos, teste ? 'liberado no teste' : 'ilimitado')}
        ${medidor(st.escopo === 'REDE' ? 'Usuários (rede)' : 'Usuários', st.uso.usuarios, st.limites.usuarios, teste ? 'liberado no teste' : 'ilimitado')}
        ${st.escopo === 'REDE' ? medidor('Unidades', st.uso.unidades, st.limites.unidades) : ''}
      </section>`;
  };

  const vagas = () => {
    if (st.escopo !== 'REDE') return '';
    const base = planoDoCatalogo(st, 'REDE').unidades;
    const pode = st.situacao === 'ATIVA' && st.plano === 'REDE' && st.limites.unidades < st.maxUnidades;
    const motivo = st.situacao === 'TESTE' ? 'Assine o plano Rede para comprar vagas extras.'
      : st.situacao === 'VENCIDA' ? 'Renove o plano Rede para comprar vagas.' : st.limites.unidades >= st.maxUnidades ? `A rede já tem o máximo de ${st.maxUnidades} unidades.` : '';
    return `<section class="painel ass-cartao ass-largo" aria-labelledby="assVagas">
        <header class="ass-cartao-topo"><h2 class="titulo-secao" id="assVagas">Vagas de <span>unidade</span></h2></header>
        <p>A rede usa <b>${st.uso.unidades}</b> de <b>${st.limites.unidades}</b> vagas (${base} do plano${st.unidadesExtras ? ` + ${st.unidadesExtras} ${st.unidadesExtras === 1 ? 'extra' : 'extras'}` : ''}). Precisa de mais? Cada vaga extra custa <b>${moeda(st.unidadeExtraMensal)}/mês</b>, até ${st.maxUnidades} unidades no total. No meio do período, você paga só o proporcional aos dias que faltam.</p>
        ${tabelaVagas(st, st.ciclo || 'MENSAL', st.limites.unidades)}
        ${gerencia ? `<div class="ass-acoes">${pode ? '<a class="btn primario" href="#/checkout?tipo=unidades&quantidade=1">Comprar vagas de unidade</a>' : `<button type="button" class="primario" disabled>Comprar vagas de unidade</button><span class="dica">${motivo}</span>`}</div>` : ''}
      </section>`;
  };

  const pendente = () => (st.pendente && gerencia ? `
      <div class="painel ass-pendente" role="status">
        <span>Há um ${st.pendente.metodo === 'PIX' ? 'Pix' : 'boleto'} de <b>${moeda(st.pendente.valor)}</b> aguardando pagamento: ${esc(st.pendente.descricao)}.</span>
        <a class="btn pequeno primario" href="${linkCheckout({ pagamento: st.pendente.id })}">Ver pagamento</a>
      </div>` : '');

  const cartoesPlanos = () => st.catalogo.map((p) => {
    const motivo = motivoIndisponivel(st, p);
    const atual = st.plano === p.id && st.situacao !== 'TESTE';
    const preco = Number(ciclo === 'ANUAL' ? p.anual : p.mensal);
    const texto = atual && st.ciclo === ciclo ? 'Renovar' : atual ? `Mudar para o ${cicloTexto(ciclo)}` : st.plano ? 'Mudar para este plano' : `Assinar o ${p.nome}`;
    const params = { plano: p.id, ciclo, extras: p.id === 'REDE' && extrasAtuais ? extrasAtuais : null };
    return `<article class="ass-plano ${atual ? 'atual' : ''} ${p.id === 'PROFISSIONAL' ? 'destaque' : ''}">
        ${atual ? '<span class="ass-plano-fita">Seu plano</span>' : p.id === 'PROFISSIONAL' ? '<span class="ass-plano-fita">Mais escolhido</span>' : ''}
        <h3>${esc(p.nome)}</h3>
        <div class="ass-plano-preco"><b>${moeda(porMes(preco, ciclo))}</b><span>/mês</span></div>
        <p class="dica">${ciclo === 'ANUAL' ? `${moeda(p.anual)} cobrados por ano` : 'cobrado todo mês'}</p>
        <ul>${RECURSOS_PLANO[p.id].map((r) => `<li>${r}</li>`).join('')}</ul>
        ${!gerencia ? '' : motivo ? `<button type="button" class="pequeno" disabled>Indisponível</button><p class="dica txt-alerta">${esc(motivo)}</p>`
          : `<a class="btn ${atual || p.id === 'PROFISSIONAL' ? 'primario' : ''}" href="${linkCheckout(params)}">${texto}</a>`}
      </article>`;
  }).join('');

  const historico = () => `
      <section class="ass-historico" aria-labelledby="assHistorico">
        <h2 class="titulo-secao" id="assHistorico">Histórico de <span>pagamentos</span></h2>
        ${st.pagamentos.length ? `<div class="tabela-wrap"><table class="responsiva">
          <thead><tr><th>Data</th><th>Descrição</th><th>Forma</th><th class="num">Valor</th><th>Situação</th></tr></thead>
          <tbody>${st.pagamentos.map((p) => `<tr>
            <td data-rotulo="Data">${data((p.pagoEm || p.criadoEm).slice(0, 10))}</td>
            <td data-rotulo="Descrição">${esc(p.descricao)}${Number(p.credito) > 0 ? `<small class="ass-credito">${p.tipo === 'UNIDADES' ? 'proporcional' : 'crédito'}: −${moeda(p.credito)}</small>` : ''}</td>
            <td data-rotulo="Forma">${formaPagamento(p)}</td>
            <td data-rotulo="Valor" class="num">${moeda(p.valor)}</td>
            <td data-rotulo="Situação">${badge(STATUS_PAGAMENTO[p.status])}</td></tr>`).join('')}</tbody></table></div>`
          : '<div class="painel vazio"><strong>Nenhum pagamento ainda</strong>Os pagamentos da assinatura aparecem aqui.</div>'}
      </section>`;

  conteudo.innerHTML = `
    <div class="ass-grade">${situacao()}${uso()}${vagas()}</div>
    ${pendente()}
    <section class="ass-planos" id="planosSistema" aria-labelledby="assPlanos">
      <header class="ass-planos-topo">
        <h2 class="titulo-secao" id="assPlanos">Compare os <span>planos</span></h2>
        ${alternadorCiclo(ciclo, 'cicloPlanos')}
      </header>
      <div class="ass-planos-grade" id="assPlanosGrade">${cartoesPlanos()}</div>
      <p class="dica demo-aviso">Ambiente de demonstração: nenhum valor é cobrado. Trocar de plano começa um período novo hoje, descontando o que sobrou do período pago.</p>
    </section>
    ${historico()}`;

  conteudo.querySelectorAll('input[name=cicloPlanos]').forEach((r) => r.addEventListener('change', () => {
    ciclo = r.value;
    $('#assPlanosGrade').innerHTML = cartoesPlanos();
  }));
  conteudo.querySelector('[data-rolar]')?.addEventListener('click', (e) => { e.preventDefault(); $('#planosSistema').scrollIntoView({ behavior: 'smooth', block: 'start' }); });
};

/* ---------------------------------------------------------------------
 * #/checkout
 * ------------------------------------------------------------------- */
const LUHN_OK = (n) => { let s = 0, d = false; for (let i = n.length - 1; i >= 0; i--) { let x = +n[i]; if (d) { x *= 2; if (x > 9) x -= 9; } s += x; d = !d; } return s % 10 === 0; };

function bandeiraCartao(n) {
  if (/^(4011|4312|4389|4514|4576|5041|5066|5067|509\d|6277|6362|6363|650\d|6516|6550)/.test(n)) return 'Elo';
  if (/^(606282|3841)/.test(n)) return 'Hipercard';
  if (/^3[47]/.test(n)) return 'Amex';
  if (/^4/.test(n)) return 'Visa';
  if (/^(5[1-5]|222[1-9]|22[3-9]\d|2[3-6]\d\d|27[01]\d|2720)/.test(n)) return 'Mastercard';
  return '';
}

/** Mesmas regras do gateway, para avisar antes de enviar. */
function validarCartao(c) {
  const erros = {};
  if (c.nome.trim().split(/\s+/).length < 2) erros.nome = 'Informe o nome como está impresso no cartão';
  const n = c.numero.replace(/\D/g, '');
  if (!/^\d{13,19}$/.test(n) || !LUHN_OK(n)) erros.numero = 'Número do cartão inválido';
  const m = c.validade.match(/^(0[1-9]|1[0-2])\s*\/\s*(\d{2})$/);
  if (!m) erros.validade = 'Use MM/AA';
  else {
    const fim = new Date(2000 + Number(m[2]), Number(m[1]), 0, 23, 59, 59);
    if (fim < new Date()) erros.validade = 'Este cartão está vencido';
  }
  const digitos = bandeiraCartao(n) === 'Amex' ? 4 : 3;
  if (!new RegExp(`^\\d{${digitos}}$`).test(c.cvv)) erros.cvv = `São ${digitos} dígitos`;
  return erros;
}

async function copiarTexto(texto, botao) {
  try { await navigator.clipboard.writeText(texto); } catch {
    const t = document.createElement('textarea'); t.value = texto; document.body.append(t); t.select(); document.execCommand('copy'); t.remove();
  }
  const antes = botao.textContent;
  botao.textContent = 'Copiado!';
  setTimeout(() => { botao.textContent = antes; }, 1600);
}

TELAS.checkout = async (params) => {
  if (!ehAdmin()) {
    cabecalho('Finalizar|Assinatura', 'Somente administradores contratam o plano.', '', 'Pagamento');
    conteudo.innerHTML = '<div class="painel vazio"><strong>Acesso restrito</strong>Somente administradores veem o plano e a cobrança.</div>';
    return;
  }
  const st = await requisitar('/assinatura', 'GET');
  const voltar = '<a class="btn" href="#/assinatura">Voltar para Plano e cobrança</a>';
  if (!st.podeGerenciar) {
    cabecalho('Finalizar|Assinatura', 'A assinatura da rede é paga pelo dono da rede.', voltar, 'Pagamento');
    conteudo.innerHTML = '<div class="painel vazio"><strong>Quem paga é o dono da rede</strong>Peça ao dono da rede para renovar ou comprar vagas.</div>';
    return;
  }

  // Pedido já criado (Pix/boleto aguardando, ou um pagamento aprovado ao recarregar a página)
  let pendenteAtual = null;
  if (params.pagamento) {
    pendenteAtual = await requisitar(`/assinatura/pagamentos/${Number(params.pagamento)}`, 'GET');
    if (pendenteAtual.status === 'APROVADO') return mostrarSucessoPagamento(pendenteAtual, st);
    if (pendenteAtual.status === 'CANCELADO') { aviso('Este pagamento foi cancelado ou substituído.'); return irPara('assinatura'); }
    // o pedido pendente define o que aparece à esquerda (e para onde "outra forma de pagamento" volta)
    if (pendenteAtual.tipo === 'UNIDADES') params = { tipo: 'unidades', quantidade: pendenteAtual.unidadesExtras };
    else params = { plano: pendenteAtual.plano, ciclo: pendenteAtual.ciclo, extras: pendenteAtual.unidadesExtras };
  }

  const tipo = params.tipo === 'unidades' ? 'UNIDADES' : 'PLANO';
  const baseRede = planoDoCatalogo(st, 'REDE').unidades;
  const minExtras = st.escopo === 'REDE' ? Math.max(0, st.uso.unidades - baseRede) : 0;
  const maxExtras = st.maxUnidades - baseRede;
  const estado = {
    plano: st.escopo === 'REDE' ? 'REDE' : String(params.plano || st.plano || 'PROFISSIONAL').toUpperCase(),
    ciclo: String(params.ciclo || st.ciclo || 'MENSAL').toUpperCase() === 'ANUAL' ? 'ANUAL' : 'MENSAL',
    extras: Math.min(maxExtras, Math.max(minExtras, Number(params.extras ?? st.unidadesExtras ?? 0) || 0)),
    quantidade: Math.max(1, Number(params.quantidade) || 1),
    metodo: 'CARTAO',
    orcamento: null,
  };
  if (!planoDoCatalogo(st, estado.plano)) estado.plano = 'PROFISSIONAL';
  const maxQuantidade = st.maxUnidades - st.limites.unidades;
  estado.quantidade = Math.min(estado.quantidade, Math.max(1, maxQuantidade));

  cabecalho(tipo === 'UNIDADES' ? 'Comprar|Vagas' : 'Finalizar|Assinatura',
    tipo === 'UNIDADES' ? `Mais vagas de unidade para a <b>${esc(st.conta)}</b>.` : `Escolha o plano e a forma de pagamento para a <b>${esc(st.conta)}</b>.`,
    voltar, 'Pagamento');

  const pedidoPlano = () => `
      <fieldset class="checkout-etapa">
        <legend><b>1</b> Plano</legend>
        <div class="checkout-planos">${st.catalogo.map((p) => {
          const motivo = motivoIndisponivel(st, p);
          return `<label class="checkout-plano ${motivo ? 'indisponivel' : ''}" ${motivo ? `title="${esc(motivo)}"` : ''}>
              <input type="radio" name="plano" value="${p.id}" ${estado.plano === p.id ? 'checked' : ''} ${motivo ? 'disabled' : ''}>
              <span class="checkout-plano-nome">${esc(p.nome)}</span>
              <span class="checkout-plano-preco" data-preco-plano="${p.id}"></span>
              <span class="checkout-plano-info">${p.id === 'REDE' ? 'Várias unidades' : `Até ${p.maxAlunos} alunos`}</span>
            </label>`;
        }).join('')}</div>
      </fieldset>
      <fieldset class="checkout-etapa">
        <legend><b>2</b> Cobrança</legend>
        ${alternadorCiclo(estado.ciclo, 'ciclo')}
      </fieldset>
      <fieldset class="checkout-etapa" id="etapaUnidades" ${estado.plano === 'REDE' ? '' : 'hidden'}>
        <legend><b>3</b> Unidades da rede</legend>
        <div class="contador" data-contador="extras">
          <button type="button" class="pequeno" data-passo="-1" aria-label="Menos uma unidade">−</button>
          <output id="totalUnidades" aria-live="polite"></output>
          <button type="button" class="pequeno" data-passo="1" aria-label="Mais uma unidade">+</button>
          <span class="dica" id="extrasTexto"></span>
        </div>
        <div id="comparativoVagas"></div>
        ${st.escopo === 'ACADEMIA' ? `
        <div class="campo" data-campo="nomeRede">
          <label class="rotulo" for="checkoutNomeRede">Nome da rede</label>
          <input id="checkoutNomeRede" name="nomeRede" maxlength="120" placeholder="Ex.: Rede ${esc(st.conta)}" autocomplete="organization">
          <small class="erro-campo" role="alert" hidden></small>
          <p class="dica">A <b>${esc(st.conta)}</b> vira a primeira unidade, com todos os dados, e você vira o dono da rede.</p>
        </div>` : ''}
      </fieldset>`;

  const pedidoUnidades = () => `
      <fieldset class="checkout-etapa">
        <legend><b>1</b> Quantas vagas?</legend>
        <div class="contador" data-contador="quantidade">
          <button type="button" class="pequeno" data-passo="-1" aria-label="Menos uma vaga">−</button>
          <output id="totalUnidades" aria-live="polite"></output>
          <button type="button" class="pequeno" data-passo="1" aria-label="Mais uma vaga">+</button>
          <span class="dica" id="extrasTexto"></span>
        </div>
        <div id="comparativoVagas"></div>
      </fieldset>`;

  conteudo.innerHTML = `
    <div class="checkout">
      <section class="painel checkout-pedido" aria-labelledby="checkoutPedido">
        <h2 class="titulo-secao" id="checkoutPedido">Seu <span>pedido</span></h2>
        ${tipo === 'UNIDADES' ? pedidoUnidades() : pedidoPlano()}
        <div class="checkout-resumo" id="checkoutResumo" aria-live="polite"><div class="carregando" role="status" aria-label="Calculando"></div></div>
      </section>
      <section class="painel checkout-pagamento" id="checkoutPagamento" aria-labelledby="checkoutPagar">
        <h2 class="titulo-secao" id="checkoutPagar">Pagamento</h2>
        <p class="demo-aviso">🔒 Ambiente de demonstração: <b>nenhum valor é cobrado</b>. Os dados do cartão são conferidos e descartados; só a bandeira e os 4 últimos dígitos ficam no histórico.</p>
        <div class="checkout-abas" role="tablist" aria-label="Forma de pagamento">
          <button type="button" role="tab" aria-selected="true" aria-controls="painelCartao" id="abaCartao" data-metodo="CARTAO">Cartão</button>
          <button type="button" role="tab" aria-selected="false" aria-controls="painelPix" id="abaPix" data-metodo="PIX" tabindex="-1">Pix</button>
          <button type="button" role="tab" aria-selected="false" aria-controls="painelBoleto" id="abaBoleto" data-metodo="BOLETO" tabindex="-1">Boleto</button>
        </div>
        <form id="painelCartao" role="tabpanel" aria-labelledby="abaCartao" class="checkout-painel" novalidate>
          <div class="campo" data-campo="nome"><label class="rotulo" for="cartaoNome">Nome impresso no cartão</label>
            <input id="cartaoNome" name="nome" autocomplete="cc-name" maxlength="80" placeholder="Como está no cartão"><small class="erro-campo" role="alert" hidden></small></div>
          <div class="campo" data-campo="numero"><label class="rotulo" for="cartaoNumero">Número do cartão</label>
            <div class="cartao-numero"><input id="cartaoNumero" name="numero" inputmode="numeric" autocomplete="cc-number" maxlength="23" placeholder="0000 0000 0000 0000"><span class="cartao-bandeira" id="cartaoBandeira" aria-live="polite"></span></div>
            <small class="erro-campo" role="alert" hidden></small></div>
          <div class="checkout-linha">
            <div class="campo" data-campo="validade"><label class="rotulo" for="cartaoValidade">Validade</label>
              <input id="cartaoValidade" name="validade" inputmode="numeric" autocomplete="cc-exp" maxlength="5" placeholder="MM/AA"><small class="erro-campo" role="alert" hidden></small></div>
            <div class="campo" data-campo="cvv"><label class="rotulo" for="cartaoCvv">CVV</label>
              <input id="cartaoCvv" name="cvv" inputmode="numeric" autocomplete="cc-csc" maxlength="4" placeholder="123"><small class="erro-campo" role="alert" hidden></small></div>
          </div>
          <p class="dica cartoes-teste">Cartões de teste: <code>4242 4242 4242 4242</code> é aprovado e <code>4000 0000 0000 0002</code> é recusado. Use qualquer validade futura e qualquer CVV.</p>
          <button type="submit" class="primario largo botao-pagar" disabled>Pagar</button>
        </form>
        <div id="painelPix" role="tabpanel" aria-labelledby="abaPix" class="checkout-painel" hidden>
          <p>O QR Code vale <b>30 minutos</b>. A assinatura é liberada assim que o pagamento é confirmado.</p>
          <button type="button" class="primario largo botao-pagar" data-gerar="PIX" disabled>Gerar Pix</button>
        </div>
        <div id="painelBoleto" role="tabpanel" aria-labelledby="abaBoleto" class="checkout-painel" hidden>
          <p>O boleto vence em <b>3 dias</b>. A assinatura é liberada quando o banco confirmar o pagamento.</p>
          <button type="button" class="primario largo botao-pagar" data-gerar="BOLETO" disabled>Gerar boleto</button>
        </div>
        <div class="checkout-credito" id="checkoutCredito" hidden>
          <p>O crédito do período atual cobre todo o valor: não há nada a pagar.</p>
          <button type="button" class="primario largo" id="botaoConfirmarCredito">Confirmar troca de plano</button>
        </div>
        <p class="checkout-erro" id="checkoutErro" role="alert" hidden></p>
      </section>
    </div>`;

  const pedido = $('.checkout-pedido');
  const pagamento = $('#checkoutPagamento');

  /* ---- Pedido: plano, cobrança e vagas ---- */
  const totalUnidadesAgora = () => (tipo === 'UNIDADES' ? st.limites.unidades + estado.quantidade : baseRede + estado.extras);

  function desenharPedido() {
    if (tipo === 'PLANO') {
      st.catalogo.forEach((p) => {
        const el = pedido.querySelector(`[data-preco-plano="${p.id}"]`);
        if (el) el.textContent = `${moeda(porMes(estado.ciclo === 'ANUAL' ? p.anual : p.mensal, estado.ciclo))}/mês`;
      });
      $('#etapaUnidades').hidden = estado.plano !== 'REDE';
      if (estado.plano !== 'REDE') return;
    }
    const total = totalUnidadesAgora();
    $('#totalUnidades').textContent = tipo === 'UNIDADES' ? `+${estado.quantidade}` : unidadesTexto(total);
    $('#extrasTexto').innerHTML = tipo === 'UNIDADES'
      ? `A rede passa de <b>${st.limites.unidades}</b> para <b>${total}</b> unidades.`
      : `${baseRede} do plano${estado.extras ? ` + <b>${estado.extras}</b> ${estado.extras === 1 ? 'vaga extra' : 'vagas extras'}` : ''}${minExtras ? ` · a rede já tem ${st.uso.unidades}` : ''}`;
    const [menos, mais] = pedido.querySelectorAll('[data-passo]');
    menos.disabled = tipo === 'UNIDADES' ? estado.quantidade <= 1 : estado.extras <= minExtras;
    mais.disabled = tipo === 'UNIDADES' ? estado.quantidade >= maxQuantidade : estado.extras >= maxExtras;
    $('#comparativoVagas').innerHTML = tabelaVagas(st, tipo === 'UNIDADES' ? st.ciclo : estado.ciclo, total);
  }

  let pedidoSeq = 0;
  async function atualizarOrcamento() {
    const seq = ++pedidoSeq;
    const resumo = $('#checkoutResumo');
    if (tipo === 'PLANO') atualizarUrl('checkout', { plano: estado.plano, ciclo: estado.ciclo, extras: estado.plano === 'REDE' && estado.extras ? estado.extras : null });
    else atualizarUrl('checkout', { tipo: 'unidades', quantidade: estado.quantidade });
    try {
      const o = tipo === 'PLANO'
        ? await requisitar(`/assinatura/orcamento?plano=${estado.plano}&ciclo=${estado.ciclo}&extras=${estado.plano === 'REDE' ? estado.extras : 0}`, 'GET')
        : await requisitar(`/assinatura/orcamento-unidades?quantidade=${estado.quantidade}`, 'GET');
      if (seq !== pedidoSeq || !resumo.isConnected) return;
      estado.orcamento = o;
      const linhas = tipo === 'PLANO'
        ? [[`Plano ${planoDoCatalogo(st, o.plano).nome} (${cicloTexto(o.ciclo)})`, moeda(o.valorPlano)],
           ...(o.unidadesExtras ? [[`${o.unidadesExtras} ${o.unidadesExtras === 1 ? 'vaga extra' : 'vagas extras'} × ${moeda(o.ciclo === 'ANUAL' ? st.unidadeExtraAnual : st.unidadeExtraMensal)}`, moeda(o.valorExtras)]] : []),
           ...(Number(o.credito) > 0 ? [['Crédito do período atual', '−' + moeda(o.credito), 'credito']] : [])]
        : [[`${o.quantidade} ${o.quantidade === 1 ? 'vaga extra' : 'vagas extras'} × ${moeda(o.ciclo === 'ANUAL' ? st.unidadeExtraAnual : st.unidadeExtraMensal)} (${cicloTexto(o.ciclo)})`, moeda(o.subtotal)],
           ...(Number(o.credito) > 0 ? [[`Proporcional até ${data(o.fim)}`, '−' + moeda(o.credito), 'credito']] : [])];
      resumo.innerHTML = `
        <dl class="resumo-linhas">${linhas.map(([r, v, c]) => `<div class="${c || ''}"><dt>${r}</dt><dd>${v}</dd></div>`).join('')}
          <div class="resumo-total"><dt>Total agora</dt><dd>${moeda(o.total)}</dd></div></dl>
        <p class="dica">${tipo === 'UNIDADES'
          ? `As vagas passam a valer na hora e entram na renovação de ${data(st.vigenteAte)}: ${moeda(Number(st.valorRenovacao || 0) + Number(o.subtotal))} por ${cicloTexto(o.ciclo) === 'anual' ? 'ano' : 'mês'}.`
          : o.renovacao ? `Renovação: o novo período vai de <b>${data(o.inicio)}</b> a <b>${data(o.fim)}</b>, emendado no atual.`
          : `Período de <b>${data(o.inicio)}</b> a <b>${data(o.fim)}</b>. Depois, ${moeda(o.subtotal)} por ${o.ciclo === 'ANUAL' ? 'ano' : 'mês'}.`}</p>`;
      definirPagavel(true);
    } catch (err) {
      if (seq !== pedidoSeq || !resumo.isConnected) return;
      estado.orcamento = null;
      resumo.innerHTML = `<p class="checkout-erro">${esc(err.message)}</p>`;
      definirPagavel(false);
    }
  }

  function definirPagavel(ok) {
    const total = estado.orcamento ? Number(estado.orcamento.total) : 0;
    const gratis = ok && estado.orcamento && total === 0;
    pagamento.querySelectorAll('.botao-pagar').forEach((b) => {
      b.disabled = !ok;
      b.textContent = b.dataset.gerar === 'PIX' ? `Gerar Pix de ${moeda(total)}` : b.dataset.gerar === 'BOLETO' ? `Gerar boleto de ${moeda(total)}` : `Pagar ${moeda(total)}`;
    });
    $('#checkoutCredito').hidden = !gratis;
    pagamento.querySelector('.checkout-abas').hidden = gratis;
    pagamento.querySelectorAll('.checkout-painel').forEach((p) => { p.hidden = gratis || p.id !== { CARTAO: 'painelCartao', PIX: 'painelPix', BOLETO: 'painelBoleto' }[estado.metodo]; });
  }

  let atraso;
  const recalcular = () => { desenharPedido(); clearTimeout(atraso); atraso = setTimeout(atualizarOrcamento, 120); };

  pedido.addEventListener('change', (e) => {
    if (e.target.name === 'plano') estado.plano = e.target.value;
    if (e.target.name === 'ciclo') estado.ciclo = e.target.value;
    if (e.target.name === 'plano' || e.target.name === 'ciclo') recalcular();
  });
  pedido.addEventListener('input', (e) => { if (e.target.name === 'nomeRede') mostrarErroCampo(pedido, 'nomeRede', null); });
  pedido.querySelectorAll('[data-passo]').forEach((b) => b.addEventListener('click', () => {
    const passo = Number(b.dataset.passo);
    if (tipo === 'UNIDADES') estado.quantidade = Math.min(maxQuantidade, Math.max(1, estado.quantidade + passo));
    else estado.extras = Math.min(maxExtras, Math.max(minExtras, estado.extras + passo));
    recalcular();
  }));

  /* ---- Abas de pagamento ---- */
  const abas = [...pagamento.querySelectorAll('[role=tab]')];
  function escolherMetodo(metodo, focar = false) {
    estado.metodo = metodo;
    abas.forEach((a) => { const sim = a.dataset.metodo === metodo; a.setAttribute('aria-selected', String(sim)); a.tabIndex = sim ? 0 : -1; if (sim && focar) a.focus(); });
    definirPagavel(!!estado.orcamento);
    esconderErro();
  }
  abas.forEach((a, i) => {
    a.addEventListener('click', () => escolherMetodo(a.dataset.metodo));
    a.addEventListener('keydown', (e) => {
      if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
      e.preventDefault();
      escolherMetodo(abas[(i + (e.key === 'ArrowRight' ? 1 : abas.length - 1)) % abas.length].dataset.metodo, true);
    });
  });

  /* ---- Cartão: formatação enquanto digita ---- */
  const form = $('#painelCartao');
  form.addEventListener('input', (e) => {
    const i = e.target;
    if (i.name === 'numero') {
      const d = i.value.replace(/\D/g, '').slice(0, 19);
      const b = bandeiraCartao(d);
      i.value = b === 'Amex' ? d.replace(/^(\d{0,4})(\d{0,6})(\d{0,5}).*/, (_, a, b2, c) => [a, b2, c].filter(Boolean).join(' ')) : d.replace(/(\d{4})(?=\d)/g, '$1 ');
      $('#cartaoBandeira').textContent = b;
      $('#cartaoCvv').maxLength = b === 'Amex' ? 4 : 3;
    }
    if (i.name === 'validade') {
      const d = i.value.replace(/\D/g, '').slice(0, 4);
      i.value = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
    }
    if (i.name === 'cvv') i.value = i.value.replace(/\D/g, '').slice(0, 4);
    if (i.name) mostrarErroCampo(form, i.name, null);
    esconderErro();
  });

  const erroBox = $('#checkoutErro');
  function mostrarErro(msg) { erroBox.textContent = msg; erroBox.hidden = false; }
  function esconderErro() { erroBox.hidden = true; }

  function corpoPedido(metodo, cartao = null) {
    if (tipo === 'UNIDADES') return { caminho: '/assinatura/unidades', corpo: { quantidade: estado.quantidade, metodo, cartao } };
    return {
      caminho: '/assinatura/checkout',
      corpo: { plano: estado.plano, ciclo: estado.ciclo, unidadesExtras: estado.plano === 'REDE' ? estado.extras : 0, nomeRede: $('#checkoutNomeRede')?.value.trim() || null, metodo, cartao },
    };
  }

  /** O nome da rede é pedido quando uma academia avulsa assina o Rede. */
  function conferirNomeRede() {
    const campo = $('#checkoutNomeRede');
    if (tipo !== 'PLANO' || estado.plano !== 'REDE' || !campo) return true;
    if (campo.value.trim()) return true;
    mostrarErroCampo(pedido, 'nomeRede', 'Informe o nome da rede');
    campo.focus();
    return false;
  }

  /** Erro do servidor no campo certo (cartão ou nome da rede) ou na caixa de erro. */
  function tratarErro(err) {
    window.Sons?.tocar('erro');
    const m = err.message;
    const campo = /^Nome no cartão/i.test(m) ? 'nome' : /^Número do cartão/i.test(m) ? 'numero' : /^Validade/i.test(m) ? 'validade' : /^Código de segurança/i.test(m) ? 'cvv' : null;
    if (campo) { mostrarErroCampo(form, campo, m.replace(/^[^:]+:\s*/, '')); form.elements[campo].focus(); return; }
    if (/^Nome da rede/i.test(m) && $('#checkoutNomeRede')) { mostrarErroCampo(pedido, 'nomeRede', m.replace(/^[^:]+:\s*/, '')); $('#checkoutNomeRede').focus(); return; }
    mostrarErro(m);
  }

  async function enviar(metodo, cartao, botao) {
    if (!conferirNomeRede()) return;
    const { caminho, corpo } = corpoPedido(metodo, cartao);
    const texto = botao.textContent;
    botao.disabled = true;
    botao.textContent = metodo === 'CARTAO' ? 'Processando…' : 'Gerando…';
    try {
      const p = await requisitar(caminho, 'POST', corpo);
      cacheGet.clear();
      if (p.status === 'APROVADO') return mostrarSucessoPagamento(p, st);
      atualizarUrl('checkout', { pagamento: p.id });
      mostrarPendente(p);
    } catch (err) {
      tratarErro(err);
      botao.disabled = false;
      botao.textContent = texto;
    }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    esconderErro();
    const c = { nome: form.nome.value, numero: form.numero.value, validade: form.validade.value, cvv: form.cvv.value };
    const erros = validarCartao(c);
    ['nome', 'numero', 'validade', 'cvv'].forEach((k) => mostrarErroCampo(form, k, erros[k] || null));
    const primeiro = ['nome', 'numero', 'validade', 'cvv'].find((k) => erros[k]);
    if (primeiro) { window.Sons?.tocar('erro'); form.elements[primeiro].focus(); return; }
    enviar('CARTAO', { nome: c.nome.trim(), numero: c.numero.replace(/\D/g, ''), validade: c.validade, cvv: c.cvv }, form.querySelector('.botao-pagar'));
  });
  pagamento.querySelectorAll('[data-gerar]').forEach((b) => b.addEventListener('click', () => enviar(b.dataset.gerar, null, b)));
  $('#botaoConfirmarCredito').addEventListener('click', (e) => enviar('CARTAO', null, e.currentTarget));

  /* ---- Pix / boleto aguardando ---- */
  function mostrarPendente(p) {
    const pix = p.metodo === 'PIX';
    pagamento.innerHTML = `
      <h2 class="titulo-secao">${pix ? 'Pague com <span>Pix</span>' : 'Seu <span>boleto</span>'}</h2>
      <p class="demo-aviso">Ambiente de demonstração: este ${pix ? 'Pix' : 'boleto'} não é cobrado. Use <b>Simular pagamento</b> para fazer o papel do banco.</p>
      <div class="pendente-valor"><span>${esc(p.descricao)}</span><b>${moeda(p.valor)}</b></div>
      ${pix ? `<div class="pendente-qr">${qrSvg(p.qr || [])}</div>
        <p class="dica centro">Abra o app do banco, escolha <b>Pix → Ler QR Code</b> ou use o código abaixo.</p>` : ''}
      <div class="campo"><label class="rotulo" for="codigoPagamento">${pix ? 'Pix copia e cola' : 'Linha digitável'}</label>
        <div class="pendente-codigo"><input id="codigoPagamento" readonly value="${esc(p.codigo)}"><button type="button" class="pequeno" id="botaoCopiar">Copiar</button></div></div>
      <p class="pendente-prazo" id="pendentePrazo"></p>
      <div class="pendente-acoes">
        <button type="button" class="primario largo" id="botaoSimular">${pix ? 'Simular pagamento do Pix' : 'Simular pagamento do boleto'}</button>
        <button type="button" class="link" id="botaoOutraForma">Escolher outra forma de pagamento</button>
      </div>
      <p class="checkout-erro" id="checkoutErro" role="alert" hidden></p>`;
    pedido.querySelectorAll('input, button').forEach((el) => { el.disabled = true; });
    const prazo = $('#pendentePrazo');
    const vence = new Date(p.venceEm);
    const tick = () => {
      if (!prazo.isConnected) return clearInterval(relogio);
      const ms = vence - new Date();
      if (pix) {
        if (ms <= 0) { prazo.innerHTML = '<b class="txt-alerta">Este Pix expirou.</b> Gere outro.'; $('#botaoSimular').disabled = true; return clearInterval(relogio); }
        const s = Math.floor(ms / 1000);
        prazo.innerHTML = `Expira em <b>${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}</b>`;
      } else {
        prazo.innerHTML = `Vence em <b>${vence.toLocaleDateString('pt-BR')}</b>`;
      }
    };
    const relogio = setInterval(tick, 1000);
    tick();
    $('#botaoCopiar').addEventListener('click', (e) => copiarTexto(p.codigo, e.currentTarget));
    $('#botaoSimular').addEventListener('click', async (e) => {
      const b = e.currentTarget;
      b.disabled = true;
      b.textContent = 'Confirmando…';
      try {
        const r = await requisitar(`/assinatura/pagamentos/${p.id}/simular`, 'POST');
        clearInterval(relogio);
        cacheGet.clear();
        mostrarSucessoPagamento(r, st);
      } catch (err) {
        window.Sons?.tocar('erro');
        const box = $('#checkoutErro'); box.textContent = err.message; box.hidden = false;
        b.disabled = false;
        b.textContent = pix ? 'Simular pagamento do Pix' : 'Simular pagamento do boleto';
      }
    });
    $('#botaoOutraForma').addEventListener('click', async () => {
      clearInterval(relogio);
      try { await requisitar(`/assinatura/pagamentos/${p.id}/cancelar`, 'POST'); } catch { /* já não estava pendente */ }
      irPara('checkout', tipo === 'UNIDADES' ? { tipo: 'unidades', quantidade: estado.quantidade } : { plano: estado.plano, ciclo: estado.ciclo, extras: estado.plano === 'REDE' && estado.extras ? estado.extras : null });
    });
  }

  if (pendenteAtual) {
    desenharPedido();
    $('#checkoutResumo').innerHTML = `<dl class="resumo-linhas"><div class="resumo-total"><dt>${esc(pendenteAtual.descricao)}</dt><dd>${moeda(pendenteAtual.valor)}</dd></div></dl>`;
    mostrarPendente(pendenteAtual);
    return;
  }
  desenharPedido();
  atualizarOrcamento();
};

/** Pagamento aprovado: atualiza a sessão (a academia pode ter virado rede) e mostra o recibo. */
async function mostrarSucessoPagamento(p, st) {
  try {
    const eu = await requisitar('/auth/eu', 'GET');
    sessao = { usuario: eu.usuario, academia: eu.academia, rede: eu.rede || null };
    atualizarCabecalhoUsuario();
  } catch { /* segue com a sessão atual */ }
  atualizarUrl('checkout', { pagamento: p.id });
  window.Sons?.tocar('confirmado');
  setTimeout(() => window.Notificacoes?.atualizar(), 900); // "Pagamento aprovado" chega no sino
  const virouRede = st.escopo === 'ACADEMIA' && !!sessao?.rede;
  cabecalho('Pagamento|Aprovado', 'Pronto: a assinatura já está valendo.', '', 'Pagamento');
  conteudo.innerHTML = `
    <section class="painel checkout-sucesso" aria-labelledby="sucessoTitulo">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12.5l5 5L20 6.5"/></svg>
      <h2 id="sucessoTitulo">Pagamento aprovado</h2>
      <dl class="resumo-linhas">
        <div><dt>Pedido</dt><dd>${esc(p.descricao)}</dd></div>
        <div><dt>Forma</dt><dd>${formaPagamento(p)}</dd></div>
        ${Number(p.credito) > 0 ? `<div class="credito"><dt>${p.tipo === 'UNIDADES' ? 'Proporcional' : 'Crédito usado'}</dt><dd>−${moeda(p.credito)}</dd></div>` : ''}
        <div class="resumo-total"><dt>Valor pago</dt><dd>${moeda(p.valor)}</dd></div>
        ${p.periodoFim ? `<div><dt>${p.tipo === 'UNIDADES' ? 'Vagas valendo até' : 'Válido até'}</dt><dd>${data(p.periodoFim)}</dd></div>` : ''}
      </dl>
      ${virouRede ? `<p class="dica">A <b>${esc(sessao.academia.nome)}</b> agora é a primeira unidade da rede <b>${esc(sessao.rede.nome)}</b>, e você é o dono da rede.</p>` : ''}
      <p class="dica">Ambiente de demonstração: nenhum valor foi cobrado.</p>
      <div class="checkout-sucesso-acoes">
        ${ehDono() ? `<a class="btn primario" href="#/rede">${p.tipo === 'UNIDADES' ? 'Criar unidades no painel da rede' : 'Ir para o painel da rede'}</a>` : '<a class="btn primario" href="#/dashboard">Ir para o painel</a>'}
        <a class="btn" href="#/assinatura">Ver Plano e cobrança</a>
      </div>
    </section>`;
}
