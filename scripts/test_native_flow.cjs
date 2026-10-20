// Node built-ins only. Native API responses are simulated; this is not Android evidence.
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const shareSource = readFileSync(join(__dirname, '../vacinas/static/vacinas/share-posto.js'), 'utf8');

function fixture(share, secure = true) {
  let listener;
  const status = { textContent: '' };
  const fallback = { hidden: true, value: '' };
  const card = { querySelector: (selector) => selector.includes('status') ? status : fallback };
  const button = { disabled: false, dataset: { postoNome: 'Unidade TESTE-001', postoEndereco: 'Rua Fictícia, 10 - Centro' }, closest: () => card };
  const context = { document: { addEventListener: (_, fn) => { listener = fn; } }, window: { isSecureContext: secure }, navigator: { share } };
  vm.runInNewContext(shareSource, context);
  return { status, fallback, button, click: () => listener({ target: { closest: () => button } }) };
}

test('CT01: hand-off concluído contém somente nome e endereço públicos', async () => {
  let payload;
  const f = fixture(async (data) => { payload = data; });
  await f.click();
  assert.equal(payload.text, 'Unidade TESTE-001\nRua Fictícia, 10 - Centro');
  assert.deepEqual(Object.keys(payload).sort(), ['text', 'title']);
  assert.match(f.status.textContent, /encaminhado/);
  assert.equal(f.button.disabled, false);
});
test('CT02: API ausente oferece cópia manual', async () => {
  const f = fixture(undefined); await f.click();
  assert.equal(f.fallback.hidden, false); assert.match(f.fallback.value, /Rua Fictícia/);
  assert.match(f.status.textContent, /indisponível/);
});
test('CT03: cancelamento não anuncia sucesso e permite nova tentativa', async () => {
  let count = 0;
  const f = fixture(async () => { if (++count === 1) throw { name: 'AbortError' }; });
  await f.click(); assert.match(f.status.textContent, /cancelado/);
  assert.doesNotMatch(f.status.textContent, /encaminhado/);
  assert.equal(f.button.disabled, false);
  await f.click(); assert.match(f.status.textContent, /encaminhado/);
});
test('CT04: bloqueio de permissão permite recuperação sem alterar endereço', async () => {
  const f = fixture(async () => { throw { name: 'NotAllowedError' }; });
  await f.click(); assert.equal(f.fallback.hidden, false);
  assert.match(f.status.textContent, /Não foi possível/); assert.equal(f.button.disabled, false);
  assert.equal(f.button.dataset.postoNome, 'Unidade TESTE-001');
});
test('CT05: falha do destino preserva alternativa manual', async () => {
  const f = fixture(async () => { throw new Error('destino indisponível'); });
  await f.click(); assert.equal(f.fallback.hidden, false);
  assert.doesNotMatch(f.status.textContent, /encaminhado/);
});
test('CT06: clique repetido não abre dois compartilhamentos simultâneos', async () => {
  let done; let calls = 0;
  const f = fixture(() => { calls++; return new Promise(resolve => { done = resolve; }); });
  const first = f.click(); await f.click(); assert.equal(calls, 1);
  assert.equal(f.button.disabled, true); done(); await first;
  assert.equal(f.button.disabled, false);
});
test('CT08: contexto inseguro usa cópia manual sem chamar recurso nativo', async () => {
  let calls = 0; const f = fixture(async () => { calls++; }, false);
  await f.click(); assert.equal(calls, 0); assert.equal(f.fallback.hidden, false);
});

// Expose the existing private function only inside the VM, without a production test API.
const offlineSource = readFileSync(join(__dirname, '../vacinas/static/vacinas/offline-sync.js'), 'utf8')
  .replace(/\}\)\(\);\s*$/, 'window.testReplay = submitQueuedItem; })();');
function replay(response) {
  let request;
  const window = { addEventListener() {} };
  vm.runInNewContext(offlineSource, {
    window, document: { addEventListener() {}, cookie: 'csrftoken=novo-token-ficticio' },
    navigator: {}, FormData,
    fetch: async (url, options) => { request = options; return response; },
  });
  return { run: () => window.testReplay({ url: '/pacientes/novo/', entries: [['cpf', 'TESTE'], ['csrfmiddlewaretoken', 'token-antigo']] }), request: () => request };
}
function jsonResponse(data, extra = {}) { return { ok: true, status: 200, redirected: false, headers: { get: () => 'application/json' }, json: async () => data, ...extra }; }
test('CT10: replay de login HTML não é confirmação de salvamento', async () => {
  const f = replay(jsonResponse(null, { redirected: true, headers: { get: () => 'text/html' } }));
  assert.equal((await f.run()).ok, false);
});
test('CT10: JSON vazio ou sem confirmação explícita não é sucesso', async () => {
  for (const data of [null, {}, { ok: false }]) assert.equal((await replay(jsonResponse(data)).run()).ok, false);
});
test('CT10: apenas resposta positiva explícita confirma replay', async () => {
  assert.equal((await replay(jsonResponse({ ok: true })).run()).ok, true);
  assert.equal((await replay(jsonResponse({ ok: true }, { ok: false, status: 422 })).run()).ok, false);
});
test('CT11: replay usa CSRF atual e mantém dados do formulário', async () => {
  const f = replay(jsonResponse({ ok: true })); await f.run();
  const request = f.request();
  assert.equal(request.body.get('csrfmiddlewaretoken'), 'novo-token-ficticio');
  assert.equal(request.body.getAll('csrfmiddlewaretoken').length, 1);
  assert.equal(request.body.get('cpf'), 'TESTE');
  assert.equal(request.headers['X-CSRFToken'], 'novo-token-ficticio');
});
