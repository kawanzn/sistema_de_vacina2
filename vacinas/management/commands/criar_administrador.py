from getpass import getpass
from django.contrib.auth.password_validation import validate_password
from django.core.management.base import BaseCommand, CommandError
from vacinas.models import Servidor


class Command(BaseCommand):
    help = 'Cria o administrador no Firestore, sem banco SQL.'

    def add_arguments(self, parser):
        parser.add_argument('--username', required=True)
        parser.add_argument('--email', default='')

    def handle(self, *args, **options):
        if Servidor.objects.filter(username=options['username']).exists():
            raise CommandError('Este usuário já existe.')
        password = getpass('Senha: ')
        if password != getpass('Repita a senha: '):
            raise CommandError('As senhas não conferem.')
        validate_password(password)
        user = Servidor(username=options['username'], email=options['email'], is_superuser=True)
        user.set_password(password)
        user.save()
        self.stdout.write(self.style.SUCCESS('Administrador criado no Firestore.'))
