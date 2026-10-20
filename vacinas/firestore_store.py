"""PersistÃªncia dos modelos do exercÃ­cio em documentos do Firestore.

Os campos e formulÃ¡rios do Django continuam validando os dados. Esta camada
implementa apenas as consultas usadas pelo projeto, sem executar SQL.
"""
import hashlib
import json
import os
import secrets
from contextlib import contextmanager
from contextvars import ContextVar
from datetime import date, datetime
from functools import lru_cache, wraps

from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q
from google.cloud import firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from google.oauth2 import service_account

_unit = ContextVar('firestore_unit', default=None)


@lru_cache(maxsize=1)
def client():
    project = os.environ['FIREBASE_PROJECT_ID']
    raw = os.environ.get('FIREBASE_SERVICE_ACCOUNT_JSON')
    credentials = service_account.Credentials.from_service_account_info(json.loads(raw)) if raw else None
    return firestore.Client(project=project, credentials=credentials)


def collection(model):
    prefix = os.environ.get('FIRESTORE_COLLECTION_PREFIX', 'easyvacc_')
    return client().collection(prefix + model._meta.model_name)


def read(ref):
    unit = _unit.get()
    if unit and ref.path in unit['writes']:
        return unit['writes'][ref.path][1].copy()
    snapshot = ref.get(transaction=unit['transaction'] if unit else None)
    return snapshot.to_dict() if snapshot.exists else None


def write(ref, data):
    unit = _unit.get()
    if unit is None:
        raise RuntimeError('GravaÃ§Ãµes precisam de uma transaÃ§Ã£o Firestore.')
    unit['writes'][ref.path] = (ref, data.copy())


def run_atomic(callback):
    if _unit.get() is not None:
        return callback()

    @firestore.transactional
    def execute(transaction):
        unit = {'transaction': transaction, 'writes': {}}
        token = _unit.set(unit)
        try:
            result = callback()
            # Todas as leituras ocorrem antes das gravaÃ§Ãµes, como exige o Firestore.
            for ref, data in unit['writes'].values():
                transaction.set(ref, data)
            return result
        finally:
            _unit.reset(token)

    return execute(client().transaction())


def atomic_view(view):
    @wraps(view)
    def wrapped(request, *args, **kwargs):
        if request.method != 'POST':
            return view(request, *args, **kwargs)
        return run_atomic(lambda: view(request, *args, **kwargs))
    return wrapped


@contextmanager
def atomic():
    if _unit.get() is None:
        raise RuntimeError('Use atomic_view para iniciar uma transaÃ§Ã£o.')
    unit = _unit.get()
    before = unit['writes'].copy()
    try:
        yield
    except Exception:
        unit['writes'] = before
        raise


def serialize(value):
    return value.isoformat() if isinstance(value, date) and not isinstance(value, datetime) else value


def hydrate(model, data):
    values = {}
    for field in model._meta.concrete_fields:
        value = data.get(field.attname, field.get_default())
        values[field.attname] = field.target_field.to_python(value) if field.is_relation else field.to_python(value)
    obj = model(**values)
    obj._state.adding = False
    obj._state.db = 'default'
    return obj


def _value(obj, path):
    for part in path:
        obj = getattr(obj, 'pk' if part == 'pk' else part, None)
        if obj is None:
            break
    return obj


LOOKUPS = {'exact', 'iexact', 'icontains', 'isnull', 'gt', 'gte', 'lt', 'lte', 'in'}


