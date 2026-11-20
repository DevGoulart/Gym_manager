/* =====================================================================
 * Força Total — Front-end do Sistema de Gestão de Academia
 * Consome a API REST do back-end Spring Boot (/api/...).
 * ===================================================================== */

// Servido pelo Spring Boot → caminho relativo. Aberto direto do disco → localhost:8080.
const API = location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api';

const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];

/* ---------------------------------------------------------------------
 * Comunicação com a API
 * GETs ficam em cache por alguns segundos (navegar entre telas fica instantâneo);
 * qualquer gravação (POST/PUT/DELETE) limpa o cache para os dados nunca ficarem velhos.
 * ------------------------------------------------------------------- */
const CACHE_MS = 20000;
const cacheGet = new Map();

/* ---------------------------------------------------------------------
 * Sessão de login: o token fica no navegador e vai no cabeçalho Authorization
 * ------------------------------------------------------------------- */
const CHAVE_TOKEN = 'forcatotal.token';
let sessao = null; // { usuario: {...}, academia: {...} }

function lerToken() { try { return localStorage.getItem(CHAVE_TOKEN); } catch { return null; } }
function salvarToken(t) { try { localStorage.setItem(CHAVE_TOKEN, t); } catch { /* navegador sem armazenamento */ } }
function apagarToken() { try { localStorage.removeItem(CHAVE_TOKEN); } catch { /* ignora */ } }
const ehAdmin = () => sessao?.usuario?.perfil === 'ADMIN';

async function requisitar(caminho, method, body) {
  const token = lerToken();
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = 'Bearer ' + token;
  let res;
  try {
    res = await fetch(API + caminho, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  } catch {
    throw new Error('Não foi possível conectar ao servidor. O back-end está rodando?');
  }
  if (res.status === 204) return null;
  const texto = await res.text();
  let dados = null;
  try { dados = texto ? JSON.parse(texto) : null; } catch { /* resposta não-JSON */ }
  // Sessão expirada ou inválida: volta para a tela de login (exceto no próprio login)
  if (res.status === 401 && !caminho.startsWith('/auth/login') && !caminho.startsWith('/auth/cadastro')) {
    encerrarSessao('Sua sessão expirou. Entre novamente.');
    throw new Error('Sessão expirada.');
  }
  if (!res.ok) throw new Error((dados && (dados.erro || dados.message)) || `Erro ${res.status} na requisição`);
  return dados;
}

async function api(caminho, { method = 'GET', body } = {}) {
  if (method !== 'GET') {
    const r = await requisitar(caminho, method, body);
    cacheGet.clear();
    return r;
  }
  const c = cacheGet.get(caminho);
  if (c && Date.now() - c.t < CACHE_MS) return structuredClone(await c.p);
  const p = requisitar(caminho, 'GET');
  cacheGet.set(caminho, { t: Date.now(), p });
  try {
    return structuredClone(await p);
  } catch (err) {
    cacheGet.delete(caminho);
    throw err;
  }
}

/* ---------------------------------------------------------------------
 * Formatação e utilitários
 * ------------------------------------------------------------------- */
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const moedaFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const moeda = (v) => (v == null ? '—' : moedaFmt.format(Number(v)));
const data = (iso) => (iso ? iso.split('-').reverse().join('/') : '—');
const num = (v, casas = 1) => (v == null ? '—' : Number(v).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas }));
const hojeISO = () => {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};
const ou = (v) => (v == null || v === '' ? '—' : esc(v));
const digitos = (v) => String(v ?? '').replace(/\D/g, '');
const normalizar = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const referenciaDe = (iso) => (iso ? `${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '');

function idade(iso) {
  if (!iso) return null;
  const n = new Date(iso + 'T00:00:00'), h = new Date();
  let a = h.getFullYear() - n.getFullYear();
  if (h.getMonth() < n.getMonth() || (h.getMonth() === n.getMonth() && h.getDate() < n.getDate())) a--;
  return a;
}

/** Destaca o termo buscado (ignorando acentos e maiúsculas). */
function destacar(texto, termo) {
  if (texto == null || texto === '') return '—';
  const orig = String(texto).normalize('NFC');
  const t = normalizar(termo).trim();
  if (!t) return esc(orig);
  const i = normalizar(orig).indexOf(t);
  if (i < 0) return esc(orig);
  return `${esc(orig.slice(0, i))}<mark>${esc(orig.slice(i, i + t.length))}</mark>${esc(orig.slice(i + t.length))}`;
}

/** Valida os dígitos verificadores do CPF. */
function cpfValido(v) {
  const d = digitos(v);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const dv = (n) => {
    let s = 0;
    for (let i = 0; i < n; i++) s += Number(d[i]) * (n + 1 - i);
    const r = (s * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return dv(9) === Number(d[9]) && dv(10) === Number(d[10]);
}

function variacao(v, unidade) {
  if (v == null || v === 0) return '';
  const sinal = v > 0 ? '▲ +' : '▼ ';
  return ` <span class="${v > 0 ? 'var-sobe' : 'var-desce'}">${sinal}${num(v)}${unidade}</span>`;
}

const STATUS_MENS = {
  PAGA: ['Paga', 'b-ok'],
  PENDENTE: ['Pendente', 'b-warn'],
  ATRASADA: ['Atrasada', 'b-danger'],
};
const SITUACAO_ALUNO = {
  EM_DIA: ['Em dia', 'b-ok'],
  PENDENTE: ['Vence em breve', 'b-warn'],
  ATRASADO: ['Atrasado', 'b-danger'],
};
const badge = ([texto, classe]) => `<span class="badge ${classe}">${texto}</span>`;

const FORMAS_PAGAMENTO = ['PIX', 'Dinheiro', 'Cartão de crédito', 'Cartão de débito', 'Boleto', 'Transferência'];
const EXERCICIOS_COMUNS = [
  'Supino reto', 'Supino inclinado', 'Supino declinado', 'Crucifixo', 'Crossover', 'Desenvolvimento com halteres',
  'Elevação lateral', 'Elevação frontal', 'Puxada frontal', 'Remada curvada', 'Remada baixa', 'Barra fixa', 'Pulldown',
  'Rosca direta', 'Rosca martelo', 'Rosca concentrada', 'Tríceps na polia', 'Tríceps francês', 'Tríceps testa', 'Mergulho',
  'Agachamento livre', 'Agachamento búlgaro', 'Leg press 45°', 'Cadeira extensora', 'Mesa flexora', 'Cadeira abdutora',
  'Stiff', 'Levantamento terra', 'Afundo', 'Elevação pélvica', 'Panturrilha em pé', 'Abdominal supra', 'Prancha',
  'Burpee', 'Polichinelo', 'Esteira', 'Bicicleta ergométrica', 'Elíptico',
];

/* ---------------------------------------------------------------------
 * Avisos (toasts)
 * ------------------------------------------------------------------- */
function aviso(msg, erro = false) {
  const t = document.createElement('div');
  t.className = 'toast' + (erro ? ' erro' : '');
  t.setAttribute('role', erro ? 'alert' : 'status');
  t.textContent = msg;
  t.title = 'Clique para fechar';
  t.addEventListener('click', () => t.remove());
  $('#toasts').append(t);
  setTimeout(() => t.remove(), erro ? 6000 : 3500);
}

/* =====================================================================
 * MODAL
 * ===================================================================== */
const modal = $('#modal');
let aoEnviar = null;
let assinaturaInicial = '';
let rodapeGuardado = null;
let cliqueComecouFora = false;

/** "Foto" dos valores do formulário, usada para saber se o usuário alterou algo. */
const assinatura = () => $$('#modalCorpo input, #modalCorpo select, #modalCorpo textarea')
  .map((e) => (e.type === 'checkbox' || e.type === 'radio' ? (e.checked ? 1 : 0) : e.value)).join('␟');
const sujo = () => !!aoEnviar && assinatura() !== assinaturaInicial;

function tentarFechar() {
  if (sujo()) mostrarDescartar(); else fecharModal();
}

function mostrarDescartar() {
  if (rodapeGuardado != null) return;
  rodapeGuardado = $('#modalRodape').innerHTML;
  $('#modalRodape').innerHTML = `<span class="aviso-descartar">Descartar as informações preenchidas?</span>
    <button type="button" data-continuar>Continuar editando</button>
    <button type="button" class="primario" data-descartar>Descartar</button>`;
  $('#modalRodape [data-continuar]').focus();
}

function restaurarRodape() {
  if (rodapeGuardado == null) return;
  $('#modalRodape').innerHTML = rodapeGuardado;
  rodapeGuardado = null;
}

modal.addEventListener('mousedown', (e) => { cliqueComecouFora = e.target === modal; });
modal.addEventListener('click', (e) => {
  if (e.target.closest('[data-descartar]')) return fecharModal();
  if (e.target.closest('[data-continuar]')) return restaurarRodape();
  if (e.target.closest('[data-fechar]') || (e.target === modal && cliqueComecouFora)) tentarFechar();
});
// Esc: só fecha direto se não houver nada preenchido.
modal.addEventListener('cancel', (e) => {
  if (sujo()) { e.preventDefault(); mostrarDescartar(); }
});
modal.addEventListener('close', () => { aoEnviar = null; rodapeGuardado = null; document.body.classList.remove('modal-aberto'); });

$('#modalForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!aoEnviar) return fecharModal();
  if (rodapeGuardado != null) restaurarRodape();
  const form = e.currentTarget;
  const botao = $('#modalRodape .primario');
  const textoOriginal = botao?.textContent;
  if (botao) { botao.disabled = true; botao.textContent = 'Salvando…'; }
  $('#modalErro').hidden = true;
  try {
    const resultado = await aoEnviar(form);
    if (resultado?.sucesso) mostrarSucesso(resultado.sucesso); else fecharModal();
  } catch (err) {
    $('#modalErro').textContent = err.message;
    $('#modalErro').hidden = false;
  } finally {
    if (botao?.isConnected) { botao.disabled = false; botao.textContent = textoOriginal; }
  }
});

// Ctrl+Enter salva de qualquer campo (inclusive de dentro das observações).
modal.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && aoEnviar) { e.preventDefault(); $('#modalForm').requestSubmit(); }
});

function abrirModal({ titulo, corpo, rodape, enviar = null, largo = false }) {
  $('#modalTitulo').textContent = titulo;
  // Troca o elemento do corpo por um novo para descartar os eventos do formulário anterior.
  const corpoAntigo = $('#modalCorpo');
  const corpoNovo = corpoAntigo.cloneNode(false);
  corpoAntigo.replaceWith(corpoNovo);
  corpoNovo.innerHTML = corpo;
  corpoNovo.scrollTop = 0;
  $('#modalRodape').innerHTML = rodape ?? '';
  $('#modalErro').hidden = true;
  rodapeGuardado = null;
  modal.classList.toggle('largo', largo);
  aoEnviar = enviar;
  if (!modal.open) modal.showModal();
  document.body.classList.add('modal-aberto'); // pausa as animações de fundo (desempenho)
  const primeiro = $('#modalCorpo input:not([type=hidden]):not([type=radio]), #modalCorpo select, #modalCorpo textarea');
  if (primeiro && enviar) primeiro.focus({ preventScroll: true });
  assinaturaInicial = assinatura();
}

function fecharModal() {
  aoEnviar = null;
  rodapeGuardado = null;
  if (modal.open) modal.close();
  document.body.classList.remove('modal-aberto'); // na hora, sem esperar o evento "close"
}

function mostrarSucesso({ titulo, html, rodape }) {
  abrirModal({
    titulo,
    corpo: `<div class="sucesso-painel"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12.5l5 5L20 6.5"/></svg>${html}</div>`,
    rodape,
  });
}

function confirmar(mensagem, textoBotao = 'Excluir') {
  return new Promise((resolve) => {
    abrirModal({
      titulo: 'Confirmar',
      corpo: `<p class="texto-confirmar">${mensagem}</p>`,
      rodape: `<button type="button" data-fechar>Cancelar</button><button type="submit" class="primario">${textoBotao}</button>`,
      enviar: async () => resolve(true),
    });
    $('#modalRodape .primario').focus();
    modal.addEventListener('close', () => resolve(false), { once: true });
  });
}

/* =====================================================================
 * FORMULÁRIOS
 * Cada campo é um objeto:
 *   { nome, rotulo, tipo, obrigatorio, opcoes, numerico, full, passo, min, max, inteiro,
 *     maxHoje, dica, mascara, placeholder, autocomplete, validar(v, dados), erroServidor: /regex/ }
 * Itens especiais: { secao: 'Título', acao: '<html>' } abre uma seção numerada;
 *                  { html: '...' } insere um bloco livre (resumos, listas...).
 * ===================================================================== */
function campoHTML(c, valores) {
  if (c.html != null) return `<div class="campo full">${c.html}</div>`;
  const v = valores[c.nome] ?? c.padrao ?? '';
  const id = `f_${c.nome}`;
  const descr = `e_${c.nome}${c.dica ? ` d_${c.nome}` : ''}`;
  const req = c.obrigatorio ? 'aria-required="true"' : '';
  const ph = c.placeholder ? `placeholder="${esc(c.placeholder)}"` : '';
  const ac = c.autocomplete ? `autocomplete="${c.autocomplete}"` : 'autocomplete="off"';
  const comum = `name="${c.nome}" id="${id}" ${req} aria-describedby="${descr}"`;
  const marca = c.obrigatorio ? ' <span class="obrig" aria-hidden="true">*</span>' : '';
  let rotulo = `<label class="rotulo" for="${id}">${esc(c.rotulo)}${marca}</label>`;
  let input;

  if (c.tipo === 'select') {
    const vazio = c.vazio === false ? '' : `<option value="">${esc(c.vazio || '— Selecione —')}</option>`;
    input = `<select ${comum}>${vazio}${c.opcoes.map((o) =>
      `<option value="${esc(o.valor)}" ${String(o.valor) === String(v) ? 'selected' : ''}>${esc(o.rotulo)}</option>`).join('')}</select>`;
  } else if (c.tipo === 'textarea') {
    input = `<textarea ${comum} ${ph} rows="3">${esc(v)}</textarea>`;
  } else if (c.tipo === 'checkbox') {
    const marcado = valores[c.nome] ?? c.padrao ?? false;
    rotulo = `<span class="rotulo">${esc(c.rotulo)}</span>`;
    input = `<label class="check"><input type="checkbox" ${comum} ${marcado ? 'checked' : ''}> ${esc(c.textoCheck || '')}</label>`;
  } else if (c.tipo === 'cartoes') {
    rotulo = `<span class="rotulo" id="r_${c.nome}">${esc(c.rotulo)}${marca}</span>`;
    input = `<div class="cartoes" role="radiogroup" aria-labelledby="r_${c.nome}">${c.opcoes.map((o, i) => `
      <label class="cartao">
        <input type="radio" name="${c.nome}" value="${esc(o.valor)}" ${String(o.valor) === String(v) ? 'checked' : ''} ${i === 0 ? `id="${id}"` : ''}>
        <span class="cartao-corpo">
          <b>${esc(o.titulo)}</b>
          ${o.preco ? `<span class="cartao-preco">${o.preco}</span>` : ''}
          <small>${esc(o.detalhe || '')}</small>
        </span>
      </label>`).join('')}</div>`;
  } else {
    const tipo = c.tipo || 'text';
    const modo = c.mascara === 'cpf' ? 'inputmode="numeric"' : c.mascara === 'otp' ? 'inputmode="numeric" maxlength="6" class="codigo-otp"'
      : c.mascara === 'tel' ? 'inputmode="tel"' : tipo === 'number' ? 'inputmode="decimal"' : '';
    const max = c.max != null ? `max="${c.max}"` : c.maxHoje ? `max="${hojeISO()}"` : '';
    input = `<input type="${tipo}" ${comum} ${ph} ${ac} ${modo} value="${esc(v)}" ${c.passo ? `step="${c.passo}"` : ''} ${c.min != null ? `min="${c.min}"` : ''} ${max} ${c.mascara ? `data-mascara="${c.mascara}"` : ''} ${c.lista ? `list="${c.lista}"` : ''}>`;
  }
  return `<div class="campo ${c.full ? 'full' : ''}" data-campo="${c.nome}">
    ${rotulo}${input}
    ${c.dica ? `<small class="dica" id="d_${c.nome}">${c.dica}</small>` : ''}
    <small class="erro-campo" id="e_${c.nome}" role="alert" hidden></small>
  </div>`;
}

