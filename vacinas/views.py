import csv
import json
import os
import requests
from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth.decorators import login_required
from .models import Vacina, Paciente, Estoque, PostoSaude, Agendamento, OfflineSubmission
from .forms import PacienteForm, VacinaForm, EstoqueForm
from django.http import HttpResponse
from django.db.models import Q
from django.conf import settings
from django.http import JsonResponse
from django.views.decorators.http import require_GET, require_POST
from django.contrib.staticfiles.storage import staticfiles_storage
from . import firestore_store as transaction
from .firestore_store import atomic_view
from django.db.utils import OperationalError, ProgrammingError
from django.contrib import messages
from django.urls import reverse
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from django import forms
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError
from .models import Servidor


class PostoForm(forms.ModelForm):
    class Meta:
        model = PostoSaude
        fields = ['nome', 'endereco', 'bairro', 'cep']


class ServidorForm(forms.Form):
    username = forms.CharField(label='Usuário', max_length=150)
    email = forms.EmailField(label='E-mail', required=False)
    password = forms.CharField(label='Senha', widget=forms.PasswordInput)

    def clean_password(self):
        password = self.cleaned_data['password']
        validate_password(password)
        return password


@login_required
@atomic_view
def configuracoes(request):
    if not request.user.is_superuser:
        return HttpResponse('Acesso exclusivo do administrador.', status=403)
    posto_form = PostoForm(request.POST if request.POST.get('action') == 'posto' else None)
    servidor_form = ServidorForm(request.POST if request.POST.get('action') == 'servidor' else None)
    if request.method == 'POST':
        if request.POST.get('action') == 'posto' and posto_form.is_valid():
            posto_form.save()
            messages.success(request, 'Posto cadastrado com sucesso.')
            return redirect('configuracoes')
        if request.POST.get('action') == 'servidor' and servidor_form.is_valid():
            data = servidor_form.cleaned_data
            if Servidor.objects.filter(username=data['username']).exists():
                servidor_form.add_error('username', 'Este usuário já existe.')
            else:
                user = Servidor(username=data['username'], email=data['email'])
                user.set_password(data['password'])
                user.save()
                messages.success(request, 'Servidor cadastrado com sucesso.')
                return redirect('configuracoes')
    return render(request, 'vacinas/configuracoes.html', {
        'posto_form': posto_form, 'servidor_form': servidor_form,
        'postos': PostoSaude.objects.order_by('nome'),
    })


OFFLINE_TRACKING_DISABLED = False


def _wants_sync_json(request):
    return (
        request.headers.get('X-Offline-Replay') == '1'
        or request.headers.get('X-Requested-With') == 'XMLHttpRequest'
    )


def _form_error_message(form):
    errors = []
    for field, field_errors in form.errors.items():
        label = form.fields.get(field).label if field in form.fields else field
        errors.append(f"{label}: {'; '.join(field_errors)}")
    return ' '.join(errors) or 'Revise os campos do formulario.'


def _offline_client_id(request):
    return (request.POST.get('offline_client_id') or '').strip()[:80]


def _offline_already_applied(request):
    global OFFLINE_TRACKING_DISABLED
    if OFFLINE_TRACKING_DISABLED:
        return False
    client_id = _offline_client_id(request)
    if not client_id:
        return False
    try:
        return OfflineSubmission.objects.filter(client_id=client_id, status='applied').exists()
    except (OperationalError, ProgrammingError):
        OFFLINE_TRACKING_DISABLED = True
        return False


def _mark_offline_applied(request):
    global OFFLINE_TRACKING_DISABLED
    if OFFLINE_TRACKING_DISABLED:
        return
    client_id = _offline_client_id(request)
    if not client_id:
        return
    try:
        OfflineSubmission.objects.update_or_create(
            client_id=client_id,
            defaults={
                'endpoint': request.path[:200],
                'user': request.user.pk if request.user.is_authenticated else None,
                'status': 'applied',
                'error_message': '',
                'applied_at': timezone.now(),
            },
        )
    except (OperationalError, ProgrammingError):
        OFFLINE_TRACKING_DISABLED = True


