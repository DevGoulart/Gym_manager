// =====================================================================
// Tira os prints das telas internas usados na página de apresentação.
//
// Abre o Edge (ou o Chrome) sem janela, entra no sistema com o usuário de
// exemplo e salva uma imagem .webp de cada tela em static/img/telas/.
//
// Uso (com o servidor rodando em http://localhost:8080):
//   node ferramentas/capturar-telas.mjs
//
// Opcional, por variáveis de ambiente:
//   BASE   endereço do sistema          (padrão http://localhost:8080)
//   EMAIL  / SENHA  usuário para entrar (padrão: o administrador de exemplo)
//   NAVEGADOR  caminho do msedge.exe ou chrome.exe
// Precisa do Node 22 ou mais novo (usa fetch e WebSocket nativos).
// =====================================================================
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE || 'http://localhost:8080';
const EMAIL = process.env.EMAIL || 'admin@forcatotal.com';
const SENHA = process.env.SENHA || 'forca2026';
const PORTA = 9333;
const LARGURA = 1440, ALTURA = 900; // tamanho padrão da "tela" fotografada

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pastasSaida = [
  join(raiz, 'backend/src/main/resources/static/img/telas'),
  join(raiz, 'backend/target/classes/static/img/telas'), // o servidor em execução lê daqui
];

// rota do sistema → arquivo; "rolar" desce até um elemento antes da foto
const TELAS = [
  { rota: 'dashboard', arquivo: 'painel.webp' },
  { rota: 'alunos', arquivo: 'alunos.webp' },
  { rota: 'planos', arquivo: 'planos.webp' },
  { rota: 'mensalidades', arquivo: 'mensalidades.webp' },
  { rota: 'treinos', arquivo: 'treinos.webp' },
  { rota: 'avaliacoes?aluno=1', arquivo: 'evolucao.webp', rolar: '.evolucao' },
  // Painel em alta resolução para o efeito que expande até a tela inteira (computador e celular)
  { rota: 'dashboard', arquivo: 'painel-grande.webp', dpr: 2 },
  { rota: 'dashboard', arquivo: 'painel-celular.webp', largura: 390, altura: 844, dpr: 2, celular: true },
];

const NAVEGADORES = [
  process.env.NAVEGADOR,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
].filter(Boolean);

const espera = (ms) => new Promise((r) => setTimeout(r, ms));
const falhar = (msg) => { console.error('Erro:', msg); process.exit(1); };

const navegador = NAVEGADORES.find((c) => existsSync(c));
if (!navegador) falhar('Edge ou Chrome não encontrado. Informe o caminho em NAVEGADOR.');

// 1) Entra no sistema pela API
let token;
try {
  const resp = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: EMAIL, senha: SENHA }),
  });
  const corpo = await resp.json();
  if (!resp.ok || !corpo.token) falhar(corpo.erro || 'não foi possível entrar (o usuário tem 2FA ativo?)');
  token = corpo.token;
} catch {
  falhar(`o sistema não respondeu em ${BASE}. O servidor está rodando?`);
}

// 2) Abre o navegador sem janela, com um perfil temporário
const perfil = mkdtempSync(join(tmpdir(), 'forcatotal-prints-'));
const proc = spawn(navegador, ['--headless=new', `--remote-debugging-port=${PORTA}`, `--user-data-dir=${perfil}`,
  '--hide-scrollbars', '--no-first-run', '--no-default-browser-check', 'about:blank'], { stdio: 'ignore' });

let ws;
try {
  let alvo;
  for (let i = 0; i < 60 && !alvo; i++) {
    await espera(250);
    try { alvo = (await (await fetch(`http://127.0.0.1:${PORTA}/json/list`)).json()).find((t) => t.type === 'page'); } catch { /* ainda abrindo */ }
  }
  if (!alvo) throw new Error('o navegador não abriu a porta de depuração');

  ws = new WebSocket(alvo.webSocketDebuggerUrl);
  await new Promise((ok, erro) => { ws.addEventListener('open', ok); ws.addEventListener('error', erro); });
  let id = 0;
  const pendentes = new Map();
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pendentes.has(m.id)) { pendentes.get(m.id)(m); pendentes.delete(m.id); }
  });
  const cdp = (method, params = {}) => new Promise((ok) => { const n = ++id; pendentes.set(n, ok); ws.send(JSON.stringify({ id: n, method, params })); });
  const avaliar = (expr) => cdp('Runtime.evaluate', { expression: expr, awaitPromise: true });

  // Sem animações: a foto sai com tudo no lugar
  await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await cdp('Emulation.setDeviceMetricsOverride', { width: LARGURA, height: ALTURA, deviceScaleFactor: 1, mobile: false });
  await cdp('Page.navigate', { url: BASE + '/app' });
  await espera(1200);
  await avaliar(`localStorage.setItem('forcatotal.token', ${JSON.stringify(token)})`);

  pastasSaida.forEach((p) => mkdirSync(p, { recursive: true }));
  for (const tela of TELAS) {
    await cdp('Emulation.setDeviceMetricsOverride', { width: tela.largura || LARGURA, height: tela.altura || ALTURA,
      deviceScaleFactor: tela.dpr || 1, mobile: !!tela.celular });
    await cdp('Page.navigate', { url: `${BASE}/app?p=${Date.now()}#/${tela.rota}` });
    await espera(2200);
    if (tela.rolar) {
      await avaliar(`(() => { const el = document.querySelector(${JSON.stringify(tela.rolar)});
        if (el) scrollTo(0, el.getBoundingClientRect().top + scrollY - 170); })()`);
      await espera(500);
    }
    const r = await cdp('Page.captureScreenshot', { format: 'webp', quality: 88 });
    if (!r.result?.data) throw new Error(`falha ao fotografar ${tela.rota}`);
    const imagem = Buffer.from(r.result.data, 'base64');
    pastasSaida.forEach((p) => writeFileSync(join(p, tela.arquivo), imagem));
    console.log(`ok  #/${tela.rota}  →  img/telas/${tela.arquivo}  (${Math.round(imagem.length / 1024)} KB)`);
  }
  console.log('\nPrints atualizados. Recarregue a página inicial (/) para ver.');
} catch (erro) {
  console.error('Erro:', erro.message);
  process.exitCode = 1;
} finally {
  ws?.close();
  // Encerra a sessão criada só para os prints
  await fetch(BASE + '/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + token } }).catch(() => {});
  proc.kill();
  await espera(500);
  try { rmSync(perfil, { recursive: true, force: true }); } catch { /* o navegador ainda segura arquivos */ }
}
