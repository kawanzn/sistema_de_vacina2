/* The browser prepares a draft. Only the Android app can confirm it. */
(() => {
  'use strict';
  const form = document.querySelector('#reminder-form');
  const list = document.querySelector('#reminder-list');
  const legacy = document.querySelector('#reminder-legacy');
  const feedback = document.querySelector('#reminder-feedback');
  const say = (text, bad = false) => {
    feedback.textContent = text;
    feedback.className = `alert ${bad ? 'alert-danger' : 'alert-info'}`;
  };
  function openDraft(title, message, at) {
    title = title.trim(); message = message.trim();
    if (!title || title.length > 80 || !message || message.length > 500)
      throw new Error('Informe título e mensagem dentro do limite indicado.');
    if (!Number.isFinite(at) || at <= Date.now())
      throw new Error('Escolha uma data e hora futuras.');
    const query = new URLSearchParams({ titulo: title, mensagem: message, quando: at });
    say('Confirme em Agendar lembrete no Android. Se o aplicativo não abrir, instale a versão 1.2.0 ou abra esta página no seu celular.');
    window.location.href = `easyvacc-aulas://lembrete/novo?${query}`;
  }
  form.onsubmit = event => {
    event.preventDefault();
    try {
      openDraft(form.elements.title.value, form.elements.message.value,
        new Date(form.elements.when.value).getTime());
    } catch (error) { say(error.message, true); }
  };

  // Retain legacy entries for transfer. Never start a browser timer.
  let store;
  function render(items) {
    list.replaceChildren();
    legacy.hidden = !items.length;
    for (const item of items) {
      const row = document.createElement('article');
      row.className = 'border rounded-3 p-3 mb-3';
      const title = document.createElement('h3');
      title.className = 'h5'; title.textContent = item.title;
      const details = document.createElement('p');
      details.textContent = new Date(item.at).toLocaleString('pt-BR') +
        (item.state === 'cancelado' ? ' · Cancelado no navegador' : ' · Registro anterior');
      row.append(title, details);
      if (item.state !== 'cancelado' && item.at > Date.now()) {
        const button = document.createElement('button');
        button.className = 'btn btn-outline-primary';
        button.textContent = 'Abrir lembrete antigo no Android';
        button.onclick = () => {
          try { openDraft(item.title, 'Confira seu compromisso no EasyVacc.', item.at); }
          catch (error) { say(error.message, true); }
        };
        row.append(button);
      }
      list.append(row);
    }
  }
  try {
    store = new EasyVaccReminders.ReminderStore({
      storage: localStorage, onChange: render, notify: async () => {}, close: async () => {}
    });
    render(store.list());
  } catch (error) {
    say('Não foi possível ler os lembretes antigos. Ainda é possível abrir um novo lembrete no Android.', true);
  }
})();
