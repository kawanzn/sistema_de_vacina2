from django import forms
from .models import Paciente, Vacina, Estoque, PostoSaude

class PacienteForm(forms.ModelForm):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        responsaveis = Paciente.objects.filter(responsavel__isnull=True).order_by('nome')
        if self.instance and self.instance.pk:
            responsaveis = responsaveis.exclude(pk=self.instance.pk)
        self.fields['responsavel'].queryset = responsaveis

    def clean_nome(self):
        return (self.cleaned_data.get('nome') or '').strip()

    def clean_cpf(self):
        return (self.cleaned_data.get('cpf') or '').strip() or None

    def clean_cartao_sus(self):
        return (self.cleaned_data.get('cartao_sus') or '').strip() or None

    class Meta:
        model = Paciente
        fields = ['nome', 'data_nascimento', 'endereco', 'cpf','email', 'cartao_sus', 'telefone', 'responsavel']
        widgets = {
            'data_nascimento': forms.DateInput(attrs={'type': 'date', 'class': 'form-control'}),
            'nome': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Nome completo'}),
            'endereco': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Rua, número, bairro e complemento'}),
            'cpf': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Opcional para dependentes'}),
            'cartao_sus': forms.TextInput(attrs={'class': 'form-control', 'placeholder': '000 0000 0000 0000'}),
            'telefone': forms.TextInput(attrs={'class': 'form-control', 'placeholder': '(00) 00000-0000'}),
            'responsavel': forms.Select(attrs={'class': 'form-select'}),
            'email': forms.EmailInput(attrs={'class': 'form-control','placeholder': 'email@email.com'}),
        }
        labels = {
            'responsavel': 'Responsável (se for dependente)',
        }

class VacinaForm(forms.ModelForm):
    # Campo para selecionar o Posto primeiro
    posto_selecionado = forms.ModelChoiceField(
        queryset=PostoSaude.objects.all(),
        empty_label="Selecione a Unidade de Saúde",
        widget=forms.Select(attrs={'class': 'form-select w-100', 'id': 'id_posto'}),
        label="Posto de Saúde"
    )

    # Campo que o JavaScript vai preencher
    item_estoque = forms.ModelChoiceField(
        queryset=Estoque.objects.none(), 
        empty_label="Escolha o posto primeiro",
        widget=forms.Select(attrs={'class': 'form-select w-100', 'id': 'id_item_estoque'}),
        label="Vacina / Lote Disponível"
    )
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # Se houver um POST, precisamos atualizar o queryset para o formulário não dar erro de "Opção Inválida"
        if 'posto_selecionado' in self.data:
            try:
                posto_id = int(self.data.get('posto_selecionado'))
                self.fields['item_estoque'].queryset = Estoque.objects.filter(posto_id=posto_id, quantidade_atual__gt=0)
            except (ValueError, TypeError):
                pass

    class Meta:
        model = Vacina
        # IMPORTANTE: Coloque 'posto_selecionado' aqui dentro
        fields = ['paciente', 'posto_selecionado', 'item_estoque']
        widgets = {
            'paciente': forms.Select(attrs={'class': 'form-select w-100'}),
        }

    class Meta:
        model = Vacina
        fields = ['paciente', 'posto_selecionado', 'item_estoque']
class EstoqueForm(forms.ModelForm):
    class Meta:
        model = Estoque
        # Adicionei o campo 'posto' aqui para você poder dizer onde a vacina está entrando
        fields = ['nome_vacina', 'lote', 'quantidade_atual', 'data_validade', 'fornecedor', 'posto']
        widgets = {
            'data_validade': forms.DateInput(attrs={'type': 'date', 'class': 'form-control'}),
            'nome_vacina': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Ex: Pfizer'}),
            'lote': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Ex: FD1234'}),
            'quantidade_atual': forms.NumberInput(attrs={'class': 'form-control'}),
            'fornecedor': forms.TextInput(attrs={'class': 'form-control'}),
            'posto': forms.Select(attrs={'class': 'form-select'}),
        }

# Você pode apagar o RegistroDoseForm se estiver usando o VacinaForm para a mesma função,
# para evitar duplicidade de código.
