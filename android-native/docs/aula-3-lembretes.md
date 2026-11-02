# Aula 3 - Notificações e lembretes locais

**EasyVacc Android 2.2.0 - 02/11/2026**

Pedro Marcos Quintanilha de Oliveira Nunes (202312595) e Kawan Lopes Mangia (202312633).
Laboratório de Desenvolvimento de Aplicativos Nativos, 8º período, Universidade de Vassouras,
Campus Saquarema. Professor Lucas Hermenegildo do Nascimento. Atividade de 22/10/2026.

## Evento escolhido e implementação

RFN02: lembrar o usuário de conferir a caderneta ou de um compromisso relacionado ao EasyVacc.
O lembrete é pessoal e local. Ele não confirma consulta municipal, não reserva vacina e não
determina indicação médica. Não requer paciente cadastrado, conta profissional, servidor ou rede.

O recurso foi integrado ao aplicativo existente: **Portal do Cidadão > Lembretes locais** ou
**Acesso profissional > Mais > Lembretes locais**. Não substitui os fluxos da Aula 2. A consulta
por CEP, seleção da unidade, compartilhamento, agendamento e sincronização foram preservados.

- Formulário de título, mensagem e data/hora, com rejeição de vazios, título maior que 80,
  mensagem maior que 500 e instante menor ou igual a agora.
- A escolha da data só é aplicada ao confirmar também a hora, zerando segundos e milissegundos.
- Permissão POST_NOTIFICATIONS em Android 13+, botão de configurações, verificação de bloqueio
  geral e do canal em Android 8+. Permitir não agenda automaticamente.
- AlarmManager.setAndAllowWhileIdle, RTC_WAKEUP, PendingIntent explícito e imutável com ID e
  URI próprios. Sem bibliotecas adicionais, Firebase, temporizador de tela ou alarme exato.
- Lista com data prevista, histórico, cancelamento e horário de publicação. Publicada significa
  enviada ao NotificationManager, sem comprovar som, leitura ou visualização pelo usuário.
- Notificação com ícone monocromático, texto completo e abertura da tela dos lembretes. O item
  correspondente fica identificado. Conteúdo público da tela bloqueada é genérico; a escolha
  final de visibilidade é controlada pelo Android e pelo usuário.
- Estados Preparando, Agendado, Cancelado, Publicada, Bloqueado na entrega, Falha ao agendar e
  Reagendar após reinício. Receiver lê o registro persistido e ignora eventos cancelados,
  duplicados ou recebidos antes do horário.
- Persistência em SQLite separado, `easyvacc_reminders.db`, aproveitando o armazenamento
  nativo do projeto. É uma adaptação da base educacional em SharedPreferences, sem dependência
  adicional. IDs são AUTOINCREMENT e não são reutilizados. O histórico não entra no backup
  clínico ou na sincronização. Evitar dados sensíveis no título e na mensagem.
- Após reboot ou atualização do pacote, um receiver marca os registros pendentes como
  Reagendar após reinício. Não restaura os alarmes: o usuário escolhe novo horário e cria outro
  lembrete. Isso evita apresentar uma reserva antiga de alarme como ainda ativa.

### Arquivos principais

`ReminderActivity.java`: interface, seletores, permissão, lista e estado do formulário.
`ReminderRules.java`: validações e regras puras executadas nos testes JVM.
`ReminderStore.java`: registros e histórico persistidos em banco separado.
`ReminderScheduler.java`: canal, permissão, alarme, cancelamento e notificação.
`ReminderReceiver.java`: recebe o alarme sem rede e atualiza o histórico; sinaliza reboot/update.
`AndroidManifest.xml` e `ic_reminder.xml`: permissão, activity, receiver e ícone.

## Instalar e executar

1. Baixe e instale `EasyVacc-Android-2.2.0.apk` por cima da versão existente, sem desinstalar.
2. Abra Portal do Cidadão > Lembretes locais. O recurso também está em Mais do profissional.
3. Toque Permitir notificações. Em Android 13+, aceite no diálogo. Se o pedido não reaparecer,
   use Configurações de notificações e habilite o aplicativo e o canal Lembretes locais.
4. Toque Testar notificação agora. Abra a bandeja, confira o teste e toque para abrir o app.
   Esse teste imediato não substitui a entrega futura do CT05.
5. Preencha um título e mensagem fictícios. Escolha uma data e hora alguns minutos à frente.
6. Toque Agendar lembrete e confira o registro Agendado na lista. Volte à tela inicial do celular.
7. Observe a bandeja após o horário. Registre o horário escolhido e o observado, mesmo com atraso.
8. Toque na notificação. Confira a abertura da tela e o horário de publicação no histórico.
9. Crie outro lembrete futuro e cancele antes do horário. Confirme Cancelado e observe se não
   ocorre publicação daquele ID, conforme CT07.

