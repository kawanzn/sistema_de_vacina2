const { test } = require('node:test');
const assert = require('node:assert/strict');
const { ReminderStore } = require('../vacinas/static/vacinas/reminders-core.js');
test('temporizadores Web são chamados com window e podem ser cancelados', async () => {
 const vm = require('node:vm');
 const fs = require('node:fs');
 let value = '[]', handle;
 const window = {
   setTimeout(fn) { assert.equal(this, window); handle = fn; return 7; },
   clearTimeout(id) { assert.equal(this, window); assert.equal(id, 7); handle = null; }
 };
 vm.runInNewContext(fs.readFileSync(require.resolve('../vacinas/static/vacinas/reminders-core.js'), 'utf8'),
   { window, crypto: { randomUUID: () => 'browser-1' } });
 const store = new window.EasyVaccReminders.ReminderStore({
   storage: { getItem: () => value, setItem: (_, v) => { value = v; } },
   now: () => 1000, notify: () => assert.fail('cancelado'), close: async () => {}
 });
 const { id } = store.create('Vacinação', 2000);
 store.schedule(id);
 assert.equal(typeof handle, 'function');
 await store.cancel(id);
 assert.equal(handle, null);
});
test('falha ao iniciar o timer não registra um agendamento falso', () => {
 const h = setup();
 const { id } = h.app.create('Vacinação', 2000);
 h.app.timer = () => { throw new Error('timer indisponível'); };
 assert.throws(() => h.app.schedule(id), /timer indisponível/);
 assert.equal(h.app.find(id).state, 'criado');
 assert.equal(JSON.parse(h.storage.getItem())[0].state, 'criado');
});
function setup(initial = '[]', notify) {
 let value = initial, now = 1000, seq = 0, calls = [], closed = [], timers = new Map();
 const storage = {getItem:()=>value,setItem:(_,v)=>{value=v;}};
 const app = new ReminderStore({storage,now:()=>now,timer:(fn)=>{timers.set(++seq,fn);return seq;},clear:id=>timers.delete(id),notify:notify || (async x=>calls.push(x.id)),close:async id=>closed.push(id)});
 return {app,calls,closed,timers,storage,advance:async time=>{now=time;for(const [id,fn] of [...timers]){timers.delete(id);fn();} await new Promise(r=>setImmediate(r));}};
}
test('C01 criar e listar um lembrete futuro',()=>{const h=setup();h.app.create('Vacinação',2000);assert.equal(h.app.list()[0].state,'criado');});
test('C02 horário inválido ou passado e título vazio são recusados',()=>{const h=setup();for(const time of [NaN,999,1000])assert.throws(()=>h.app.create('Vacinação',time));assert.throws(()=>h.app.create('',2000));assert.equal(h.app.list().length,0);});
test('C03 agendar e notificar uma única vez no horário',async()=>{const h=setup();const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);await h.advance(1500);assert.equal(h.calls.length,0);await h.advance(2000);await h.advance(3000);assert.deepEqual(h.calls,[x.id]);assert.equal(h.app.list()[0].state,'notificado');});
test('C04 cancelar antes do horário impede a notificação',async()=>{const h=setup();const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);await h.app.cancel(x.id);await h.advance(3000);assert.equal(h.calls.length,0);assert.equal(h.timers.size,0);assert.equal(h.app.list()[0].state,'cancelado');});
test('C05 cancelar depois fecha a notificação existente',async()=>{const h=setup();const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);await h.advance(2000);await h.app.cancel(x.id);assert.deepEqual(h.closed,[x.id]);assert.equal(h.app.list()[0].state,'cancelado');});
test('C06 falha ou permissão revogada fica explícita',async()=>{const h=setup('[]',async()=>{throw Error('Permissão negada');});const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);await h.advance(2000);assert.equal(h.app.list()[0].state,'falha');assert.match(h.app.list()[0].error,/Permissão/);});
test('C07 recarregar preserva lista, mas pausa o agendamento',()=>{const h=setup();const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);const restored=setup(h.storage.getItem());assert.equal(restored.app.list()[0].state,'pausado');assert.equal(restored.timers.size,0);});
test('C08 JSON inválido e falha de armazenamento são tratados',()=>{assert.throws(()=>setup('corrompido'));const h=setup();h.storage.setItem=()=>{throw Error('Sem espaço');};assert.throws(()=>h.app.create('Vacinação',2000));assert.equal(h.app.list().length,0);});
test('C09 cancelamento durante o disparo também fecha a notificação',async()=>{let release;const h=setup('[]',()=>new Promise(r=>{release=r;}));const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);await h.advance(2000);await h.app.cancel(x.id);release();await new Promise(r=>setImmediate(r));assert.equal(h.app.list()[0].state,'cancelado');assert.ok(h.closed.length>=2);});
test('C10 ID inexistente e agendamento duplicado são recusados',()=>{const h=setup();assert.throws(()=>h.app.schedule('inexistente'));const x=h.app.create('Vacinação',2000);h.app.schedule(x.id);assert.throws(()=>h.app.schedule(x.id));assert.equal(h.timers.size,1);});
