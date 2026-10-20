(function () {
  'use strict';

  document.addEventListener('click', async function (event) {
    var button = event.target.closest('[data-share-posto]');
    if (!button || button.disabled) return;
    var card = button.closest('[data-posto-card]');
    var status = card.querySelector('[data-share-status]');
    var fallback = card.querySelector('[data-share-fallback]');
    var data = {
      title: button.dataset.postoNome,
      text: button.dataset.postoNome + '\n' + button.dataset.postoEndereco,
    };

    // Keep the share call in the click handler: the OS requires user activation.
    fallback.hidden = true;
    status.textContent = '';
    if (!window.isSecureContext || typeof navigator.share !== 'function') {
      fallback.value = data.text;
      fallback.hidden = false;
      status.textContent = 'Compartilhamento indisponível. Selecione e copie o endereço abaixo.';
      return;
    }

    button.disabled = true;
    status.textContent = 'Escolha um aplicativo no menu de compartilhamento.';
    try {
      await navigator.share(data);
      // Resolution is a hand-off to the target, not proof of message delivery.
      status.textContent = 'Endereço encaminhado ao compartilhamento do aparelho.';
    } catch (error) {
      if (error && error.name === 'AbortError') {
        status.textContent = 'Compartilhamento cancelado ou sem destino disponível. Você pode tentar novamente.';
      } else {
        fallback.value = data.text;
        fallback.hidden = false;
        status.textContent = 'Não foi possível compartilhar. Tente novamente ou copie o endereço abaixo.';
      }
    } finally {
      button.disabled = false;
    }
  });
})();
