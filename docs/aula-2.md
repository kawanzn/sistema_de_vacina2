# Aula 2 - EasyVacc

## Equipe e ambiente
Pedro Marcos Quintanilha de Oliveira Nunes (202312595) e Kawan Lopes Mangia (202312633).
Revisão de 20/10/2026. Testes executados com assistência do Codex em Windows,
Python 3.13, Django 5.2.13, SQLite isolado, Node.js e navegador integrado.
Os dados Unidade TESTE-001 e Rua Fictícia, 10 são fictícios e existem somente no ambiente local.

## Primeiro fluxo
RF01: compartilhar o nome e endereço públicos de um posto pelo menu nativo do aparelho.
Entrada: Portal > Buscar Postos. Pesquisar bairro/CEP, conferir endereço, tocar
Compartilhar endereço e escolher um destino ou cancelar.
Aceite: o menu nativo recebe somente nome e endereço; o retorno informa encaminhamento,
cancelamento/ausência de destino ou falha. Falhar/cancelar preserva a busca e permite nova tentativa.
Resolver a API não é prova de entrega da mensagem ao destinatário.

RF02: buscar postos por CEP ou bairro e anunciar somente lotes vigentes com saldo livre.
RF03: confirmar replay offline somente com HTTP de sucesso e JSON `ok=true`, sem redirecionamento,
usando o token CSRF atual no corpo e cabeçalho.

## Integração e alternativas
O APK analisado é uma Trusted Web Activity: pacote `app.vercel.sistema_vacina.twa`,
versão `1.0.0.0` (versionCode 1), minSdk 23, targetSdk 36, abertura em
`https://sistema-vacina.vercel.app/`. O código Android e a chave de assinatura não estão no repositório.
O arquivo APK original não foi alterado nem recompilado. Sua assinatura criptográfica e sua execução
em dispositivo Android não foram validadas nesta revisão. A inspeção estática usa o `aapt` do SDK.

O fluxo usa Web Share API (`navigator.share`) diretamente no clique em
`vacinas/static/vacinas/share-posto.js`, integrado a `postos.html`.
Não solicita câmera, contatos ou localização. HTTPS, ativação do usuário, suporte e política
do navegador são necessários. `NotAllowedError` apresenta alternativa manual, sem alterar permissões.
`AbortError` informa cancelamento ou falta de destino. API ausente, contexto inseguro ou falha
exibem o endereço selecionável para cópia. Não há gravação de paciente ou alteração de estoque no fluxo.
O professor precisa confirmar se TWA com integração Web Share atende à tecnologia exigida na disciplina.

## Projeto de testes

### T01 - Particionamento de equivalência
As condições de suporte, bloqueio, resposta do servidor e disponibilidade produzem comportamentos distintos. Escolhemos um representante de cada grupo.

Grupos: API presente/ausente; operação permitida/bloqueada; destino disponível/com falha; contexto seguro/inseguro; busca com/sem resultado; replay confirmado/não confirmado; lote livre/reservado/vencido. Casos: CT01, CT02, CT04, CT05, CT08, CT09, CT10, CT12.

### T02 - Transição de estados
Verifica retornos, repetição e recuperação sem perda de dados nem sucesso indevido.

Busca vazia, resultado, compartilhando, encaminhado, cancelado/falha e nova tentativa. Botão bloqueado enquanto compartilha. Pendência antiga passa a envio com CSRF atual. Casos: CT03, CT06, CT07, CT11.

## Casos documentados

### CT01 - Encaminhar endereço ao destino nativo
Requisito: RF01. Técnica: T01. Prioridade: Alta.

Pré-condições: Android com navegador compatível, HTTPS e posto fictício encontrado. Usar destino de teste.

Dados: Unidade TESTE-001; Rua Fictícia, 10; Centro; CEP 28990000.

Passos: 1. Abrir Buscar Postos. 2. Buscar Centro. 3. Tocar Compartilhar endereço. 4. Escolher um destino de teste. 5. Verificar texto e retorno.

Esperado: Menu nativo recebe somente nome e endereço públicos. Ao resolver a API, informar encaminhamento e reabilitar o botão. Não afirmar entrega de mensagem.

### CT02 - Navegador sem Web Share API
Requisito: RF01. Técnica: T01. Prioridade: Alta.

Pré-condições: Posto encontrado; navegador sem navigator.share.

Dados: Unidade TESTE-001.

Passos: 1. Buscar o posto. 2. Tocar Compartilhar endereço. 3. Selecionar o texto da alternativa manual.

Esperado: Mostrar indisponibilidade e endereço selecionável para cópia manual. Não anunciar compartilhamento concluído.

### CT03 - Cancelar e tentar novamente
Requisito: RF01. Técnica: T02. Prioridade: Alta.

Pré-condições: Android compatível; posto encontrado; menu nativo disponível.

Dados: Unidade TESTE-001; primeiro retorno AbortError.