def _sync_success(request, redirect_name, message='Registro sincronizado com sucesso.'):
    if _wants_sync_json(request):
        return JsonResponse({
            'ok': True,
            'message': message,
            'redirect_url': reverse(redirect_name),
        })
    return redirect(redirect_name)


def _sync_error(request, message, status=422):
    if _wants_sync_json(request):
        return JsonResponse({'ok': False, 'message': message}, status=status)
    return None


def _normalizar_nome(nome):
    return ' '.join((nome or '').split())


def _buscar_paciente_por_identificacao(data):
    cpf = (data.get('cpf') or '').strip()
    if cpf:
        paciente = Paciente.objects.filter(cpf=cpf).first()
        if paciente:
            return paciente

    nome = _normalizar_nome(data.get('nome'))
    data_nascimento = parse_date((data.get('data_nascimento') or '').strip())
    if not nome or not data_nascimento:
        return None
    return Paciente.objects.filter(
        nome__iexact=nome,
        data_nascimento=data_nascimento,
    ).order_by('id').first()


def _parse_data_hora_agendamento(value):
    data_hora = parse_datetime((value or '').strip())
    if not data_hora:
        return None
    if timezone.is_naive(data_hora):
        data_hora = timezone.make_aware(data_hora, timezone.get_current_timezone())
    return data_hora

@login_required
def home(request):
    # 1. Buscamos todas as aplicações (vacinas aplicadas)
    todas_aplicacoes = Vacina.objects.all().select_related('paciente', 'item_estoque__posto').order_by('-data_aplicacao')
    
    # 2. Contamos o total de pacientes
    total_pacientes = Paciente.objects.count()
    
    # 3. Contamos o total de doses aplicadas (o número que vai no card)
    total_doses = todas_aplicacoes.count()
    
    return render(request, 'vacinas/index.html', {
        'ultimas_aplicacoes': todas_aplicacoes, # Nome alterado para bater com o HTML do Dashboard
        'total_pacientes': total_pacientes,
        'total_doses': total_doses,             # Adicionado para o card de doses
    })

@login_required
@atomic_view
def cadastrar_paciente(request):
    if request.method == 'POST': 
        if _offline_already_applied(request):
            return _sync_success(request, 'home', 'Paciente ja sincronizado anteriormente.')
        form = PacienteForm(request.POST)
        if form.is_valid():     
            form.save()
            _mark_offline_applied(request)
            return _sync_success(request, 'home')
        error_response = _sync_error(request, _form_error_message(form))
        if error_response:
            return error_response
    else:
        initial = {}
        responsavel_id = request.GET.get('responsavel')
        if responsavel_id and Paciente.objects.filter(pk=responsavel_id).exists():
            initial['responsavel'] = responsavel_id
        form = PacienteForm(initial=initial)
    return render(request, 'vacinas/cadastro_paciente.html', {'form': form})

@login_required
@atomic_view
def registrar_dose(request):
    if request.method == 'POST':
        if _offline_already_applied(request):
            return _sync_success(request, 'home', 'Dose ja sincronizada anteriormente.')
        form = VacinaForm(request.POST)
        if form.is_valid():
            form.save()
            _mark_offline_applied(request)
            return _sync_success(request, 'home')
        else:
            error_response = _sync_error(request, _form_error_message(form))
            if error_response:
                return error_response
    else:
        form = VacinaForm()
    return render(request, 'vacinas/registrar_dose.html', {'form': form})

@login_required
def listar_pacientes(request):
    pacientes = Paciente.objects.select_related('responsavel').prefetch_related('dependentes').order_by('nome')
    return render(request, 'vacinas/listar_pacientes.html', {'pacientes': pacientes})

