# Aula 2 - EasyVacc Android nativo

Equipe: Pedro Marcos Quintanilha de Oliveira Nunes (202312595) e Kawan Lopes Mangia (202312633).

Versão 2.0.0. Implementação Java e SQLite local. Pacote br.com.easyvacc.android.

Planilha: https://docs.google.com/spreadsheets/d/1iwQcQ8WCWBVvBLuUQP2BSq9GRRGqWQ0tUndov4G_VlU/edit

## Primeiro fluxo
RFN01 - Postos, busca, prévia, menu nativo de compartilhamento e retorno. Somente nome e endereço públicos. O usuário pode cancelar a prévia ou o menu sem alterar o cadastro. O destino é escolhido pelo usuário. A seleção não comprova entrega. Não há permissões perigosas necessárias.

## Técnicas justificadas

### TN01 - Particionamento de equivalência
Os grupos de dados, saldo, identificação e resultados do recurso nativo têm comportamentos diferentes. Um representante de cada grupo reduz repetição sem omitir alternativas.

Grupos: prévia pública/dados privados; destino escolhido/sem destino; saldo livre/zero/reservado; CPF válido/inválido/vazio; credencial correta/incorreta; busca com/sem resultado; artefato válido/inválido.

Casos: CTN01, CTN04, CTN08, CTN11, CTN14, CTN15, CTN16, CTN17, CTN18.

### TN02 - Transição de estados
O primeiro fluxo usa estados explícitos para preservar endereço, impedir chamadas simultâneas e distinguir cancelamento de seleção de destino.

READY > PREVIEW > CHOOSER > SELECTED. Alternativas CANCELLED e ERROR permitem nova tentativa. SELECTED significa destino escolhido, sem prova de entrega. Recriação restaura texto e estado.

Casos: CTN02, CTN03, CTN05, CTN06, CTN07.

### TN03 - Análise de valor limite
Validade, horário e quantidade têm limites observáveis que podem produzir erros de comparação.

Validade ontem/hoje/amanhã; horário antes/igual/depois de agora; quantidade -1/0/1/1000000/1000001; datas impossíveis e ano bissexto.

Casos: CTN09, CTN10, CTN12, CTN13.

## Casos

### CTN01 - Prévia de endereço público
Requisito RFN01. Técnica TN01.

Pré-condições: Unidade fictícia cadastrada; aparelho Android.

Dados: Unidade TESTE-001; Rua Fictícia, 10; Centro; 28990000.

Passos: 1. Abrir Postos. 2. Buscar Centro. 3. Tocar Compartilhar endereço. 4. Conferir a prévia.

Resultado esperado: Exibir somente nome e endereço da unidade. Não incluir paciente, CPF ou caderneta.

### CTN02 - Cancelar a prévia
Requisito RFN01. Técnica TN02.

Pré-condições: Prévia de compartilhamento aberta.

Dados: Mesma unidade fictícia.

Passos: 1. Abrir a prévia. 2. Tocar Cancelar e voltar à busca. 3. Conferir termo e unidade.

Resultado esperado: Voltar à busca com o termo preservado, sem abrir destino nem anunciar envio.

### CTN03 - Cancelar o menu do Android
Requisito RFN01. Técnica TN02.

Pré-condições: Prévia aberta; menu do Android disponível.

Dados: Endereço público fictício.

Passos: 1. Tocar Escolher aplicativo. 2. Fechar o menu sem escolher destino. 3. Conferir retorno.

Resultado esperado: Informar cancelamento ou nenhum destino escolhido, preservar endereço e permitir nova tentativa.

### CTN04 - Selecionar destino de compartilhamento
Requisito RFN01. Técnica TN01.

Pré-condições: Um destino de teste instalado; prévia aberta.

Dados: Endereço público fictício.

Passos: 1. Abrir o menu nativo. 2. Selecionar destino de teste. 3. Voltar ao EasyVacc.

Resultado esperado: Informar aplicativo de destino selecionado. Não afirmar que a mensagem foi entregue.

### CTN05 - Falha ao abrir o recurso nativo
Requisito RFN01. Técnica TN02.

Pré-condições: Ambiente controlado com ActivityNotFoundException ou SecurityException.

Dados: Falha simulada e endereço fictício.