Passos: 1. Tocar Compartilhar endereço. 2. Cancelar o menu nativo. 3. Conferir posto e busca. 4. Tocar novamente. 5. Escolher destino de teste.

Esperado: Cancelamento ou falta de destino é informado sem sucesso indevido. Busca e endereço permanecem. Botão permite nova tentativa.

### CT04 - Recurso bloqueado pela política do navegador
Requisito: RF01. Técnica: T01. Prioridade: Alta.

Pré-condições: Posto encontrado; chamada de compartilhamento retorna NotAllowedError.

Dados: Unidade TESTE-001; retorno controlado NotAllowedError.

Passos: 1. Abrir resultado. 2. Acionar compartilhar no ambiente bloqueado. 3. Conferir mensagem. 4. Conferir alternativa e botão.

Esperado: Mostrar falha, endereço para cópia e botão habilitado. Não alterar dados nem exibir sucesso.

### CT05 - Falha do serviço ou destino
Requisito: RF01. Técnica: T01. Prioridade: Alta.

Pré-condições: Posto encontrado; API disponível; destino retorna falha diferente de cancelamento.

Dados: Falha controlada do destino.

Passos: 1. Buscar posto. 2. Compartilhar. 3. Provocar falha no destino. 4. Conferir recuperação.

Esperado: Mensagem de falha, alternativa manual e possibilidade de repetir. Nome e endereço preservados.

### CT06 - Toques repetidos durante operação
Requisito: RF01. Técnica: T02. Prioridade: Alta.

Pré-condições: Posto encontrado; operação nativa permanece aberta.

Dados: Unidade TESTE-001; promessa de compartilhamento pendente.

Passos: 1. Acionar compartilhar. 2. Tentar acionar outra vez antes do retorno. 3. Encerrar a operação nativa. 4. Verificar botão.

Esperado: Uma chamada ativa por botão. Botão fica desabilitado durante a operação e volta a habilitar após retorno.

### CT07 - Busca por bairro existente
Requisito: RF02. Técnica: T02. Prioridade: Alta.

Pré-condições: Banco de teste com Unidade TESTE-001 no Centro; não usar produção.

Dados: Centro; Rua Fictícia, 10; CEP 28990000.

Passos: 1. Abrir /postos/. 2. Digitar Centro. 3. Tocar Buscar posto. 4. Conferir unidade, endereço e ação de compartilhar.

Esperado: Unidade fictícia aparece com endereço e botão Compartilhar endereço. A busca local por bairro não consulta ViaCEP.

### CT08 - Contexto não seguro
Requisito: RF01. Técnica: T01. Prioridade: Média.

Pré-condições: Ambiente de teste controlado com isSecureContext=false. Não desativar proteções do aparelho.

Dados: Unidade TESTE-001.

Passos: 1. Abrir o resultado no ambiente de teste. 2. Acionar compartilhar. 3. Conferir alternativa.

Esperado: Nenhuma chamada nativa. Mostrar indisponibilidade e endereço para cópia manual.

### CT09 - Bairro inexistente
Requisito: RF02. Técnica: T01. Prioridade: Alta.

Pré-condições: Banco de teste sem o bairro procurado.

Dados: Bairro Inexistente TESTE-999.

Passos: 1. Abrir /postos/. 2. Digitar Bairro Inexistente TESTE-999. 3. Buscar. 4. Conferir mensagem e campo.

Esperado: Nenhuma unidade localizada. Termo permanece no campo para correção. Texto de bairro não é enviado como CEP ao ViaCEP.

### CT10 - Resposta HTML de login ou JSON sem confirmação
Requisito: RF03. Técnica: T01. Prioridade: Alta.

Pré-condições: Teste isolado de replay; resposta simulada de sessão expirada, redirecionamento ou JSON inválido para confirmação.

Dados: Pendência fictícia; HTTP 200 HTML, JSON nulo, {}, ok=false e controle ok=true.

Passos: 1. Preparar pendência fictícia. 2. Simular cada resposta. 3. Executar replay. 4. Conferir resultado retornado.

Esperado: Confirmar somente HTTP de sucesso com JSON ok=true e sem redirecionamento. Nos demais casos retornar falha para preservar pendência.

### CT11 - Renovação do token CSRF
Requisito: RF03. Técnica: T02. Prioridade: Alta.

Pré-condições: Formulário antigo na fila e cookie CSRF atual; ambiente isolado.

Dados: Token antigo no corpo e novo-token-ficticio no cookie.

Passos: 1. Preparar formulário com token antigo. 2. Definir cookie atual. 3. Executar replay. 4. Inspecionar corpo e cabeçalho.

Esperado: Usar o token atual uma única vez no corpo e no cabeçalho. Preservar os demais campos.

### CT12 - Doses vencidas ou totalmente reservadas
Requisito: RF02. Técnica: T01. Prioridade: Alta.

Pré-condições: Banco isolado com um lote livre, um vencido e um totalmente reservado.

Dados: Três lotes fictícios; validade ontem/amanhã; quantidade 1; reserva 0/1.

