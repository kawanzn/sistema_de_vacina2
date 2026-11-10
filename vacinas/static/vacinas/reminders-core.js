/* Lembretes locais didáticos. Dependências injetáveis permitem testar sem relógio real. */
(function (root) {
  'use strict';
  class ReminderStore {
    constructor({ storage, notify, close, now = Date.now, timer = (fn, delay) => root.setTimeout(fn, delay), clear = id => root.clearTimeout(id), onChange = () => {} }) {
      Object.assign(this, { storage, notify, close, now, timer, clear, onChange });
      this.key = 'easyvacc-reminders-v1';
      this.timers = new Map();
      const saved = JSON.parse(storage.getItem(this.key) || '[]');
      const states = ['criado', 'agendado', 'pausado', 'notificado', 'cancelado', 'falha'];
      if (!Array.isArray(saved) || saved.some(x => !x || typeof x.id !== 'string' ||
          typeof x.title !== 'string' || !Number.isFinite(x.at) || !states.includes(x.state))) {
        throw new Error('A lista salva está inválida. Limpe os dados deste site para reiniciar os lembretes.');
      }
      // Persistir a lista não restaura os timers após recarregar ou reiniciar.
      this.items = saved.map(x => ({ ...x, state: x.state === 'agendado' ? 'pausado' : x.state }));
    }
    list() { return this.items.map(x => ({ ...x })); }
    find(id) {
      const item = this.items.find(x => x.id === id);
      if (!item) throw new Error('Lembrete não encontrado.');
      return item;
    }
    save() {
      this.storage.setItem(this.key, JSON.stringify(this.items));
      this.onChange(this.list());
    }
    create(title, at) {
      title = String(title || '').trim();
      at = Number(at);
      if (!title || title.length > 80) throw new Error('Informe um título com até 80 caracteres.');
      if (!Number.isFinite(at) || at <= this.now()) throw new Error('Escolha uma data e hora futuras.');
      if (this.items.length >= 100) throw new Error('Limite de 100 lembretes neste navegador.');
      const item = { id: crypto.randomUUID(), title, at, state: 'criado', error: '' };
      this.items.push(item);
      try { this.save(); } catch (error) { this.items.pop(); throw error; }
      return { ...item };
    }
    schedule(id) {
      const item = this.find(id);
      if (!['criado', 'pausado', 'falha'].includes(item.state)) throw new Error('Este lembrete não pode ser agendado novamente.');
      if (item.at <= this.now()) throw new Error('O horário já passou. Crie um novo lembrete.');
      const previous = { state: item.state, error: item.error };
      const tick = () => {
        if (item.state !== 'agendado') return;
        const remaining = item.at - this.now();
        if (remaining > 0) this.timers.set(id, this.timer(tick, Math.min(remaining, 2147483647)));
        else void this.fire(item);
      };
      const handle = this.timer(tick, Math.min(item.at - this.now(), 2147483647));
      this.timers.set(id, handle);
      item.state = 'agendado'; item.error = '';
      try { this.save(); }
      catch (error) {
        this.clear(handle); this.timers.delete(id); Object.assign(item, previous); throw error;
      }
    }
    async fire(item) {
      this.timers.delete(item.id);
      try {
        await this.notify(item);
        // Um cancelamento enquanto a notificação era criada também deve fechá-la.
        if (item.state === 'cancelado') await this.close(item.id);
        else item.state = 'notificado';
      } catch (error) {
        if (item.state !== 'cancelado') { item.state = 'falha'; item.error = error.message; }
      }
      this.save();
    }
    async cancel(id) {
      const item = this.find(id);
      const previous = item.state;
      item.state = 'cancelado';
      try { this.save(); } catch (error) { item.state = previous; throw error; }
      this.clear(this.timers.get(id)); this.timers.delete(id);
      await this.close(id);
    }
  }
  if (typeof module !== 'undefined') module.exports = { ReminderStore };
  else root.EasyVaccReminders = { ReminderStore };
})(typeof window === 'undefined' ? globalThis : window);
