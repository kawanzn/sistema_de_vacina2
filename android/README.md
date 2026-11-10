# EasyVacc Android

Aplicativo Android nativo em Java, versão 1.1.0, para as Aulas 2 e 3.
Os dados ficam no aparelho; nenhuma chave Firebase ou senha do site é incluída.
As telas do diretório e dos lembretes funcionam offline. Os lembretes não são
sincronizados com os agendamentos do Django e não alteram reservas ou consultas.
Esta versão didática usa o pacote `br.edu.easyvacc` e pode coexistir com o
EasyVacc completo da branch main. Veja o [roteiro das aulas](AULAS-2-E-3.md).

## Funcionalidades

- Buscar postos por nome, bairro ou endereço e compartilhar somente o endereço público.
- Criar um lembrete com título, mensagem e data/hora futuros.
- Agendar um alarme não exato com `AlarmManager.setAndAllowWhileIdle`.
- Listar os lembretes salvos em `SharedPreferences`.
- Emitir notificação nativa por `BroadcastReceiver` e canal de notificações.
- Pedir `POST_NOTIFICATIONS` no Android 13 ou superior.
- Cancelar tanto o alarme pendente quanto uma notificação já emitida.
- Testar uma notificação imediatamente, sem criar registro ou alarme.
- Informar erros de validação, persistência e falta de permissão.

Compatibilidade: Android 8.0 (API 26) ou superior; compilação e alvo API 35.

## Obter o APK

Na aba **Actions** do GitHub, abra a execução mais recente de
**Android - compilar e testar EasyVacc**. Quando concluída com sucesso, baixe o
artefato **EasyVacc-Android-debug**. O ZIP contém o APK em `app-debug.apk`,
os relatórios de testes e a análise lint. Os artefatos são mantidos por 14 dias;
uma nova execução pode ser solicitada pelo botão **Run workflow**.

O APK é uma compilação de desenvolvimento, assinada com chave de debug.
Não é uma publicação na Google Play. Instale apenas em um aparelho ou emulador
de testes, autorizando a instalação conforme as configurações do seu Android.

## Compilar localmente

Requisitos: JDK 17 e Android SDK com `platforms;android-35` e Build Tools.
Abra esta pasta no Android Studio, aguarde a sincronização e execute o app.
Também é possível usar o Gradle Wrapper incluído:

```powershell
# Na pasta android, com JAVA_HOME e ANDROID_HOME configurados:
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

No Linux/macOS: `./gradlew testDebugUnitTest assembleDebug lintDebug`.
O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Casos automatizados

Os testes usam Robolectric, que simula APIs Android na JVM:

1. Criação e persistência da lista.
2. Recusa de título vazio e data passada.
3. Agendamento no AlarmManager e bloqueio de duplicidade.
4. Cancelamento anterior ao disparo, sem notificação.
5. Disparo único e remoção da notificação ao cancelar.
6. Identificação de itens pausados após mudança do contador de boot.
7. Identificador inexistente.
8. Permissão de notificação negada no Android 13.
9. Mensagem vazia e teste imediato sem registro nem alarme.
10. Busca de postos, prévia pública, cancelamento e abertura do seletor nativo.

Esses testes não substituem evidências obtidas em um celular ou emulador.
O estado `Notificado` indica que o app solicitou a notificação ao Android;
o sistema pode ocultar banners ou sons conforme suas configurações.

## Limitações conhecidas

- **Alarmes não exatos:** o Android pode atrasar o disparo, especialmente em
  economia de bateria, Doze ou sob restrições do fabricante.
- **Sem restauração após reinício:** não há receptor `BOOT_COMPLETED`.
  A lista é preservada, mas alarmes são perdidos no reboot. O app identifica
  a mudança do contador de inicialização e mostra os itens como pausados;
  o usuário deve agendar novamente aqueles cujo horário ainda é futuro.
- Encerrar a tela não é o mesmo que forçar a parada do app. A parada forçada
  pode impedir a entrega de alarmes e notificações até reabrir o aplicativo.
- Permissão negada ou canal desativado impede agendamento e notificação.
- Cancelar um lembrete não cancela uma consulta nem uma reserva de vacinação.
- Limite de 100 itens; dados removidos ao limpar armazenamento ou desinstalar.
- Sem alarmes exatos, notificações push ou garantia para alertas críticos.
  Não utilizar como garantia de aviso de urgência ou administração de medicamentos.
- Não foi validado em um dispositivo físico nesta etapa. A disponibilidade do
  APK e os resultados automatizados devem ser conferidos na execução do CI.

## Referências técnicas

- [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [Permissão de notificações](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Testes com Robolectric](https://robolectric.org/getting-started/)