Passos: 1. Cadastrar lotes fictícios no teste. 2. Buscar CEP 28990000. 3. Conferir vacinas anunciadas.

Esperado: A busca anuncia somente lote com validade vigente e quantidade atual maior que a reservada.

## Planilha
A documentação principal e o registro das execuções estão na [planilha do projeto](https://docs.google.com/spreadsheets/d/1iwQcQ8WCWBVvBLuUQP2BSq9GRRGqWQ0tUndov4G_VlU/edit).

## Execuções e evidências
- EX00A, EX00B e EX00C: regressões reproduzidas na cópia do código anterior (2d79469 local,
  mesmo código web do e56ad2f publicado), [log antes](evidencias/regressao-servidor-antes.txt).
  Sete testes executados, três falhas. O teste de compartilhamento foi copiado somente como harness.
- EX00D e EX00E: [replay anterior](evidencias/regressao-offline-antes.txt), quatro verificações,
  três falhas e um controle positivo. A implementação nova de share foi copiada à pasta isolada
  somente para carregar o harness; os testes selecionados cobrem exclusivamente o offline antigo.
- EX01/CT07: busca manual por Centro, [print](evidencias/EX01_CT07_busca_bairro.png).
- EX02/CT09: busca manual sem resultado, [print](evidencias/EX02_CT09_busca_sem_resultado.png).
- EX03-EX09 e EX12-EX13: [11 verificações JavaScript aprovadas](evidencias/testes-javascript.txt).
  São testes com dublês de DOM e API, não execuções Android.
- EX10, EX11 e EX14: [7 testes Django aprovados](evidencias/testes-servidor.txt).
  Incluem CEP formatado, falha ViaCEP, escape de HTML e disponibilidade de lotes.
- EX15/CT01: bloqueado para Android. O SDK está presente, mas o adb não conseguiu acessar
  a pasta de credenciais do usuário. Nenhum dispositivo Android foi controlado.

## Correções e reexecuções
DF01: busca por bairro, validada em EX10. DF02: consulta ViaCEP somente com oito dígitos,
validada em EX11. DF03: filtro de validade/saldo livre na listagem de postos, validado em EX14.
DF04: replay exige confirmação positiva, validado em EX12. DF05: atualização de CSRF,
validada em EX13. Ver planilha para passos e histórico completo.
Também foi removida uma declaração `Meta` duplicada em VacinaForm, que sobrescrevia os widgets,
e a falha ao gravar a fila no IndexedDB agora informa erro sem limpar o formulário.
Essas duas últimas alterações foram revisadas no código, sem casos dedicados de execução.

## Reproduzir verificações
```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe manage.py test --verbosity 2
node --test scripts/test_native_flow.cjs
```
Use banco de teste separado. `manage.py test` cria o banco de teste temporário.
Os testes JavaScript usam somente módulos nativos do Node.js e dados fictícios.
O arquivo [versao-testada.json](evidencias/versao-testada.json) identifica por SHA-256 os arquivos
executados. Nenhum resultado local comprova, sozinho, funcionamento no domínio publicado ou no APK.

## Evolução e pendências
- APK inicial: [e56ad2f](https://github.com/kawanzn/sistema_de_vacina2/commit/e56ad2f253d5b38340b3f09dc88fd51a844a7ce3).
- Código, planilha e evidências da revisão Aula 2: [histórico](https://github.com/kawanzn/sistema_de_vacina2/commits/main/).
- Até a aula 4 já estão planejados 12 casos. Executar CT01-CT06 em Android compatível,
  registrar versão do SO/navegador, prints e vídeo do menu nativo, cancelamento e falha.
- Confirmar publicação do código web no domínio aberto pelo APK. Commit no GitHub não comprova deploy.
- Obter projeto Android original e assinatura para gerar uma atualização instalável compatível;
  não substituir o APK por um binário reempacotado ou assinado com outra chave.
- A revisão cobre os fluxos descritos e não certifica todo o sistema de vacinação.

## Referências técnicas
- [Web Share API](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share)
- [Trusted Web Activity](https://developer.chrome.com/docs/android/trusted-web-activity/integration-guide)
- Materiais da disciplina: guia Aula 2 fornecido pelo professor, planilha e PDF recebidos em 20/10/2026.

## Verificação da publicação em 20/10/2026
A consulta HTTP pública retornou 200 para a página inicial, manifest e service worker.
O novo /static/vacinas/share-posto.js retornou 404: o domínio ainda não publicou os arquivos desta revisão.
/.well-known/assetlinks.json também retornou 404. Foi aberto DF06 (origem EX16, CT01 bloqueado)
para configurar e validar a associação entre o domínio e o pacote/certificado do APK.
A validação do certificado e do vínculo precisa ser concluída no projeto Android original e em dispositivo compatível.
Essas pendências não foram marcadas como corrigidas. Consulte [a evidência HTTP](evidencias/verificacao-publicacao.txt).