@login_required
@atomic_view
def editar_paciente(request, pk):
    paciente = get_object_or_404(Paciente, pk=pk)
    form = PacienteForm(request.POST or None, instance=paciente) 
    if request.method == 'POST' and _offline_already_applied(request):
        return _sync_success(request, 'listar_pacientes', 'Paciente ja sincronizado anteriormente.')
    if form.is_valid():
        form.save()
        _mark_offline_applied(request)
        return _sync_success(request, 'listar_pacientes')
    if request.method == 'POST':
        error_response = _sync_error(request, _form_error_message(form))
        if error_response:
            return error_response
    return render(request, 'vacinas/cadastro_paciente.html', {'form': form, 'editando': True})

def portal_boas_vindas(request):
    return render(request, 'vacinas/portal_boas_vindas.html')

def caderneta_paciente(request):
    paciente = None
    doses = [] # Criamos a lista de doses vazia por padrão
    busca_ativa = bool(request.GET.get('nome') or request.GET.get('data_nascimento') or request.GET.get('cpf'))
    
    if busca_ativa:
        paciente = _buscar_paciente_por_identificacao(request.GET)
        if paciente:
    
            doses = Vacina.objects.filter(paciente=paciente).select_related('item_estoque__posto').order_by('-data_aplicacao')
    
    return render(request, 'vacinas/caderneta.html', {
        'paciente': paciente,
        'doses': doses, 
        'busca_ativa': busca_ativa
    })

def index_escolha(request):
    return render(request, 'vacinas/index_escolha.html')


PORTAL_CHAT_SYSTEM_PROMPT = """Você é o assistente educativo do Portal do Cidadão do sistema EasyVacc (imunização).
Responda SEMPRE em português do Brasil, com linguagem clara e acessível ao público geral.

Escopo: apenas temas relacionados a vacinas e imunização (importância, prevenção de doenças, imunidade individual e coletiva,
calendário vacinal como referência geral do SUS, segurança das vacinas, mitos frequentes, efeitos comuns, campanhas).

Regras:
- Explique a importância das vacinas com base em evidências científicas e consensos de saúde pública.
- Reforce que vacinas protegem a pessoa vacinada e contribuem para proteger quem não pode vacinar (imunização coletiva).
- Nunca prescreva medicamentos, doses, intervalos personalizados nem diga se a pessoa deve ou não tomar uma vacina específica: oriente buscar unidade de saúde ou profissional para decisão individual.
- Não interprete exames, diagnósticos ou sintomas; se perguntarem sobre caso clínico, recomende procurar serviço de saúde.
- Se a pergunta fugir do tema vacinas/imunização, recuse educadamente e convide a perguntar sobre vacinas.
- Respostas objetivas (parágrafos curtos). Use tópicos quando ajudar a leitura no celular."""


def _portal_chat_llm_config():
    """Groq (tier gratuito) tem prioridade; senão OpenAI. Retorna None se nenhuma chave."""
    groq = (os.environ.get('GROQ_API_KEY') or '').strip()
    if groq:
        return {
            'api_key': groq,
            'url': 'https://api.groq.com/openai/v1/chat/completions',
            'model': (os.environ.get('GROQ_MODEL') or 'llama-3.1-8b-instant').strip() or 'llama-3.1-8b-instant',
        }
    oa = (os.environ.get('OPENAI_API_KEY') or '').strip()
    if oa:
        return {
            'api_key': oa,
            'url': 'https://api.openai.com/v1/chat/completions',
            'model': (os.environ.get('OPENAI_MODEL') or 'gpt-4o-mini').strip() or 'gpt-4o-mini',
        }
    return None