Passos: 1. Abrir prévia. 2. Acionar compartilhamento com falha controlada. 3. Tentar novamente.

Resultado esperado: Exibir falha e manter endereço. Retomar sem sucesso indevido. Não solicitar permissões desnecessárias.

### CTN06 - Evitar compartilhamento duplicado
Requisito RFN01. Técnica TN02.

Pré-condições: Um menu de compartilhamento já está aberto.

Dados: Dois acionamentos rápidos.

Passos: 1. Acionar compartilhamento. 2. Repetir antes do retorno. 3. Encerrar o menu.

Resultado esperado: No máximo uma operação ativa. Retornar ao estado de nova tentativa após o encerramento.

### CTN07 - Recriação da tela durante o fluxo
Requisito RFN01. Técnica TN02.

Pré-condições: Prévia/menu abertos; aparelho de teste.

Dados: Endereço fictício e estado CHOOSER.

Passos: 1. Abrir prévia e menu. 2. Recriar atividade/processo no ambiente de teste. 3. Retornar.

Resultado esperado: Restaurar endereço e estado do compartilhamento sem criar envio ou sucesso indevido.

### CTN08 - Doses livres e reservas
Requisito RFN02. Técnica TN01.

Pré-condições: Lote vigente com estoque de teste.

Dados: Quantidade/reserva: 0/0; 1/1; 2/1.

Passos: 1. Preparar cada grupo. 2. Tentar aplicação livre. 3. Conferir caderneta e saldo.

Resultado esperado: Bloquear estoque zero e reserva total. Permitir lote com saldo livre; registrar uma dose e baixar uma unidade.

### CTN09 - Validade do lote
Requisito RFN02. Técnica TN03.

Pré-condições: Banco isolado; data de referência definida.

Dados: Validade: ontem, hoje e amanhã.

Passos: 1. Preparar cada validade. 2. Consultar disponibilidade. 3. Tentar aplicar.

Resultado esperado: Bloquear lote vencido. Aceitar a data atual ou futura quando houver saldo livre.

### CTN10 - Horário e validade no agendamento
Requisito RFN03. Técnica TN03.

Pré-condições: Pessoa e lote cadastrados; relógio de teste controlado.

Dados: Horário anterior, igual e posterior ao atual; lote que vence antes do atendimento.

Passos: 1. Testar cada horário. 2. Testar validade antes do atendimento. 3. Conferir reserva.

Resultado esperado: Aceitar somente horário futuro e lote vigente na data marcada. Falhas não criam reserva.

### CTN11 - Identificação e CPF
Requisito RFN04. Técnica TN01.

Pré-condições: Cadastro de teste aberto.

Dados: CPF fictício de teste 11144477735; repetido; dígito incorreto; vazio.

Passos: 1. Preencher os representantes. 2. Salvar. 3. Consultar com data de nascimento.

Resultado esperado: Normalizar CPF válido e bloquear dígitos inválidos. CPF vazio é permitido; a consulta exige nascimento correspondente.

### CTN12 - Datas válidas e inválidas
Requisito RFN04. Técnica TN03.

Pré-condições: Cadastro de teste aberto.

Dados: 31/02/2026; 29/02/2024; data seguida de texto; nascimento futuro.

Passos: 1. Validar cada valor no teste. 2. Conferir salvamento e mensagem.

Resultado esperado: Rejeitar datas impossíveis, texto extra e nascimento futuro. Aceitar data válida de ano bissexto.

### CTN13 - Limites de quantidade
Requisito RFN02. Técnica TN03.

Pré-condições: Lote de teste.

Dados: -1; 1,5; 0; 1000000; 1000001.

Passos: 1. Tentar cadastrar entradas e lotes com cada valor. 2. Conferir mensagem e saldo.

Resultado esperado: Lote aceita inteiros de 0 a 1000000. Entrada exige pelo menos 1. Rejeitar negativos, frações e valores acima do limite.

### CTN14 - Exportar relatório CSV
Requisito RFN05. Técnica TN01.

Pré-condições: Acesso profissional; doses fictícias registradas.

Dados: Nome com aspas e nome começando por =, inclusive com quebra de linha.

Passos: 1. Abrir Mais. 2. Exportar CSV. 3. Escolher destino. 4. Conferir cabeçalho, aspas e fórmulas. 5. Repetir cancelando.

