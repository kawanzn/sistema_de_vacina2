from django.db import models
from django.core.exceptions import ValidationError
from django.core.validators import MinValueValidator
from django.utils import timezone
from .firestore_store import DocumentModel, run_atomic
from django.contrib.auth.base_user import AbstractBaseUser


class Servidor(DocumentModel, AbstractBaseUser):
    username = models.CharField(max_length=150, unique=True)
    email = models.EmailField(blank=True)
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=True)
    is_superuser = models.BooleanField(default=False)
    USERNAME_FIELD = 'username'
    REQUIRED_FIELDS = []

    def has_perm(self, perm, obj=None):
        return self.is_active and self.is_superuser

    def has_module_perms(self, app_label):
        return self.is_active and self.is_superuser

    def get_full_name(self):
        return self.username

    def get_short_name(self):
        return self.username

class Paciente(DocumentModel):
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
    
class PostoSaude(DocumentModel):
    nome = models.CharField(max_length=100)
    endereco = models.CharField(max_length=255)
    bairro = models.CharField(max_length=100)
    cep = models.CharField(max_length=9)

    def __str__(self):
        return self.nome
    
class Estoque(DocumentModel):
    nome_vacina = models.CharField(max_length=100)
    lote = models.CharField(max_length=50)
    quantidade_atual = models.IntegerField(default=0, validators=[MinValueValidator(0)])
    data_validade = models.DateField()
    fornecedor = models.CharField(max_length=100, default="Secretaria de Saúde")
    posto = models.ForeignKey(PostoSaude, on_delete=models.CASCADE, related_name='vacinas_estoque', null=True, blank=True)
    quantidade_reservada = models.PositiveIntegerField(default=0)

    @property
    def dias_para_vencer(self):
        return (self.data_validade - timezone.localdate()).days

    @property
    def vencido(self):
        # A data informada vale até o fim do dia, no fuso do sistema.
        return self.dias_para_vencer < 0

    @property
    def validade_status(self):
        if self.vencido:
            return 'vencido'
        if self.dias_para_vencer <= 30:
            return 'proximo'
        return 'regular'

    @property
    def disponivel_para_uso(self):
        return not self.vencido and self.quantidade_atual > self.quantidade_reservada

    def __str__(self):
        return f"{self.nome_vacina} - Lote: {self.lote} ({self.posto.nome if self.posto else 'Sem Posto'})"

class Vacina(DocumentModel):
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
        if self.item_estoque and self.item_estoque.vencido:
            raise ValidationError('Este lote está vencido e não pode ser utilizado.')
        if self.item_estoque and self.item_estoque.quantidade_atual <= self.item_estoque.quantidade_reservada:
            raise ValidationError('Não há dose disponível fora das reservas. Confirme o agendamento para usar uma dose reservada.')

    def save(self, *args, **kwargs):
        def apply():
            creating = not self.pk
            estoque = Estoque.objects.get(pk=self.item_estoque_id) if self.item_estoque_id else None
            if estoque:
                if creating and estoque.quantidade_atual <= estoque.quantidade_reservada:
                    raise ValidationError("Não há dose disponível fora das reservas.")
                self.item_estoque = estoque
                self.nome_vacina = estoque.nome_vacina
                self.lote = estoque.lote
                self.posto = estoque.posto
            self.full_clean()
            super(Vacina, self).save(*args, **kwargs)
            if creating and estoque:
                estoque.quantidade_atual -= 1
                estoque.save()
        return run_atomic(apply)

    def __str__(self):
        return f"{self.nome_vacina} - {self.paciente.nome}"

class Agendamento(DocumentModel):
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


class OfflineSubmission(DocumentModel):
    STATUS_CHOICES = [
        ('applied', 'Aplicado'),
        ('failed', 'Falhou'),
    ]

    client_id = models.CharField(max_length=80, unique=True)
    endpoint = models.CharField(max_length=200)
    user = models.BigIntegerField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='applied')
    error_message = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    applied_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.client_id} - {self.status}"

# A lista de dependentes também é consultada no Firestore.
Paciente.dependentes = property(lambda self: Paciente.objects.filter(responsavel_id=self.pk))
