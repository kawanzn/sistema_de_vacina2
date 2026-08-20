from django.db import models
from django.conf import settings
from django.utils import timezone
from django.db.models.signals import post_save
from django.dispatch import receiver
from django.core.exceptions import ValidationError

class Paciente(models.Model):
    nome = models.CharField(max_length=200)
    cpf = models.CharField(max_length=14, unique=True, blank=True, null=True)
    email = models.EmailField(blank=True)
    cartao_sus = models.CharField(max_length=15, unique=True, blank=True, null=True, verbose_name="Cartão SUS")
    data_nascimento = models.DateField()
    endereco = models.CharField(max_length=255, blank=True, default="")
    telefone = models.CharField(max_length=15, blank=True, null=True) # Trocado de WhatsApp para Telefone
    responsavel = models.ForeignKey(
        'self',
        on_delete=models.SET_NULL,
        blank=True,
        null=True,
        related_name='dependentes',
        verbose_name="Responsável",
    )

    def __str__(self):
        return self.nome
    
class PostoSaude(models.Model):
    nome = models.CharField(max_length=100)
    endereco = models.CharField(max_length=255)
    bairro = models.CharField(max_length=100)
    cep = models.CharField(max_length=9)

    def __str__(self):
        return self.nome
    
class Estoque(models.Model):
    nome_vacina = models.CharField(max_length=100)
    lote = models.CharField(max_length=50)
    quantidade_atual = models.IntegerField(default=0)
    data_validade = models.DateField()
    fornecedor = models.CharField(max_length=100, default="Secretaria de Saúde")
    posto = models.ForeignKey(PostoSaude, on_delete=models.CASCADE, related_name='vacinas_estoque', null=True, blank=True)
    quantidade_reservada = models.PositiveIntegerField(default=0)
    def __str__(self):
        return f"{self.nome_vacina} - Lote: {self.lote} ({self.posto.nome if self.posto else 'Sem Posto'})"

class Vacina(models.Model):
    nome_vacina = models.CharField(max_length=100) # Nome descritivo
    lote = models.CharField(max_length=50)
    data_aplicacao = models.DateTimeField(auto_now_add=True)
    paciente = models.ForeignKey(Paciente, on_delete=models.CASCADE)
    
    # Este é o campo chave que liga a aplicação ao item físico no estoque
    item_estoque = models.ForeignKey(Estoque, on_delete=models.SET_NULL, null=True, verbose_name="Vacina do Estoque")
    
    # Posto onde a vacina foi aplicada
    posto = models.ForeignKey(PostoSaude, on_delete=models.SET_NULL, null=True, blank=True)

    def clean(self):
        # Validação para garantir que não aplique vacina sem estoque
        if self.item_estoque and self.item_estoque.quantidade_atual <= 0:
            raise ValidationError(f"O estoque da vacina {self.item_estoque.nome_vacina} está esgotado neste posto.")

    def save(self, *args, **kwargs):
        # Se selecionou um item do estoque, puxa o nome, lote e posto automaticamente para a aplicação
        if self.item_estoque:
            self.nome_vacina = self.item_estoque.nome_vacina
            self.lote = self.item_estoque.lote
            self.posto = self.item_estoque.posto
        
        self.full_clean() # Executa a validação do clean() antes de salvar
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.nome_vacina} - {self.paciente.nome}"

# --- SINAL PARA BAIXAR ESTOQUE AUTOMATICAMENTE ---
@receiver(post_save, sender=Vacina)
def baixar_estoque(sender, instance, created, **kwargs):
    """
    Sempre que uma nova Vacina (aplicação) for criada, 
    subtrai 1 unidade do item correspondente no Estoque.
    """
    if created and instance.item_estoque:
        estoque = instance.item_estoque
        if estoque.quantidade_atual > 0:
            estoque.quantidade_atual -= 1
            estoque.save()

class Agendamento(models.Model):
    STATUS_CHOICES = [
        ('pendente', 'Pendente'),
        ('concluido', 'Concluído'),
        ('cancelado', 'Cancelado'),
        ('ausente', 'Não Compareceu'),
    ]

    paciente = models.ForeignKey(Paciente, on_delete=models.CASCADE)
    item_estoque = models.ForeignKey(Estoque, on_delete=models.CASCADE, related_name='agendamentos')
    data_hora = models.DateTimeField()
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pendente')
    criado_em = models.DateTimeField(auto_now_add=True)
    justificativa_cancelamento = models.TextField(blank=True, null=True)

    def __str__(self):
        return f"{self.paciente.nome} - {self.item_estoque.nome_vacina} ({self.data_hora})"

# Adicione este campo no seu modelo Estoque já existente:
# quantidade_reservada = models.PositiveIntegerField(default=0)


class OfflineSubmission(models.Model):
    STATUS_CHOICES = [
        ('applied', 'Aplicado'),
        ('failed', 'Falhou'),
    ]

    client_id = models.CharField(max_length=80, unique=True)
    endpoint = models.CharField(max_length=200)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
    )
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='applied')
    error_message = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    applied_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.client_id} - {self.status}"