def _match(obj, node):
    if isinstance(node, Q):
        results = [_match(obj, child) for child in node.children]
        result = any(results) if node.connector == Q.OR else all(results)
        return not result if node.negated else result
    key, expected = node
    parts = key.split('__')
    lookup = parts.pop() if parts[-1] in LOOKUPS else 'exact'
    actual = _value(obj, parts)
    if hasattr(actual, 'pk'):
        actual = actual.pk
    if hasattr(expected, 'pk'):
        expected = expected.pk
    if isinstance(actual, int) and isinstance(expected, str):
        try:
            expected = int(expected)
        except ValueError:
            return False
    if lookup == 'isnull':
        return (actual is None) == expected
    if lookup in {'iexact', 'icontains'}:
        left, right = str(actual or '').casefold(), str(expected or '').casefold()
        return left == right if lookup == 'iexact' else right in left
    if lookup == 'in':
        return actual in expected
    if lookup == 'exact':
        return actual == expected
    if actual is None:
        return False
    return {'gt': lambda: actual > expected, 'gte': lambda: actual >= expected,
            'lt': lambda: actual < expected, 'lte': lambda: actual <= expected}[lookup]()


class DocumentQuery:
    _prefetch_related_lookups = ()
    _db = 'default'
    ordered = True

    def __init__(self, model, predicates=(), ordering=(), empty=False):
        self.model = model
        self.predicates = predicates
        self.ordering = ordering
        self.empty = empty
        self._result_cache = None

    def all(self):
        return DocumentQuery(self.model, self.predicates, self.ordering, self.empty)

    def none(self):
        return DocumentQuery(self.model, empty=True)

    def using(self, alias):
        return self.all()

    def _add_hints(self, **hints):
        pass

    def filter(self, *args, **kwargs):
        return DocumentQuery(self.model, self.predicates + (Q(*args, **kwargs),), self.ordering, self.empty)

    def exclude(self, *args, **kwargs):
        return DocumentQuery(self.model, self.predicates + (~Q(*args, **kwargs),), self.ordering, self.empty)

    def complex_filter(self, value):
        return self.filter(value) if isinstance(value, Q) else self.filter(**value)

    def order_by(self, *fields):
        return DocumentQuery(self.model, self.predicates, fields, self.empty)

    def select_related(self, *fields):
        return self

    def prefetch_related(self, *fields):
        return self

    def select_for_update(self):
        if _unit.get() is None:
            raise RuntimeError('Leitura de estoque fora de uma transaÃ§Ã£o.')
        return self

    def _load(self):
        if self._result_cache is not None:
            return self._result_cache
        if self.empty:
            return []
        query = collection(self.model)
        direct_id = None
        # Usa Ã­ndices simples do Firestore. Filtros de texto e OR sÃ£o avaliados
        # sobre os candidatos; nÃ£o hÃ¡ traduÃ§Ã£o arbitrÃ¡ria de consultas SQL.
        for node in self.predicates:
            if node.negated or node.connector != Q.AND:
                continue
            for child in node.children:
                if not isinstance(child, tuple):
                    continue
                key, value = child
                parts = key.split('__')
                op = parts.pop() if parts[-1] in LOOKUPS else 'exact'
                if len(parts) != 1 or op != 'exact':
                    continue
                name = parts[0]
                if name in {'id', 'pk'}:
                    direct_id = str(value)
                    continue
                try:
                    field = self.model._meta.get_field(name.removesuffix('_id'))
                except Exception:
                    continue
                value = value.pk if hasattr(value, 'pk') else value
                value = field.target_field.to_python(value) if field.is_relation else field.to_python(value)
                query = query.where(filter=FieldFilter(field.attname, '==', serialize(value)))
        if direct_id is not None:
            data = read(collection(self.model).document(direct_id))
            candidates = [data] if data else []
        else:
            unit = _unit.get()
            docs = query.stream(transaction=unit['transaction'] if unit else None)
            candidates_by_id = {doc.id: doc.to_dict() for doc in docs}
            if unit:
                for ref, data in unit['writes'].values():
                    if ref.parent.id == collection(self.model).id:
                        candidates_by_id[ref.id] = data
            candidates = candidates_by_id.values()
        result = [hydrate(self.model, data) for data in candidates]
        result = [obj for obj in result if all(_match(obj, node) for node in self.predicates)]
        for field in reversed(self.ordering):
            name = field.lstrip('-').split('__')
            result.sort(key=lambda obj: (_value(obj, name) is not None, _value(obj, name)), reverse=field.startswith('-'))
        self._result_cache = result
        return result

    def __iter__(self):
        return iter(self._load())

    def __len__(self):
        return len(self._load())

    def __bool__(self):
        return bool(self._load())

    def __getitem__(self, key):
        return self._load()[key]

    def iterator(self, *args, **kwargs):
        return iter(self)

    def count(self):
        return len(self)

    def exists(self):
        return bool(self)

    def first(self):
        return next(iter(self), None)

    def get(self, *args, **kwargs):
        values = list(self.filter(*args, **kwargs))
        if not values:
            raise self.model.DoesNotExist()
        if len(values) != 1:
            raise self.model.MultipleObjectsReturned()
        return values[0]

    def values(self, *fields):
        return [{field: _value(obj, field.split('__')) for field in fields} for obj in self]

    def create(self, **kwargs):
        obj = self.model(**kwargs)
        obj.save()
        return obj

    def update_or_create(self, defaults=None, **kwargs):
        obj = self.filter(**kwargs).first()
        created = obj is None
        obj = obj or self.model(**kwargs)
        for key, value in (defaults or {}).items():
            setattr(obj, key, value)
        obj.save()
        return obj, created


