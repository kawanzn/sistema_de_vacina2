"""Public directory; a residential CEP identifies a region, not a clinic."""
import json
import re
import unicodedata
from functools import lru_cache
from pathlib import Path

import requests


def key(value):
    plain = ''.join(c for c in unicodedata.normalize('NFD', value.casefold())
                    if unicodedata.category(c) != 'Mn')
    return ' '.join(re.findall(r'[a-z0-9]+', plain))


@lru_cache(maxsize=1)
def directory():
    return tuple(json.loads(Path(__file__).with_name('postos-saquarema.json').read_text(encoding='utf-8')))


def unit_key(name):
    return re.sub(r'^(esf|ubs|psf) ', '', key(name))


def selected_unit(name):
    wanted = unit_key(name)
    return next((unit for unit in directory() if unit_key(unit['name']) == wanted), None)


def lookup_cep(cep):
    if cep == '28994160':
        return {'bairro': 'Condado de Bacaxá (Bacaxá)', 'localidade': 'Saquarema', 'uf': 'RJ'}
    response = requests.get(f'https://viacep.com.br/ws/{cep}/json/', timeout=4)
    response.raise_for_status()
    return response.json()


def search(term, units):
    units = sorted(units, key=lambda unit: key(unit['nome']))
    if not term:
        return units, '', ''
    if not re.fullmatch(r'[0-9.\s-]+', term):
        words = key(term).split()
        return [unit for unit in units if all(
            word in key(f"{unit['nome']} {unit['bairro']} {unit['endereco']} Saquarema RJ")
            for word in words)], '', ''
    cep = re.sub(r'\D', '', term)
    if len(cep) != 8:
        return [], '', 'Informe um CEP com 8 dígitos ou digite o bairro.'
    exact = [unit for unit in units if re.sub(r'\D', '', unit.get('cep') or '') == cep]
    if exact:
        return exact, '', ''
    try:
        region = lookup_cep(cep)
    except (requests.RequestException, ValueError):
        return units, '', 'A consulta de CEP está indisponível. Veja os postos de Saquarema abaixo ou busque pelo bairro.'
    if region.get('erro') or not region.get('localidade'):
        return [], '', 'CEP não encontrado. Confira os números ou busque pelo bairro.'
    if key(region['localidade']) != 'saquarema' or region.get('uf') != 'RJ':
        return [], '', 'Este CEP fica fora de Saquarema, RJ. A busca cobre o município de Saquarema.'
    neighborhood = region.get('bairro') or ''
    words = {word for word in key(neighborhood).split() if len(word) >= 4}
    units.sort(key=lambda unit: not bool(words.intersection(key(unit['bairro'] + ' ' + unit['nome']).split())))
    return units, neighborhood, 'Postos de Saquarema, com os que citam o bairro pesquisado primeiro. A lista não calcula distância.'
