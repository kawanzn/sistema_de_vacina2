import os
from unittest.mock import patch
from django.test import SimpleTestCase
import requests


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