function formularioHTML(campos, valores = {}, classe = '') {
  let html = '';
  let grupo = [];
  let n = 0;
  const fecharGrupo = () => {
    if (grupo.length) html += `<div class="campos ${classe}">${grupo.join('')}</div>`;
    grupo = [];
  };
  for (const c of campos) {
    if (c.secao) {
      fecharGrupo();
      html += `<h3 class="form-secao"><span class="n">${String(++n).padStart(2, '0')}</span><span>${esc(c.secao)}</span>${c.acao || ''}</h3>`;
    } else {
      grupo.push(campoHTML(c, valores));
    }
  }
  fecharGrupo();
  return html;
}

function lerCampos(form, campos) {
  const out = {};
  for (const c of campos) {
    if (!c.nome) continue;
    const el = form.elements[c.nome];
    if (!el) continue;
    if (c.tipo === 'checkbox') { out[c.nome] = el.checked; continue; }
    const v = String(el.value ?? '').trim();
    if (v === '') out[c.nome] = null;
    else if (c.tipo === 'number' || c.numerico) out[c.nome] = Number(v.replace(',', '.'));
    else out[c.nome] = v;
  }
  return out;
}

/** Regras de validação comuns + a regra específica do campo. Retorna a mensagem de erro ou null. */
function erroDoCampo(c, v, dados) {
  const vazio = v == null || v === '';
  if (c.obrigatorio && vazio) return c.tipo === 'select' || c.tipo === 'cartoes' ? 'Selecione uma opção' : 'Preencha este campo';
  if (vazio) return null;
  // CPFs já salvos não são revalidados (os dados de exemplo usam CPFs fictícios).
  if (c.mascara === 'cpf' && v !== c.original && !cpfValido(v)) return 'CPF inválido. Confira os números digitados';
  if (c.mascara === 'tel' && ![10, 11].includes(digitos(v).length)) return 'Telefone incompleto. Use DDD + número';
  if (c.tipo === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v)) return 'E-mail inválido';
  if (c.tipo === 'number') {
    if (Number.isNaN(v)) return 'Número inválido';
    if (c.inteiro && !Number.isInteger(v)) return 'Use um número inteiro';
    if (c.min != null && v < c.min) return `O mínimo é ${num(c.min, Number.isInteger(c.min) ? 0 : 2)}`;
    if (c.max != null && v > c.max) return `O máximo é ${num(c.max, Number.isInteger(c.max) ? 0 : 2)}`;
  }
  if (c.tipo === 'date') {
    if (v < '1900-01-01' || v > '2100-12-31') return 'Data inválida';
    if (c.maxHoje && v > hojeISO()) return 'A data não pode estar no futuro';
  }
  return c.validar?.(v, dados) ?? null;
}

function mostrarErroCampo(form, nome, msg) {
  const wrap = form.querySelector(`[data-campo="${nome}"]`);
  if (!wrap) return false;
  wrap.classList.toggle('invalido', !!msg);
  const slot = wrap.querySelector('.erro-campo');
  slot.textContent = msg || '';
  slot.hidden = !msg;
  $$('input, select, textarea', wrap).forEach((i) => (msg ? i.setAttribute('aria-invalid', 'true') : i.removeAttribute('aria-invalid')));
  return true;
}

function focarPrimeiroErro(form) {
  const el = form.querySelector('.campo.invalido input, .campo.invalido select, .campo.invalido textarea');
  if (el) { el.focus({ preventScroll: true }); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
}

/** Tenta encaixar a mensagem do back-end no campo certo ("cpf: inválido", "Já existe ... CPF ..."). */
function errosDoServidor(msg, form, campos) {
  let mapeou = false;
  for (const parte of msg.split(';')) {
    const m = parte.match(/^\s*(\w+):\s*(.+)$/);
    const c = m && campos.find((x) => x.nome === m[1]);
    if (c) { mostrarErroCampo(form, c.nome, `${c.rotulo} ${m[2].trim()}`); mapeou = true; }
  }
  for (const c of campos) {
    if (c.erroServidor?.test(msg)) { mostrarErroCampo(form, c.nome, msg); mapeou = true; }
  }
  return mapeou;
}

function abrirFormulario({ titulo, campos, valores = {}, salvar, classe = '', aoAbrir, largo = false, textoBotao = 'Salvar', validarTudo }) {
  campos.forEach((c) => { if (c.nome) c.original = valores[c.nome] ?? null; });
  const comNome = campos.filter((c) => c.nome);
  abrirModal({
    titulo,
    largo,
    corpo: formularioHTML(campos, valores, classe) + '<p class="legenda-obrig"><span class="obrig">*</span> obrigatório<span class="so-teclado"> · <kbd>Ctrl</kbd>+<kbd>Enter</kbd> salva</span></p>',
    rodape: `<button type="button" data-fechar>Cancelar</button><button type="submit" class="primario">${textoBotao}</button>`,
    enviar: async (form) => {
      const dados = lerCampos(form, campos);
      let invalidos = 0;
      for (const c of comNome) {
        const e = erroDoCampo(c, dados[c.nome], dados);
        mostrarErroCampo(form, c.nome, e);
        if (e) invalidos++;
      }
      if (invalidos) {
        focarPrimeiroErro(form);
        throw new Error(invalidos === 1 ? 'Corrija o campo destacado.' : `Corrija os ${invalidos} campos destacados.`);
      }
      const geral = validarTudo?.(dados, form);
      if (geral) throw new Error(geral);
      try {
        return await salvar(dados, form);
      } catch (err) {
        if (errosDoServidor(err.message, form, comNome)) { focarPrimeiroErro(form); throw new Error('Corrija o campo destacado.'); }
        throw err;
      }
    },
  });

  const corpo = $('#modalCorpo');
  const form = $('#modalForm');
  const validarUm = (nome) => {
    const c = comNome.find((x) => x.nome === nome);
    if (c) mostrarErroCampo(form, nome, erroDoCampo(c, lerCampos(form, campos)[nome], lerCampos(form, campos)));
  };
  // Valida ao sair do campo (se o usuário mexeu nele) e revalida enquanto ele corrige um erro.
  corpo.addEventListener('input', (e) => {
    const w = e.target.closest('[data-campo]');
    if (!w) return;
    w.dataset.tocado = '1';
    if (w.classList.contains('invalido')) validarUm(w.dataset.campo);
  });
  corpo.addEventListener('change', (e) => {
    const w = e.target.closest('[data-campo]');
    if (w && (w.classList.contains('invalido') || e.target.type === 'radio' || e.target.tagName === 'SELECT')) validarUm(w.dataset.campo);
  });
  corpo.addEventListener('focusout', (e) => {
    const w = e.target.closest('[data-campo]');
    if (w?.dataset.tocado) validarUm(w.dataset.campo);
  });

  aplicarMascaras(corpo);
  aoAbrir?.(corpo, form);
  assinaturaInicial = assinatura();
}

/* Máscaras de CPF, telefone e maiúsculas */
function aplicarMascaras(raiz) {
  $$('[data-mascara]', raiz).forEach((el) => {
    el.addEventListener('input', () => {
      const d = digitos(el.value);
      if (el.dataset.mascara === 'cpf') {
        el.value = d.slice(0, 11).replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d{1,2})$/, '$1-$2');
      } else if (el.dataset.mascara === 'tel') {
        const x = d.slice(0, 11);
        el.value = x.length > 10
          ? x.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3')
          : x.replace(/(\d{2})(\d{0,4})(\d{0,4})/, (m, a, b, c) => (b ? `(${a}) ${b}${c ? '-' + c : ''}` : a));
      } else if (el.dataset.mascara === 'otp') {
        el.value = d.slice(0, 6);
      } else if (el.dataset.mascara === 'maiusculas') {
        const p = el.selectionStart;
        el.value = el.value.toUpperCase();
        el.setSelectionRange(p, p);
      }
    });
  });
}

/* ---------------------------------------------------------------------
 * Listas auxiliares (para os selects)
 * ------------------------------------------------------------------- */
const opcoesAlunos = (alunos) => alunos.map((a) => ({ valor: a.id, rotulo: a.nome + (a.ativo ? '' : ' (inativo)') }));
const opcoesProfessores = (profs) => profs.map((p) => ({ valor: p.id, rotulo: p.nome + (p.especialidade ? ` — ${p.especialidade}` : '') }));
const opcoesFormas = FORMAS_PAGAMENTO.map((f) => ({ valor: f, rotulo: f }));

function selectFiltro(id, opcoes, selecionado, vazio, rotulo = vazio) {
  return `<select id="${id}" aria-label="${esc(rotulo)}"><option value="">${esc(vazio)}</option>${opcoes.map((o) =>
    `<option value="${esc(o.valor)}" ${String(o.valor) === String(selecionado ?? '') ? 'selected' : ''}>${esc(o.rotulo)}</option>`).join('')}</select>`;
}

/* =====================================================================
 * TABELAS: rótulos para o modo "cartão" no celular e ordenação por coluna
 * <table class="responsiva" data-chave="..."> e <th data-ordem="texto|numero">;
 * células podem ter data-v com o valor usado na ordenação (ex.: data ISO).
 * ===================================================================== */
const ordemTabelas = {};

function prepararTabelas(raiz = conteudo) {
  $$('table.responsiva', raiz).forEach((tabela) => {
    if (tabela.dataset.pronta) return; // cada tabela é preparada uma única vez
    tabela.dataset.pronta = '1';
    const ths = $$('thead th', tabela);
    $$('tbody tr', tabela).forEach((tr) => [...tr.cells].forEach((td, i) => {
      const r = ths[i]?.textContent.trim();
      if (r) td.dataset.label = r;
    }));
    ths.forEach((th) => {
      if (!th.dataset.ordem) return;
      th.setAttribute('aria-sort', 'none');
      th.innerHTML = `<button type="button" class="ordenar" title="Ordenar">${th.innerHTML}<i aria-hidden="true"></i></button>`;
    });
    const chave = tabela.dataset.chave;
    if (ordemTabelas[chave]) ordenarTabela(tabela, ordemTabelas[chave].col, ordemTabelas[chave].dir);
    tabela.addEventListener('click', (e) => {
      const th = e.target.closest('.ordenar')?.closest('th');
      if (!th) return;
      const col = ths.indexOf(th);
      const atual = ordemTabelas[chave];
      const dir = atual?.col === col && atual.dir === 'asc' ? 'desc' : 'asc';
      ordemTabelas[chave] = { col, dir };
      ordenarTabela(tabela, col, dir);
    });
  });
}