def _portal_chat_offline_reply(text):
    """Respostas educativas fixas (sem API, sem custo). Não substitui orientação individual de saúde."""
    t = text.lower().strip()
    if any(w in t for w in ('influenza', 'gripe')):
        return (
            'A vacina da influenza (gripe) reduz o risco de infecção grave, internação e óbito, principalmente em '
            'idosos, crianças, gestantes e pessoas com doenças crônicas. A gripe muda de cepa a cada temporada; por '
            'isso a dose é atualizada anualmente. Para saber se você está no calendário indicado neste ano e onde '
            'vacinar, procure uma unidade de saúde.'
        )
    if any(w in t for w in ('covid', 'coronavírus', 'coronavirus', 'pfizer', 'astrazeneca', 'coronavac')):
        return (
            'As vacinas contra COVID-19 foram desenvolvidas e monitoradas com os mesmos critérios de segurança e '
            'eficácia das demais vacinas. Elas ajudam a evitar formas graves da doença e aliviam a pressão sobre o '
            'SUS. Dúvidas sobre dose de reforço ou contraindicações pessoais devem ser resolvidas com um profissional '
            'de saúde ou no posto de vacinação.'
        )
    if 'sarampo' in t:
        return (
            'O sarampo é altamente contagioso e pode causar complicações graves. A vacina tríplice viral (ou '
            'vacinas que a incluem) é a principal forma de proteção individual e de bloquear surtos (imunidade '
            'coletiva). Mantenha a caderneta em dia e siga orientações do calendário do SUS na sua faixa etária.'
        )
    if 'hpv' in t or 'papiloma' in t:
        return (
            'A vacina contra o HPV protege dos tipos de vírus mais ligados a câncer de colo do útero e outras '
            'neoplasias, além de verrugas. No SUS, há faixas etárias e públicos-alvo definidos pelo Ministério da '
            'Saúde. A decisão de vacinar e o esquema exato devem ser confirmados na unidade de saúde.'
        )
    if any(w in t for w in ('gestante', 'gravida', 'grávida', 'amamentação', 'amamentacao')):
        return (
            'Gestantes e puérperas costumam ter indicações específicas no calendário (ex.: dTpa, influenza, '
            'hepatite B, conforme orientação local). Vacinar na gestação também protege o bebê nos primeiros meses. '
            'Sempre leve a carteira de pré-natal e pergunte na equipe de saúde qual é o esquema indicado para você.'
        )
    if any(w in t for w in ('bebê', 'bebe', 'criança', 'crianca', 'infantil', 'calendário', 'calendario')):
        return (
            'O calendário infantil organiza doses ao longo dos primeiros anos de vida para proteger contra doenças '
            'como poliomielite, sarampo, meningite, entre outras. Atrasar doses aumenta a janela de risco; ao '
            'retomar o esquema, o posto orienta como “recuperar” as vacinas. Leve a caderneta da criança em todo '
            'atendimento.'
        )
    if any(w in t for w in ('efeito', 'reação', 'dor no braço', 'febre', 'mito', 'autismo')):
        return (
            'Reações leves (dor no local, cansaço, febre baixa) podem ocorrer e costumam melhorar em poucos dias. '
            'Centenas de estudos não associam vacinas ao autismo; essa ideia é um mito desmentido pela ciência. '
            'Sinais muito intensos ou persistentes merecem avaliação presencial no serviço de saúde.'
        )
    if any(w in t for w in ('feb', 'amarela', 'febre amarela')):
        return (
            'A vacina contra febre amarela é altamente eficaz e recomendada em áreas de risco ou viagem para '
            'regiões endêmicas. Existem contraindicações importantes (ex.: imunodeprimidos, alergia grave a ovo em '
            'alguns contextos). Confirme no posto se você está apto e se há necessidade de comprovante para viagem.'
        )
    if any(w in t for w in ('importância', 'importancia', 'por que vacinar', 'porque vacinar', 'vale a pena')):
        return (
            'Vacinas treinam o sistema imunológico a reconhecer germes sem você precisar passar pela doença completa. '
            'Assim você evita sequelas, internações e mortes evitáveis. Além disso, quando muitas pessoas vacinam, '
            'protegemos quem não pode receber a vacina (imunidade coletiva). É um dos investimentos de saúde pública '
            'com maior retorno para a sociedade.'
        )
    if any(w in t for w in ('olá', 'ola', 'oi ', ' oi', 'bom dia', 'boa tarde', 'boa noite')):
        return (
            'Olá! Sou o assistente educativo do EasyVacc sobre vacinas. Pergunte, por exemplo, sobre influenza, '
            'calendário infantil, importância da imunização ou mitos comuns. Esta resposta é automática e gratuita; '
            'para orientação individual (doses, datas, vacinas indicadas para você), procure sempre um profissional '
            'de saúde.'
        )
    return (
        'Sou um assistente educativo sobre vacinas neste portal. Posso falar de prevenção, importância da imunização '
        'coletiva, segurança das vacinas e mitos frequentes em linguagem simples. Não substituo consulta médica nem '
        'digo quais doses você deve tomar sem avaliar seu caso.\n\n'
        'Dica gratuita: para respostas mais longas e personalizadas, crie uma conta em console.groq.com, gere uma '
        'chave e coloque no servidor como GROQ_API_KEY (uso generoso em tier gratuito).'
    )


