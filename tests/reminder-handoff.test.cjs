const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function setup(when) {
  const form = { elements: {
    title: { value: 'Retorno & revisão' },
    message: { value: 'Conferir no EasyVacc' },
    when: { value: when }
  } };
  const list = { replaceChildren() {} };
  const legacy = {}, feedback = {}, location = {};
  class Store {
    list() { return []; }
    schedule() { assert.fail('A página não deve agendar alarmes'); }
    create() { assert.fail('Um rascunho não deve ser salvo como lembrete'); }
  }
  const elements = { '#reminder-form': form, '#reminder-list': list,
    '#reminder-legacy': legacy, '#reminder-feedback': feedback };
  vm.runInNewContext(fs.readFileSync(require.resolve('../vacinas/static/vacinas/reminders.js'), 'utf8'), {
    document: { querySelector: id => elements[id] }, window: { location }, URLSearchParams,
    localStorage: {}, EasyVaccReminders: { ReminderStore: Store }
  });
  return { form, location, feedback };
}

test('abre rascunho no app com os campos codificados', () => {
  const future = new Date(Date.now() + 3600000);
  const { form, location, feedback } = setup(future.toISOString());
  form.onsubmit({ preventDefault() {} });
  const url = new URL(location.href);
  assert.equal(url.protocol, 'easyvacc-aulas:');
  assert.equal(url.hostname, 'lembrete');
  assert.equal(url.pathname, '/novo');
  assert.equal(url.searchParams.get('titulo'), 'Retorno & revisão');
  assert.equal(url.searchParams.get('mensagem'), 'Conferir no EasyVacc');
  assert.equal(Number(url.searchParams.get('quando')), future.getTime());
  assert.match(feedback.textContent, /Confirme/);
});

test('horário passado não abre o app', () => {
  const { form, location, feedback } = setup('2000-01-01T10:00:00');
  form.onsubmit({ preventDefault() {} });
  assert.equal(location.href, undefined);
  assert.match(feedback.textContent, /futuras/);
});
