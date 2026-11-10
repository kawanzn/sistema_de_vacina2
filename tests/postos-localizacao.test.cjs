const { test } = require('node:test');
const assert = require('node:assert/strict');
const { point, kilometers, route } = require('../vacinas/static/vacinas/postos-localizacao.js');

test('distância usa coordenadas e deixa unidades sem posição depois das demais', () => {
  const user = point(-22.89, -42.46);
  const bacaxa = point(-22.8930379, -42.457298);
  const jacone = point(-22.92376, -42.59481);
  assert.ok(kilometers(user, bacaxa) < 1);
  assert.ok(kilometers(user, bacaxa) < kilometers(user, jacone));
  assert.equal(kilometers(user, point('', '')), Infinity);
  assert.equal(point(93, -42.46), null);
});

test('rota leva a posição real e as coordenadas do posto ao Maps somente pelo link', () => {
  const link = new URL(route(point(-22.89, -42.46), point(-22.9, -42.47), 'ESF'));
  assert.equal(link.origin, 'https://www.google.com');
  assert.equal(link.searchParams.get('origin'), '-22.89,-42.46');
  assert.equal(link.searchParams.get('destination'), '-22.9,-42.47');
});

test('rota por endereço funciona sem coordenadas e sem localização concedida', () => {
  const address = 'Rua 4, 47, Guarani, Saquarema - RJ';
  const link = new URL(route(null, null, address));
  assert.equal(link.searchParams.has('origin'), false);
  assert.equal(link.searchParams.get('destination'), address);
});

test('botão solicita localização, reordena a lista e mantém o agendamento do posto selecionado', () => {
  const vm = require('node:vm');
  const fs = require('node:fs');
  function element() {
    return { addEventListener(_, fn) { this.click = fn; }, focus() {} };
  }
  function card(name, lat, lon) {
    const children = {
      '.selecionar-posto': element(), '.nome-sem-js': {}, '.distancia-posto': {},
      '.agendar-posto': { href: '/agendar/?unidade=' + encodeURIComponent(name) }
    };
    return { dataset: { nome: name, lat, lon, endereco: 'Saquarema' }, querySelector: key => children[key] };
  }
  const bacaxa = card('ESF Bacaxá', '-22.893', '-42.457');
  const jacone = card('ESF Jaconé', '-22.924', '-42.595');
  const order = [];
  const nodes = Object.fromEntries(['localizar-postos', 'localizacao-status', 'posto-selecionado',
    'posto-rota', 'posto-agendar', 'posto-nome', 'posto-endereco'].map(id => [id, element()]));
  nodes['lista-postos'] = { querySelectorAll: () => [jacone, bacaxa], append: item => order.push(item) };
  let requests = 0;
  vm.runInNewContext(fs.readFileSync(require.resolve('../vacinas/static/vacinas/postos-localizacao.js'), 'utf8'), {
    document: { getElementById: id => nodes[id] }, window: { isSecureContext: true }, URL,
    navigator: { geolocation: { getCurrentPosition(success) {
      requests++;
      success({ coords: { latitude: -22.89, longitude: -42.46, accuracy: 30 } });
    } } }
  });
  assert.equal(requests, 0);
  bacaxa.querySelector('.selecionar-posto').click();
  assert.equal(new URL(nodes['posto-rota'].href).searchParams.has('origin'), false);
  nodes['localizar-postos'].click();
  assert.equal(requests, 1);
  assert.deepEqual(order, [bacaxa, jacone]);
  assert.equal(new URL(nodes['posto-rota'].href).searchParams.get('origin'), '-22.89,-42.46');
  assert.equal(nodes['posto-agendar'].href, '/agendar/?unidade=ESF%20Bacax%C3%A1');
});