@require_POST
def portal_chat(request):
    """Chat educativo: Groq (grátis), OpenAI (pago) ou modo offline sem chave."""
    try:
        body = json.loads(request.body.decode('utf-8'))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return JsonResponse({'error': True, 'message': 'Requisição inválida.'}, status=400)

    raw_messages = body.get('messages')
    if not isinstance(raw_messages, list) or not raw_messages:
        return JsonResponse({'error': True, 'message': 'Envie sua pergunta.'}, status=400)

    cleaned = []
    for m in raw_messages[-16:]:
        if not isinstance(m, dict):
            continue
        role = m.get('role')
        content = m.get('content')
        if role not in ('user', 'assistant') or not isinstance(content, str):
            continue
        content = content.strip()[:4000]
        if not content:
            continue
        cleaned.append({'role': role, 'content': content})

    if not cleaned or cleaned[-1]['role'] != 'user':
        return JsonResponse({'error': True, 'message': 'Pergunta inválida.'}, status=400)

    last_question = cleaned[-1]['content']
    cfg = _portal_chat_llm_config()
    if not cfg:
        reply = _portal_chat_offline_reply(last_question)
        return JsonResponse({'reply': reply, 'offline': True})

    api_messages = [{'role': 'system', 'content': PORTAL_CHAT_SYSTEM_PROMPT}] + cleaned

    try:
        r = requests.post(
            cfg['url'],
            headers={
                'Authorization': f'Bearer {cfg["api_key"]}',
                'Content-Type': 'application/json',
            },
            json={
                'model': cfg['model'],
                'messages': api_messages,
                'max_tokens': 900,
                'temperature': 0.55,
            },
            timeout=60,
        )
    except requests.RequestException:
        return JsonResponse(
            {'error': True, 'message': 'Não foi possível contatar o serviço de IA. Tente novamente.'},
            status=502,
        )

    if r.status_code != 200:
        msg = 'Serviço de IA indisponível no momento. Tente mais tarde.'
        if settings.DEBUG:
            try:
                err = r.json()
                if isinstance(err, dict) and 'error' in err:
                    inner = err['error']
                    detail = inner.get('message') if isinstance(inner, dict) else str(inner)
                else:
                    detail = str(err)[:400]
                if detail:
                    msg = f'{msg} Detalhe: {detail}'
            except (ValueError, TypeError):
                msg = f'{msg} (HTTP {r.status_code})'
        return JsonResponse({'error': True, 'message': msg}, status=502)

    try:
        data = r.json()
        reply = data['choices'][0]['message']['content'].strip()
    except (KeyError, IndexError, TypeError):
        return JsonResponse(
            {'error': True, 'message': 'Resposta inesperada do serviço. Tente novamente.'},
            status=502,
        )

    return JsonResponse({'reply': reply, 'offline': False})

@login_required
def listar_estoque(request):
    # Exibe os itens de estoque e o posto ao qual pertencem
    itens = Estoque.objects.all().select_related('posto')
    return render(request, 'vacinas/estoque.html', {'itens': itens})

@login_required
@atomic_view
def cadastrar_estoque(request):
    if request.method == 'POST':
        if _offline_already_applied(request):
            return _sync_success(request, 'listar_estoque', 'Lote ja sincronizado anteriormente.')
        form = EstoqueForm(request.POST)
        if form.is_valid():
            form.save()
            _mark_offline_applied(request)
            return _sync_success(request, 'listar_estoque')
        error_response = _sync_error(request, _form_error_message(form))
        if error_response:
            return error_response
    else:
        form = EstoqueForm()
    return render(request, 'vacinas/cadastrar_estoque.html', {'form': form})

