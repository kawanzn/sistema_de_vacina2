"""Authenticated native sync. Three-way merge refuses conflicting clinical/stock edits.

All tables commit together; UUID receipts make retries idempotent. No public export.
"""
import hashlib
import json
import secrets
import uuid
from datetime import date, datetime, timezone as dt_timezone
from functools import wraps
from django.conf import settings
from django.contrib.auth import authenticate, get_user_model
from django.core import signing
from django.db import transaction, IntegrityError, OperationalError, ProgrammingError
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST, require_GET
from .models import Paciente, PostoSaude, Estoque, Vacina, Agendamento, AndroidSyncLock, AndroidSyncReceipt, AndroidDoseLink

TABLES = ('posts', 'patients', 'stock', 'appointments', 'doses')
MODELS = dict(zip(TABLES, (PostoSaude, Paciente, Estoque, Agendamento, Vacina)))
FIELDS = {
    'posts': {'name':'nome', 'address':'endereco', 'district':'bairro', 'zip':'cep'},
    'patients': {'name':'nome', 'dob':'data_nascimento', 'cpf':'cpf', 'sus':'cartao_sus', 'email':'email', 'phone':'telefone', 'address':'endereco', 'guardian_id':'responsavel_id'},
    'stock': {'name':'nome_vacina', 'lot':'lote', 'qty':'quantidade_atual', 'reserved':'quantidade_reservada', 'expiry':'data_validade', 'supplier':'fornecedor', 'post_id':'posto_id'},
    'appointments': {'patient_id':'paciente_id', 'stock_id':'item_estoque_id', 'when_at':'data_hora', 'status':'status', 'reason':'justificativa_cancelamento'},
    'doses': {'patient_id':'paciente_id', 'stock_id':'item_estoque_id', 'post_id':'posto_id', 'name':'nome_vacina', 'lot':'lote', 'applied_at':'data_aplicacao'},
}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def response(value, status=200):
    result = JsonResponse(value, status=status)
    result['Cache-Control'] = 'no-store'
    return result


@require_GET
def status(request):
    try:
        ready=AndroidSyncLock.objects.filter(pk=1).exists() and not settings.SECRET_KEY.startswith('django-insecure-')
    except (OperationalError, ProgrammingError):
        ready=False
    return response({'protocol':1,'ready':ready}, 200 if ready else 503)


def payload(request):
    if len(request.body) > 10 * 1024 * 1024:
        raise ValueError('A sincronização excedeu 10 MB. Exporte um backup e procure o administrador.')
    return json.loads(request.body)


def token_user(request):
    token = request.headers.get('Authorization', '').removeprefix('Bearer ')
    data = signing.loads(token, salt='easyvacc-android-auth', max_age=30*86400)
    user = get_user_model().objects.get(pk=data['uid'], is_active=True, is_staff=True)
    if data['password'] != hashlib.sha256(user.password.encode()).hexdigest():
        raise signing.BadSignature('A senha foi alterada.')
    return user


def authorized(view):
    @wraps(view)
    def wrapped(request):
        try:
            user = token_user(request)
        except (signing.BadSignature, get_user_model().DoesNotExist, KeyError, ValueError):
            return response({'error':'Entre novamente com uma conta profissional do servidor.'}, 401)
        try:
            return view(request, user)
        except (ValueError, KeyError, TypeError, json.JSONDecodeError, OverflowError):
            return response({'error':'Dados inválidos. Nenhuma alteração desta requisição foi aplicada.'}, 422)
        except IntegrityError:
            return response({'error':'Há cadastro duplicado ou vínculo incompatível. Os dados locais foram preservados.'}, 409)
        except (OperationalError, ProgrammingError):
            return response({'error':'Integração indisponível. O administrador deve aplicar as migrações e conferir o banco persistente do servidor.'}, 503)
    return wrapped


