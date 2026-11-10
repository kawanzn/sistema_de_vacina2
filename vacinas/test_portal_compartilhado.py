import json
import uuid
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from django.utils import timezone

from .models import Estoque, Paciente, PostoSaude


@override_settings(SECRET_KEY='teste-local-portal-compartilhado')
class PortalCompartilhadoTests(TestCase):
    def setUp(self):
        self.post = PostoSaude.objects.create(nome='UBS Bacaxá', endereco='Rua Alfredo Menezes, 980',
                                             bairro='Bacaxá', cep='28994160')
        self.lot = Estoque.objects.create(nome_vacina='Vacina fictícia', lote='TESTE',
                                         quantidade_atual=2, posto=self.post,
                                         data_validade=timezone.localdate()+timedelta(days=10))
        self.patient = Paciente.objects.create(nome='Pessoa fictícia', data_nascimento='2001-01-01')
        get_user_model().objects.create_user(username='profissional', password='Senha-teste-2026!', is_staff=True)

    def test_portal_and_app_use_the_same_reservation(self):
        page = self.client.get('/agendar/', {'unidade': 'ESF Bacaxá'})
        self.assertContains(page, f'<option value="{self.post.pk}" selected>')
        self.assertContains(self.client.get('/postos/'), 'Localizar postos próximos')
        self.assertContains(self.client.get('/lembretes/'), 'Abrir no app Android')
        booking = self.client.post('/agendar/', {
            'nome': self.patient.nome, 'data_nascimento': '2001-01-01',
            'posto': self.post.pk, 'item_estoque': self.lot.pk,
            'data_hora': (timezone.now()+timedelta(days=1)).isoformat(),
        })
        self.assertEqual(booking.status_code, 302)
        token = self.client.post('/api/android/login/', {
            'username': 'profissional', 'password': 'Senha-teste-2026!'
        }, content_type='application/json').json()['token']
        local = {'format': 'EasyVaccNative', 'version': 1,
                 **{t: [] for t in ('posts', 'patients', 'stock', 'appointments', 'doses')}}
        reply = self.client.post('/api/android/sync/', json.dumps({
            'local': local, 'base': None, 'revision': '', 'request_id': str(uuid.uuid4())
        }), content_type='application/json', HTTP_AUTHORIZATION='Bearer '+token)
        self.assertEqual(reply.status_code, 200, reply.content)
        snapshot = reply.json()['snapshot']
        self.assertEqual(snapshot['appointments'][0]['stock_id'], str(self.lot.pk))
        self.assertEqual(snapshot['stock'][0]['reserved'], '1')

    def test_unit_not_registered_has_no_booking_form(self):
        page = self.client.get('/agendar/', {'unidade': 'ESF Vilatur'})
        self.assertContains(page, 'ainda não foi cadastrada')
        self.assertNotContains(page, 'CONFIRMAR AGENDAMENTO')