def busca_postos(request):
    """
    Função unificada para busca de postos por CEP ou Bairro (ViaCEP)
    E agora carregando as vacinas disponíveis em cada um.
    """
    cep_digitado = request.GET.get('cep', '').strip()
    postos_encontrados = PostoSaude.objects.none()
    bairro_detectado = None 

    if cep_digitado:
        cep_limpo = cep_digitado.replace("-", "").replace(" ", "").replace(".", "")
        
        postos_encontrados = PostoSaude.objects.filter(
            Q(cep=cep_digitado) | Q(cep=cep_limpo)
        )

        if not postos_encontrados.exists():
            url = f"https://viacep.com.br/ws/{cep_limpo}/json/"
            try:
                response = requests.get(url, timeout=5)
                if response.status_code == 200:
                    dados = response.json()
                    if 'erro' not in dados:
                        bairro_detectado = dados.get('bairro')
                        postos_encontrados = PostoSaude.objects.filter(bairro__icontains=bairro_detectado)
            except:
                pass 

    for posto in postos_encontrados:
        
        from .models import Estoque # Importe dentro ou no topo do arquivo
        posto.estoque_vacinas = Estoque.objects.filter(posto=posto, quantidade_atual__gt=0)

    return render(request, 'vacinas/postos.html', {
        'postos': postos_encontrados,
        'bairro_localizado': bairro_detectado,
        'cep_pesquisado': cep_digitado
    })

@login_required
def exportar_dados_csv(request):
    response = HttpResponse(content_type='text/csv; charset=utf-8')
    response['Content-Disposition'] = 'attachment; filename="relatorio_vacinas_saquarema.csv"'

    response.write(u'\ufeff'.encode('utf8')) 
    writer = csv.writer(response, delimiter=';')

    writer.writerow(['Paciente', 'CPF', 'Vacina', 'Lote', 'Data de Aplicação', 'Posto de Saúde'])

    # Otimização de consulta com select_related
    vacinacoes = Vacina.objects.all().select_related('paciente', 'item_estoque__posto')

    for v in vacinacoes:
        writer.writerow([
            v.paciente.nome,
            v.paciente.cpf,
            v.nome_vacina,
            v.lote,
            v.data_aplicacao.strftime('%d/%m/%Y %H:%M'),
            v.posto.nome if v.posto else "N/A"
        ])

    return response

def carregar_vacinas_posto(request):
    posto_id = request.GET.get('posto_id')
    vacinas = Estoque.objects.filter(posto_id=posto_id, quantidade_atual__gt=0).values('id', 'nome_vacina', 'lote')
    return JsonResponse(list(vacinas), safe=False)


@require_GET
def offline_bootstrap(request):
    estoques = []
    for item in Estoque.objects.filter(quantidade_atual__gt=0).select_related('posto').order_by('nome_vacina'):
        disponivel = max(item.quantidade_atual - item.quantidade_reservada, 0)
        if disponivel <= 0:
            continue
        estoques.append({
            'id': item.id,
            'nome_vacina': item.nome_vacina,
            'lote': item.lote,
            'posto_id': item.posto_id,
            'posto_nome': item.posto.nome if item.posto else '',
            'disponivel': disponivel,
        })

    payload = {
        'postos': list(PostoSaude.objects.order_by('nome').values('id', 'nome', 'bairro', 'cep')),
        'estoques': estoques,
        'pacientes': [],
    }

    if request.user.is_authenticated:
        payload['pacientes'] = list(
            Paciente.objects.order_by('nome').values(
                'id',
                'nome',
                'data_nascimento',
                'endereco',
                'cpf',
                'cartao_sus',
                'responsavel_id',
            )
        )

    return JsonResponse(payload)