function ordenarTabela(tabela, col, dir) {
  const ths = $$('thead th', tabela);
  const tipo = ths[col]?.dataset.ordem;
  if (!tipo) return;
  const valor = (tr) => {
    const td = tr.cells[col];
    const v = td?.dataset.v ?? td?.textContent.trim() ?? '';
    return tipo === 'numero' ? (v === '' ? -Infinity : Number(v)) : v;
  };
  const linhas = [...tabela.tBodies[0].rows];
  linhas.sort((a, b) => {
    const x = valor(a), y = valor(b);
    const r = tipo === 'numero' ? x - y : String(x).localeCompare(String(y), 'pt-BR', { numeric: true, sensitivity: 'base' });
    return dir === 'asc' ? r : -r;
  });
  tabela.tBodies[0].append(...linhas);
  ths.forEach((th, i) => { if (th.dataset.ordem) th.setAttribute('aria-sort', i === col ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'); });
}

/* =====================================================================
 * ROTEAMENTO (#/modulo?param=valor)
 * ===================================================================== */
const conteudo = $('#conteudo');

function rotaAtual() {
  const [caminho, query = ''] = location.hash.replace(/^#\/?/, '').split('?');
  return { modulo: caminho || 'dashboard', params: Object.fromEntries(new URLSearchParams(query)) };
}

function montarHash(modulo, params) {
  const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')).toString();
  return `#/${modulo}${q ? '?' + q : ''}`;
}

function irPara(modulo, params = {}) {
  const novo = montarHash(modulo, params);
  if (location.hash === novo) renderizar(); else location.hash = novo;
}

/** Atualiza a URL (para recarregar/compartilhar com os mesmos filtros) sem redesenhar a tela. */
const atualizarUrl = (modulo, params) => history.replaceState(null, '', montarHash(modulo, params));

const ORDEM_MODULOS = ['dashboard', 'alunos', 'planos', 'mensalidades', 'professores', 'treinos', 'avaliacoes', 'usuarios'];
const ROTULOS = {
  dashboard: 'Painel de controle', alunos: 'Gestão de alunos', planos: 'Planos e preços', mensalidades: 'Financeiro',
  professores: 'Equipe', treinos: 'Treinos', avaliacoes: 'Acompanhamento', usuarios: 'Acesso ao sistema',
};

/** Título em duas linhas: a primeira em vermelho, a segunda só com contorno. Ex.: 'Nossos|Alunos'. */
function cabecalho(titulo, subtitulo, acoes = '', rotulo = '') {
  const [l1, l2 = ''] = titulo.split('|');
  const { modulo } = rotaAtual();
  const n = String(Math.max(ORDEM_MODULOS.indexOf(modulo), 0) + 1).padStart(2, '0');
  $('#kicker').innerHTML = `<b>${n}</b> &nbsp;/&nbsp; ${esc(rotulo || ROTULOS[modulo] || l1)}`;
  $('#titulo .t1').textContent = l1;
  $('#titulo .t2').textContent = l2;
  $('#subtitulo').innerHTML = subtitulo;
  $('#acoes').innerHTML = acoes;
  document.title = `${(l1 + ' ' + l2).trim()} · ${sessao?.academia?.nome || 'Força Total'}`;
}

/* Menu mobile */
$('#hamburguer').addEventListener('click', () => {
  const aberto = document.body.classList.toggle('menu-aberto');
  $('#hamburguer').setAttribute('aria-expanded', aberto);
});
window.addEventListener('hashchange', () => {
  document.body.classList.remove('menu-aberto');
  $('#hamburguer').setAttribute('aria-expanded', 'false');
});

/* Atalho: "/" vai para a busca da tela */
document.addEventListener('keydown', (e) => {
  if (e.key !== '/' || modal.open || /INPUT|SELECT|TEXTAREA/.test(document.activeElement?.tagName)) return;
  const busca = $('#conteudo .busca');
  if (busca) { e.preventDefault(); busca.focus(); busca.select(); }
});

let ultimoModulo = null;

async function renderizar() {
  if (!sessao) return; // sem login, só a tela de acesso aparece
  const { modulo, params } = rotaAtual();
  const tela = TELAS[modulo] || TELAS.dashboard;
  $$('#menu a').forEach((a) => {
    const ativo = a.dataset.mod === modulo;
    a.classList.toggle('ativo', ativo);
    if (ativo) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
  // Ao trocar de módulo mostra o carregamento; ao atualizar o mesmo módulo mantém a tela (sem "pulos").
  const trocou = modulo !== ultimoModulo;
  ultimoModulo = modulo;
  if (trocou) conteudo.innerHTML = '<div class="carregando" role="status" aria-label="Carregando"></div>';
  try {
    await tela(params);
    prepararTabelas(conteudo);
    if (trocou) window.scrollTo({ top: 0, behavior: 'instant' });
  } catch (err) {
    conteudo.innerHTML = `<div class="erro-api"><strong>Não foi possível carregar os dados.</strong>${esc(err.message)}<br><br>
      Inicie o back-end (classe <code>AcademiaApplication</code> no IntelliJ) e <button type="button" class="link" onclick="location.reload()">recarregue a página</button>.</div>`;
  }
}

window.addEventListener('hashchange', renderizar);

/* Delegação de cliques: botões com data-acao="nome" chamam ACOES[nome](id, botao) */
const ACOES = {};
document.addEventListener('click', async (e) => {
  const b = e.target.closest('[data-acao]');
  if (!b) return;
  e.preventDefault();
  const fn = ACOES[b.dataset.acao];
  if (!fn || b.dataset.ocupado) return;
  b.dataset.ocupado = '1'; // evita abrir duas vezes com clique duplo
  try {
    await fn(b.dataset.id ? Number(b.dataset.id) : undefined, b);
  } catch (err) {
    aviso(err.message, true);
  } finally {
    delete b.dataset.ocupado;
  }
});

/* =====================================================================
 * TELAS
 * ===================================================================== */
const TELAS = {};

/* ---------------------------- INÍCIO ---------------------------- */
const FRASES = ['Força Total é para todos', 'Disciplina', 'Constância', 'Resultado', 'Treino é compromisso', 'Supere seus limites'];

TELAS.dashboard = async () => {
  const hoje = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  cabecalho('Visão|Geral',
    `Hoje é <b>${esc(hoje)}</b> na <b>${esc(sessao?.academia?.nome || 'academia')}</b>. Alunos, pagamentos e treinos em um só lugar.`,
    `<button data-acao="novaAvaliacao">Nova avaliação</button><button class="primario" data-acao="novoAluno">+ Matricular aluno</button>`,
    'Painel de controle');
  const [d, mensalidades] = await Promise.all([api('/dashboard'), api('/mensalidades')]);
  const atrasadas = mensalidades.filter((m) => m.status === 'ATRASADA').sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento));
  const proximas = mensalidades.filter((m) => m.status === 'PENDENTE').sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento)).slice(0, 6);

  const destaque = (href, valor, rotulo, fantasma, escuro = false) =>
    `<a class="destaque ${escuro ? 'escuro' : ''}" href="${href}"><span class="fantasma" aria-hidden="true">${fantasma}</span><span class="valor">${valor}</span><span class="rotulo">${rotulo}</span></a>`;
  const numero = (href, valor, rotulo) => `<a class="numero" href="${href}"><b>${valor}</b><span>${rotulo}</span></a>`;
  const frases = FRASES.map((f) => `<span>${f}</span>`).join('');

  // Academia recém-cadastrada: guia de primeiros passos
  const comecando = d.totalAlunos === 0 && d.planosAtivos === 0;
  const passo = (n, titulo, texto, botao) => `<li><span class="n">${n}</span><div><b>${titulo}</b><p>${texto}</p>${botao}</div></li>`;
  const guia = !comecando ? '' : `
    <section class="painel primeiros-passos">
      <h2 class="titulo-secao">Primeiros <span>passos</span></h2>
      <p>A <b>${esc(sessao?.academia?.nome || 'sua academia')}</b> está pronta. Os dados dela ficam separados de todas as outras academias do sistema.</p>
      <ol>
        ${passo('01', 'Crie seus planos', 'Defina os planos e preços que a academia oferece.',
          ehAdmin() ? '<button class="pequeno primario" data-acao="novoPlano">+ Criar plano</button>' : '<small>Peça a um administrador.</small>')}
        ${passo('02', 'Cadastre os professores', 'Eles serão responsáveis pelas fichas e avaliações.', '<button class="pequeno" data-acao="novoProfessor">+ Novo professor</button>')}
        ${passo('03', 'Matricule os alunos', 'A primeira mensalidade é gerada automaticamente pelo plano.', '<button class="pequeno" data-acao="novoAluno">+ Matricular aluno</button>')}
        ${ehAdmin() ? passo('04', 'Convide sua equipe', 'Crie logins para os funcionários da recepção.', '<a class="btn pequeno" href="#/usuarios">Gerenciar usuários</a>') : ''}
      </ol>
    </section>`;

  conteudo.innerHTML = `${guia}
    <div class="destaques">
      ${destaque('#/alunos', d.alunosAtivos, `Alunos ativos · ${d.totalAlunos} cadastrados`, d.alunosAtivos)}
      ${destaque('#/mensalidades?status=PAGA', moeda(d.recebidoNoMes), 'Recebido neste mês', 'R$')}
      ${destaque('#/mensalidades?status=ATRASADA', d.mensalidadesAtrasadas, 'Mensalidades atrasadas', d.mensalidadesAtrasadas, d.mensalidadesAtrasadas === 0)}
      ${destaque('#/mensalidades?status=ABERTO', moeda(d.valorEmAberto), 'Total em aberto', '$', true)}
    </div>
    <div class="numeros">
      ${numero('#/mensalidades?status=PENDENTE', d.mensalidadesPendentes, 'A vencer')}
      ${numero('#/planos', d.planosAtivos, 'Planos ativos')}
      ${numero('#/professores', d.professores, 'Professores')}
      ${numero('#/treinos', d.fichasTreino, 'Fichas de treino')}
      ${numero('#/avaliacoes', d.avaliacoes, 'Avaliações físicas')}
    </div>
    <div class="ticker" aria-hidden="true"><div class="ticker-trilho">${frases}</div></div>
    <div class="grade-2">
      <section>
        <h2 class="titulo-secao">Mensalidades <span>atrasadas</span><a class="link" href="#/mensalidades?status=ATRASADA">Ver todas</a></h2>
        <div class="painel">${tabelaMensalidadesCurta(atrasadas.slice(0, 6), 'Tudo em dia', 'Nenhuma mensalidade atrasada.')}</div>
      </section>
      <section>
        <h2 class="titulo-secao">Próximos <span>vencimentos</span><a class="link" href="#/mensalidades?status=PENDENTE">Ver todas</a></h2>
        <div class="painel">${tabelaMensalidadesCurta(proximas, 'Nada a vencer', 'Nenhuma mensalidade pendente.')}</div>
      </section>
    </div>`;
  window.montarTickers?.(conteudo); // repete as frases até cobrir a largura da tela
};

function tabelaMensalidadesCurta(lista, titulo, msgVazio) {
  if (!lista.length) return `<div class="vazio"><strong>${titulo}</strong>${msgVazio}</div>`;
  return `<div class="tabela-wrap"><table class="responsiva"><thead><tr><th>Aluno</th><th>Ref.</th><th>Vencimento</th><th class="num">Valor</th><th></th></tr></thead><tbody>
    ${lista.map((m) => `<tr>
      <td><strong>${esc(m.alunoNome)}</strong></td><td>${esc(m.referencia)}</td><td>${data(m.dataVencimento)}</td>
      <td class="num valor-celula">${moeda(m.valor)}</td>
      <td class="acoes-linha"><button class="pequeno primario" data-acao="pagar" data-id="${m.id}">Receber</button></td>
    </tr>`).join('')}</tbody></table></div>`;
}

/* ---------------------------- ALUNOS ---------------------------- */
const FILTROS_ALUNO = [
  { valor: 'EM_DIA', rotulo: 'Em dia', teste: (a) => a.ativo && a.situacaoFinanceira === 'EM_DIA' },
  { valor: 'PENDENTE', rotulo: 'Vence em breve', teste: (a) => a.ativo && a.situacaoFinanceira === 'PENDENTE' },
  { valor: 'ATRASADO', rotulo: 'Atrasados', teste: (a) => a.ativo && a.situacaoFinanceira === 'ATRASADO' },
  { valor: 'SEM_PLANO', rotulo: 'Sem plano', teste: (a) => !a.planoId },
  { valor: 'INATIVO', rotulo: 'Inativos', teste: (a) => !a.ativo },
];

function alunoCombina(a, termo) {
  const t = normalizar(termo).trim();
  if (!t) return true;
  const d = digitos(termo);
  return normalizar(a.nome).includes(t) || normalizar(a.email).includes(t)
    || (d.length >= 3 && (digitos(a.cpf).includes(d) || digitos(a.telefone).includes(d)));
}

TELAS.alunos = async (params) => {
  cabecalho('Nossos|Alunos', 'Cadastre, consulte e gerencie quem treina na academia.<span class="so-teclado"> Aperte <kbd>/</kbd> para buscar.</span>',
    `<button class="primario" data-acao="novoAluno">+ Novo aluno</button>`);
  const alunos = await api('/alunos');

  conteudo.innerHTML = `
    <div class="barra">
      <input id="buscaAluno" class="busca" type="search" placeholder="Nome, CPF, e-mail ou telefone…" value="${esc(params.busca || '')}" aria-label="Buscar alunos">
      ${selectFiltro('fSituacao', FILTROS_ALUNO, params.situacao, 'Todas as situações', 'Filtrar por situação')}
      <span class="resumo-filtro" id="contagem" aria-live="polite"></span>
    </div>
    <div class="painel" id="listaAlunos"></div>`;

  const desenhar = () => {
    const termo = $('#buscaAluno').value;
    const sit = FILTROS_ALUNO.find((f) => f.valor === $('#fSituacao').value);
    const lista = alunos.filter((a) => alunoCombina(a, termo) && (!sit || sit.teste(a)));
    $('#contagem').innerHTML = `<strong>${lista.length}</strong> de ${alunos.length} aluno(s)`;
    $('#listaAlunos').innerHTML = lista.length ? `<div class="tabela-wrap"><table class="responsiva" data-chave="alunos">
      <thead><tr><th data-ordem="texto">Aluno</th><th>CPF</th><th>Telefone</th><th data-ordem="texto">Plano</th><th data-ordem="texto">Matrícula</th><th data-ordem="texto">Financeiro</th><th></th></tr></thead>
      <tbody>${lista.map((a) => `<tr>
        <td class="nome-celula"><strong>${destacar(a.nome, termo)}</strong><small>${a.email ? destacar(a.email, termo) : 'sem e-mail'}</small></td>
        <td>${ou(a.cpf)}</td>
        <td>${ou(a.telefone)}</td>
        <td>${a.planoNome ? esc(a.planoNome) : '<span class="badge b-mute">Sem plano</span>'}</td>
        <td data-v="${a.dataMatricula}">${data(a.dataMatricula)}</td>
        <td>${a.ativo ? badge(SITUACAO_ALUNO[a.situacaoFinanceira] || ['—', 'b-mute']) : '<span class="badge b-mute">Inativo</span>'}</td>
        <td class="acoes-linha">
          <button class="pequeno" data-acao="verAluno" data-id="${a.id}">Ficha</button>
          <button class="pequeno" data-acao="editarAluno" data-id="${a.id}">Editar</button>
          <button class="pequeno perigo" data-acao="excluirAluno" data-id="${a.id}">Excluir</button>
        </td></tr>`).join('')}</tbody></table></div>`
      : `<div class="vazio"><strong>Nenhum aluno encontrado</strong>${alunos.length ? 'Tente outro termo ou limpe os filtros.' : 'Cadastre o primeiro aluno.'}</div>`;
    prepararTabelas($('#listaAlunos'));
    atualizarUrl('alunos', { busca: termo.trim(), situacao: $('#fSituacao').value });
  };

  $('#buscaAluno').addEventListener('input', desenhar);
  $('#fSituacao').addEventListener('change', desenhar);
  desenhar();
  if (params.busca) $('#buscaAluno').focus();
};

async function formAluno(aluno) {
  const planos = await api('/planos');
  const disponiveis = planos.filter((p) => p.ativo || p.id === aluno?.planoId);
  const duracao = (m) => (m === 1 ? 'Mensal · 1 mês' : `Contrato de ${m} meses`);
  const novo = !aluno;

  const campos = [
    { secao: 'Dados pessoais' },
    { nome: 'nome', rotulo: 'Nome completo', obrigatorio: true, full: true, autocomplete: 'name', placeholder: 'Ex.: Maria da Silva',
      validar: (v) => (v.split(/\s+/).length < 2 ? 'Informe nome e sobrenome' : v.length > 120 ? 'Máximo de 120 caracteres' : null) },
    { nome: 'cpf', rotulo: 'CPF', mascara: 'cpf', placeholder: '000.000.000-00', erroServidor: /CPF/i },
    { nome: 'dataNascimento', rotulo: 'Data de nascimento', tipo: 'date', maxHoje: true, dica: '<span id="idadeInfo">&nbsp;</span>',
      validar: (v) => (idade(v) < 3 ? 'Confira a data: idade abaixo de 3 anos' : idade(v) > 110 ? 'Confira o ano de nascimento' : null) },
    { secao: 'Contato' },
    { nome: 'telefone', rotulo: 'Celular / WhatsApp', mascara: 'tel', placeholder: '(00) 00000-0000', autocomplete: 'tel' },
    { nome: 'email', rotulo: 'E-mail', tipo: 'email', placeholder: 'nome@email.com', autocomplete: 'email' },
    { secao: 'Matrícula' },
    { nome: 'planoId', rotulo: 'Plano', tipo: 'cartoes', numerico: true, full: true, padrao: '',
      opcoes: [
        ...disponiveis.map((p) => ({ valor: p.id, titulo: p.nome, preco: `${moeda(p.valorMensal)}<small>/mês</small>`, detalhe: duracao(p.duracaoMeses) + (p.ativo ? '' : ' · desativado') })),
        { valor: '', titulo: 'Sem plano', detalhe: 'Escolher depois' },
      ] },
    { nome: 'dataMatricula', rotulo: 'Data de matrícula', tipo: 'date', padrao: hojeISO(), obrigatorio: true },
    ...(novo ? [] : [{ nome: 'ativo', rotulo: 'Situação', tipo: 'checkbox', textoCheck: 'Aluno ativo (frequentando a academia)', padrao: true }]),
    { html: '<div class="resumo-modal" id="resumoMatricula" aria-live="polite"></div>' },
  ];

  abrirFormulario({
    titulo: novo ? 'Matricular aluno' : 'Editar aluno',
    largo: true,
    textoBotao: novo ? 'Matricular' : 'Salvar alterações',
    campos,
    valores: aluno ? { ...aluno, planoId: aluno.planoId ?? '' } : {},
    aoAbrir: (corpo, form) => {
      const atualizar = () => {
        const nasc = form.elements.dataNascimento.value;
        const anos = idade(nasc);
        $('#idadeInfo').textContent = nasc && anos >= 0 ? `${anos} anos` : ' ';
        const plano = planos.find((p) => String(p.id) === form.elements.planoId.value);
        const matricula = form.elements.dataMatricula.value;
        let html;
        if (!novo) {
          html = plano && plano.id !== aluno.planoId
            ? `Trocando para o plano <strong>${esc(plano.nome)}</strong>. As mensalidades já lançadas não mudam. As próximas geradas usarão <strong>${moeda(plano.valorMensal)}</strong>.`
            : 'As mensalidades já lançadas não são alteradas ao editar o aluno.';
        } else if (plano) {
          html = `Ao matricular, a <strong>1ª mensalidade de ${moeda(plano.valorMensal)}</strong> (ref. ${referenciaDe(matricula) || '—'}) será lançada com vencimento em <strong>${data(matricula)}</strong>.`;
        } else {
          html = 'Sem plano, nenhuma mensalidade é gerada agora. Você pode escolher um plano depois.';
        }
        $('#resumoMatricula').innerHTML = html;
      };
      corpo.addEventListener('input', atualizar);
      corpo.addEventListener('change', atualizar);
      atualizar();
    },
    salvar: async (dados) => {
      if (!novo) {
        await api(`/alunos/${aluno.id}`, { method: 'PUT', body: dados });
        aviso('Aluno atualizado.');
        renderizar();
        return null;
      }
      const criado = await api('/alunos', { method: 'POST', body: dados });
      renderizar();
      return {
        sucesso: {
          titulo: 'Aluno matriculado',
          html: `<h3>${esc(criado.nome)}</h3>
            <p>${criado.planoNome ? `Plano <b>${esc(criado.planoNome)}</b> · 1ª mensalidade lançada.` : 'Matriculado sem plano.'}</p>
            <p class="dica">Próximos passos sugeridos:</p>
            <div class="proximos">
              <button type="button" data-acao="novoTreino" data-id="${criado.id}">Montar ficha de treino</button>
              <button type="button" data-acao="novaAvaliacao" data-id="${criado.id}">Registrar avaliação física</button>
              <button type="button" data-acao="verAluno" data-id="${criado.id}">Ver ficha do aluno</button>
            </div>`,
          rodape: `<button type="button" data-acao="novoAluno">+ Matricular outro</button><button type="button" class="primario" data-fechar>Concluir</button>`,
        },
      };
    },
  });
}

ACOES.novoAluno = () => formAluno(null);
ACOES.editarAluno = async (id) => formAluno(await api(`/alunos/${id}`));
ACOES.excluirAluno = async (id) => {
  const a = await api(`/alunos/${id}`);
  if (!(await confirmar(`Excluir o aluno <strong>${esc(a.nome)}</strong>?<br><br>As mensalidades, fichas de treino e avaliações físicas dele também serão excluídas. Essa ação não pode ser desfeita.`))) return;
  await api(`/alunos/${id}`, { method: 'DELETE' });
  aviso('Aluno excluído.');
  renderizar();
};

ACOES.verAluno = async (id) => {
  const [a, mens, treinos, avals] = await Promise.all([
    api(`/alunos/${id}`),
    api(`/mensalidades?alunoId=${id}`),
    api(`/treinos?alunoId=${id}`),
    api(`/avaliacoes?alunoId=${id}`),
  ]);
  const ultima = avals[0];
  const anos = idade(a.dataNascimento);
  const item = (rot, val) => `<div><small>${rot}</small><span>${val}</span></div>`;
  abrirModal({
    titulo: a.nome,
    largo: true,
    corpo: `
      <div class="detalhe-grid">
        ${item('CPF', ou(a.cpf))}${item('E-mail', ou(a.email))}${item('Telefone', ou(a.telefone))}
        ${item('Nascimento', a.dataNascimento ? `${data(a.dataNascimento)} · ${anos} anos` : '—')}${item('Matrícula', data(a.dataMatricula))}${item('Plano', ou(a.planoNome))}
        ${item('Situação', a.ativo ? 'Ativo' : 'Inativo')}${item('Financeiro', badge(SITUACAO_ALUNO[a.situacaoFinanceira] || ['—', 'b-mute']))}
        ${item('Última avaliação', ultima ? `${data(ultima.dataAvaliacao)} · IMC ${num(ultima.imc)}` : '—')}
      </div>
      <div class="detalhe-secao">
        <h4><span>Mensalidades<em>${mens.length}</em></span> <a class="link" href="#/mensalidades?aluno=${id}" data-fechar>Gerenciar</a></h4>
        ${mens.length ? `<table class="responsiva"><thead><tr><th>Ref.</th><th>Vencimento</th><th>Pagamento</th><th class="num">Valor</th><th>Status</th></tr></thead><tbody>
          ${mens.slice(0, 6).map((m) => `<tr><td>${esc(m.referencia)}</td><td>${data(m.dataVencimento)}</td><td>${data(m.dataPagamento)}</td><td class="num valor-celula">${moeda(m.valor)}</td><td>${badge(STATUS_MENS[m.status])}</td></tr>`).join('')}
        </tbody></table>` : '<div class="vazio"><strong>Sem mensalidades</strong></div>'}
      </div>
      <div class="detalhe-secao">
        <h4><span>Fichas de treino<em>${treinos.length}</em></span> <a class="link" href="#/treinos?aluno=${id}" data-fechar>Gerenciar</a></h4>
        ${treinos.length ? `<table class="responsiva"><thead><tr><th>Ficha</th><th>Objetivo</th><th>Exercícios</th><th>Status</th></tr></thead><tbody>${treinos.map((t) => `<tr><td><strong>${esc(t.nome)}</strong></td><td>${ou(t.objetivo)}</td><td>${t.exercicios.length}</td><td>${t.vigente ? badge(['Vigente', 'b-ok']) : badge(['Encerrada', 'b-mute'])}</td></tr>`).join('')}</tbody></table>`
          : '<div class="vazio"><strong>Sem fichas de treino</strong></div>'}
      </div>
      <div class="detalhe-secao">
        <h4><span>Avaliações<em>${avals.length}</em></span> <a class="link" href="#/avaliacoes?aluno=${id}" data-fechar>Ver evolução</a></h4>
        ${avals.length ? `<table class="responsiva"><thead><tr><th>Data</th><th class="num">Peso</th><th class="num">IMC</th><th class="num">% Gordura</th></tr></thead><tbody>
          ${avals.map((v) => `<tr><td>${data(v.dataAvaliacao)}</td><td class="num">${num(v.peso)} kg</td><td class="num">${num(v.imc)}</td><td class="num">${v.percentualGordura == null ? '—' : num(v.percentualGordura) + '%'}</td></tr>`).join('')}
        </tbody></table>` : '<div class="vazio"><strong>Sem avaliações</strong></div>'}
      </div>`,
    rodape: `<button type="button" class="pequeno" data-acao="editarAluno" data-id="${id}">Editar aluno</button><button type="button" class="primario" data-fechar>Fechar</button>`,
  });
  prepararTabelas($('#modalCorpo'));
};

/* ---------------------------- PLANOS ---------------------------- */
TELAS.planos = async () => {
  const admin = ehAdmin();
  cabecalho('Nossos|Planos', admin
    ? 'Os planos oferecidos pela academia. Desative um plano para parar de vendê-lo sem afetar quem já está matriculado.'
    : 'Os planos oferecidos pela academia. <b>Somente administradores</b> criam ou alteram planos.',
  admin ? `<button class="primario" data-acao="novoPlano">+ Novo plano</button>` : '');
  const [planos, alunos] = await Promise.all([api('/planos'), api('/alunos')]);
  const qtd = (id) => alunos.filter((a) => a.planoId === id && a.ativo).length;
  const duracao = (m) => (m === 1 ? '1 mês' : `${m} meses`);

  conteudo.innerHTML = planos.length ? `<div class="planos">${planos.map((p) => {
    const [reais, centavos] = Number(p.valorMensal).toFixed(2).split('.');
    return `
    <article class="plano ${p.ativo ? '' : 'inativo'}">
      <div class="plano-topo">
        ${p.ativo ? badge(['Ativo', 'b-ok']) : badge(['Inativo', 'b-mute'])}
        <h3>${esc(p.nome)}</h3>
        <small>Contrato de ${duracao(p.duracaoMeses)}</small>
      </div>
      <div class="plano-corpo">
        <div class="preco"><sup>R$</sup>${Number(reais).toLocaleString('pt-BR')}<small>,${centavos} / mês</small></div>
        <p>${ou(p.descricao)}</p>
        <ul>
          <li><span>Alunos ativos</span><b>${qtd(p.id)}</b></li>
          <li><span>Total do contrato</span><b>${moeda(p.valorMensal * p.duracaoMeses)}</b></li>
        </ul>
      </div>
      ${admin ? `<div class="plano-rodape">
        <button data-acao="editarPlano" data-id="${p.id}">Editar</button>
        <button data-acao="alternarPlano" data-id="${p.id}">${p.ativo ? 'Desativar' : 'Ativar'}</button>
        <button class="perigo" data-acao="excluirPlano" data-id="${p.id}">Excluir</button>
      </div>` : ''}
    </article>`;
  }).join('')}</div>`
    : `<div class="painel vazio"><strong>Nenhum plano</strong>${admin ? 'Crie o primeiro plano da academia.' : 'Um administrador precisa cadastrar os planos.'}</div>`;
};

function corpoPlano(p) {
  return { nome: p.nome, descricao: p.descricao, valorMensal: p.valorMensal, duracaoMeses: p.duracaoMeses, ativo: p.ativo };
}

function formPlano(plano) {
  abrirFormulario({
    titulo: plano ? 'Editar plano' : 'Novo plano',
    campos: [
      { secao: 'Identificação' },
      { nome: 'nome', rotulo: 'Nome do plano', obrigatorio: true, placeholder: 'Ex.: Semestral', full: true,
        validar: (v) => (v.length > 80 ? 'Máximo de 80 caracteres' : null) },
      { nome: 'ativo', rotulo: 'Situação', tipo: 'checkbox', textoCheck: 'Disponível para novas matrículas', padrao: true, full: true },
      { secao: 'Preço' },
      { nome: 'valorMensal', rotulo: 'Valor mensal (R$)', tipo: 'number', passo: '0.01', min: 1, max: 10000, obrigatorio: true, placeholder: '0,00' },
      { nome: 'duracaoMeses', rotulo: 'Duração do contrato (meses)', tipo: 'number', passo: '1', min: 1, max: 60, inteiro: true, obrigatorio: true, padrao: 1 },
      { html: '<div class="resumo-modal" id="resumoPlano" aria-live="polite"></div>' },
      { secao: 'Benefícios' },
      { nome: 'descricao', rotulo: 'Descrição', tipo: 'textarea', full: true, placeholder: 'Ex.: Musculação + aulas coletivas',
        validar: (v) => (v.length > 255 ? 'Máximo de 255 caracteres' : null) },
    ],
    valores: plano || {},
    aoAbrir: (corpo, form) => {
      const atualizar = () => {
        const v = parseFloat(form.elements.valorMensal.value), m = parseInt(form.elements.duracaoMeses.value, 10);
        $('#resumoPlano').innerHTML = v > 0 && m > 0
          ? `Total do contrato: <strong>${moeda(v * m)}</strong> em ${m} ${m === 1 ? 'mês' : 'meses'}.`
          : 'Informe o valor e a duração para ver o total do contrato.';
      };
      corpo.addEventListener('input', atualizar);
      atualizar();
    },
    salvar: async (dados) => {
      if (plano) await api(`/planos/${plano.id}`, { method: 'PUT', body: dados });
      else await api('/planos', { method: 'POST', body: dados });
      aviso(plano ? 'Plano atualizado.' : 'Plano cadastrado.');
      renderizar();
    },
  });
}

ACOES.novoPlano = () => formPlano(null);
ACOES.editarPlano = async (id) => formPlano(await api(`/planos/${id}`));
ACOES.alternarPlano = async (id) => {
  const p = await api(`/planos/${id}`);
  await api(`/planos/${id}`, { method: 'PUT', body: { ...corpoPlano(p), ativo: !p.ativo } });
  aviso(p.ativo ? 'Plano desativado.' : 'Plano ativado.');
  renderizar();
};
ACOES.excluirPlano = async (id) => {
  const p = await api(`/planos/${id}`);
  if (!(await confirmar(`Excluir o plano <strong>${esc(p.nome)}</strong>?`))) return;
  await api(`/planos/${id}`, { method: 'DELETE' });
  aviso('Plano excluído.');
  renderizar();
};

/* ------------------------- MENSALIDADES ------------------------- */
const FILTROS_MENS = [
  ...Object.entries(STATUS_MENS).map(([k, [r]]) => ({ valor: k, rotulo: r, teste: (m) => m.status === k })),
  { valor: 'ABERTO', rotulo: 'Em aberto (pendente + atrasada)', teste: (m) => m.status !== 'PAGA' },
];

TELAS.mensalidades = async (params) => {
  cabecalho('Controle de|Mensalidades', 'Pagamentos, pendências e atrasos. Gere as mensalidades pelo plano do aluno e registre cada recebimento.',
    `<button data-acao="gerarMensalidades">Gerar mensalidades</button><button class="primario" data-acao="novaMensalidade">+ Nova mensalidade</button>`);
  const [todas, alunos] = await Promise.all([api('/mensalidades'), api('/alunos')]);

  conteudo.innerHTML = `
    <div class="barra">
      ${selectFiltro('fAluno', opcoesAlunos(alunos), params.aluno, 'Todos os alunos', 'Filtrar por aluno')}
      ${selectFiltro('fStatus', FILTROS_MENS, params.status, 'Todas as situações', 'Filtrar por situação')}
      <span class="resumo-filtro" id="contagem" aria-live="polite"></span>
    </div>
    <div class="painel" id="listaMens"></div>`;

  const desenhar = () => {
    const alunoId = $('#fAluno').value;
    const filtro = FILTROS_MENS.find((f) => f.valor === $('#fStatus').value);
    const lista = todas.filter((m) => (!alunoId || String(m.alunoId) === alunoId) && (!filtro || filtro.teste(m)));
    const somar = (st) => lista.filter((m) => st.includes(m.status)).reduce((s, m) => s + Number(m.valor), 0);
    $('#contagem').innerHTML = `<strong>${lista.length}</strong> mensalidade(s) · Recebido <strong>${moeda(somar(['PAGA']))}</strong> · Em aberto <strong>${moeda(somar(['PENDENTE', 'ATRASADA']))}</strong>`;
    $('#listaMens').innerHTML = lista.length ? `<div class="tabela-wrap"><table class="responsiva" data-chave="mensalidades">
      <thead><tr><th data-ordem="texto">Aluno</th><th data-ordem="texto">Referência</th><th data-ordem="texto">Vencimento</th><th class="num" data-ordem="numero">Valor</th><th data-ordem="texto">Pagamento</th><th>Forma</th><th data-ordem="texto">Status</th><th></th></tr></thead>
      <tbody>${lista.map((m) => `<tr>
        <td><strong>${esc(m.alunoNome)}</strong></td>
        <td data-v="${m.referencia.slice(3)}-${m.referencia.slice(0, 2)}">${esc(m.referencia)}</td>
        <td data-v="${m.dataVencimento}">${data(m.dataVencimento)}</td>
        <td class="num valor-celula" data-v="${m.valor}">${moeda(m.valor)}</td>
        <td data-v="${m.dataPagamento || ''}">${data(m.dataPagamento)}</td>
        <td>${ou(m.formaPagamento)}</td>
        <td data-v="${m.status === 'ATRASADA' ? 0 : m.status === 'PENDENTE' ? 1 : 2}">${badge(STATUS_MENS[m.status])}</td>
        <td class="acoes-linha">
          ${m.status === 'PAGA'
            ? `<button class="pequeno" data-acao="estornar" data-id="${m.id}">Estornar</button>`
            : `<button class="pequeno primario" data-acao="pagar" data-id="${m.id}">Registrar pagamento</button>`}
          <button class="pequeno" data-acao="editarMensalidade" data-id="${m.id}">Editar</button>
          <button class="pequeno perigo" data-acao="excluirMensalidade" data-id="${m.id}">Excluir</button>
        </td></tr>`).join('')}</tbody></table></div>`
      : `<div class="vazio"><strong>Nenhuma mensalidade</strong>Altere os filtros ou gere mensalidades para um aluno.</div>`;
    prepararTabelas($('#listaMens'));
    atualizarUrl('mensalidades', { aluno: alunoId, status: $('#fStatus').value });
  };

  $('#fAluno').addEventListener('change', desenhar);
  $('#fStatus').addEventListener('change', desenhar);
  desenhar();
};

ACOES.pagar = async (id) => {
  const m = await api(`/mensalidades/${id}`);
  abrirFormulario({
    titulo: 'Registrar pagamento',
    textoBotao: 'Confirmar pagamento',
    campos: [
      { html: `<p class="resumo-modal"><strong>${moeda(m.valor)}</strong><br>${esc(m.alunoNome)} · referência ${esc(m.referencia)} · vencimento ${data(m.dataVencimento)}${m.status === 'ATRASADA' ? ' · <b class="txt-alerta">atrasada</b>' : ''}</p>` },
      { nome: 'formaPagamento', rotulo: 'Forma de pagamento', tipo: 'select', opcoes: opcoesFormas, vazio: false, padrao: 'PIX' },
      { nome: 'dataPagamento', rotulo: 'Data do pagamento', tipo: 'date', padrao: hojeISO(), obrigatorio: true, maxHoje: true },
    ],
    salvar: async (dados) => {
      await api(`/mensalidades/${id}/pagar`, { method: 'POST', body: dados });
      aviso(`Pagamento de ${moeda(m.valor)} registrado.`);
      renderizar();
    },
  });
};

ACOES.estornar = async (id) => {
  const m = await api(`/mensalidades/${id}`);
  if (!(await confirmar(`Estornar o pagamento de <strong>${esc(m.alunoNome)}</strong> (ref. ${esc(m.referencia)}, ${moeda(m.valor)})?<br>A mensalidade voltará a ficar em aberto.`, 'Estornar'))) return;
  await api(`/mensalidades/${id}/estornar`, { method: 'POST' });
  aviso('Pagamento estornado.');
  renderizar();
};

async function formMensalidade(m) {
  const [alunos, planos] = await Promise.all([api('/alunos'), api('/planos')]);
  abrirFormulario({
    titulo: m ? 'Editar mensalidade' : 'Nova mensalidade',
    campos: [
      { secao: 'Cobrança' },
      { nome: 'alunoId', rotulo: 'Aluno', tipo: 'select', numerico: true, obrigatorio: true, opcoes: opcoesAlunos(alunos), full: true, padrao: rotaAtual().params.aluno },
      { nome: 'dataVencimento', rotulo: 'Vencimento', tipo: 'date', obrigatorio: true, padrao: hojeISO() },
      { nome: 'valor', rotulo: 'Valor (R$)', tipo: 'number', passo: '0.01', min: 1, max: 10000, placeholder: 'Valor do plano', erroServidor: /valor/i, dica: '<span id="dicaValor">Em branco usa o valor do plano do aluno</span>' },
      { nome: 'referencia', rotulo: 'Referência (MM/AAAA)', placeholder: 'Automática pelo vencimento', full: true,
        validar: (v) => (/^(0[1-9]|1[0-2])\/\d{4}$/.test(v) ? null : 'Use o formato MM/AAAA, ex.: 03/2026') },
      { secao: 'Pagamento (opcional)' },
      { nome: 'dataPagamento', rotulo: 'Data de pagamento', tipo: 'date', maxHoje: true, dica: 'Em branco se ainda não foi paga' },
      { nome: 'formaPagamento', rotulo: 'Forma de pagamento', tipo: 'select', opcoes: opcoesFormas, vazio: '—',
        validar: (v, d) => (!d.dataPagamento ? 'Informe também a data do pagamento' : null) },
    ],
    valores: m || {},
    aoAbrir: (corpo, form) => {
      const atualizar = () => {
        const a = alunos.find((x) => String(x.id) === form.elements.alunoId.value);
        const p = a && planos.find((x) => x.id === a.planoId);
        form.elements.valor.placeholder = p ? moeda(p.valorMensal) : 'Valor do plano';
        $('#dicaValor').textContent = !a ? 'Em branco usa o valor do plano do aluno'
          : p ? `Em branco usa ${moeda(p.valorMensal)} (plano ${p.nome})` : 'Este aluno não tem plano: informe o valor';
        form.elements.referencia.placeholder = referenciaDe(form.elements.dataVencimento.value) || 'MM/AAAA';
      };
      corpo.addEventListener('change', atualizar);
      corpo.addEventListener('input', atualizar);
      atualizar();
    },
    salvar: async (dados) => {
      if (m) await api(`/mensalidades/${m.id}`, { method: 'PUT', body: dados });
      else await api('/mensalidades', { method: 'POST', body: dados });
      aviso(m ? 'Mensalidade atualizada.' : 'Mensalidade lançada.');
      renderizar();
    },
  });
}

ACOES.novaMensalidade = () => formMensalidade(null);
ACOES.editarMensalidade = async (id) => formMensalidade(await api(`/mensalidades/${id}`));
ACOES.excluirMensalidade = async (id) => {
  const m = await api(`/mensalidades/${id}`);
  if (!(await confirmar(`Excluir a mensalidade de <strong>${esc(m.alunoNome)}</strong> (ref. ${esc(m.referencia)})?`))) return;
  await api(`/mensalidades/${id}`, { method: 'DELETE' });
  aviso('Mensalidade excluída.');
  renderizar();
};

ACOES.gerarMensalidades = async () => {
  const [todos, planos] = await Promise.all([api('/alunos'), api('/planos')]);
  const alunos = todos.filter((a) => a.planoId);
  abrirFormulario({
    titulo: 'Gerar mensalidades',
    textoBotao: 'Gerar',
    campos: [
      { nome: 'alunoId', rotulo: 'Aluno', tipo: 'select', numerico: true, obrigatorio: true, full: true, padrao: rotaAtual().params.aluno,
        opcoes: alunos.map((a) => ({ valor: a.id, rotulo: `${a.nome} — plano ${a.planoNome}` })), dica: 'Só aparecem alunos com plano' },
      { nome: 'meses', rotulo: 'Quantidade de meses', tipo: 'number', min: 1, max: 12, passo: '1', inteiro: true, obrigatorio: true, padrao: 3 },
      { html: '<div class="resumo-modal" id="resumoGerar" aria-live="polite"></div>' },
    ],
    aoAbrir: (corpo, form) => {
      const atualizar = () => {
        const a = alunos.find((x) => String(x.id) === form.elements.alunoId.value);
        const p = a && planos.find((x) => x.id === a.planoId);
        const n = parseInt(form.elements.meses.value, 10);
        $('#resumoGerar').innerHTML = p && n > 0
          ? `<strong>${n} × ${moeda(p.valorMensal)} = ${moeda(n * p.valorMensal)}</strong><br>Criadas mês a mês após a última já lançada. Meses repetidos são ignorados.`
          : 'Escolha o aluno e a quantidade de meses.';
      };
      corpo.addEventListener('input', atualizar);
      corpo.addEventListener('change', atualizar);
      atualizar();
    },
    salvar: async (dados) => {
      const criadas = await api('/mensalidades/gerar', { method: 'POST', body: dados });
      aviso(criadas.length ? `${criadas.length} mensalidade(s) gerada(s).` : 'Nenhuma mensalidade nova (os meses já existiam).');
      irPara('mensalidades', { aluno: dados.alunoId });
    },
  });
};

/* -------------------------- PROFESSORES -------------------------- */
TELAS.professores = async (params) => {
  cabecalho('Equipe de|Professores', 'Os profissionais responsáveis pelos treinos e pelas avaliações físicas.',
    `<button class="primario" data-acao="novoProfessor">+ Novo professor</button>`);
  const [profs, treinos] = await Promise.all([api('/professores'), api('/treinos')]);
  const fichasDe = (id) => treinos.filter((t) => t.professorId === id).length;

  conteudo.innerHTML = `
    <div class="barra">
      <input id="buscaProf" class="busca" type="search" placeholder="Nome, especialidade ou CREF…" value="${esc(params.busca || '')}" aria-label="Buscar professores">
      <span class="resumo-filtro" id="contagem" aria-live="polite"></span>
    </div>
    <div class="painel" id="listaProf"></div>`;

  const desenhar = () => {
    const termo = $('#buscaProf').value;
    const t = normalizar(termo).trim();
    const lista = profs.filter((p) => !t || [p.nome, p.especialidade, p.cref, p.email].some((x) => normalizar(x).includes(t)));
    $('#contagem').innerHTML = `<strong>${lista.length}</strong> de ${profs.length} professor(es)`;
    $('#listaProf').innerHTML = lista.length ? `<div class="tabela-wrap"><table class="responsiva" data-chave="professores">
      <thead><tr><th data-ordem="texto">Professor</th><th data-ordem="texto">Especialidade</th><th>CREF</th><th>CPF</th><th>Telefone</th><th class="num" data-ordem="numero">Fichas</th><th></th></tr></thead>
      <tbody>${lista.map((p) => `<tr>
        <td class="nome-celula"><strong>${destacar(p.nome, termo)}</strong><small>${p.email ? destacar(p.email, termo) : 'sem e-mail'}</small></td>
        <td>${destacar(p.especialidade, termo)}</td><td>${destacar(p.cref, termo)}</td><td>${ou(p.cpf)}</td><td>${ou(p.telefone)}</td>
        <td class="num" data-v="${fichasDe(p.id)}">${fichasDe(p.id)}</td>
        <td class="acoes-linha">
          <button class="pequeno" data-acao="editarProfessor" data-id="${p.id}">Editar</button>
          <button class="pequeno perigo" data-acao="excluirProfessor" data-id="${p.id}">Excluir</button>
        </td></tr>`).join('')}</tbody></table></div>`
      : `<div class="vazio"><strong>Nenhum professor encontrado</strong>${profs.length ? 'Tente outro termo de busca.' : 'Cadastre o primeiro professor.'}</div>`;
    prepararTabelas($('#listaProf'));
    atualizarUrl('professores', { busca: termo.trim() });
  };

  $('#buscaProf').addEventListener('input', desenhar);
  desenhar();
  if (params.busca) $('#buscaProf').focus();
};

function formProfessor(p) {
  abrirFormulario({
    titulo: p ? 'Editar professor' : 'Novo professor',
    campos: [
      { secao: 'Dados pessoais' },
      { nome: 'nome', rotulo: 'Nome completo', obrigatorio: true, full: true, autocomplete: 'name',
        validar: (v) => (v.split(/\s+/).length < 2 ? 'Informe nome e sobrenome' : null) },
      { nome: 'cpf', rotulo: 'CPF', mascara: 'cpf', placeholder: '000.000.000-00', erroServidor: /CPF/i, full: true },
      { secao: 'Atuação' },
      { nome: 'especialidade', rotulo: 'Especialidade', placeholder: 'Ex.: Musculação, Funcional', lista: 'listaEspecialidades' },
      { nome: 'cref', rotulo: 'CREF', placeholder: '000000-G/SP', mascara: 'maiusculas', dica: 'Registro no Conselho de Educação Física',
        validar: (v) => (/^\d{4,6}-[GP]\/[A-Z]{2}$/.test(v) ? null : 'Formato: 000000-G/UF') },
      { html: `<datalist id="listaEspecialidades">${['Musculação', 'Funcional', 'Pilates', 'Crossfit', 'Spinning', 'Yoga', 'Lutas', 'Natação', 'Dança'].map((e) => `<option value="${e}">`).join('')}</datalist>` },
      { secao: 'Contato' },
      { nome: 'telefone', rotulo: 'Celular / WhatsApp', mascara: 'tel', placeholder: '(00) 00000-0000', autocomplete: 'tel' },
      { nome: 'email', rotulo: 'E-mail', tipo: 'email', placeholder: 'nome@academia.com', autocomplete: 'email' },
    ],
    valores: p || {},
    salvar: async (dados) => {
      if (p) await api(`/professores/${p.id}`, { method: 'PUT', body: dados });
      else await api('/professores', { method: 'POST', body: dados });
      aviso(p ? 'Professor atualizado.' : 'Professor cadastrado.');
      renderizar();
    },
  });
}

ACOES.novoProfessor = () => formProfessor(null);
ACOES.editarProfessor = async (id) => formProfessor(await api(`/professores/${id}`));
ACOES.excluirProfessor = async (id) => {
  const p = await api(`/professores/${id}`);
  if (!(await confirmar(`Excluir o professor <strong>${esc(p.nome)}</strong>?<br>As fichas e avaliações dele continuam salvas, sem professor responsável.`))) return;
  await api(`/professores/${id}`, { method: 'DELETE' });
  aviso('Professor excluído.');
  renderizar();
};

/* ---------------------------- TREINOS ---------------------------- */
TELAS.treinos = async (params) => {
  cabecalho('Fichas de|Treino', 'Monte a ficha de cada aluno com exercícios, séries, repetições e carga.',
    `<button class="primario" data-acao="novoTreino">+ Nova ficha</button>`);
  const [fichas, alunos] = await Promise.all([
    api('/treinos' + (params.aluno ? `?alunoId=${params.aluno}` : '')),
    api('/alunos'),
  ]);
  const periodo = (t) => `${data(t.dataInicio)} — ${t.dataFim ? data(t.dataFim) : 'sem fim'}`;

  conteudo.innerHTML = `
    <div class="barra">
      ${selectFiltro('fAluno', opcoesAlunos(alunos), params.aluno, 'Todos os alunos', 'Filtrar por aluno')}
      <span class="resumo-filtro"><strong>${fichas.length}</strong> ficha(s) · <strong>${fichas.filter((f) => f.vigente).length}</strong> vigente(s)</span>
    </div>
    ${fichas.length ? `<div class="fichas">${fichas.map((t) => `
      <article class="painel ficha">
        <div class="ficha-topo">
          ${t.vigente ? badge(['Vigente', 'b-ok']) : badge(['Encerrada', 'b-mute'])}
          <h3>${esc(t.nome)}</h3>
          <div class="aluno">${esc(t.alunoNome)}</div>
        </div>
        <div class="ficha-info">
          <div><small>Objetivo</small><b>${ou(t.objetivo)}</b></div>
          <div><small>Professor</small><b>${ou(t.professorNome)}</b></div>
          <div><small>Período</small><b>${periodo(t)}</b></div>
        </div>
        ${t.exercicios.length ? `<table>
          <thead><tr><th>#</th><th>Exercício</th><th class="num">Séries</th><th class="num">Repetições</th><th>Carga</th></tr></thead>
          <tbody>${t.exercicios.map((e, i) => `<tr><td>${String(i + 1).padStart(2, '0')}</td><td>${esc(e.nome)}</td><td class="num">${e.series ?? '—'}</td><td class="num">${ou(e.repeticoes)}</td><td>${ou(e.carga)}</td></tr>`).join('')}</tbody>
        </table>` : '<div class="vazio"><strong>Sem exercícios</strong></div>'}
        ${t.observacoes ? `<p class="obs"><b>Obs.</b>${esc(t.observacoes)}</p>` : ''}
        <div class="ficha-rodape">
          <button class="pequeno" data-acao="duplicarTreino" data-id="${t.id}">Duplicar</button>
          <button class="pequeno" data-acao="editarTreino" data-id="${t.id}">Editar</button>
          <button class="pequeno perigo" data-acao="excluirTreino" data-id="${t.id}">Excluir</button>
        </div>
      </article>`).join('')}</div>`
    : `<div class="painel vazio"><strong>Nenhuma ficha</strong>Crie uma ficha para ${params.aluno ? 'este aluno' : 'um aluno'}.</div>`}`;

  $('#fAluno').addEventListener('change', (e) => irPara('treinos', { aluno: e.target.value }));
};

function linhaExercicio(e = {}) {
  return `<div class="ex-linha">
    <span class="n"></span>
    <input data-ex="nome" placeholder="Nome do exercício" aria-label="Exercício" list="listaExercicios" value="${esc(e.nome)}">
    <input data-ex="series" type="number" min="1" max="20" inputmode="numeric" placeholder="Séries" aria-label="Séries" value="${esc(e.series)}">
    <input data-ex="repeticoes" placeholder="Ex.: 12" aria-label="Repetições" value="${esc(e.repeticoes)}">
    <input data-ex="carga" placeholder="Ex.: 20 kg" aria-label="Carga" value="${esc(e.carga)}">
    <button type="button" class="fechar" data-remover-ex aria-label="Remover exercício"><svg viewBox="0 0 24 24"><path d="M5 5l14 14M19 5L5 19"/></svg></button>
  </div>`;
}

function numerarExercicios(raiz) {
  // Só as linhas da lista (o cabeçalho também usa a classe .ex-linha, mas não tem número).
  $$('#listaEx .ex-linha', raiz).forEach((l, i) => { $('.n', l).textContent = String(i + 1).padStart(2, '0'); });
}

async function formTreino(t, { copia = false, alunoId } = {}) {
  const [alunos, profs] = await Promise.all([api('/alunos'), api('/professores')]);
  const editando = t && !copia;
  const valores = t ? { ...t, nome: copia ? `${t.nome} (cópia)` : t.nome } : { alunoId: alunoId ?? rotaAtual().params.aluno };
  const exercicios = t?.exercicios?.length ? t.exercicios : [{}, {}, {}];
  abrirFormulario({
    titulo: editando ? 'Editar ficha de treino' : copia ? 'Duplicar ficha de treino' : 'Nova ficha de treino',
    largo: true,
    campos: [
      { secao: 'Identificação' },
      { nome: 'nome', rotulo: 'Nome da ficha', obrigatorio: true, placeholder: 'Ex.: Treino A - Peito e Tríceps', full: true },
      { nome: 'alunoId', rotulo: 'Aluno', tipo: 'select', numerico: true, obrigatorio: true, opcoes: opcoesAlunos(alunos) },
      { nome: 'professorId', rotulo: 'Professor responsável', tipo: 'select', numerico: true, opcoes: opcoesProfessores(profs), vazio: 'Nenhum' },
      { nome: 'objetivo', rotulo: 'Objetivo', placeholder: 'Ex.: Hipertrofia', lista: 'listaObjetivos' },
      { nome: 'dataInicio', rotulo: 'Início', tipo: 'date', padrao: hojeISO() },
      { nome: 'dataFim', rotulo: 'Fim', tipo: 'date', dica: 'Em branco = sem data de término',
        validar: (v, d) => (d.dataInicio && v < d.dataInicio ? 'O fim deve ser depois do início' : null) },
      { secao: 'Exercícios', acao: '<button type="button" class="pequeno" data-add-ex>+ Adicionar exercício</button>' },
      { html: `
        <div class="ex-linha ex-cab" aria-hidden="true"><span>#</span><span>Exercício</span><span>Séries</span><span>Repetições</span><span>Carga</span><span></span></div>
        <div class="exercicios" id="listaEx">${exercicios.map(linhaExercicio).join('')}</div>
        <small class="erro-campo" id="erroEx" role="alert" hidden></small>
        <datalist id="listaExercicios">${EXERCICIOS_COMUNS.map((e) => `<option value="${e}">`).join('')}</datalist>
        <datalist id="listaObjetivos">${['Hipertrofia', 'Emagrecimento', 'Condicionamento', 'Força', 'Resistência', 'Reabilitação'].map((e) => `<option value="${e}">`).join('')}</datalist>` },
      { secao: 'Observações' },
      { nome: 'observacoes', rotulo: 'Observações', tipo: 'textarea', full: true, placeholder: 'Ex.: Descanso de 60s entre as séries' },
    ],
    valores,
    aoAbrir: (raiz) => {
      const lista = $('#listaEx', raiz);
      numerarExercicios(raiz);
      raiz.addEventListener('click', (e) => {
        if (e.target.closest('[data-add-ex]')) {
          lista.insertAdjacentHTML('beforeend', linhaExercicio());
          numerarExercicios(raiz);
          $('.ex-linha:last-child input', lista).focus();
        } else if (e.target.closest('[data-remover-ex]')) {
          e.target.closest('.ex-linha').remove();
          numerarExercicios(raiz);
        }
      });
      // Enter no último campo de carga cria uma nova linha (montar a ficha só pelo teclado).
      lista.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.ctrlKey && e.target.dataset.ex === 'carga' && e.target.closest('.ex-linha') === lista.lastElementChild) {
          e.preventDefault();
          $('[data-add-ex]', raiz).click();
        }
      });
    },
    validarTudo: () => {
      const temExercicio = $$('#listaEx [data-ex="nome"]').some((i) => i.value.trim());
      const erro = $('#erroEx');
      erro.hidden = temExercicio;
      erro.textContent = temExercicio ? '' : 'Adicione pelo menos um exercício com nome.';
      if (!temExercicio) $('#listaEx [data-ex="nome"]')?.focus();
      return temExercicio ? null : 'A ficha precisa de pelo menos um exercício.';
    },
    salvar: async (dados) => {
      dados.exercicios = $$('#listaEx .ex-linha').map((l) => {
        const v = (k) => $(`[data-ex="${k}"]`, l).value.trim();
        return { nome: v('nome'), series: v('series') ? Number(v('series')) : null, repeticoes: v('repeticoes') || null, carga: v('carga') || null };
      }).filter((e) => e.nome);
      if (editando) await api(`/treinos/${t.id}`, { method: 'PUT', body: dados });
      else await api('/treinos', { method: 'POST', body: dados });
      aviso(editando ? 'Ficha atualizada.' : 'Ficha de treino criada.');
      renderizar();
    },
  });
}