Android 6+ é mantido no aplicativo; para a aula, prefira Android 8+ e inclua Android 13+ se possível.
Não é necessário internet para executar os lembretes. Não forneça dados de pessoas reais nas evidências.

## Ambiente e resultados efetivamente obtidos

Compilação: Windows, JDK 17.0.20.1+1, SDK android-37.0 disponível, minSdk 23, targetSdk 35.
APK 2.2.0, versionCode 20200, pacote `br.com.easyvacc.android`, mesma chave das versões anteriores.
Assinatura e compilação do APK e runner instrumentado verificadas.

**JVM: 95 verificações aprovadas, zero falhas.** Incluem as 81 anteriores e 14 novas para título,
mensagem, data passada, limite igual/agora/+1 ms, entrega antecipada, cancelada, duplicada e reboot.
Log: `evidencias/testes-jvm-2.2.0.txt`. Isso verifica regras, não a execução das APIs do Android.

**Android: não executado.** A consulta USB não detectou aparelho. O usuário escolheu receber o APK
e o roteiro para testar por conta própria. Não foi produzida captura de notificação ou cancelamento.
Os testes instrumentados adicionais de banco, IDs, persistência e abertura de tela foram compilados,
mas não são registrados como aprovados. Evidências reais CT05 e CT07 continuam pendentes.

## Casos acumulados e roteiro de execução

Os quatro primeiros casos mantêm o fluxo nativo da Aula 2. Os novos CT05 e CT06 atendem às duas
situações obrigatórias sugeridas; os demais ampliam a cobertura. As linhas Android só devem ser
marcadas Aprovado após execução no dispositivo. A aprovação de regras JVM está descrita separadamente.

### CT01 - Prévia de endereço público (Aula 2 / CTN01)

Técnica: particionamento de equivalência, dados públicos versus dados pessoais.
Pré-condição: abrir Postos e selecionar uma unidade pública. Dados: ESF Água Branca.
Passos: buscar Água Branca; abrir Compartilhar endereço; conferir a prévia.
Esperado: somente nome e endereço públicos, sem CPF, paciente ou caderneta.
Obtido real: conteúdo de `Rules.shareText` validado nos testes JVM desta versão.
Resultado da interface: **Pendente em Android**, sem captura.

### CT02 - Cancelar a prévia (Aula 2 / CTN02)

Técnica: transição PREVIEW > CANCELLED.
Pré-condição: prévia aberta. Passos: cancelar e voltar à busca; conferir unidade e filtro.
Esperado: busca e endereço preservados; nenhum envio anunciado.
Obtido real: estado e preservação de texto aprovados no teste JVM.
Resultado da interface: **Pendente em Android**, sem captura.

### CT03 - Cancelar seletor nativo (Aula 2 / CTN03)

Técnica: transição CHOOSER > CANCELLED.
Pré-condição: prévia conferida. Passos: escolher aplicativo; voltar sem selecionar destino.
Esperado: não anunciar entrega e manter o endereço para nova tentativa.
Obtido real: regra de cancelamento do seletor aprovada no teste JVM.
Resultado da interface: **Pendente em Android**, sem captura.

### CT04 - Retorno após escolher destino (Aula 2 / CTN04)

Técnica: particionamento e transição CHOOSER > SELECTED.
Passos: abrir seletor, escolher um destino de teste e retornar ao EasyVacc.
Esperado: distinguir escolha de destino de comprovação de envio.
Obtido real: transição e regra de ausência de prova de entrega aprovadas no teste JVM.
Resultado da interface: **Pendente em Android**, sem captura.

### CT05 - Lembrete futuro em segundo plano

Técnica: particionamento, horário futuro válido e permissão concedida.
Pré-condições: canal e notificações permitidos; aparelho com data correta; app sem parada forçada.
Dados: título `CT05 Conferir caderneta`; mensagem `Teste fictício da aula`; horário cinco minutos adiante.
Passos: criar; anotar ID e hora; colocar o app em segundo plano; observar a bandeja; tocar no aviso;
atualizar lista. Não usar o teste imediato como substituto.
Esperado: não publicar antes; posteriormente publicar sujeito à janela do Android; abrir tela dos
lembretes; identificar o ID e apresentar Publicada com horário observado pelo aplicativo.
Obtido real: validação de futuro e limite de entrega aprovados em JVM; **entrega futura não executada**.
Resultado Android: **Pendente**. Preencher hora prevista: ____; hora observada: ____; atraso: ____;
marca/modelo/API: ____; arquivo de evidência: ____; resultado: ____.

### CT06 - Horário passado e valor limite