@csrf_exempt
@require_POST
def login(request):
    if settings.SECRET_KEY.startswith('django-insecure-'):
        return response({'error':'O administrador precisa configurar SECRET_KEY segura no servidor.'}, 503)
    try:
        data = payload(request)
        user = authenticate(request, username=data.get('username', ''), password=data.get('password', ''))
    except (ValueError, TypeError, AttributeError):
        user = None
    if user is None or not user.is_active or not user.is_staff:
        return response({'error':'Usuário ou senha inválidos, ou conta sem acesso profissional.'}, 401)
    token = signing.dumps({'uid':user.pk, 'password':hashlib.sha256(user.password.encode()).hexdigest()}, salt='easyvacc-android-auth')
    return response({'token':token, 'name':user.get_full_name() or user.username, 'protocol':1})


def serialize():
    data = {'format':'EasyVaccNative', 'version':1}
    links = {str(link.dose_id):str(link.appointment_id) for link in AndroidDoseLink.objects.all()}
    for table in TABLES:
        result = []
        for obj in MODELS[table].objects.order_by('pk'):
            row = {'id':str(obj.pk)}
            for key, attr in FIELDS[table].items():
                value = getattr(obj, attr)
                if isinstance(value, datetime):
                    value = int(value.timestamp()*1000)
                elif isinstance(value, date):
                    value = value.isoformat()
                row[key] = None if value is None else str(value)
            if table == 'patients':
                row['cpf'] = ''.join(c for c in row['cpf'] or '' if c.isdigit()) or None
                row['sus'] = ''.join(c for c in row['sus'] or '' if c.isdigit()) or None
                row['phone'] = row['phone'] or ''
                row['name_key'] = ''  # Android regenerates this local search index.
            if table in ('appointments',):
                row['reason'] = row['reason'] or ''
            if table == 'stock' and row['post_id'] is None:
                raise ValueError('Há lote sem unidade no site. Corrija no servidor antes de sincronizar.')
            if table == 'doses':
                row.update(appointment_id=links.get(row['id']), status='ativa', reason='')
                if row['stock_id'] is None or row['post_id'] is None:
                    raise ValueError('Há aplicação antiga sem lote ou unidade no servidor.')
            result.append(row)
        data[table] = result
    # The legacy web model does not keep the appointment -> dose FK. Pair once.
    used = {d['id'] for d in data['doses'] if d['appointment_id'] is not None}
    for a in data['appointments']:
        if a['status'] != 'concluido':
            continue
        if any(d['appointment_id']==a['id'] for d in data['doses']):
            continue
        matches = [d for d in data['doses'] if d['id'] not in used and d['patient_id']==a['patient_id'] and d['stock_id']==a['stock_id']]
        if not matches:
            raise ValueError('Há agendamento concluído sem aplicação no site. Revise o histórico no servidor.')
        d = min(matches, key=lambda d: abs(int(d['applied_at'])-int(a['when_at'])))
        d['appointment_id'] = a['id']
        used.add(d['id'])
    return data


def canonical(data):
    result = {'format':'EasyVaccNative', 'version':1}
    if data.get('format') != 'EasyVaccNative' or data.get('version') != 1:
        raise ValueError('Protocolo incompatível.')
    total = 0
    for table in TABLES:
        rows = data[table]
        if not isinstance(rows, list):
            raise ValueError('Tabela inválida.')
        total += len(rows)
        if total > 50000:
            raise ValueError('Limite de registros excedido.')
        result[table] = []
        ids = set()
        for row in rows:
            pk = str(int(row['id']))
            if int(pk) <= 0 or int(pk) > 9007199254740991 or pk in ids:
                raise ValueError('Identificador inválido.')
            ids.add(pk)
            clean = {'id':pk}
            for field in FIELDS[table]:
                clean[field] = None if row[field] is None else str(row[field])
            if table == 'patients':
                clean['name_key'] = ''
            if table == 'doses':
                if row.get('status', 'ativa') != 'ativa':
                    raise ValueError('Aplicação anulada não é suportada pelo servidor atual.')
                clean.update(status='ativa', reason='', appointment_id=None if row.get('appointment_id') is None else str(row['appointment_id']))
            result[table].append(clean)
        result[table].sort(key=lambda row:int(row['id']))
    return result


