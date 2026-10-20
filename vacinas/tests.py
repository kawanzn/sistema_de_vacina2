"""IntegraÃ§Ã£o com documentos fictÃ­cios em coleÃ§Ãµes temporÃ¡rias separadas.

Execute com RUN_FIRESTORE_TESTS=1 e credenciais de um projeto de testes.
"""
import os
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from unittest import skipUnless
from unittest.mock import patch
from django.core.exceptions import ValidationError
from django.test import Client, SimpleTestCase, override_settings
from django.utils import timezone
from .firestore_store import client, run_atomic
from .forms import PacienteForm
from .models import Agendamento, Estoque, Paciente, PostoSaude, Servidor, Vacina


class StockValidityTests(SimpleTestCase):
    @patch('vacinas.models.timezone.localdate', return_value=date(2026, 10, 20))
    def test_validity_boundaries(self, today):
        for days, status in [(-1, 'vencido'), (0, 'proximo'), (30, 'proximo'), (31, 'regular')]:
            with self.subTest(days=days):
                stock = Estoque(data_validade=today.return_value+timedelta(days=days), quantidade_atual=2)
                self.assertEqual(stock.validade_status, status)
                self.assertEqual(stock.vencido, days < 0)
                self.assertEqual(stock.disponivel_para_uso, days >= 0)

    @patch('vacinas.models.timezone.localdate', return_value=date(2026, 10, 20))
    def test_reserved_stock_is_not_available(self, today):
        stock = Estoque(data_validade=today.return_value, quantidade_atual=1, quantidade_reservada=1)
        self.assertFalse(stock.disponivel_para_uso)


