# Busca de postos — Android 2.1.2

A busca anterior consultava exclusivamente a tabela local `posts`. Uma instalação nova ou
sem sincronização retornava uma lista vazia. O novo servidor também não continha os cadastros
da hospedagem anterior.

## Correção

- Diretório público de 21 unidades de Saquarema incluído no APK: disponível na primeira
  abertura, sem internet e sem configurar conta profissional.
- Consulta por nome, bairro, rua, cidade ou CEP conhecido, normalizando acentos, pontuação,
  termos como UBS/ESF/posto de saúde e a ordem das palavras. Filtragem ao digitar e pelo teclado.
- Contagem de resultados, botão para mostrar todas as unidades, ligação pelo discador e
  compartilhamento nativo de endereço. O aplicativo não solicita permissão de chamada.
- Atualização anônima em `/api/android/posts/`, com cache separado por servidor. Falhas de
  rede preservam o diretório incluído e a última resposta válida. O catálogo não contém
  pacientes, prontuários, doses pessoais, credenciais ou números de lote.
- Diretório público e cadastros clínicos permanecem separados: consultar postos não insere
  unidades, lotes ou doses fictícias no banco profissional e não interfere na fila de sync.
- Unidades profissionais de mesmo nome são combinadas com o diretório, com indicação de
  que o endereço vem do cadastro profissional. Não se publica uma edição como endereço oficial.
- Ausência de estoque registrado é apresentada como disponibilidade não informada. Nenhuma
  quantidade municipal foi inventada. O catálogo não habilita reservas sem estoque profissional.

## Fontes públicas consultadas em 22/10/2026

- [Portal da Transparência — unidades de saúde](https://transparencia.saquarema.rj.gov.br/informacoes-da-saude/unidades-de-saude/), página com atualização indicada em 15/10/2026.
- [Prefeitura — campanha de vacinação de julho de 2026](https://www.saquarema.rj.gov.br/prefeitura-amplia-campanha-de-vacinacao-contra-a-influenza-para-toda-a-populacao-a-partir-de-6-meses-de-idade/), endereço de Boqueirão.
- [Prefeitura — rede de vacinação de agosto de 2026](https://www.saquarema.rj.gov.br/prefeitura-convoca-populacao-para-atualizar-a-caderneta-nacional-de-vacinacao/), cobertura de bairros.
- [Prefeitura — UBS Engenho Grande](https://www.saquarema.rj.gov.br/prefeitura-realizara-o-dia-d-de-multivacinacao/), endereço publicado em 2023.

A página de transparência respondeu 503 na consulta direta; os dados puderam ser conferidos
na cópia indexada da mesma página. Fontes municipais divergem em alguns endereços. A versão
prioriza o diretório com atualização mais recente e orienta confirmar com a unidade. Em Jaconé,
a página municipal indica vacinação no P.U.; essa observação está no cartão do posto.
CEPs ausentes não são preenchidos com o CEP genérico da cidade. Procure pelo bairro nesses casos.
Horários e doses disponíveis devem ser confirmados com a equipe de saúde.

## Verificação

Os testes JVM incluem busca por acentos, combinação de cidade/bairro, termos em qualquer ordem,
CEP formatado, CEP desconhecido, resultado vazio e cada uma das 21 unidades offline. Testes
Django cobrem catálogo sem login em banco vazio, exposição apenas de dados públicos,
deduplicação e exclusão de estoques vencidos ou sem saldo livre. Há também a suíte anterior
de cadastro, autenticação, sincronização e conflitos de estoque.

A compilação e a assinatura foram verificadas. Execução da interface em Android real continua
pendente: nenhum aparelho foi detectado. Testes JVM e HTTP não substituem essa execução.
