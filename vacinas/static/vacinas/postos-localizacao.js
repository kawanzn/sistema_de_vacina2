(() => {
  'use strict';
  function point(latitude, longitude) {
    if (latitude == null || longitude == null || String(latitude).trim() === '' || String(longitude).trim() === '') return null;
    const lat = Number(latitude), lon = Number(longitude);
    return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? { lat, lon } : null;
  }
  function kilometers(a, b) {
    if (!a || !b) return Infinity;
    const rad = Math.PI / 180;
    const h = Math.sin((b.lat - a.lat) * rad / 2) ** 2 +
      Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin((b.lon - a.lon) * rad / 2) ** 2;
    return 12742 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h))));
  }
  function route(origin, destination, address) {
    const maps = new URL('https://www.google.com/maps/dir/');
    maps.searchParams.set('api', '1');
    maps.searchParams.set('destination', destination ? `${destination.lat},${destination.lon}` : address);
    if (origin) maps.searchParams.set('origin', `${origin.lat},${origin.lon}`);
    return maps.href;
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { point, kilometers, route };
    return;
  }
  const list = document.getElementById('lista-postos');
  const locate = document.getElementById('localizar-postos');
  if (!list || !locate) return;
  const status = document.getElementById('localizacao-status');
  const panel = document.getElementById('posto-selecionado');
  const mapLink = document.getElementById('posto-rota');
  const appointments = document.getElementById('posto-agendar');
  const cards = Array.from(list.querySelectorAll('[data-nome]'));
  const coordinates = card => point(card.dataset.lat, card.dataset.lon);
  let origin = null;
  let selected = null;
  function updateRoute() {
    if (selected) mapLink.href = route(origin, coordinates(selected), selected.dataset.endereco);
  }
  for (const card of cards) {
    const button = card.querySelector('.selecionar-posto');
    card.querySelector('.nome-sem-js').hidden = true;
    button.hidden = false;
    button.addEventListener('click', () => {
      selected = card;
      document.getElementById('posto-nome').textContent = card.dataset.nome;
      document.getElementById('posto-endereco').textContent = card.dataset.endereco;
      appointments.href = card.querySelector('.agendar-posto').href;
      updateRoute();
      panel.hidden = false;
      panel.focus();
    });
  }
  locate.hidden = false;
  locate.addEventListener('click', () => {
    if (!window.isSecureContext || !navigator.geolocation) {
      status.textContent = 'A localização não está disponível neste navegador ou endereço. Use a busca ou abra a página em HTTPS.';
      return;
    }
    locate.disabled = true;
    status.textContent = 'Buscando sua localização…';
    navigator.geolocation.getCurrentPosition(position => {
      locate.disabled = false;
      const current = point(position.coords.latitude, position.coords.longitude);
      if (!current) {
        status.textContent = 'Não foi possível determinar sua localização. Tente novamente.';
        return;
      }
      origin = current;
      const ranked = cards.map(card => ({ card, distance: kilometers(origin, coordinates(card)) }));
      ranked.sort((a, b) => {
        if (a.distance === b.distance) return 0;
        return a.distance < b.distance ? -1 : 1;
      });
      for (const { card, distance } of ranked) {
        const label = card.querySelector('.distancia-posto');
        label.textContent = Number.isFinite(distance)
          ? `${distance.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km em linha reta`
          : 'Sem coordenadas confirmadas para calcular a distância.';
        label.hidden = false;
        list.append(card);
      }
      status.textContent = ranked.some(item => Number.isFinite(item.distance))
        ? 'Postos exibidos ordenados pela distância em linha reta. A rota mostra o trajeto real.'
        : 'Os postos exibidos não têm coordenadas confirmadas. Selecione uma unidade para consultar a rota pelo endereço.';
      if (position.coords.accuracy > 1000) status.textContent += ' A localização informada pelo aparelho é aproximada.';
      updateRoute();
    }, error => {
      locate.disabled = false;
      status.textContent = error.code === 1
        ? 'Localização não autorizada. Você ainda pode buscar por CEP, bairro ou nome.'
        : 'Não foi possível obter sua localização. Confira as configurações do aparelho ou use a busca.';
    }, { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 });
  });
})();
