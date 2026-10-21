# EasyVacc Android 2.1.0

Aplicativo nativo Java/Views e SQLite, com modo offline e sincronização HTTPS com as mesmas
tabelas do site. Sem WebView. Android 6 ou superior, pacote `br.com.easyvacc.android`, versionCode
20100, targetSdk 35. A assinatura é a mesma de 2.0.0: instale por cima, sem desinstalar.

## Começar

1. Instale `releases/EasyVacc-Android-2.1.0.apk`. Escolha Servidor Público ou Portal do Cidadão.
2. No primeiro acesso profissional, configure usuário e senha locais.
3. Em Mais > Sincronização, informe o domínio HTTPS e uma conta profissional existente no site.
   O acesso local e a conta do site são separados. A senha do servidor não é salva no APK.
4. Confira Sincronizar agora. Somente após uma resposta aceita os dados são apresentados como
   sincronizados. Sem conexão, os registros e a requisição pendente ficam preservados.

O servidor precisa ter a integração Android publicada e as migrações aplicadas. Publicar no GitHub
não comprova que o domínio foi atualizado. Até isso ocorrer, o aplicativo funciona com dados locais
e informa a indisponibilidade da integração. Não há download público de pacientes: cada aparelho
que recebe a base precisa da configuração de uma conta profissional ativa.

## Funções

| Serviço do site | Implementação Android |
| --- | --- |
| Servidor Público / Portal do Cidadão | Áreas separadas e atalhos equivalentes |
| Painel | Pacientes, doses, aplicações recentes, próximos atendimentos e estado da sincronização |
| Pacientes | Cadastro e edição de nome, nascimento, CPF, SUS, contato, endereço e responsável |
| Caderneta | Consulta por nome/CPF/SUS e nascimento, doses, lote, posto, situação e dependentes |
| Aplicação | Escolher paciente, unidade e lote; conferir e confirmar com baixa atômica |
| Estoque | Lotes, entradas, saldo livre, reservas, validade, busca e filtro de situação |
| Agendamento | Unidade → vacina → horário, confirmação e reserva da dose |
| Agenda / histórico | Busca, filtro, confirmar dose, cancelar com justificativa e registrar ausência |
| Postos | Busca local, disponibilidade e compartilhamento nativo de endereço |
| Relatório | Aplicações pesquisáveis e CSV pelo seletor do Android |
| Assistente | Respostas locais de uso e perguntas frequentes |
| Dados | Offline, sincronização com o site e backup JSON transacional |

O assistente não replica a integração opcional com um provedor de IA do site. Ele não determina
condutas clínicas, doses ou intervalos. O painel Django de administração permanece no servidor.

## Sincronização e conflitos

O token profissional expira em 30 dias e deixa de valer quando a senha do servidor muda. Uma fila
durável no SQLite guarda a tentativa e sua base. Recibos no servidor impedem que repetir uma
requisição duplique registros ou desconte a dose novamente. Resposta e base são aplicadas juntas
na mesma transação SQLite. Novos registros usam IDs aleatórios amplos para evitar colisões entre
aparelhos. Cadastros legados com IDs iguais podem exigir conciliação na primeira conexão.

Alterações em registros diferentes são combinadas. Alterações incompatíveis no mesmo cadastro
ou lote geram conflito e precisam de revisão profissional. O local fica preservado e o servidor
não aplica parcialmente o envio. Não é possível garantir saldo global enquanto dois aparelhos
trabalham sem conexão. Exporte o backup antes de conciliar; não limpe os dados para esconder erros.

Após a conferência, a opção Usar a base do servidor guarda a versão local anterior em uma cópia
de recuperação privada e recebe a base remota, com confirmação explícita. As pendências antigas
ficam nessa cópia para revisão, sem novo envio automático. Exporte as cópias em Sincronização.

O Android agenda sincronização ao abrir o aplicativo, após salvamentos e periodicamente com rede
disponível. Economia de bateria pode adiar a tarefa. Use Sincronizar agora quando precisar conferir
o resultado. Restaurar backup não autoriza excluir cadastros compartilhados no servidor.

Permissões: INTERNET, ACCESS_NETWORK_STATE e RECEIVE_BOOT_COMPLETED. Não pede câmera, localização,
contatos ou armazenamento amplo. Arquivos e compartilhamento usam os seletores do próprio Android.

## Servidor

Publique o código da raiz do repositório no serviço que atende o domínio. Configure `DATABASE_URL`
persistente e `SECRET_KEY` segura. Execute `python manage.py migrate --noinput` no ambiente do serviço.
`GET /api/android/status/` deve responder HTTP 200 com `{"protocol":1,"ready":true}`.
Detalhes em `docs/sincronizacao-android.md` na raiz. Não publique credenciais nem a chave de assinatura.

## Verificações e limites

- 45 verificações JVM de regras, buscas, estados, CPF, datas, CSV e senha.
- Suíte Django com as regressões web e testes da API. Logs em `docs/evidencias/`.
- Compilação do APK e do runner Android, alinhamento e assinatura v1/v2/v3.
- Inspeção do manifesto e DEX para confirmar código nativo, sem WebView/TWA.

Ainda não houve execução em aparelho: navegação, seletores, retorno à conexão e sincronização entre
dois celulares e servidor publicado precisam de teste. Compilar não comprova esses fluxos. A planilha
e documentos da versão 2.0.0 ficam como histórico; o plano adicional está em `docs/plano-2.1.md`.

## Compilar

Android Studio: JDK 17, Gradle 8.9, plugin 8.7.3 e SDK 35. A compilação independente desta entrega
usa o android.jar 37.0 disponível, com minSdk 23 e targetSdk 35.

```powershell
.\test.ps1 -JavaHome 'C:\caminho\jdk-17'
.\build.ps1 -JavaHome 'C:\caminho\jdk-17' -AndroidSdk 'C:\caminho\Sdk' `
  -SigningStore 'C:\privado\easyvacc-native.p12' `
  -SigningPasswordFile 'C:\privado\password.txt' -Platform android-35 -BuildDeviceTests
.\test-device.ps1 -ApkDir '.\build\output'
```

O runner testa o banco e merge exclusivamente em `instrumentation_test.db` e abre a tela inicial.
A chave privada foi entregue separadamente; preserve-a para atualizações futuras.

Pedro Marcos Quintanilha de Oliveira Nunes — 202312595. Kawan Lopes Mangia — 202312633.
Universidade de Vassouras.
