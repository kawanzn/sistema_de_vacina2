# EasyVacc — sistema de vacinação

Acesse a demonstração: https://easyvacc-juniorcotasaqua-5449s-projects.vercel.app/

Projeto desenvolvido como exercício de aula para cadastrar pacientes, consultar
postos de saúde, controlar vacinas e organizar agendamentos.

## Melhoria realizada no projeto

O banco anterior no Supabase foi excluído. Para continuar utilizando o sistema,
o armazenamento foi adaptado para o **Firebase Cloud Firestore**, e a aplicação
foi preparada para publicação na **Vercel**.

O objetivo desta melhoria é permitir demonstrar o projeto pela internet e
praticar a integração entre uma aplicação Django e um banco de documentos.

### O que mudou

- Os cadastros passaram a ser salvos em documentos do Firestore.
- Os formulários e as telas do Django foram mantidos.
- O login dos servidores também passou a usar o Firestore, com senhas em hash.
- Uma tela de administração permite cadastrar postos e servidores.
- Agendamentos, reservas e baixa de doses usam transações para evitar estoque
  incorreto quando duas pessoas realizam operações ao mesmo tempo.
- A repetição de uma sincronização offline não deve criar o mesmo registro duas vezes.
- O cancelamento de uma dose já aplicada é bloqueado: ela não pode voltar ao estoque.
- As credenciais ficam em variáveis de ambiente e não são enviadas ao GitHub.

### Como os dados são organizados

| Informação | Coleção |
| --- | --- |
| Pacientes e dependentes | `easyvacc_paciente` |
| Postos de saúde | `easyvacc_postosaude` |
| Estoques de vacinas | `easyvacc_estoque` |
| Doses aplicadas | `easyvacc_vacina` |
| Agendamentos | `easyvacc_agendamento` |
| Servidores do sistema | `easyvacc_servidor` |
| Controle de sincronização | `easyvacc_offlinesubmission` |
| Controle de valores únicos | `easyvacc_unique` |

Os campos dos modelos do Django continuam servindo para validar os formulários.
O arquivo `vacinas/firestore_store.py` faz a leitura e a gravação no Firestore.
Ele implementa somente as consultas usadas neste exercício, não um backend SQL
completo. As listagens e buscas por texto ainda não têm paginação; o projeto é
destinado a demonstrações pequenas com dados fictícios.

## Executar localmente

Requisitos: Python 3.12 e um projeto Firebase com Firestore Standard.

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

Preencha `.env` com o ID do projeto, uma `SECRET_KEY` aleatória e o caminho da
credencial em `GOOGLE_APPLICATION_CREDENTIALS`. Guarde o JSON privado fora do Git.
A conta de serviço precisa do papel `roles/datastore.user` no projeto.

```powershell
python manage.py check
python manage.py collectstatic --noinput
python manage.py criar_administrador --username admin --email seu-email@example.com
python manage.py runserver
```

Não é necessário executar `migrate`: esta versão não usa tabelas SQL. As
migrações antigas foram mantidas como histórico da versão anterior.

Depois do login, abra `/admin/` para cadastrar postos e servidores. Cadastre os
lotes em `/estoque/novo/` e os pacientes em `/pacientes/novo/`.

## Firebase e Vercel

O projeto Firebase é `easyvacc-jrz-2026`, com o banco `(default)` em São Paulo
(`southamerica-east1`) no plano Spark. O uso está sujeito às cotas gratuitas.

Para publicar as regras e os índices:

```powershell
npx -y firebase-tools@latest deploy --only firestore --project easyvacc-jrz-2026
```

As regras bloqueiam o acesso direto dos navegadores ao banco. O Django faz as
operações no servidor usando a conta de serviço. Essas regras são um protótipo
para o exercício e devem ser revisadas antes de uso amplo ou com dados reais.

Na Vercel, configure estas variáveis em Production e Preview:

| Variável | Conteúdo |
| --- | --- |
| `FIREBASE_PROJECT_ID` | ID do projeto Firebase |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | JSON da conta de serviço, salvo como Secret |
| `SECRET_KEY` | Chave aleatória do Django, salva como Secret |
| `DEBUG` | `False` |

O arquivo `vercel.json` seleciona o suporte a Django e a região São Paulo.
A Vercel instala as dependências e executa `collectstatic` durante o build.
Não há restauração automática dos registros do Supabase excluído: para recuperar
os dados anteriores, é necessário ter um backup.

## Verificação

Os testes de integração verificam login, telas, formulários, dependentes,
unicidade, reservas simultâneas, confirmação, cancelamento, sincronização e
rollback. Usam coleções temporárias com prefixo aleatório, removidas ao final.
Execute em um projeto de testes com credenciais próprias:

```powershell
$env:RUN_FIRESTORE_TESTS = '1'
python manage.py test vacinas --verbosity 2
```

Sem essa variável, os testes que acessam o Firestore são ignorados.

## Integrantes

- Ataides Alves Cota Junior — 202312481
- Kaio Abreu Oliveira — 202312160
- Kawan Lopes Mangia — 202312633
- João Victor Sampaio Gentil — 202312639
- Pedro Marcos Quintanilha de Oliveira Nunes — 202312595