class DocumentManager(models.Manager.from_queryset(DocumentQuery)):
    def get_queryset(self):
        return DocumentQuery(self.model)


class DocumentModel(models.Model):
    objects = DocumentManager()

    class Meta:
        abstract = True
        base_manager_name = 'objects'

    def save(self, *args, **kwargs):
        def persist():
            adding = not self.pk
            self.pk = self.pk or secrets.randbelow(2**52 - 1) + 1
            ref = collection(type(self)).document(str(self.pk))
            previous = read(ref)
            data = {}
            reservations = []
            for field in self._meta.concrete_fields:
                value = getattr(self, field.attname)
                if getattr(field, 'auto_now', False) or (adding and getattr(field, 'auto_now_add', False)):
                    from django.utils import timezone
                    value = timezone.now()
                    setattr(self, field.attname, value)
                data[field.attname] = serialize(value)
                if field.unique and not field.primary_key and value not in (None, ''):
                    digest = hashlib.sha256(f'{self._meta.model_name}:{field.name}:{value}'.encode()).hexdigest()
                    unique_ref = client().collection(os.environ.get('FIRESTORE_COLLECTION_PREFIX', 'easyvacc_') + 'unique').document(digest)
                    owner = read(unique_ref)
                    if owner and owner.get('owner') not in (None, self.pk):
                        raise ValidationError({field.name: 'Este valor jÃ¡ estÃ¡ cadastrado.'})
                    reservations.append((unique_ref, {'owner': self.pk}))
            # Um valor anterior liberado fica reutilizÃ¡vel sem remover documentos.
            if previous:
                for field in self._meta.concrete_fields:
                    old = previous.get(field.attname)
                    if field.unique and not field.primary_key and old not in (None, '') and old != data[field.attname]:
                        digest = hashlib.sha256(f'{self._meta.model_name}:{field.name}:{old}'.encode()).hexdigest()
                        old_ref = client().collection(os.environ.get('FIRESTORE_COLLECTION_PREFIX', 'easyvacc_') + 'unique').document(digest)
                        read(old_ref)
                        reservations.append((old_ref, {'owner': None}))
            for unique_ref, ownership in reservations:
                write(unique_ref, ownership)
            write(ref, data)
            self._state.adding = False
            self._state.db = 'default'
        return run_atomic(persist)

    def delete(self, *args, **kwargs):
        raise NotImplementedError('ExclusÃ£o de documentos nÃ£o Ã© usada neste exercÃ­cio.')
