from datetime import timedelta
from unittest.mock import patch

import requests
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from .models import Estoque, PostoSaude


class BuscaPostosTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.posto = PostoSaude.objects.create(
            nome='Unidade TESTE-001', endereco='Rua Fictícia, 10',
            bairro='Centro', cep='28990000',
        )

    @patch('vacinas.views.requests.get')
    def test_busca_por_bairro_sem_requisicao_externa(self, get):
        response = self.client.get(reverse('busca_postos'), {'cep': 'centro'})
        self.assertContains(response, 'Unidade TESTE-001')
        self.assertContains(response, 'data-share-posto')
        get.assert_not_called()

    def test_busca_por_cep_formatado(self):
        response = self.client.get(reverse('busca_postos'), {'cep': '28990-000'})
        self.assertContains(response, 'Rua Fictícia, 10')

    @patch('vacinas.views.requests.get')
    def test_texto_desconhecido_nao_consulta_viacep(self, get):
        response = self.client.get(reverse('busca_postos'), {'cep': 'Inexistente'})
        self.assertContains(response, 'Nenhuma unidade')
        get.assert_not_called()

    @patch('vacinas.views.requests.get', side_effect=requests.Timeout)
    def test_timeout_preserva_busca(self, get):
        response = self.client.get(reverse('busca_postos'), {'cep': '11111111'})
        self.assertContains(response, 'Nenhuma unidade')
        self.assertContains(response, 'value="11111111"')

    @patch('vacinas.views.requests.get')
    def test_cep_sem_bairro_nao_lista_todos(self, get):
        get.return_value.status_code = 200
        get.return_value.json.return_value = {'localidade': 'Cidade fictícia'}
        response = self.client.get(reverse('busca_postos'), {'cep': '11111111'})
        self.assertNotContains(response, 'Unidade TESTE-001')

    def test_estoque_vencido_ou_reservado_nao_anuncia_disponibilidade(self):
        for name, reserved, days in [('Reservada', 1, 10), ('Vencida', 0, -1), ('Disponível', 0, 10)]:
            Estoque.objects.create(
                nome_vacina=name, lote='FICTICIO', quantidade_atual=1,
                quantidade_reservada=reserved, posto=self.posto,
                data_validade=timezone.localdate() + timedelta(days=days),
            )
        response = self.client.get(reverse('busca_postos'), {'cep': '28990000'})
        self.assertEqual(list(response.context['postos'][0].estoque_vacinas.values_list('nome_vacina', flat=True)), ['Disponível'])

    def test_dados_de_compartilhamento_escapam_html(self):
        self.posto.nome = '<script>alert("teste")</script>'
        self.posto.save()
        response = self.client.get(reverse('busca_postos'), {'cep': '28990000'})
        self.assertContains(response, '&lt;script&gt;')
        self.assertNotContains(response, '<script>alert(')
