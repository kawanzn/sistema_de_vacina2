import copy
import json
import uuid
from datetime import timedelta
from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from django.utils import timezone
from .models import Paciente, PostoSaude, Estoque, Vacina, Agendamento, AndroidSyncReceipt, AndroidDoseLink
from .android_sync import serialize, canonical, merge


@override_settings(SECRET_KEY='test-only-android-sync-key-not-production')
class AndroidSyncTests(TestCase):
    def setUp(self):
        self.user=get_user_model().objects.create_user('profissional',password='Testing-2026!',is_staff=True)
        self.post=PostoSaude.objects.create(nome='Unidade fictícia',endereco='Rua de teste, 1',bairro='Centro',cep='28990000')
        self.patient=Paciente.objects.create(nome='Paciente fictício',data_nascimento='2000-01-01',cpf='11144477735')
        self.stock=Estoque.objects.create(nome_vacina='Vacina fictícia',lote='TESTE',quantidade_atual=5,quantidade_reservada=0,data_validade='2099-12-31',posto=self.post)
        result=self.client.post('/api/android/login/',json.dumps({'username':'profissional','password':'Testing-2026!'}),content_type='application/json')
        self.assertEqual(result.status_code,200)
        self.headers={'HTTP_AUTHORIZATION':'Bearer '+result.json()['token']}

    def send(self, local, base=None, revision='', request_id=None):
        return self.client.post('/api/android/sync/',json.dumps({'local':local,'base':base,'revision':revision,'request_id':request_id or str(uuid.uuid4())}),content_type='application/json',**self.headers)

    def empty(self):
        return {'format':'EasyVaccNative','version':1,**{t:[] for t in ('posts','patients','stock','appointments','doses')}}

    def bootstrap(self):
        result=self.send(self.empty());self.assertEqual(result.status_code,200,result.content);return result.json()

    def test_requires_professional_auth(self):
        self.assertEqual(self.client.post('/api/android/sync/',{},content_type='application/json').status_code,401)
        get_user_model().objects.create_user('citizen',password='Testing-2026!')
        result=self.client.post('/api/android/login/',json.dumps({'username':'citizen','password':'Testing-2026!'}),content_type='application/json')
        self.assertEqual(result.status_code,401)

    def test_bootstrap_downloads_web_records(self):
        result=self.bootstrap()['snapshot'];self.assertEqual(len(result['patients']),1);self.assertEqual(result['stock'][0]['qty'],'5')

    def test_offline_patient_is_visible_on_web(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);new=copy.deepcopy(local['patients'][0]);new.update(id='1000000001234',cpf=None,name='Novo paciente offline');local['patients'].append(new)
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,200,result.content);self.assertTrue(Paciente.objects.filter(pk=1000000001234,nome='Novo paciente offline').exists())

    def test_retry_does_not_duplicate_or_deduct_twice(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['stock'][0]['qty']='4';applied=int(timezone.now().timestamp()*1000)
        local['doses'].append(dict(id='1000000001111',patient_id=str(self.patient.pk),stock_id=str(self.stock.pk),post_id=str(self.post.pk),name=self.stock.nome_vacina,lot='TESTE',applied_at=str(applied),appointment_id=None,status='ativa',reason=''))
        request_id=str(uuid.uuid4());first=self.send(local,init['snapshot'],init['revision'],request_id);second=self.send(local,init['snapshot'],init['revision'],request_id)
        self.assertEqual(first.status_code,200,first.content);self.assertEqual(first.json(),second.json());self.assertEqual(Vacina.objects.count(),1);self.stock.refresh_from_db();self.assertEqual(self.stock.quantidade_atual,4);self.assertEqual(int(Vacina.objects.get().data_aplicacao.timestamp()*1000),applied)

    def test_independent_device_and_web_edits_merge(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['patients'][0]['address']='Rua offline, 2';self.post.nome='Unidade editada no site';self.post.save()
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,200,result.content);self.patient.refresh_from_db();self.assertEqual(self.patient.endereco,'Rua offline, 2');self.assertEqual(result.json()['snapshot']['posts'][0]['name'],'Unidade editada no site')

    def test_concurrent_stock_changes_preserve_both_sides(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['stock'][0]['qty']='4';self.stock.quantidade_atual=3;self.stock.save()
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,409);self.stock.refresh_from_db();self.assertEqual(self.stock.quantidade_atual,3);self.assertEqual(local['stock'][0]['qty'],'4')

    def test_cannot_forge_baseline(self):
        init=self.bootstrap();base=copy.deepcopy(init['snapshot']);base['stock'][0]['qty']='999';result=self.send(base,base,init['revision']);self.assertEqual(result.status_code,409)

    def test_invalid_reservation_rolls_back_every_table(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['patients'][0]['name']='Não salvar';local['stock'][0]['reserved']='4'
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,409,result.content);self.patient.refresh_from_db();self.assertEqual(self.patient.nome,'Paciente fictício')

    def test_inconsistent_dose_link_is_rejected(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['doses'].append(dict(id='1000000001111',patient_id=str(self.patient.pk),stock_id=str(self.stock.pk),post_id=str(self.post.pk),name='Vacina fictícia',lot='TESTE',applied_at=str(int(timezone.now().timestamp()*1000)),appointment_id='999999',status='ativa',reason=''))
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,409);self.assertEqual(Vacina.objects.count(),0)

    def test_password_change_revokes_token(self):
        self.user.set_password('Changed-2026!');self.user.save();self.assertEqual(self.send(self.empty()).status_code,401)

    def test_duplicate_identity_cannot_partial_write(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);new=copy.deepcopy(local['patients'][0]);new['id']='1000000004444';local['patients'].append(new);local['posts'][0]['name']='Não salvar'
        result=self.send(local,init['snapshot'],init['revision']);self.assertEqual(result.status_code,409,result.content);self.post.refresh_from_db();self.assertEqual(self.post.nome,'Unidade fictícia');self.assertEqual(Paciente.objects.count(),1)

    def test_offline_reservation_then_confirmation_updates_web(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['stock'][0]['reserved']='1';a=dict(id='1000000002222',patient_id=str(self.patient.pk),stock_id=str(self.stock.pk),when_at=str(int((timezone.now()+timedelta(days=1)).timestamp()*1000)),status='pendente',reason='');local['appointments'].append(a)
        scheduled=self.send(local,init['snapshot'],init['revision']);self.assertEqual(scheduled.status_code,200,scheduled.content);next=scheduled.json();local=copy.deepcopy(next['snapshot']);local['appointments'][0]['status']='concluido';local['stock'][0].update(qty='4',reserved='0');local['doses'].append(dict(id='1000000003333',patient_id=str(self.patient.pk),stock_id=str(self.stock.pk),post_id=str(self.post.pk),name='Vacina fictícia',lot='TESTE',applied_at=str(int(timezone.now().timestamp()*1000)),appointment_id=a['id'],status='ativa',reason=''))
        done=self.send(local,next['snapshot'],next['revision']);self.assertEqual(done.status_code,200,done.content);self.assertEqual(AndroidDoseLink.objects.get().appointment_id,int(a['id']));self.stock.refresh_from_db();self.assertEqual((self.stock.quantidade_atual,self.stock.quantidade_reservada),(4,0))

    def test_cannot_delete_shared_data_via_backup(self):
        init=self.bootstrap();result=self.send(self.empty(),init['snapshot'],init['revision']);self.assertEqual(result.status_code,409);self.assertEqual(Paciente.objects.count(),1)

    def test_same_change_on_two_devices_is_idempotent(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);local['patients'][0]['address']='Mesmo endereço';a=self.send(local,init['snapshot'],init['revision']);b=self.send(local,init['snapshot'],init['revision']);self.assertEqual(a.status_code,200,a.content);self.assertEqual(b.status_code,200,b.content);self.assertEqual(Paciente.objects.count(),1)

    def test_recovery_download_never_changes_server(self):
        result=self.client.post('/api/android/sync/',json.dumps({'request_id':str(uuid.uuid4()),'pull_only':True}),content_type='application/json',**self.headers)
        self.assertEqual(result.status_code,200,result.content);self.assertEqual(len(result.json()['snapshot']['patients']),1);self.assertEqual(AndroidSyncReceipt.objects.count(),0);self.stock.refresh_from_db();self.assertEqual(self.stock.quantidade_atual,5)

    def test_retry_id_cannot_be_reused_for_different_changes(self):
        init=self.bootstrap();local=copy.deepcopy(init['snapshot']);uid=str(uuid.uuid4());first=self.send(local,init['snapshot'],init['revision'],uid);self.assertEqual(first.status_code,200,first.content);local['patients'][0]['name']='Mudança com UUID incorreto';second=self.send(local,init['snapshot'],init['revision'],uid);self.assertEqual(second.status_code,409);self.patient.refresh_from_db();self.assertEqual(self.patient.nome,'Paciente fictício')
