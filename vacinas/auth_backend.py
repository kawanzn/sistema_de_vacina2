from django.contrib.auth.hashers import check_password, make_password
from .models import Servidor


class FirestoreBackend:
    def authenticate(self, request, username=None, password=None, **kwargs):
        user = Servidor.objects.filter(username=username).first()
        if user is None:
            # Mantém o custo do hash mesmo quando o usuário não existe.
            check_password(password or '', make_password('invalid'))
            return None
        if user.is_active and user.is_staff and user.check_password(password):
            return user
        return None

    def get_user(self, user_id):
        user = Servidor.objects.filter(pk=user_id).first()
        return user if user and user.is_active and user.is_staff else None