Técnica: particionamento passado/igual/futuro; análise de valor limite.
Pré-condições: contar registros antes de começar. Dados: um minuto no passado, igual a agora e futuro.
Passos: preencher título/mensagem; selecionar passado; tocar Agendar; conferir erro e lista sem novo ID.
Esperado: rejeitar passado e igual a agora; não salvar registro nem criar alarme.
Obtido real: passado (-1 ms), igualdade e +1 ms aprovados na regra JVM; validação precede insert/alarme.
Resultado da interface: **Pendente em Android**. Contagem antes/depois: ____; evidência: ____.

### CT07 - Cancelar antes do disparo

Técnica: Agendado > Cancelado, transição irreversível para aquele ID.
Dados: `CT07 Cancelamento`, futuro com pelo menos 10 minutos de margem.
Passos: criar e anotar ID; cancelar com confirmação; fechar tela; observar após a janela prevista;
reabrir lista. Evitar cancelar no mesmo instante do disparo.
Esperado: histórico Cancelado; alarme e eventual notificação removidos; não publicar aquele ID.
Obtido real: predicado de entrega recusa Cancelado em JVM. **AlarmManager.cancel não executado**.
Resultado Android: **Pendente**. ID: ____; hora do cancelamento: ____; fim da observação: ____;
captura do histórico Cancelado: ____; captura/registro da observação: ____.

### CT08 - Notificações negadas e canal bloqueado

Técnica: particionamento permitido/negado; permissão e canal são condições independentes.
Passos: negar POST_NOTIFICATIONS no Android 13+; tentar Agendar; conferir ausência de novo registro;
habilitar nas configurações; bloquear somente o canal e repetir; habilitar e tocar Agendar novamente.
Esperado: orientar sem travar nem confirmar falso sucesso; conceder não cria lembrete automaticamente.
Obtido: **Não executado**, pendente em Android. Evidências: ____.

### CT09 - Dois lembretes e persistência

Técnica: IDs únicos e transições independentes.
Passos: criar A e B com datas futuras; anotar IDs; cancelar A; sair e reabrir; observar B.
Esperado: A Cancelado e B Agendado, sem sobrescrita; B pode publicar sem reviver A.
Obtido: runner de banco compilado, **não executado**. Resultado Android: **Pendente**.

### CT10 - Rotação, modo avião e reinício

Técnica: preservação de estado e recuperação de interrupção.
Passos: preencher formulário e escolher data; girar aparelho; conferir campos; agendar em modo avião;
verificar entrega. Em teste separado, criar futuro, reiniciar e desbloquear, reabrir a lista.
Esperado: formulário preservado na recriação; disparo sem internet; após reboot, histórico marcado
Reagendar após reinício, sem afirmar restauração do alarme. Preparar novo lembrete exige data futura.
Obtido: regra de não entregar estado Reagendar aprovada em JVM. **Ciclo Android não executado**.
Resultado Android: **Pendente**. Evidências: ____.

## Limitações e riscos conhecidos

- Horários aproximados. Android 12+ pode atrasar uma hora em condições normais; Doze, economia de
  bateria e regras do fabricante podem aumentar o atraso. Não exigir disparo em 30 segundos.
- Não restaura alarmes após reboot/update. Histórico e avisos são locais; o usuário recria pendentes.
- Forçar parada pode impedir entrega. Fechar a tela de recentes tem comportamento distinto.
- Mudança manual de relógio/fuso pode alterar a interpretação do horário; os dados usam epoch.
- Não há edição, recorrência, nuvem ou confirmação de leitura. Som depende do canal e Não Perturbe.
- Banco e AlarmManager não formam transação única. Falha ao registrar alarme tenta cancelar e marca
  Falha; encerramento abrupto pode deixar Preparando. Confira o histórico e recrie se necessário.
- Cancelamento e receiver são serializados no processo; não é possível desfazer um aviso já visto.
- Dados ficam no armazenamento privado do app, sem criptografia adicional. Desinstalar apaga histórico;
  backup clínico e restauração não incluem lembretes. Não armazenar dados sensíveis nas mensagens.
- Não atende alarmes médicos, emergenciais ou alertas críticos.

## Conferência da entrega

Implementado e compilado: criar, agendar, listar, cancelar, validação, permissão, canal, teste imediato,
abertura ao toque, histórico e instruções. Repositório e APK atualizados; regras JVM executadas.

**Falta completar no aparelho:** executar CT01-CT10, registrar horários e anexar evidência real da
notificação futura e do cancelamento. A entrega acadêmica não deve ser apresentada como integralmente
validada antes dessas execuções. Não se criaram resultados ou imagens simulando notificações reais.

### Referências oficiais

- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/ui/views/notifications/notification-permission
- https://developer.android.com/develop/ui/views/notifications/channels

Materiais fornecidos pelo usuário: texto Aula 3 e PDF O Aplicativo Lembra por Você. Não são
incluídos no repositório público; os requisitos foram adaptados à base nativa do EasyVacc.
