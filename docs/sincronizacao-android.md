# Publicar a sincronização Android

A API usa as mesmas tabelas de Paciente, PostoSaude, Estoque, Agendamento e Vacina do site. Novas
tabelas guardam bloqueio, recibos e vínculo dose/agendamento sem alterar colunas das tabelas antigas.
As telas web continuam funcionando antes da migração; a API informa indisponibilidade.

## Ativar no serviço que atende o domínio

1. Faça backup do banco e publique a revisão atual deste repositório no serviço correto.
2. Configure `DATABASE_URL` com banco persistente e `SECRET_KEY` exclusiva e segura. SQLite em
   armazenamento efêmero de funções serverless não serve para a base compartilhada.
3. Execute `python manage.py migrate --noinput` com as variáveis do próprio serviço.
4. Confira `python manage.py check` e `GET https://SEU-DOMINIO/api/android/status/`.
   Esperado: HTTP 200, `protocol: 1` e `ready: true`.
5. Configure o APK em Mais > Sincronização com o domínio e uma conta Django ativa `is_staff`.
   A conta local do APK não cria usuário no servidor. Não coloque senhas em arquivos públicos.

Commit no GitHub não garante publicação nem migração. HTTP 404 significa que o domínio ainda
não atende essas rotas; publique no projeto que efetivamente atende o endereço. HTTP 503 exige
conferir migrações, banco e SECRET_KEY. O APK preserva os dados enquanto o servidor está indisponível.

## Contrato

- POST `/api/android/login/`: credenciais profissionais, token válido por 30 dias.
- POST `/api/android/sync/`: Bearer token, UUID da tentativa, imagem local e base assinada.
- A mesma tentativa retorna o mesmo recibo, sem reaplicar reservas ou baixas de estoque.
- Merge em três versões: base, local e servidor. Registros independentes combinam; duas alterações
  diferentes no mesmo registro geram HTTP 409 e nenhuma aplicação parcial da tentativa.
- Validações de vínculos, identidades únicas, reservas e aplicação concluída. Estoque não sofre
  merge de campos separados: saldo e reservas são uma unidade de consistência.
- O insert das aplicações não dispara o sinal de baixa de estoque novamente e preserva a data offline.
- Exclusões em massa por restauração de backup local não são expostas pela API.

Uma base antiga com lote sem unidade, dose sem lote/posto ou reserva inconsistente exige revisão.
A API não inventa registros ou quantidades para esconder inconsistências. Exporte backup local
e confira o lote/cadastro antes de conciliar conflito. Não desinstale com pendências de envio.

Se houver edição durante uma resposta em trânsito, o Android combina a nova mudança independente
antes de aplicar a resposta. Base, fila e resposta usam transação SQLite. Senha do site não é
salva no APK. Recibos contêm dados pessoais: restrinja banco e backups à equipe autorizada e não
habilite logs de corpo de requisição ou cabeçalho Authorization.

## Validar

`python manage.py test vacinas` cobre regressões web e API: autorização, download inicial, paciente
offline, repetição sem dupla baixa, alterações independentes, conflito de estoque, base assinada,
rollback, vínculos, revogação de token, identidade duplicada, reserva/confirmação e exclusão indevida.

Esses testes locais não comprovam publicação ou comportamento em celular. Execute o plano Android
com dois aparelhos e dados fictícios antes de usar a base real.