Resultado esperado: Salvar somente após escolha válida; escapar texto para CSV e neutralizar fórmulas. Cancelamento preserva registros sem sucesso indevido.

### CTN15 - Acesso profissional com senha local
Requisito RFN06. Técnica TN01.

Pré-condições: Acesso ainda não configurado no aparelho de teste.

Dados: Senha fictícia Ficticia-2026; senha errada; senha curta.

Passos: 1. Criar acesso. 2. Sair. 3. Testar senha correta, errada e curta. 4. Inspecionar hash no teste isolado.

Resultado esperado: Senha armazenada como hash com sal único. Credencial correta entra; incorreta não entra; mínimo de 8 caracteres.

### CTN16 - Busca local sem diferenciar acentos
Requisito RFN04. Técnica TN01.

Pré-condições: Unidade São João cadastrada no banco de teste.

Dados: sao joao; São João; CEP formatado; termo inexistente.

Passos: 1. Abrir Postos. 2. Buscar cada termo. 3. Conferir resultados em modo avião.

Resultado esperado: Encontrar por nome/bairro/CEP sem rede; normalizar acentos. Busca inexistente não inventa unidade.

### CTN17 - Executar suíte automatizada de regras e estados
Requisito RFN01-RFN06. Técnica TN01.

Pré-condições: JDK 17; código-fonte desta versão; execução em JVM. Não exige Android.

Dados: Dados fictícios incorporados em tests/DomainTests.java.

Passos: 1. Abrir a pasta android-native. 2. Executar test.ps1 -JavaHome caminho_do_JDK. 3. Conferir log e código de saída.

Resultado esperado: 35 verificações aprovadas, 0 falhas, saída 0. Não interpretar aprovação da suíte como execução dos fluxos Android completos.

### CTN18 - Integridade do APK nativo gerado
Requisito RFN07. Técnica TN01.

Pré-condições: APK compilado, SDK Build Tools e chave desta versão. Não exige executar interface.

Dados: EasyVacc-Android-2.0.0.apk; pacote br.com.easyvacc.android.

Passos: 1. Compilar Java/DEX e empacotar. 2. Verificar assinatura com apksigner. 3. Conferir alinhamento e manifesto. 4. Inspecionar referências do DEX.

Resultado esperado: Assinatura v1/v2/v3 válida, pacote correto, minSdk 23, targetSdk 35; sem INTERNET, WebView, TWA ou domínio antigo no aplicativo.

## Execuções e evidências
O arquivo `evidencias/testes-jvm.txt` registra 35 verificações aprovadas nas classes reais Rules,
ShareFlow e Secrets. Os testes executam regras e estados em JVM; não são testes manuais nem execuções Android.
Os cenários nativos completos permanecem planejados para aparelho. O runner
`tests/android/NativeInstrumentation.java` cobre SQLite, autenticação, reservas, histórico, backup
e abertura do aplicativo, usando exclusivamente um banco separado de teste.

## Defeito encontrado e corrigido
DFN01: a primeira execução de CTN01 em JVM revelou que a normalização de texto removia as quebras
de linha da prévia. Corrigido em ShareFlow.preview, preservando o endereço original após validar o tamanho.
A reexecução aprovou o texto público, com nome, endereço e bairro/CEP em linhas separadas.

## Limitações e próximos passos
Não há Android acessível neste ambiente. Registrar no aparelho modelo, SO, versão, prints e vídeo
de CTN01-CTN07. Testar cadastros, aplicação, reserva, cancelamento, exportação e restauração de backup.
Validar modo avião e rotação. Testar o retorno após o sistema encerrar o processo durante o seletor nativo.
Não marcar caso completo como aprovado somente por um teste de regra ou compilação.
O conjunto já contém 18 casos, superando o planejamento de oito até a aula 4.
Os antigos CT01-CT12 e EXs web na planilha são históricos da implementação anterior.
Os bloqueios de publicação web/assetlinks não se aplicam a esta versão nativa, que não acessa o domínio.

## Referências
- https://developer.android.com/training/sharing/send
- https://developer.android.com/training/data-storage/shared/documents-files
- Guia, planilha e PDF fornecidos pelo professor para a Aula 2.
