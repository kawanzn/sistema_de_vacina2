Sistema-vacina 2

Ataides Alves Cota Junior - 202312481
Kaio Abreu Oliveira - 202312160
Kawan Lopes Mangia - 202312633
João Victor Sampaio Gentil - 202312639
Pedro Marcos Quintanilha de Oliveira Nunes - 202312595

1) Criar o projeto no Supabase
- Acesse: https://supabase.com/dashboard
- Clique em "New project"
- Copie as credenciais do banco em: Project Settings > Database > Connection info

2) Criar arquivo .env na raiz do projeto (mesma pasta do manage.py)
DEBUG=True
ALLOWED_HOSTS=127.0.0.1,localhost
DB_NAME=postgres
DB_USER=postgres.<seu_project_ref>
DB_PASSWORD=<sua_senha_do_banco_supabase>
DB_HOST=db.<seu_project_ref>.supabase.co
DB_PORT=5432
DB_SSLMODE=require

3) Instalação local
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt

4) Migrar e subir o sistema
python manage.py makemigrations
python manage.py migrate
python manage.py runserver
