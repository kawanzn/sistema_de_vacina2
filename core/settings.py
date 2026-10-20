import os
from pathlib import Path
from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent

# Carrega .env sempre da raiz do projeto (não depende da pasta de onde você rodou o runserver)
load_dotenv(BASE_DIR / '.env')

# 2. Configurações Básicas
SECRET_KEY = os.environ.get('SECRET_KEY', 'local-development-only-do-not-use-in-production')
if os.environ.get('VERCEL') and not os.environ.get('SECRET_KEY'):
    raise RuntimeError('Configure SECRET_KEY na Vercel.')

# MELHORIA: DEBUG False em produção evita que mostre seu código em erros
DEBUG = os.environ.get('DEBUG', 'False').lower() in {'1', 'true', 'yes'}

# MELHORIA: Liste seu domínio da Vercel aqui para segurança
ALLOWED_HOSTS = os.environ.get('ALLOWED_HOSTS', 'localhost,127.0.0.1,.vercel.app').split(',')

# MELHORIA: Resolve o erro 403 do seu amigo
CSRF_TRUSTED_ORIGINS = [
    'https://sistema-vacina.vercel.app',
    'https://*.vercel.app'
]

# HTTPS atrás do proxy da Vercel (manifest PWA e redirects corretos)
if os.environ.get('VERCEL'):
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')

LOGIN_REDIRECT_URL = 'home'
LOGOUT_REDIRECT_URL = 'index_escolha'

# 3. Apps e Middlewares
INSTALLED_APPS = [
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'vacinas',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'whitenoise.middleware.WhiteNoiseMiddleware', # Mantém o WhiteNoise aqui
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'core.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [os.path.join(BASE_DIR, 'templates')], # Caso você crie pastas de templates globais
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'core.wsgi.application'

# O Firestore é o banco persistente. O backend dummy impede uso acidental de SQL.
DATABASES = {'default': {'ENGINE': 'django.db.backends.dummy'}}
AUTH_USER_MODEL = 'vacinas.Servidor'
AUTHENTICATION_BACKENDS = ['vacinas.auth_backend.FirestoreBackend']
SESSION_ENGINE = 'django.contrib.sessions.backends.signed_cookies'
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_AGE = 3600
SESSION_COOKIE_SECURE = bool(os.environ.get('VERCEL'))
CSRF_COOKIE_SECURE = bool(os.environ.get('VERCEL'))
SECURE_CONTENT_TYPE_NOSNIFF = True

# 5. Configurações de Segurança e Idioma
AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator'},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'},
    {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'},
]

LANGUAGE_CODE = 'pt-br'
TIME_ZONE = 'America/Sao_Paulo'
USE_I18N = True
USE_TZ = True

# 6. Arquivos Estáticos (Configuração Robusta para Vercel)
STATIC_URL = 'static/'
STATIC_ROOT = os.path.join(BASE_DIR, 'staticfiles')
STATICFILES_DIRS = [
    os.path.join(BASE_DIR, 'vacinas', 'static'),
]

# MELHORIA: WhiteNoise com compressão (deixa o site mais rápido no celular)
STORAGES = {
    'default': {'BACKEND': 'django.core.files.storage.FileSystemStorage'},
    'staticfiles': {'BACKEND': 'whitenoise.storage.CompressedManifestStaticFilesStorage'},
}

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
