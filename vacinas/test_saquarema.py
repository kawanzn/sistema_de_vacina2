import os
from types import SimpleNamespace
from unittest.mock import patch
from django.test import SimpleTestCase, override_settings
import requests


@override_settings(STORAGES={'staticfiles': {'BACKEND': 'django.contrib.staticfiles.storage.StaticFilesStorage'}})
class SaquaremaTests(SimpleTestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'FIREBASE_PROJECT_ID': ''})
        self.env.start()
        self.addCleanup(self.env.stop)

    def test_directory_without_cloud(self):
        response = self.client.get('/postos/')
        self.assertContains(response, 'ESF Bacaxá')
        self.assertEqual(len(response.context['postos']), 21)
        self.assertContains(response, 'Disponibilidade de vacinas não consultada')
        self.assertNotContains(response, 'Ex.: 28994-160 ou Bacaxá')

    def test_cep_with_or_without_mask_prioritizes_bacaxa(self):
        for cep in ('28994-160', '28994160'):
            with self.subTest(cep=cep):
                response = self.client.get('/postos/', {'cep': cep})
                self.assertContains(response, 'Condado de Bacaxá')
                self.assertEqual(response.context['postos'][0]['nome'], 'ESF Bacaxá')
                self.assertEqual(len(response.context['postos']), 21)

    def test_bairro_without_accents(self):
        response = self.client.get('/postos/', {'cep': 'jacone'})
        self.assertContains(response, 'ESF Jaconé')
        self.assertEqual(len(response.context['postos']), 1)

    @patch('vacinas.saquarema.lookup_cep', return_value={'localidade': 'Niterói', 'uf': 'RJ'})
    def test_other_city_explained(self, lookup):
        self.assertContains(self.client.get('/postos/', {'cep': '24000-000'}), 'fora de Saquarema')

    @patch('vacinas.saquarema.lookup_cep', side_effect=requests.Timeout)
    def test_timeout_keeps_public_list(self, lookup):
        response = self.client.get('/postos/', {'cep': '28990-000'})
        self.assertContains(response, 'consulta de CEP está indisponível')
        self.assertContains(response, 'ESF Bacaxá')

    def test_calendar_page_explains_missing_db(self):
        self.assertContains(self.client.get('/agendar/'), 'O agendamento está indisponível nesta instância')
        self.assertEqual(self.client.post('/agendar/').status_code, 503)

    def test_directory_has_coordinates_and_booking_links(self):
        response = self.client.get('/postos/')
        self.assertContains(response, 'Localizar postos próximos')
        self.assertContains(response, 'Ver rota no Google Maps')
        units = response.context['postos']
        self.assertEqual(sum(unit['lat'] is not None and unit['lon'] is not None for unit in units), 18)
        self.assertContains(response, '?unidade=ESF%20Bacax%C3%A1')
        self.assertContains(self.client.get('/agendar/', {'unidade': 'ESF Bacaxá'}), 'ESF Bacaxá')

    @patch.dict(os.environ, {'FIREBASE_PROJECT_ID': 'teste-isolado'})
    @patch('vacinas.views.PostoSaude.objects.all')
    def test_booking_preselects_registered_unit(self, posts):
        posts.return_value = [SimpleNamespace(pk=17, id=17, nome='UBS Bacaxá'),
                              SimpleNamespace(pk=22, id=22, nome='ESF Jaconé')]
        response = self.client.get('/agendar/', {'unidade': 'ESF Bacaxá'})
        self.assertContains(response, '<option value="17" selected>')
        self.assertNotContains(response, '<option value="22" selected>')
        self.assertContains(self.client.get('/agendar/', {'unidade': 'ESF Vilatur'}), 'ainda não foi cadastrada')