ACOES.novoTreino = (alunoId) => formTreino(null, { alunoId });
ACOES.editarTreino = async (id) => formTreino(await api(`/treinos/${id}`));
ACOES.duplicarTreino = async (id) => formTreino(await api(`/treinos/${id}`), { copia: true });
ACOES.excluirTreino = async (id) => {
  const t = await api(`/treinos/${id}`);
  if (!(await confirmar(`Excluir a ficha <strong>${esc(t.nome)}</strong> de ${esc(t.alunoNome)}?`))) return;
  await api(`/treinos/${id}`, { method: 'DELETE' });
  aviso('Ficha excluída.');
  renderizar();
};

/* ------------------------ AVALIAÇÕES FÍSICAS ------------------------ */
TELAS.avaliacoes = async (params) => {
  cabecalho('Avaliações|Físicas', 'Peso, medidas e composição corporal. Escolha um aluno para ver a <b>evolução</b> em gráficos.',
    `<button class="primario" data-acao="novaAvaliacao">+ Nova avaliação</button>`);
  const [avals, alunos] = await Promise.all([
    api('/avaliacoes' + (params.aluno ? `?alunoId=${params.aluno}` : '')),
    api('/alunos'),
  ]);

  let evolucao = '';
  if (params.aluno) {
    const cron = [...avals].sort((a, b) => a.dataAvaliacao.localeCompare(b.dataAvaliacao));
    if (cron.length >= 1) {
      const serie = (campo) => cron.filter((a) => a[campo] != null).map((a) => ({ x: data(a.dataAvaliacao).slice(0, 5), y: a[campo] }));
      evolucao = `<div class="evolucao">
        ${grafico('Peso', 'kg', serie('peso'))}
        ${grafico('IMC', '', serie('imc'))}
        ${grafico('Gordura corporal', '%', serie('percentualGordura'))}
        ${grafico('Cintura', 'cm', serie('cintura'))}
      </div>`;
    }
  }
  const cm = (v) => (v == null ? '—' : num(v) + ' cm');

  conteudo.innerHTML = `
    <div class="barra">
      ${selectFiltro('fAluno', opcoesAlunos(alunos), params.aluno, 'Todos os alunos', 'Filtrar por aluno')}
      <span class="resumo-filtro">${params.aluno ? '' : 'Escolha um aluno para ver os gráficos · '}<strong>${avals.length}</strong> avaliação(ões)</span>
    </div>
    ${evolucao}
    <div class="painel">
      ${avals.length ? `<div class="tabela-wrap"><table class="responsiva" data-chave="avaliacoes">
        <thead><tr><th data-ordem="texto">Data</th><th data-ordem="texto">Aluno</th><th>Avaliador</th><th class="num" data-ordem="numero">Peso</th><th class="num">Altura</th><th data-ordem="numero">IMC</th><th class="num" data-ordem="numero">% Gordura</th><th class="num">Cintura</th><th class="num">Quadril</th><th class="num">Braço</th><th class="num">Coxa</th><th></th></tr></thead>
        <tbody>${avals.map((a) => `<tr>
          <td data-v="${a.dataAvaliacao}">${data(a.dataAvaliacao)}</td>
          <td><strong>${esc(a.alunoNome)}</strong></td>
          <td>${ou(a.professorNome)}</td>
          <td class="num" data-v="${a.peso}">${num(a.peso)} kg${variacao(a.variacaoPeso, ' kg')}</td>
          <td class="num">${num(a.altura, 2)} m</td>
          <td data-v="${a.imc ?? ''}">${num(a.imc)} ${badge([esc(a.classificacaoImc), classeImc(a.imc)])}</td>
          <td class="num" data-v="${a.percentualGordura ?? ''}">${a.percentualGordura == null ? '—' : num(a.percentualGordura) + '%'}${variacao(a.variacaoGordura, '%')}</td>
          <td class="num">${cm(a.cintura)}</td>
          <td class="num">${cm(a.quadril)}</td>
          <td class="num">${cm(a.braco)}</td>
          <td class="num">${cm(a.coxa)}</td>
          <td class="acoes-linha">
            <button class="pequeno" data-acao="editarAvaliacao" data-id="${a.id}">Editar</button>
            <button class="pequeno perigo" data-acao="excluirAvaliacao" data-id="${a.id}">Excluir</button>
          </td></tr>`).join('')}</tbody></table></div>`
      : `<div class="vazio"><strong>Nenhuma avaliação física</strong>Registre a primeira avaliação.</div>`}
    </div>`;

  $('#fAluno').addEventListener('change', (e) => irPara('avaliacoes', { aluno: e.target.value }));
};

