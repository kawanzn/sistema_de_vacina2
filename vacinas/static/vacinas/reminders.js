/* Interface sem dados de pacientes; a lista pertence a este navegador. */
(async () => {
  'use strict';
  const form = document.querySelector('#reminder-form');
  const list = document.querySelector('#reminder-list');
  const feedback = document.querySelector('#reminder-feedback');
  const say = (text, bad = false) => {
    feedback.textContent = text;
    feedback.className = `alert ${bad ? 'alert-danger' : 'alert-success'}`;
  };
  async function registration() {
    if (!('Notification' in window) || !('serviceWorker' in navigator)) throw new Error('Este navegador não oferece notificações compatíveis.');
    return navigator.serviceWorker.register('/sw.js');
  }
  async function close(id) {
    if (!('serviceWorker' in navigator)) return;
    const reg = await navigator.serviceWorker.getRegistration('/');
    if (reg) (await reg.getNotifications({ tag: `easyvacc-reminder-${id}` })).forEach(x => x.close());
  }
  async function notify(item) {
    if (!('Notification' in window) || Notification.permission !== 'granted') throw new Error('Permissão de notificação não concedida.');
    await registration();
    const reg = await navigator.serviceWorker.ready;
    await reg.showNotification('EasyVacc - lembrete', {
      body: 'Você tem um compromisso de vacinação para conferir no EasyVacc.',
      tag: `easyvacc-reminder-${item.id}`, data: { url: '/lembretes/' },
      icon: '/static/vacinas/icon-192.png',
    });
  }
  function render(items) {
    list.replaceChildren();
    if (!items.length) { list.textContent = 'Nenhum lembrete criado neste navegador.'; return; }
    const labels = { criado: 'Criado', agendado: 'Agendado nesta página', pausado: 'Pausado após reabrir', notificado: 'Notificação enviada ao navegador', cancelado: 'Cancelado', falha: 'Falha na notificação' };
    for (const item of items) {
      const card = document.createElement('article'); card.className = 'border rounded-3 p-3 mb-3';
      const title = document.createElement('h3'); title.className = 'h5'; title.textContent = item.title;
      const time = document.createElement('p'); time.className = 'mb-1'; time.textContent = new Date(item.at).toLocaleString('pt-BR');
      const status = document.createElement('p'); status.className = 'fw-bold'; status.textContent = labels[item.state];
      card.append(title, time, status);
      if (item.error) { const error = document.createElement('p'); error.textContent = item.error; card.append(error); }
      if (['criado', 'pausado', 'falha'].includes(item.state)) {
        const button = document.createElement('button'); button.className = 'btn btn-primary me-2'; button.textContent = 'Agendar notificação';
        button.onclick = async () => {
          button.disabled = true;
          try {
            if (!('Notification' in window)) throw new Error('Este navegador não oferece notificações compatíveis.');
            if (await Notification.requestPermission() !== 'granted') throw new Error('Notificações não autorizadas. Permita-as nas configurações do navegador para agendar.');
            await registration();
            store.schedule(item.id); say('Lembrete agendado. Mantenha esta página aberta até o horário.');
          } catch (error) { say(error.message, true); button.disabled = false; }
        };
        card.append(button);
      }
      if (item.state !== 'cancelado') {
        const button = document.createElement('button'); button.className = 'btn btn-outline-danger'; button.textContent = 'Cancelar lembrete';
        button.onclick = async () => {
          try { await store.cancel(item.id); say('Lembrete cancelado. O timer foi removido e a notificação aberta foi fechada.'); }
          catch (error) { say(`Não foi possível concluir o cancelamento: ${error.message}`, true); }
        };
        card.append(button);
      }
      list.append(card);
    }
  }
  let store;
  try {
    store = new EasyVaccReminders.ReminderStore({ storage: localStorage, notify, close, onChange: render });
    render(store.list());
  } catch (error) { say(`Não foi possível carregar os lembretes: ${error.message}`, true); form.querySelector('button').disabled = true; return; }
  form.onsubmit = event => {
    event.preventDefault();
    try {
      store.create(form.elements.title.value, new Date(form.elements.when.value).getTime());
      say('Lembrete criado. Clique em Agendar notificação para ativá-lo.');
      form.reset();
    } catch (error) { say(`Não foi possível criar: ${error.message}`, true); }
  };
})();