@atomic_view
def agendar_vacina(request):
    # Definimos a variável no topo para ela sempre existir
    postos = PostoSaude.objects.all()
    
    if request.method == 'POST':
        if _offline_already_applied(request):
            return _sync_success(request, 'agendar_vacina', 'Agendamento ja sincronizado anteriormente.')
        item_id = request.POST.get('item_estoque')
        data_hora = _parse_data_hora_agendamento(request.POST.get('data_hora'))
        
        paciente = _buscar_paciente_por_identificacao(request.POST)
        
        if not paciente:
            error_response = _sync_error(request, 'Pessoa nao encontrada. Confira o nome completo e a data de nascimento.')
            if error_response:
                return error_response
            messages.error(request, 'Pessoa não encontrada. Confira o nome completo e a data de nascimento.')
            return render(request, 'vacinas/agendar_vacina.html', {'postos': postos})

        if not data_hora:
            error_response = _sync_error(request, 'Informe uma data e horario validos para o agendamento.')
            if error_response:
                return error_response
            messages.error(request, 'Informe uma data e horário válidos para o agendamento.')
            return render(request, 'vacinas/agendar_vacina.html', {'postos': postos})

        try:
            with transaction.atomic():
                estoque = Estoque.objects.select_for_update().get(id=item_id)
                
                if (estoque.quantidade_atual - estoque.quantidade_reservada) > 0:
                    Agendamento.objects.create(
                        paciente=paciente,
                        item_estoque=estoque,
                        data_hora=data_hora,
                        status='pendente'
                    )
                    estoque.quantidade_reservada += 1
                    estoque.save()
                    _mark_offline_applied(request)
                    
                    messages.success(request, "Agendamento realizado com sucesso!")
                    # Redirecionamos para a tela de escolha para garantir o funcionamento
                    return _sync_success(request, 'agendar_vacina', 'Agendamento sincronizado com sucesso.')
                else:
                    error_response = _sync_error(request, 'Nao ha doses disponiveis.', status=409)
                    if error_response:
                        return error_response
                    messages.error(request, "Não há doses disponíveis.")
        except Exception as e:
            error_response = _sync_error(request, f"Erro no sistema: {e}", status=500)
            if error_response:
                return error_response
            messages.error(request, f"Erro no sistema: {e}")

    # Este retorno garante que o GET (carregamento inicial) funcione sem erro de 'postos'
    return render(request, 'vacinas/agendar_vacina.html', {'postos': postos})
            
@login_required
def listar_agendamentos(request):
    agendamentos = Agendamento.objects.filter(status='pendente').select_related('paciente', 'item_estoque', 'item_estoque__posto').order_by('data_hora')
    
    return render(request, 'vacinas/listar_agendamentos.html', {'agendamentos': agendamentos})

def meus_agendamentos(request):
    agendamentos = None
    busca_ativa = bool(request.GET.get('nome') or request.GET.get('data_nascimento') or request.GET.get('cpf'))

    if busca_ativa:
        paciente = _buscar_paciente_por_identificacao(request.GET)
        if paciente:
            agendamentos = Agendamento.objects.filter(
                paciente=paciente
            ).select_related('item_estoque', 'item_estoque__posto').order_by('-data_hora')
        else:
            agendamentos = []

    return render(request, 'vacinas/meus_agendamentos.html', {
        'agendamentos': agendamentos,
        'busca_ativa': busca_ativa
    })

