# EasyVacc — Aulas 2 e 3 na branch melhoria-firebase-vercel

APK didático Android 1.1.0, Android 8 ou superior. O Django e o Firestore da branch
continuam separados do APK. Os lembretes são pessoais, para conferir a caderneta
ou um compromisso; não confirmam uma consulta ou reserva de vacina.

## Demonstrar no Android

1. Instale o APK de teste. Abra **Buscar e compartilhar postos** e digite
   `agua branca`. Confira o endereço público, abra a prévia e escolha o destino
   pelo seletor nativo. Voltar não é confirmação de envio.
2. Na tela de lembretes, habilite as notificações e teste a notificação imediata.
   Toque nela para voltar ao EasyVacc. O teste não cria item ou alarme.
3. Preencha título, mensagem e data/hora futura. Toque em **Agendar lembrete**,
   confira a lista e coloque o app em segundo plano. Anote o horário real do
   disparo e toque na notificação.
4. Crie outro lembrete futuro e cancele antes do horário. O status fica
   **Cancelado**, e o alarme deixa de poder publicar o aviso.
5. Use dados fictícios nas capturas. Não inclua dados de saúde ou de pacientes.

Diretório offline: 21 unidades importadas da base pública já documentada no
[projeto EasyVacc](https://github.com/kawanzn/sistema_de_vacina2/blob/main/android-native/docs/postos-saquarema-2.1.2.md).
Não representa estoque, agenda, cobertura ou horários oficiais em tempo real.

## Casos acumulados

| Caso | Execução | Esperado | Registro no Android real |
| --- | --- | --- | --- |
| CT01 — busca e prévia pública | Buscar `agua branca` e tocar Compartilhar | Nome, rua e bairro da unidade; sem dados pessoais | Pendente |
| CT02 — cancelar prévia | Tocar Voltar à busca | Filtro preservado, sem abrir seletor | Pendente |
| CT03 — cancelar seletor | Escolher aplicativo e voltar sem enviar | Busca preservada, sem mensagem de envio concluído | Pendente |
| CT04 — escolher destino | Abrir seletor e escolher um destino de teste | Entrega somente do texto público ao destino escolhido | Pendente |
| CT05 — alarme futuro | Agendar, ir para a tela inicial e aguardar | Não dispara antes; notifica depois, sujeito ao atraso, e abre o app ao tocar | Pendente: data, hora prevista, hora observada, captura ou vídeo |
| CT06 — horário passado | Usar título e mensagem válidos e horário passado | Erro e nenhum novo registro/alarme | Pendente |
| CT07 — cancelamento antecipado | Agendar outro aviso e cancelar antes da hora | Cancelado permanece na lista; sem notificação daquele aviso | Pendente: hora do cancelamento, fim da observação e captura |
| CT08 — permissão negada | Negar notificações no Android 13+ e tentar agendar | Orientação; nada é agendado. Permitir depois exige tocar Agendar novamente | Pendente |
| CT09 — teste imediato | Testar a notificação e tocar nela | Abre o app sem inserir um lembrete ou alarme | Pendente |
| CT10 — mensagem vazia | Preencher os outros campos e deixar mensagem vazia | Erro e nenhuma nova entrada | Pendente |

Técnicas: particionamento (busca com/sem resultado, permissão e campos);
transição de estados (agendar, cancelar, notificar); valor limite (passado,
igual a agora e futuro). Para cada execução, anote aparelho, versão Android,
dados de teste, resultado observado e referência da evidência. Os testes JVM
simulam APIs; o teste imediato não substitui o CT05.

## Resultado da execução em 09/11/2026

- Android, Gradle 8.13 e JDK 17: **14 testes Robolectric aprovados** (4 de postos
  e 10 de lembretes). APK debug compilado; lint concluído sem erros.
- Os testes cobriram o texto entregue ao seletor, o alarme registrado,
  a recusa de horário passado, o bloqueio de entrega antecipada e o
  cancelamento. A entrega foi simulada, sem gravação de notificação real.
- Web: início, portal e lembretes responderam HTTP 200 em servidor local.
  Dez testes JavaScript e dois testes Django passaram. Doze testes de
  integração com Firestore ficaram de fora, sem uso de credenciais da nuvem.
- Android físico/emulador: pendente. Nenhum aparelho foi detectado por USB e
  o emulador local não tinha aceleração disponível. As evidências da aula
  devem ser obtidas no dispositivo, conforme a tabela.

## Limitações

Alarmes são aproximados, podem atrasar por economia de bateria e não são
restaurados após reinício. A lista é local e preserva o histórico; avisos
pendentes do boot anterior aparecem pausados. Forçar parada pode impedir a
entrega. “Notificado” significa enviado ao sistema, sem confirmação de leitura.
Não usar para alertas médicos críticos. Som e visibilidade dependem do aparelho.

## Reproduzir no computador

Para compilar o APK e executar a simulação das APIs Android, use JDK 17 e
Android SDK 35. Na pasta `android`, execute:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

O APK de teste fica em `android/app/build/outputs/apk/debug/app-debug.apk`.
Para validar o sistema web separadamente, instale `requirements.txt`, execute
`python manage.py check` e `python manage.py collectstatic --noinput`, depois
`python manage.py runserver 127.0.0.1:8764`. As páginas públicas podem ser
demonstradas sem credencial do Firestore; login e operações de dados usam
a configuração Firebase descrita no README principal.