function classeImc(imc) {
  if (imc == null) return 'b-mute';
  if (imc < 18.5) return 'b-info';
  if (imc < 25) return 'b-ok';
  if (imc < 30) return 'b-warn';
  return 'b-danger';
}

const classificarImc = (imc) => (imc < 18.5 ? 'Abaixo do peso' : imc < 25 ? 'Peso normal' : imc < 30 ? 'Sobrepeso'
  : imc < 35 ? 'Obesidade grau I' : imc < 40 ? 'Obesidade grau II' : 'Obesidade grau III');

/** Gráfico de linha simples em SVG. */
let idGrafico = 0;
function grafico(titulo, unidade, pts) {
  if (!pts.length) return `<div class="painel grafico"><h4>${titulo}</h4><div class="delta">Sem dados</div></div>`;
  const W = 300, H = 150, pl = 24, pr = 24, pt = 26, pb = 24;
  const gid = 'gradArea' + (++idGrafico);
  const ys = pts.map((p) => p.y);
  let min = Math.min(...ys), max = Math.max(...ys);
  if (min === max) { min -= 1; max += 1; }
  const folga = (max - min) * 0.15; min -= folga; max += folga;
  const x = (i) => pts.length === 1 ? W / 2 : pl + (i * (W - pl - pr)) / (pts.length - 1);
  const y = (v) => pt + (1 - (v - min) / (max - min)) * (H - pt - pb);
  const linha = pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.y).toFixed(1)}`).join(' ');
  const area = `${linha} L${x(pts.length - 1).toFixed(1)},${H - pb} L${x(0).toFixed(1)},${H - pb} Z`;
  const delta = pts.length > 1 ? pts[pts.length - 1].y - pts[0].y : null;
  const deltaTxt = delta == null ? 'Primeira avaliação'
    : `<b class="${delta < 0 ? 'desce' : delta > 0 ? 'sobe' : ''}">${delta > 0 ? '+' : ''}${num(delta)}${unidade ? ' ' + unidade : ''}</b> desde a primeira avaliação`;
  return `<div class="painel grafico">
    <h4>${titulo}${unidade ? ` <small>(${unidade})</small>` : ''}</h4>
    <div class="delta">${deltaTxt}</div>
    <svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Evolução de ${titulo}">
      <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d30000" stop-opacity=".45"/><stop offset="1" stop-color="#d30000" stop-opacity="0"/></linearGradient></defs>
      <line class="grid" x1="0" x2="${W}" y1="${H - pb}" y2="${H - pb}"/>
      ${pts.length > 1 ? `<path d="${area}" fill="url(#${gid})"/><path class="linha" d="${linha}"/>` : ''}
      ${pts.map((p, i) => `<circle class="pt" cx="${x(i)}" cy="${y(p.y)}" r="4.5"/>
        <text class="rot" x="${x(i)}" y="${y(p.y) - 11}" text-anchor="middle">${num(p.y)}</text>
        <text x="${x(i)}" y="${H - 4}" text-anchor="middle">${p.x}</text>`).join('')}
    </svg>
  </div>`;
}

async function formAvaliacao(a, alunoId) {
  const [alunos, profs] = await Promise.all([api('/alunos'), api('/professores')]);
  const medida = (nome, rotulo) => ({ nome, rotulo, tipo: 'number', passo: '0.1', min: 10, max: 250, placeholder: 'cm' });
  abrirFormulario({
    titulo: a ? 'Editar avaliação física' : 'Nova avaliação física',
    classe: 'tres',
    largo: true,
    campos: [
      { secao: 'Identificação' },
      { nome: 'alunoId', rotulo: 'Aluno', tipo: 'select', numerico: true, obrigatorio: true, opcoes: opcoesAlunos(alunos), padrao: alunoId ?? rotaAtual().params.aluno },
      { nome: 'professorId', rotulo: 'Avaliador', tipo: 'select', numerico: true, opcoes: opcoesProfessores(profs), vazio: 'Nenhum' },
      { nome: 'dataAvaliacao', rotulo: 'Data', tipo: 'date', padrao: hojeISO(), maxHoje: true },
      { secao: 'Composição corporal' },
      { nome: 'peso', rotulo: 'Peso (kg)', tipo: 'number', passo: '0.1', min: 20, max: 350, obrigatorio: true, placeholder: 'Ex.: 72,5' },
      { nome: 'altura', rotulo: 'Altura', tipo: 'number', passo: '0.01', obrigatorio: true, placeholder: 'Ex.: 1,75', dica: 'Em metros (1,75) ou centímetros (175)',
        validar: (v) => ((v >= 1 && v <= 2.5) || (v >= 100 && v <= 250) ? null : 'Use metros (1,75) ou centímetros (175)') },
      { nome: 'percentualGordura', rotulo: '% de gordura', tipo: 'number', passo: '0.1', min: 2, max: 70, placeholder: 'Ex.: 18' },
      { html: '<div class="imc-preview" id="imcPreview" aria-live="polite"></div>' },
      { secao: 'Circunferências' },
      medida('cintura', 'Cintura (cm)'),
      medida('quadril', 'Quadril (cm)'),
      medida('braco', 'Braço (cm)'),
      medida('coxa', 'Coxa (cm)'),
      { secao: 'Observações' },
      { nome: 'observacoes', rotulo: 'Observações', tipo: 'textarea', full: true, placeholder: 'Ex.: Retorno em 30 dias' },
    ],
    valores: a || {},
    aoAbrir: (raiz, form) => {
      const atualizar = () => {
        const p = parseFloat(form.elements.peso.value);
        let h = parseFloat(form.elements.altura.value);
        if (h > 3) h /= 100;
        const el = $('#imcPreview', raiz);
        if (p > 0 && h >= 1 && h <= 2.5) {
          const imc = p / (h * h);
          el.innerHTML = `IMC calculado: <strong>${num(imc, 2)}</strong> ${badge([classificarImc(imc), classeImc(imc)])}`;
        } else el.textContent = 'Informe peso e altura para calcular o IMC.';
      };
      raiz.addEventListener('input', atualizar);
      atualizar();
    },
    salvar: async (dados) => {
      if (a) await api(`/avaliacoes/${a.id}`, { method: 'PUT', body: dados });
      else await api('/avaliacoes', { method: 'POST', body: dados });
      aviso(a ? 'Avaliação atualizada.' : 'Avaliação registrada.');
      if (rotaAtual().modulo === 'avaliacoes' && !rotaAtual().params.aluno) irPara('avaliacoes', { aluno: dados.alunoId });
      else renderizar();
    },
  });
}

ACOES.novaAvaliacao = (alunoId) => formAvaliacao(null, alunoId);
ACOES.editarAvaliacao = async (id) => formAvaliacao(await api(`/avaliacoes/${id}`));
ACOES.excluirAvaliacao = async (id) => {
  const a = await api(`/avaliacoes/${id}`);
  if (!(await confirmar(`Excluir a avaliação de <strong>${esc(a.alunoNome)}</strong> do dia ${data(a.dataAvaliacao)}?`))) return;
  await api(`/avaliacoes/${id}`, { method: 'DELETE' });
  aviso('Avaliação excluída.');
  renderizar();
};

/* ---------------------------- USUÁRIOS (admin) ---------------------------- */
const PERFIS = {
  ADMIN: ['Administrador', 'b-danger'],
  FUNCIONARIO: ['Funcionário', 'b-info'],
};

TELAS.usuarios = async () => {
  if (!ehAdmin()) {
    cabecalho('Usuários da|Academia', 'Somente administradores gerenciam os usuários.');
    conteudo.innerHTML = '<div class="painel vazio"><strong>Acesso restrito</strong>Peça a um administrador da academia.</div>';
    return;
  }
  cabecalho('Usuários da|Academia', `Quem pode entrar no sistema da <b>${esc(sessao.academia.nome)}</b>. Funcionários operam o dia a dia; administradores também cuidam de planos e usuários.`,
    `<button class="primario" data-acao="novoUsuario">+ Novo usuário</button>`);
  const usuarios = await api('/usuarios');
  conteudo.innerHTML = `
    <div class="barra"><span class="resumo-filtro"><strong>${usuarios.length}</strong> usuário(s) · <strong>${usuarios.filter((u) => u.perfil === 'ADMIN' && u.ativo).length}</strong> administrador(es) ativo(s)</span></div>
    <div class="painel"><div class="tabela-wrap"><table class="responsiva" data-chave="usuarios">
      <thead><tr><th data-ordem="texto">Usuário</th><th data-ordem="texto">Perfil</th><th data-ordem="texto">Situação</th><th data-ordem="texto">2 etapas</th><th data-ordem="texto">Desde</th><th></th></tr></thead>
      <tbody>${usuarios.map((u) => {
        const voce = u.id === sessao.usuario.id;
        return `<tr>
        <td class="nome-celula"><strong>${esc(u.nome)}${voce ? ' <span class="badge b-mute">você</span>' : ''}</strong><small>${esc(u.email)}</small></td>
        <td>${badge(PERFIS[u.perfil])}</td>
        <td>${u.ativo ? badge(['Ativo', 'b-ok']) : badge(['Desativado', 'b-mute'])}</td>
        <td>${u.doisFatores ? badge(['Ativa', 'b-ok']) : badge(['Desligada', 'b-mute'])}</td>
        <td data-v="${u.criadoEm}">${data(u.criadoEm.slice(0, 10))}</td>
        <td class="acoes-linha">
          ${u.doisFatores && !voce ? `<button class="pequeno" data-acao="redefinirDoisFatores" data-id="${u.id}" title="Para quem perdeu o celular">Redefinir 2 etapas</button>` : ''}
          <button class="pequeno" data-acao="editarUsuario" data-id="${u.id}">Editar</button>
          ${voce ? '' : `<button class="pequeno perigo" data-acao="excluirUsuario" data-id="${u.id}">Excluir</button>`}
        </td></tr>`;
      }).join('')}</tbody></table></div></div>`;
};

function formUsuario(u) {
  const voce = u && u.id === sessao.usuario.id;
  abrirFormulario({
    titulo: u ? 'Editar usuário' : 'Novo usuário',
    textoBotao: u ? 'Salvar alterações' : 'Criar usuário',
    campos: [
      { secao: 'Dados de acesso' },
      { nome: 'nome', rotulo: 'Nome completo', obrigatorio: true, full: true, autocomplete: 'off',
        validar: (v) => (v.split(/\s+/).length < 2 ? 'Informe nome e sobrenome' : null) },
      { nome: 'email', rotulo: 'E-mail (login)', tipo: 'email', obrigatorio: true, full: true, autocomplete: 'off', erroServidor: /e-mail/i },
      { nome: 'senha', rotulo: u ? 'Nova senha' : 'Senha inicial', tipo: 'password', obrigatorio: !u, autocomplete: 'new-password',
        placeholder: '8 caracteres ou mais', dica: u ? 'Em branco mantém a senha atual. Se trocar, a pessoa precisará entrar de novo.' : 'Passe a senha para a pessoa, que pode trocá-la depois.',
        validar: (v) => (v.length < 8 ? 'A senha precisa de pelo menos 8 caracteres' : v.length > 72 ? 'Máximo de 72 caracteres' : null) },
      { secao: 'Perfil' },
      { nome: 'perfil', rotulo: 'O que essa pessoa pode fazer', tipo: 'cartoes', obrigatorio: true, full: true, padrao: 'FUNCIONARIO',
        opcoes: [
          { valor: 'FUNCIONARIO', titulo: 'Funcionário', detalhe: 'Alunos, mensalidades, professores, treinos e avaliações' },
          { valor: 'ADMIN', titulo: 'Administrador', detalhe: 'Tudo, inclusive planos e usuários' },
        ] },
      ...(u && !voce ? [{ nome: 'ativo', rotulo: 'Situação', tipo: 'checkbox', textoCheck: 'Acesso liberado (desmarque para bloquear o login)', padrao: true, full: true }] : []),
      ...(voce ? [{ html: '<p class="dica">Esta é a sua conta: você não pode tirar seu próprio perfil de administrador nem bloquear seu acesso.</p>' }] : []),
    ],
    valores: u ? { ...u, senha: '' } : {},
    salvar: async (dados) => {
      const corpo = { ...dados, ativo: voce ? true : dados.ativo ?? true };
      if (u) await api(`/usuarios/${u.id}`, { method: 'PUT', body: corpo });
      else await api('/usuarios', { method: 'POST', body: corpo });
      aviso(u ? 'Usuário atualizado.' : `Usuário ${dados.email} criado.`);
      if (voce) { sessao.usuario = { ...sessao.usuario, nome: dados.nome, email: dados.email }; atualizarCabecalhoUsuario(); }
      renderizar();
    },
  });
  if (voce) $$('#modalCorpo input[name=perfil]').forEach((r) => { r.disabled = r.value !== 'ADMIN'; });
}

ACOES.novoUsuario = () => formUsuario(null);
ACOES.editarUsuario = async (id) => formUsuario((await api('/usuarios')).find((u) => u.id === id));
ACOES.excluirUsuario = async (id) => {
  const u = (await api('/usuarios')).find((x) => x.id === id);
  if (!(await confirmar(`Excluir o usuário <strong>${esc(u.nome)}</strong> (${esc(u.email)})?<br>Ele não poderá mais entrar no sistema. Se for algo temporário, prefira desativar.`))) return;
  await api(`/usuarios/${id}`, { method: 'DELETE' });
  aviso('Usuário excluído.');
  renderizar();
};

ACOES.alterarSenha = () => {
  fecharMenuUsuario();
  abrirFormulario({
    titulo: 'Alterar minha senha',
    textoBotao: 'Alterar senha',
    campos: [
      { nome: 'senhaAtual', rotulo: 'Senha atual', tipo: 'password', obrigatorio: true, full: true, autocomplete: 'current-password', erroServidor: /senha atual/i },
      { nome: 'novaSenha', rotulo: 'Nova senha', tipo: 'password', obrigatorio: true, autocomplete: 'new-password', placeholder: '8 caracteres ou mais',
        validar: (v, d) => (v.length < 8 ? 'A senha precisa de pelo menos 8 caracteres' : v === d.senhaAtual ? 'Use uma senha diferente da atual' : null) },
      { nome: 'confirmar', rotulo: 'Confirmar nova senha', tipo: 'password', obrigatorio: true, autocomplete: 'new-password',
        validar: (v, d) => (v !== d.novaSenha ? 'As senhas não conferem' : null) },
    ],
    salvar: async (dados) => {
      await api('/auth/senha', { method: 'POST', body: { senhaAtual: dados.senhaAtual, novaSenha: dados.novaSenha } });
      aviso('Senha alterada.');
    },
  });
};

/* =====================================================================
 * SESSÃO: entrar, sair, tela de login e menu do usuário
 * ===================================================================== */
const iniciais = (nome) => String(nome || '?').trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase();

function atualizarCabecalhoUsuario() {
  const { usuario, academia } = sessao;
  $('#nomeAcademia').textContent = academia.nome;
  $('#nomeUsuario').textContent = `${usuario.nome} · ${PERFIS[usuario.perfil][0]}`;
  $('#avatarUsuario').textContent = iniciais(usuario.nome);
  $('#menuNome').textContent = usuario.nome;
  $('#menuEmail').textContent = usuario.email;
  $('#menuPerfil').className = 'badge ' + PERFIS[usuario.perfil][1];
  $('#menuPerfil').textContent = PERFIS[usuario.perfil][0];
  $('#menuDoisFatores').className = 'badge ' + (usuario.doisFatores ? 'b-ok' : 'b-mute');
  $('#menuDoisFatores').textContent = usuario.doisFatores ? 'Ativa' : 'Desligada';
  document.body.classList.toggle('perfil-admin', ehAdmin());
}

function entrarNoApp(dados) {
  sessao = { usuario: dados.usuario, academia: dados.academia };
  cacheGet.clear(); // nunca reaproveitar dados de outra academia/usuário
  ultimoModulo = null;
  atualizarCabecalhoUsuario();
  document.body.classList.remove('nao-logado', 'verificando');
  if (!ehAdmin() && rotaAtual().modulo === 'usuarios') location.hash = '#/dashboard';
  renderizar();
}

function encerrarSessao(mensagem) {
  apagarToken();
  sessao = null;
  cacheGet.clear();
  ultimoModulo = null;
  fecharModal();
  fecharMenuUsuario();
  document.body.classList.remove('verificando', 'perfil-admin');
  document.body.classList.add('nao-logado');
  conteudo.innerHTML = '';
  document.title = 'Entrar · Força Total';
  trocarAba('entrar');
  const erro = $('#formLogin [data-erro-geral]');
  erro.textContent = mensagem || '';
  erro.hidden = !mensagem;
  erro.classList.toggle('info', !!mensagem);
  setTimeout(() => $('#loginEmail').focus(), 50);
}

ACOES.sair = async () => {
  fecharMenuUsuario();
  try { await requisitar('/auth/logout', 'POST'); } catch { /* sai mesmo sem resposta do servidor */ }
  encerrarSessao('Você saiu do sistema.');
  history.replaceState(null, '', '#/dashboard');
};

/* Menu do usuário no topo */
function fecharMenuUsuario() {
  $('#menuUsuario').hidden = true;
  $('#botaoUsuario').setAttribute('aria-expanded', 'false');
}
$('#botaoUsuario').addEventListener('click', (e) => {
  e.stopPropagation();
  const abrir = $('#menuUsuario').hidden;
  $('#menuUsuario').hidden = !abrir;
  $('#botaoUsuario').setAttribute('aria-expanded', String(abrir));
});
document.addEventListener('click', (e) => { if (!e.target.closest('.usuario-area')) fecharMenuUsuario(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') fecharMenuUsuario(); });
$('#menuUsuario').addEventListener('click', (e) => { if (e.target.closest('a')) fecharMenuUsuario(); });

/* Abas Entrar / Cadastrar academia */
function trocarAba(aba) {
  const entrar = aba === 'entrar';
  desafioAtual = null;
  $('#formDoisFatores').hidden = true;
  $('.login-cartao').classList.remove('etapa-codigo');
  $('#formLogin').hidden = !entrar;
  $('#formCadastro').hidden = entrar;
  $('#abaEntrar').setAttribute('aria-selected', String(entrar));
  $('#abaCadastro').setAttribute('aria-selected', String(!entrar));
}
$('#telaLogin').addEventListener('click', (e) => {
  const b = e.target.closest('[data-aba]');
  if (b) { trocarAba(b.dataset.aba); (b.dataset.aba === 'entrar' ? $('#loginEmail') : $('#cadAcademia')).focus(); }
  const ver = e.target.closest('[data-ver-senha]');
  if (ver) {
    const input = ver.previousElementSibling;
    const mostrar = input.type === 'password';
    input.type = mostrar ? 'text' : 'password';
    ver.textContent = mostrar ? 'Ocultar' : 'Mostrar';
    ver.setAttribute('aria-label', mostrar ? 'Ocultar senha' : 'Mostrar senha');
  }
});

/** Valida um formulário da tela de acesso. regras = { campo: (valor, todos) => mensagem | null } */
function validarAcesso(form, regras) {
  const valores = Object.fromEntries([...new FormData(form)].map(([k, v]) => [k, String(v).trim()]));
  let primeiro = null;
  for (const [campo, regra] of Object.entries(regras)) {
    const msg = regra(valores[campo] ?? '', valores);
    mostrarErroCampo(form, campo, msg);
    if (msg && !primeiro) primeiro = form.elements[campo];
  }
  primeiro?.focus();
  return primeiro ? null : valores;
}
const EMAIL_OK = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const exigir = (msg) => (v) => (!v ? msg : null);

async function enviarAcesso(form, caminho, corpo) {
  const botao = $('button[type=submit]', form);
  const texto = botao.textContent;
  const erro = $('[data-erro-geral]', form);
  erro.hidden = true;
  erro.classList.remove('info');
  botao.disabled = true;
  botao.textContent = 'Aguarde…';
  try {
    const r = await requisitar(caminho, 'POST', corpo);
    if (r.doisFatores) { mostrarEtapaCodigo(r.desafio); return null; } // senha certa, falta o código do celular
    salvarToken(r.token);
    form.reset();
    entrarNoApp(r);
    return r;
  } catch (err) {
    // "Já existe uma conta com este e-mail" pertence ao campo; o resto (senha errada, bloqueio...) é geral
    if (/já existe/i.test(err.message) && form.elements.email) mostrarErroCampo(form, 'email', err.message);
    else { erro.textContent = err.message; erro.hidden = false; }
    return null;
  } finally {
    botao.disabled = false;
    botao.textContent = texto;
  }
}

$('#formLogin').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = validarAcesso(e.currentTarget, {
    email: (x) => (!x ? 'Informe seu e-mail' : !EMAIL_OK.test(x) ? 'E-mail inválido' : null),
    senha: exigir('Informe sua senha'),
  });
  if (v) await enviarAcesso(e.currentTarget, '/auth/login', { email: v.email, senha: $('#loginSenha').value });
});

$('#formCadastro').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = validarAcesso(e.currentTarget, {
    nomeAcademia: exigir('Informe o nome da academia'),
    nome: (x) => (!x ? 'Informe seu nome' : x.split(/\s+/).length < 2 ? 'Informe nome e sobrenome' : null),
    email: (x) => (!x ? 'Informe seu e-mail' : !EMAIL_OK.test(x) ? 'E-mail inválido' : null),
    senha: (x) => (x.length < 8 ? 'A senha precisa de pelo menos 8 caracteres' : null),
    confirmar: (x, t) => (x !== t.senha ? 'As senhas não conferem' : null),
  });
  if (!v) return;
  const r = await enviarAcesso(e.currentTarget, '/auth/cadastro', {
    nomeAcademia: v.nomeAcademia, cidade: v.cidade || null, nome: v.nome, email: v.email, senha: $('#cadSenha').value,
  });
  if (r) { location.hash = '#/dashboard'; aviso(`Cadastro concluído: “${r.academia.nome}” já está pronta para uso.`); }
});

// Erros somem enquanto a pessoa corrige o campo
$('#telaLogin').addEventListener('input', (e) => {
  const w = e.target.closest('[data-campo]');
  if (w?.classList.contains('invalido')) mostrarErroCampo(e.target.form, w.dataset.campo, null);
});

/* =====================================================================
 * VERIFICAÇÃO EM DUAS ETAPAS
 * ===================================================================== */
let desafioAtual = null;     // devolvido pelo login quando a conta usa duas etapas
let modoRecuperacao = false; // digitando um código de recuperação em vez do código do app

function mostrarEtapaCodigo(desafio) {
  desafioAtual = desafio;
  modoRecuperacao = false;
  ajustarModoCodigo();
  $('#formLogin').hidden = true;
  $('#formCadastro').hidden = true;
  $('#formDoisFatores').hidden = false;
  $('.login-cartao').classList.add('etapa-codigo');
  const f = $('#formDoisFatores');
  f.reset();
  mostrarErroCampo(f, 'codigo', null);
  $('[data-erro-geral]', f).hidden = true;
  setTimeout(() => $('#codigo2fa').focus(), 30);
}

function ajustarModoCodigo() {
  const input = $('#codigo2fa');
  input.value = '';
  input.maxLength = modoRecuperacao ? 11 : 6;
  input.inputMode = modoRecuperacao ? 'text' : 'numeric';
  input.placeholder = modoRecuperacao ? 'XXXXX-XXXXX' : '000000';
  input.autocomplete = modoRecuperacao ? 'off' : 'one-time-code';
  input.classList.toggle('recuperacao', modoRecuperacao);
  $('#rotuloCodigo2fa').textContent = modoRecuperacao ? 'Código de recuperação' : 'Código do aplicativo';
  $('#textoDoisFatores').textContent = modoRecuperacao
    ? 'Digite um dos códigos de recuperação que você guardou ao ativar. Cada código só pode ser usado uma vez.'
    : 'Abra o aplicativo autenticador no celular e digite o código de 6 dígitos da sua conta.';
  $('[data-alternar-recuperacao]').textContent = modoRecuperacao ? 'Usar o código do aplicativo' : 'Perdeu o celular? Use um código de recuperação';
}

$('#formDoisFatores').addEventListener('click', (e) => {
  if (e.target.closest('[data-alternar-recuperacao]')) {
    modoRecuperacao = !modoRecuperacao;
    ajustarModoCodigo();
    mostrarErroCampo(e.currentTarget, 'codigo', null);
    $('#codigo2fa').focus();
  } else if (e.target.closest('[data-voltar-login]')) {
    trocarAba('entrar');
    $('#loginSenha').value = '';
    $('#loginSenha').focus();
  }
});

$('#codigo2fa').addEventListener('input', (e) => {
  const el = e.target;
  if (modoRecuperacao) {
    const v = el.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10);
    el.value = v.length > 5 ? `${v.slice(0, 5)}-${v.slice(5)}` : v;
  } else {
    el.value = el.value.replace(/\D/g, '').slice(0, 6);
    if (el.value.length === 6) $('#formDoisFatores').requestSubmit(); // envia sozinho ao completar
  }
});

$('#formDoisFatores').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const codigo = $('#codigo2fa').value.trim();
  const ok = modoRecuperacao ? /^[A-Z0-9]{5}-?[A-Z0-9]{5}$/.test(codigo) : /^\d{6}$/.test(codigo);
  mostrarErroCampo(form, 'codigo', ok ? null : modoRecuperacao ? 'Use o formato XXXXX-XXXXX' : 'Digite os 6 números do aplicativo');
  if (!ok || form.dataset.enviando) return;
  form.dataset.enviando = '1';
  const botao = $('button[type=submit]', form);
  botao.disabled = true;
  botao.textContent = 'Verificando…';
  try {
    const r = await requisitar('/auth/login/2fa', 'POST', { desafio: desafioAtual, codigo });
    salvarToken(r.token);
    desafioAtual = null;
    form.reset();
    $('#formLogin').reset();
    entrarNoApp(r);
    if (modoRecuperacao) aviso('Você entrou com um código de recuperação. Ele não vale mais: confira quantos restam em “Verificação em duas etapas”.');
  } catch (err) {
    // Desafio vencido ou tentativas esgotadas: precisa da senha de novo
    if (/senha de novo|expirou|aguarde/i.test(err.message)) {
      trocarAba('entrar');
      const erro = $('#formLogin [data-erro-geral]');
      erro.textContent = err.message;
      erro.hidden = false;
      $('#loginSenha').value = '';
      $('#loginSenha').focus();
    } else {
      mostrarErroCampo(form, 'codigo', err.message);
      $('#codigo2fa').select();
    }
  } finally {
    delete form.dataset.enviando;
    botao.disabled = false;
    botao.textContent = 'Verificar e entrar';
  }
});

/** Desenha o QR Code (grade de "0" e "1" vinda do servidor) em SVG. */
function qrSvg(linhas) {
  const n = linhas.length;
  let d = '';
  linhas.forEach((linha, y) => { for (let x = 0; x < linha.length; x++) if (linha[x] === '1') d += `M${x} ${y}h1v1h-1z`; });
  return `<svg class="qr" viewBox="0 0 ${n} ${n}" shape-rendering="crispEdges" role="img" aria-label="QR Code para o aplicativo autenticador">
    <rect width="${n}" height="${n}" fill="#fff" stroke="none"/><path d="${d}" fill="#000" stroke="none"/></svg>`;
}

ACOES.doisFatores = async () => {
  fecharMenuUsuario();
  const st = await requisitar('/auth/2fa', 'GET');
  if (st.ativo) return telaDoisFatoresAtiva(st);
  abrirModal({
    titulo: 'Verificação em duas etapas',
    corpo: `
      <div class="explica-2fa">
        <p><b>Proteja sua conta mesmo se alguém descobrir sua senha.</b> Depois de ativar, o login pede também um código de 6 dígitos que muda a cada 30 segundos no seu celular.</p>
        <ol class="passos-2fa">
          <li>Instale um aplicativo autenticador no celular: <b>Google Authenticator</b>, <b>Microsoft Authenticator</b> ou <b>Authy</b> (gratuitos).</li>
          <li>Leia o QR Code que vamos mostrar.</li>
          <li>Digite o código que aparecer no aplicativo para confirmar.</li>
        </ol>
      </div>`,
    rodape: `<button type="button" data-fechar>Agora não</button><button type="button" class="primario" data-acao="iniciarDoisFatores">Configurar agora</button>`,
  });
};

ACOES.iniciarDoisFatores = async () => {
  const ini = await requisitar('/auth/2fa/iniciar', 'POST');
  abrirFormulario({
    titulo: 'Configurar o aplicativo',
    textoBotao: 'Ativar',
    campos: [
      { html: `
        <div class="qr-bloco">
          ${qrSvg(ini.qr)}
          <div>
            <p><b>1.</b> No aplicativo autenticador, toque em <b>+</b> e escolha <b>ler QR Code</b>.</p>
            <p><b>2.</b> Não consegue ler? Escolha <b>inserir chave</b> e digite:</p>
            <code class="segredo-2fa" id="segredo2fa">${esc(ini.segredo)}</code>
            <p class="dica">Conta: ${esc(sessao.usuario.email)} · tipo: baseado em tempo</p>
            <p><b>3.</b> Digite abaixo o código de 6 dígitos que aparecer.</p>
          </div>
        </div>` },
      { nome: 'codigo', rotulo: 'Código do aplicativo', mascara: 'otp', obrigatorio: true, placeholder: '000000', full: true, erroServidor: /código/i,
        validar: (v) => (/^\d{6}$/.test(v) ? null : 'Digite os 6 números que aparecem no aplicativo') },
    ],
    salvar: async (dados) => {
      const r = await api('/auth/2fa/ativar', { method: 'POST', body: { codigo: dados.codigo } });
      sessao.usuario.doisFatores = true;
      atualizarCabecalhoUsuario();
      if (rotaAtual().modulo === 'usuarios') renderizar();
      return { sucesso: telaCodigosRecuperacao(r.codigos) };
    },
  });
};

function telaCodigosRecuperacao(codigos) {
  window.__codigosRecuperacao = codigos;
  return {
    titulo: 'Verificação ativada',
    html: `<h3>Guarde seus códigos de recuperação</h3>
      <p>Se perder o celular, cada código abaixo permite entrar <b>uma vez</b>. Eles <b>não aparecerão de novo</b>: copie, baixe ou anote em um lugar seguro.</p>
      <div class="codigos-grid">${codigos.map((c) => `<code>${esc(c)}</code>`).join('')}</div>
      <div class="proximos dois">
        <button type="button" data-acao="copiarCodigos">Copiar códigos</button>
        <button type="button" data-acao="baixarCodigos">Baixar arquivo .txt</button>
      </div>`,
    rodape: '<button type="button" class="primario" data-fechar>Já guardei os códigos</button>',
  };
}

const textoCodigos = () => ['Força Total — códigos de recuperação da verificação em duas etapas',
  `Conta: ${sessao.usuario.email}`, 'Cada código pode ser usado uma única vez.', '', ...window.__codigosRecuperacao].join('\n');

ACOES.copiarCodigos = async () => {
  try { await navigator.clipboard.writeText(textoCodigos()); aviso('Códigos copiados.'); }
  catch { aviso('Não foi possível copiar automaticamente. Selecione os códigos e copie.', true); }
};
ACOES.baixarCodigos = () => {
  const url = URL.createObjectURL(new Blob([textoCodigos()], { type: 'text/plain;charset=utf-8' }));
  const a = Object.assign(document.createElement('a'), { href: url, download: 'codigos-recuperacao-forca-total.txt' });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
};

function telaDoisFatoresAtiva(st) {
  const desde = st.ativadoEm ? data(st.ativadoEm.slice(0, 10)) : '—';
  abrirFormulario({
    titulo: 'Verificação em duas etapas',
    textoBotao: 'Desativar',
    campos: [
      { html: `<p class="resumo-modal"><strong>Ativa</strong><br>Desde ${desde} · ${st.codigosRestantes} código(s) de recuperação restante(s)${st.codigosRestantes <= 2 ? ' — <b class="txt-alerta">poucos códigos: desative e ative de novo para gerar outros</b>' : ''}.</p>` },
      { secao: 'Desativar' },
      { html: '<p class="dica">Para desligar, confirme sua senha e um código do aplicativo (ou de recuperação). Sem a verificação, sua conta fica protegida só pela senha.</p>' },
      { nome: 'senha', rotulo: 'Sua senha', tipo: 'password', obrigatorio: true, autocomplete: 'current-password', erroServidor: /senha/i },
      { nome: 'codigo', rotulo: 'Código do aplicativo', obrigatorio: true, placeholder: '000000 ou XXXXX-XXXXX', erroServidor: /código/i },
    ],
    salvar: async (dados) => {
      await api('/auth/2fa/desativar', { method: 'POST', body: dados });
      sessao.usuario.doisFatores = false;
      atualizarCabecalhoUsuario();
      aviso('Verificação em duas etapas desativada.');
      if (rotaAtual().modulo === 'usuarios') renderizar();
    },
  });
}

ACOES.redefinirDoisFatores = async (id) => {
  const u = (await api('/usuarios')).find((x) => x.id === id);
  if (!(await confirmar(`Redefinir a verificação em duas etapas de <strong>${esc(u.nome)}</strong>?<br><br>Use isto quando a pessoa perdeu o celular e os códigos de recuperação. Ela vai entrar só com a senha e poderá ativar de novo. As sessões abertas dela serão encerradas.`, 'Redefinir'))) return;
  await api(`/usuarios/${id}/2fa/redefinir`, { method: 'POST' });
  aviso(`Verificação em duas etapas de ${u.nome} redefinida.`);
  renderizar();
};

/* ---------------------------------------------------------------------
 * Início: com token salvo, confirma a sessão; senão, mostra o login
 * ------------------------------------------------------------------- */
async function iniciar() {
  // Vindo da página de apresentação: "/#cadastro" abre direto a aba de cadastro de academia
  if (location.hash === '#cadastro') {
    history.replaceState(null, '', location.pathname);
    if (!lerToken()) { encerrarSessao(); trocarAba('cadastro'); setTimeout(() => $('#cadAcademia').focus(), 80); return; }
  }
  if (!lerToken()) return encerrarSessao();
  try {
    entrarNoApp(await requisitar('/auth/eu', 'GET'));
  } catch (err) {
    if (sessao == null) encerrarSessao(/sess/i.test(err.message) ? 'Sua sessão expirou. Entre novamente.' : err.message);
  }
}
iniciar();
