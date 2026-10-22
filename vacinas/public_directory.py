"""Anonymous municipal directory: public addresses and vaccine names only."""
import json
import unicodedata
from pathlib import Path
from django.db.models import Sum, F
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.http import require_GET
from .models import PostoSaude, Estoque


def identity(name):
    import re
    key = ''.join(c for c in unicodedata.normalize('NFD', name.lower()) if unicodedata.category(c) != 'Mn')
    key = re.sub(r'[^a-z0-9]+', ' ', key).strip()
    return re.sub(r'^(esf|psf|ubs) +', '', key).replace('correa', 'correia')


@require_GET
def posts(request):
    data = json.loads(Path(__file__).with_name('saquarema_units.json').read_text(encoding='utf-8'))
    units = {identity(row['name']): row for row in data['units']}
    stocks = {}
    for row in Estoque.objects.filter(data_validade__gte=timezone.localdate(), quantidade_atual__gt=F('quantidade_reservada')).values('posto_id', 'nome_vacina').annotate(free=Sum(F('quantidade_atual')-F('quantidade_reservada'))):
        stocks.setdefault(row['posto_id'], []).append(row['nome_vacina'])
    for post in PostoSaude.objects.order_by('nome'):
        key = identity(post.nome)
        row = dict(units.get(key, {}))
        row.update(name=post.nome,address=post.endereco,district=post.bairro,zip=post.cep,
                   source='Cadastro profissional no EasyVacc',verified=timezone.localdate().isoformat(),
                   availability=', '.join(sorted(stocks.get(post.pk, []))))
        units[key] = row
    data['units'] = sorted(units.values(), key=lambda row: identity(row['name']))
    result = JsonResponse(data)
    result['Cache-Control'] = 'public, max-age=300'
    return result