@login_required
@atomic_view
@require_POST
def confirmar_agendamento(request, agendamento_id):
    agendamento = get_object_or_404(Agendamento, id=agendamento_id)

    if _offline_already_applied(request):
        return _sync_success(request, 'listar_agendamentos', 'Confirmacao ja sincronizada anteriormente.')

    if agendamento.status != 'pendente':
        error_response = _sync_error(request, 'Este agendamento ja foi processado.', status=409)
        if error_response:
            return error_response
        messages.warning(request, "Este agendamento já foi processado.")
        return redirect('listar_agendamentos')

    with transaction.atomic():
        estoque = Estoque.objects.select_for_update().get(pk=agendamento.item_estoque_id)
        if estoque.quantidade_atual > 0 and estoque.quantidade_reservada > 0:
            estoque.quantidade_reservada -= 1
            estoque.save(update_fields=['quantidade_reservada'])

            Vacina.objects.create(
                paciente=agendamento.paciente,
                item_estoque=estoque,
            )

            agendamento.status = 'concluido'
            agendamento.save(update_fields=['status'])
            _mark_offline_applied(request)
            messages.success(request, f"Dose confirmada para {agendamento.paciente.nome}!")
        else:
            error_response = _sync_error(request, 'Estoque insuficiente ou reserva inconsistente.', status=409)
            if error_response:
                return error_response
            messages.error(request, "Estoque insuficiente ou reserva inconsistente.")

    return _sync_success(request, 'listar_agendamentos', 'Confirmacao sincronizada com sucesso.')

@login_required
def historico_agendamentos(request):
    # Aqui pegamos todos que NÃO estão pendentes para ser o seu arquivo morto
    agendamentos = Agendamento.objects.exclude(status='pendente').order_by('-data_hora')
    return render(request, 'vacinas/historico_agendamentos.html', {'agendamentos': agendamentos})

@login_required
@atomic_view
@require_POST
def cancelar_agendamento(request, agendamento_id):
    agendamento = get_object_or_404(Agendamento, id=agendamento_id)
    
    if request.method == 'POST':
        if _offline_already_applied(request):
            return _sync_success(request, 'listar_agendamentos', 'Cancelamento ja sincronizado anteriormente.')
        justificativa = request.POST.get('justificativa')
        
        with transaction.atomic():
            estoque = agendamento.item_estoque
            
            if agendamento.status != 'pendente':
                return _sync_error(request, 'Somente agendamentos pendentes podem ser cancelados.', status=409) or redirect('listar_agendamentos')
            # Uma dose aplicada nunca volta para o estoque físico.
            estoque.quantidade_reservada = max(0, estoque.quantidade_reservada - 1)
            
            estoque.save()
            
            agendamento.status = 'cancelado'
            agendamento.justificativa_cancelamento = justificativa
            agendamento.save()
            _mark_offline_applied(request)
            
            messages.warning(request, "Agendamento cancelado e estoque atualizado.")
            return _sync_success(request, 'listar_agendamentos', 'Cancelamento sincronizado com sucesso.')

@require_GET
def manifest(request):
    """Web App Manifest com URLs de ícone resolvidas para produção (Whitenoise + hash)."""
    # Ícones 192px e 512px reais: o Chrome desktop rejeita o mesmo PNG com sizes incorretos
    # (easyvacc.png era 161×159), o Windows cai na letra monogramada.
    icon192 = request.build_absolute_uri(
        staticfiles_storage.url('vacinas/icon-192.png')
    )
    icon512 = request.build_absolute_uri(
        staticfiles_storage.url('vacinas/icon-512.png')
    )
    payload = {
        'name': 'EasyVacc',
        'short_name': 'EasyVacc',
        'description': 'Sistema de gestão de imunização',
        'start_url': '/',
        'scope': '/',
        'display': 'standalone',
        'background_color': '#f8fafc',
        'theme_color': '#2563eb',
        'icons': [
            {
                'src': icon192,
                'sizes': '192x192',
                'type': 'image/png',
                'purpose': 'any',
            },
            {
                'src': icon512,
                'sizes': '512x512',
                'type': 'image/png',
                'purpose': 'any',
            },
        ],
        'lang': 'pt-BR',
        'dir': 'ltr',
    }
    return HttpResponse(
        json.dumps(payload, ensure_ascii=False),
        content_type='application/manifest+json; charset=utf-8',
    )


@require_GET
def service_worker(request):
    """Publicado na raiz para o escopo cobrir todo o site (instalação PWA)."""
    sw_path = settings.BASE_DIR / 'vacinas' / 'static' / 'vacinas' / 'sw.js'
    body = sw_path.read_text(encoding='utf-8')
    response = HttpResponse(body, content_type='application/javascript; charset=utf-8')
    response['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    return response