def merge(base, local, remote):
    """Merge independent records, refusing two different edits to the same record.

    A stock row is indivisible: quantities/reservations must never be field-merged.
    """
    result = {'format':'EasyVaccNative', 'version':1}
    conflicts = []
    for table in TABLES:
        b, l, r = ({row['id']:row for row in data[table]} for data in (base, local, remote))
        rows = []
        for pk in sorted(b.keys() | l.keys() | r.keys(), key=int):
            before, here, there = b.get(pk), l.get(pk), r.get(pk)
            if here == before:
                chosen = there
            elif there == before or here == there:
                chosen = here
            else:
                conflicts.append({'table':table, 'id':pk})
                continue
            if chosen is not None:
                rows.append(chosen)
        result[table] = rows
    return result, conflicts


def apply(data):
    # Validate every row and all relationships before updating existing database.
    parsed = {}
    for table in TABLES:
        parsed[table] = {}
        for row in data[table]:
            kwargs = {'id':int(row['id'])}
            for key, attr in FIELDS[table].items():
                val = row[key]
                if key in ('when_at','applied_at'):
                    val = datetime.fromtimestamp(int(val)/1000, dt_timezone.utc)
                elif key in ('dob','expiry'):
                    val = date.fromisoformat(val)
                elif key.endswith('_id') or key in ('qty','reserved'):
                    val = None if val is None else int(val)
                kwargs[attr] = val
            obj = MODELS[table](**kwargs)
            # FK validation follows against the merged dataset, not old rows.
            for field in obj._meta.fields:
                if not field.is_relation and not field.primary_key:
                    field.clean(getattr(obj, field.attname), obj)
            parsed[table][obj.pk] = obj
    refs = {'patients':{'guardian_id':'patients'}, 'stock':{'post_id':'posts'}, 'appointments':{'patient_id':'patients','stock_id':'stock'}, 'doses':{'patient_id':'patients','stock_id':'stock','post_id':'posts'}}
    for table, fields in refs.items():
        for row in data[table]:
            for field, target in fields.items():
                if row[field] is not None and int(row[field]) not in parsed[target]:
                    raise ValueError('Vínculo inexistente.')
    for p in parsed['patients'].values():
        if p.data_nascimento > timezone.localdate() or p.data_nascimento.year < 1900:
            raise ValueError('Nascimento inválido.')
        if p.responsavel_id and (p.responsavel_id==p.pk or parsed['patients'][p.responsavel_id].responsavel_id):
            raise ValueError('Dependência circular.')
    for s in parsed['stock'].values():
        reserved = sum(a.status=='pendente' and a.item_estoque_id==s.pk for a in parsed['appointments'].values())
        if not 0 <= s.quantidade_reservada <= s.quantidade_atual <= 1000000 or reserved != s.quantidade_reservada:
            raise ValueError('Reservas e quantidade incompatíveis.')
    dose_links = {int(d['id']):int(d['appointment_id']) if d['appointment_id'] is not None else None for d in data['doses']}
    for a in parsed['appointments'].values():
        if a.status not in {'pendente','concluido','cancelado','ausente'}:
            raise ValueError('Situação inválida.')
        if a.status in {'cancelado','ausente'} and not a.justificativa_cancelamento.strip():
            raise ValueError('Justificativa obrigatória.')
        linked = [d for d in parsed['doses'].values() if dose_links[d.pk]==a.pk]
        if a.status=='concluido' and len(linked)!=1:
            raise ValueError('Agendamento concluído exige uma aplicação.')
    for d in parsed['doses'].values():
        if dose_links[d.pk]:
            a=parsed['appointments'].get(dose_links[d.pk])
            if a is None or a.status!='concluido' or a.paciente_id!=d.paciente_id or a.item_estoque_id!=d.item_estoque_id:
                raise ValueError('Aplicação incompatível com a reserva.')
        if parsed['stock'][d.item_estoque_id].posto_id != d.posto_id:
            raise ValueError('Unidade incompatível com o lote aplicado.')
    pending = [(a.paciente_id,a.item_estoque_id) for a in parsed['appointments'].values() if a.status=='pendente']
    if len(pending) != len(set(pending)):
        raise ValueError('Reserva duplicada.')
    # Never use Vacina.save(): its post_save signal would deduct stock twice.
    for table in TABLES:
        for pk, obj in parsed[table].items():
            values = {attr:getattr(obj, attr) for attr in FIELDS[table].values()}
            if table == 'patients':
                values['responsavel_id'] = None
            if MODELS[table].objects.filter(pk=pk).exists():
                MODELS[table].objects.filter(pk=pk).update(**values)
            else:
                MODELS[table].objects.bulk_create([MODELS[table](id=pk, **values)])
                if table == 'doses':
                    MODELS[table].objects.filter(pk=pk).update(data_aplicacao=obj.data_aplicacao)
    for p in parsed['patients'].values():
        Paciente.objects.filter(pk=p.pk).update(responsavel_id=p.responsavel_id)
    for dose, appointment in dose_links.items():
        if appointment:
            AndroidDoseLink.objects.update_or_create(dose_id=dose, defaults={'appointment_id':appointment})
    # Deleting shared records is deliberately not exposed in the native sync protocol.
    for table in TABLES:
        if MODELS[table].objects.exclude(pk__in=parsed[table]).exists():
            raise ValueError('O backup não pode excluir registros compartilhados. Use o administrador do servidor.')


