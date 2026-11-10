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

### Nova melhoria para a aula: alertas de validade dos lotes

Antes, a tela mostrava “Estoque Ok” mesmo quando a validade do lote já havia
passado. Agora ela apresenta três situações:

- **Vencido:** alerta vermelho e bloqueio de aplicação e novos agendamentos.
- **Próximo do vencimento:** alerta amarelo quando faltam até 30 dias, com a
  quantidade de dias restantes ou o aviso “Vence hoje”.
- **Dentro da validade:** indicação verde quando faltam mais de 30 dias.

Lotes vencidos continuam visíveis no controle de estoque, mas deixam de aparecer
nas opções de vacinas do portal e da sincronização offline. O servidor confere
a validade novamente ao salvar, inclusive quando um pedido offline é reenviado.
O agendamento também é bloqueado se a data escolhida ultrapassar a validade do
lote. Caso o lote vença depois da reserva, a confirmação é bloqueada sem alterar
o estoque; o servidor pode cancelar e organizar outro agendamento.

**Como demonstrar em aula:** cadastre lotes fictícios com validade ontem, daqui
a 15 dias e daqui a 60 dias. Abra “Controle de Estoque” para comparar os alertas.
Depois, tente agendar uma data posterior à validade de um lote. Neste exercício,
a validade é considerada até o fim da data cadastrada, no fuso de São Paulo.

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

## Aplicativo Android - lembretes de vacinação

### Postos próximos no portal web

Em **Postos**, use **Localizar postos próximos** e permita a localização do
navegador. O cálculo é feito no aparelho e ordena as unidades exibidas por
distância em linha reta. A localização não é salva no EasyVacc. **Ver rota no
Google Maps** abre o trajeto da posição informada até o posto selecionado.

O diretório usa os endereços da Prefeitura de Saquarema e coordenadas de 18
unidades do [CNES/DATASUS](https://apidadosabertos.saude.gov.br/cnes/estabelecimentos?codigo_municipio=330550)
conferidas em 10/11/2026. Madressilva, Rio Seco e Vilatur têm endereços
divergentes nas fontes: continuam disponíveis por endereço, sem distância
exibida. Agendar abre com a unidade selecionada quando há cadastro profissional;
reservar depende do Firestore, paciente e estoque.

O projeto nativo está na pasta [android](android/README.md). Ele reúne busca e
compartilhamento de postos da Aula 2 e lembretes locais da Aula 3. Consulte o
[roteiro e os resultados das Aulas 2 e 3](android/AULAS-2-E-3.md).
O [APK 1.2.0](releases/EasyVacc-Melhoria-Aulas-1.2.0.apk) aceita rascunhos
da página de lembretes; confirme no Android para receber o aviso fora da tela.
O GitHub Actions compila um APK de desenvolvimento e executa os testes.
Consulte [as execuções Android](https://github.com/kawanzn/sistema_de_vacina2/actions/workflows/android.yml)
para conferir o resultado e baixar o artefato de uma execução bem-sucedida.

Os lembretes ficam no dispositivo e não cancelam consultas do sistema web.
A versão didática não usa alarmes exatos nem restaura alarmes após reinício.

## Integrantes do projeto

- Ataides Alves Cota Junior — 202312481
- Kaio Abreu Oliveira — 202312160
- Kawan Lopes Mangia — 202312633
- João Victor Sampaio Gentil — 202312639
- Pedro Marcos Quintanilha de Oliveira Nunes — 202312595