@skipUnless(os.environ.get('RUN_FIRESTORE_TESTS') == '1', 'Requer Firestore de teste explÃ­cito.')
@override_settings(ALLOWED_HOSTS=['testserver'])
class FirestoreIntegrationTests(SimpleTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.old_prefix = os.environ.get('FIRESTORE_COLLECTION_PREFIX')
        cls.prefix = 'test_' + uuid.uuid4().hex + '_'
        os.environ['FIRESTORE_COLLECTION_PREFIX'] = cls.prefix

    @classmethod
    def tearDownClass(cls):
        try:
            for group in client().collections():
                if group.id.startswith(cls.prefix):
                    for doc in group.stream():
                        doc.reference.delete()
        finally:
            if cls.old_prefix is None:
                os.environ.pop('FIRESTORE_COLLECTION_PREFIX', None)
            else:
                os.environ['FIRESTORE_COLLECTION_PREFIX'] = cls.old_prefix
            super().tearDownClass()

    def setUp(self):
        self.user = Servidor(username=uuid.uuid4().hex, is_superuser=True)
        self.user.set_password('Teste-aula-2026!')
        self.user.save()
        self.client.force_login(self.user, backend='vacinas.auth_backend.FirestoreBackend')
        self.patient = Paciente.objects.create(nome='Pessoa FictÃ­cia '+uuid.uuid4().hex, data_nascimento=date(2000, 1, 1))
        self.posto = PostoSaude.objects.create(nome='Posto fictÃ­cio', endereco='Rua de teste', bairro='Centro', cep='00000-000')
        self.stock = Estoque.objects.create(nome_vacina='Vacina fictÃ­cia', lote=uuid.uuid4().hex, quantidade_atual=2,
                                           data_validade=date.today()+timedelta(days=365), posto=self.posto)

    def schedule(self, web=None, replay=None):
        response = (web or self.client).post('/agendar/', {
            'nome': self.patient.nome, 'data_nascimento': '2000-01-01',
            'item_estoque': self.stock.pk, 'data_hora': (timezone.now()+timedelta(days=1)).isoformat(),
            'offline_client_id': replay or uuid.uuid4().hex,
        }, HTTP_X_OFFLINE_REPLAY='1')
        self.assertNotEqual(response.status_code, 500, response.content)
        return response

    def test_pages_forms_and_login_without_sql(self):
        for url in ['/', '/portal/', '/login/', '/dashboard/', '/pacientes/', '/pacientes/novo/', '/estoque/',
                    '/estoque/novo/', '/registrar-dose/', '/agendar/', '/agendamentos/listar/',
                    '/agendamentos/historico/', '/admin/', '/api/offline/bootstrap/', '/manifest.webmanifest', '/sw.js']:
            with self.subTest(url=url):
                self.assertEqual(self.client.get(url).status_code, 200)
        web = Client()
        self.assertEqual(web.post('/login/', {'username':self.user.username, 'password':'Teste-aula-2026!'}).status_code,302)
        self.assertEqual(web.get('/dashboard/').status_code, 200)

    def test_schedule_confirm_and_replay(self):
        replay = uuid.uuid4().hex
        self.assertEqual(self.schedule(replay=replay).status_code, 200)
        self.assertEqual(self.schedule(replay=replay).status_code, 200)
        booking = Agendamento.objects.get(paciente=self.patient)
        stock = Estoque.objects.get(pk=self.stock.pk)
        self.assertEqual((stock.quantidade_atual,stock.quantidade_reservada),(2,1))
        url = f'/agendamentos/confirmar/{booking.pk}/'
        data = {'offline_client_id':uuid.uuid4().hex}
        self.assertEqual(self.client.post(url,data,HTTP_X_OFFLINE_REPLAY='1').status_code,200)
        self.assertEqual(self.client.post(url,data,HTTP_X_OFFLINE_REPLAY='1').status_code,200)
        self.assertEqual(Vacina.objects.filter(paciente=self.patient).count(),1)
        stock = Estoque.objects.get(pk=self.stock.pk)
        self.assertEqual((stock.quantidade_atual,stock.quantidade_reservada),(1,0))
        response = self.client.post(f'/agendamentos/cancelar/{booking.pk}/',{'justificativa':'Teste'},HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code,409)
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_atual,1)

    def test_cancellation_does_not_create_doses(self):
        self.assertEqual(self.schedule().status_code,200)
        booking = Agendamento.objects.get(paciente=self.patient)
        url = f'/agendamentos/cancelar/{booking.pk}/'
        data = {'justificativa':'Teste fictÃ­cio','offline_client_id':uuid.uuid4().hex}
        self.assertEqual(self.client.post(url,data,HTTP_X_OFFLINE_REPLAY='1').status_code,200)
        self.assertEqual(self.client.post(url,data,HTTP_X_OFFLINE_REPLAY='1').status_code,200)
        stock = Estoque.objects.get(pk=self.stock.pk)
        self.assertEqual((stock.quantidade_atual,stock.quantidade_reservada),(2,0))
        self.assertFalse(Vacina.objects.filter(paciente=self.patient).exists())

    def test_last_dose_can_only_be_reserved_once(self):
        self.stock.quantidade_atual = 1
        self.stock.save()
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(lambda _: self.schedule(web=Client()),range(2)))
        self.assertEqual(sorted(response.status_code for response in responses),[200,409])
        self.assertEqual(Agendamento.objects.filter(paciente=self.patient).count(),1)
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_reservada,1)

    def test_patient_uniqueness_and_dependents(self):
        cpf = uuid.uuid4().hex[:14]
        self.patient.cpf = cpf
        self.patient.save()
        form = PacienteForm({'nome':'Dependente fictÃ­cio','data_nascimento':'2020-01-01','responsavel':self.patient.pk})
        self.assertTrue(form.is_valid(),form.errors)
        dependent = form.save()
        self.assertEqual(dependent.responsavel.nome,self.patient.nome)
        self.assertEqual(self.patient.dependentes.count(),1)
        with self.assertRaises(ValidationError):
            Paciente.objects.create(nome='Duplicado',data_nascimento=date(2000,1,1),cpf=cpf)

    def test_transaction_rolls_back(self):
        def fail():
            stock = Estoque.objects.get(pk=self.stock.pk)
            stock.quantidade_atual = 99
            stock.save()
            raise ValueError('Falha simulada')
        with self.assertRaises(ValueError):
            run_atomic(fail)
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_atual,2)

    def test_patient_and_dose_forms_save_to_firestore(self):
        name = 'Cadastro fictício ' + uuid.uuid4().hex
        response = self.client.post('/pacientes/novo/', {
            'nome': name, 'data_nascimento': '2001-02-03',
            'endereco': 'Endereço fictício', 'offline_client_id': uuid.uuid4().hex,
        }, HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code, 200, response.content)
        patient = Paciente.objects.get(nome=name)
        response = self.client.post('/registrar-dose/', {
            'paciente': patient.pk, 'posto_selecionado': self.posto.pk,
            'item_estoque': self.stock.pk, 'offline_client_id': uuid.uuid4().hex,
        }, HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code, 200, response.content)
        self.assertEqual(Vacina.objects.filter(paciente=patient).count(), 1)
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_atual, 1)

    def test_reserved_dose_requires_confirmation_of_booking(self):
        self.stock.quantidade_atual = 1
        self.stock.save()
        self.assertEqual(self.schedule().status_code, 200)
        response = self.client.post('/registrar-dose/', {
            'paciente': self.patient.pk, 'posto_selecionado': self.posto.pk,
            'item_estoque': self.stock.pk,
        }, HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code, 422, response.content)
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_atual, 1)

    def test_anonymous_and_non_admin_access(self):
        self.assertEqual(Client().get('/dashboard/').status_code,302)
        self.assertEqual(Client().get('/agendamentos/historico/').status_code,302)
        self.user.is_superuser = False
        self.user.save()
        self.assertEqual(self.client.get('/admin/').status_code,403)

    def test_expired_lot_is_visible_but_cannot_be_used(self):
        self.stock.data_validade = timezone.localdate()-timedelta(days=1)
        self.stock.save()
        page = self.client.get('/estoque/')
        self.assertContains(page, 'Vencido — uso bloqueado')
        self.assertEqual(self.schedule().status_code, 409)
        self.assertFalse(Agendamento.objects.filter(paciente=self.patient).exists())
        response = self.client.post('/registrar-dose/', {
            'paciente':self.patient.pk, 'posto_selecionado':self.posto.pk,
            'item_estoque':self.stock.pk,
        }, HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code, 422)
        self.assertFalse(Vacina.objects.filter(paciente=self.patient).exists())
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_atual, 2)
        self.assertNotIn(self.stock.pk, [item['id'] for item in self.client.get(
            '/ajax/carregar-vacinas/', {'posto_id':self.posto.pk}).json()])
        self.assertNotIn(self.stock.pk, [item['id'] for item in self.client.get(
            '/api/offline/bootstrap/').json()['estoques']])
        with self.assertRaises(ValidationError):
            Vacina.objects.create(paciente=self.patient, item_estoque=self.stock)

    def test_booking_cannot_outlive_lot_validity(self):
        self.stock.data_validade = timezone.localdate()
        self.stock.save()
        self.assertEqual(self.schedule().status_code, 409)
        self.assertFalse(Agendamento.objects.filter(paciente=self.patient).exists())
        self.assertEqual(Estoque.objects.get(pk=self.stock.pk).quantidade_reservada, 0)

    def test_confirmation_rechecks_lot_validity(self):
        self.assertEqual(self.schedule().status_code, 200)
        booking = Agendamento.objects.get(paciente=self.patient)
        self.stock = Estoque.objects.get(pk=self.stock.pk)
        self.stock.data_validade = timezone.localdate()-timedelta(days=1)
        self.stock.save()
        response = self.client.post(f'/agendamentos/confirmar/{booking.pk}/', {}, HTTP_X_OFFLINE_REPLAY='1')
        self.assertEqual(response.status_code, 409)
        self.assertEqual(Agendamento.objects.get(pk=booking.pk).status, 'pendente')
        self.assertFalse(Vacina.objects.filter(paciente=self.patient).exists())
        stock = Estoque.objects.get(pk=self.stock.pk)
        self.assertEqual((stock.quantidade_atual, stock.quantidade_reservada), (2,1))
