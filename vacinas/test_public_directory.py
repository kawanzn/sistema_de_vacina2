import json
from datetime import date
from django.test import TestCase
from .models import PostoSaude, Estoque, Paciente


class PublicDirectoryTests(TestCase):
    def test_empty_server_has_entire_municipal_directory_without_login(self):
        response=self.client.get('/api/android/posts/')
        self.assertEqual(response.status_code,200)
        rows=response.json()['units']
        self.assertEqual(len(rows),21)
        for name in ('ESF Boqueirão','ESF Guarani','ESF Saquarema','UBS Engenho Grande'):
            self.assertIn(name,[row['name'] for row in rows])

    def test_directory_does_not_disclose_patients_or_lots(self):
        Paciente.objects.create(nome='DADO PRIVADO TESTE',data_nascimento=date(2000,1,1))
        post=PostoSaude.objects.create(nome='ESF Bacaxá',endereco='Rua de teste',bairro='Bacaxá',cep='28990000')
        for lot,qty,reserved,expiry in [('PRIVADO-A',5,1,'2099-01-01'),('PRIVADO-B',0,0,'2099-01-01'),('PRIVADO-C',5,0,'2000-01-01')]:
            Estoque.objects.create(posto=post,nome_vacina='Vacina '+lot[-1],lote=lot,quantidade_atual=qty,quantidade_reservada=reserved,data_validade=expiry)
        response=self.client.get('/api/android/posts/')
        text=response.content.decode()
        self.assertNotIn('DADO PRIVADO TESTE',text)
        self.assertNotIn('PRIVADO-',text)
        self.assertNotIn('"patients"',text)
        rows=response.json()['units']
        self.assertEqual(len(rows),21)
        bacaxa=next(r for r in rows if r['name']=='ESF Bacaxá')
        self.assertEqual(bacaxa['availability'],'Vacina A')
        self.assertEqual(bacaxa['address'],'Rua de teste')

    def test_public_directory_never_creates_operational_records(self):
        self.client.get('/api/android/posts/')
        self.assertEqual(PostoSaude.objects.count(),0)
        self.assertEqual(Estoque.objects.count(),0)

    def test_directory_accepts_get_only(self):
        self.assertEqual(self.client.post('/api/android/posts/').status_code,405)