@csrf_exempt
@require_POST
@authorized
def sync(request, user):
    from django.core.exceptions import ValidationError
    data = payload(request)
    request_id = uuid.UUID(data['request_id'])
    with transaction.atomic():
        AndroidSyncLock.objects.select_for_update().get(pk=1)
        cached = AndroidSyncReceipt.objects.filter(request_id=request_id).first()
        if cached:
            if cached.user_id != user.pk or cached.request_hash != digest(data):
                return response({'error':'Identificador já utilizado.'}, 409)
            return response(cached.response)
        for model in (Estoque, PostoSaude, Paciente, Agendamento, Vacina):
            list(model.objects.select_for_update().order_by('pk').values_list('pk', flat=True))
        remote = canonical(serialize())
        if data.get('pull_only') is True:
            return response({'snapshot':remote, 'revision':signing.dumps(digest(remote), salt='easyvacc-android-base'), 'protocol':1})
        local = canonical(data['local'])
        if data.get('base') is None:
            # First sync can join an empty local installation. Existing local records
            # use random high IDs; collisions are explicit conflicts, never overwrite.
            base = {'format':'EasyVaccNative','version':1, **{t:[] for t in TABLES}}
        else:
            base = canonical(data['base'])
            try:
                signature = signing.loads(data.get('revision',''), salt='easyvacc-android-base')
            except signing.BadSignature:
                return response({'error':'Base de sincronização inválida.'}, 409)
            if signature != digest(base):
                return response({'error':'A base local não corresponde à revisão recebida.'}, 409)
        merged, conflicts = merge(base, local, remote)
        if conflicts:
            return response({'error':'Há alterações simultâneas nos mesmos registros. Seus dados locais foram preservados. Exporte o backup e revise com o administrador.', 'conflicts':conflicts}, 409)
        try:
            with transaction.atomic():
                apply(merged)
                result_data = canonical(serialize())
        except (ValueError, ValidationError, IntegrityError) as error:
            return response({'error':str(error)[:300], 'preserved':True}, 409)
        result = {'snapshot':result_data, 'revision':signing.dumps(digest(result_data), salt='easyvacc-android-base'), 'protocol':1}
        AndroidSyncReceipt.objects.create(request_id=request_id, request_hash=digest(data), user=user, response=result)
        return response(result)
