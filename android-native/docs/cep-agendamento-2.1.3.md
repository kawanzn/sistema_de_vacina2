# CEP residencial e abertura do agendamento — 2.1.3

## Problemas corrigidos

A busca numérica comparava exclusivamente o CEP com o endereço cadastrado do posto. Um CEP
residencial válido não encontrava unidades da sua região. Agora o CEP completo é resolvido
para bairro/cidade, e são exibidas as unidades da cidade, priorizando as que citam o bairro.
Isso não calcula distâncias nem identifica a unidade de referência territorial.

O CEP informado pelo usuário, **28994-160**, foi consultado em ViaCEP e BrasilAPI: Rua Angélica,
Condado de Bacaxá (Bacaxá), Saquarema/RJ. Essa região está incluída para consulta offline desde
a instalação. Outros CEPs exigem internet na primeira consulta e ficam salvos depois. Consulta
online automática ocorre apenas após completar os oito dígitos e aguardar 500 ms. Falhas da
consulta preservam os dados anteriores e mostram orientação para pesquisar pelo bairro.

O botão Agendar nova dose redirecionava diretamente à identificação do cidadão. Agora ele
abre a própria tela de agendamento, com ações para identificar a pessoa, consultar unidades
ou configurar os cadastros profissionais. Quando faltam pacientes ou doses livres, o motivo
e as ações apropriadas aparecem separadamente. Uma identificação removida por restauração
ou sincronização é limpa antes de renderizar, evitando consulta a um registro inexistente.
O destino correto também é preservado na identificação de agenda, histórico e caderneta.

Reservar uma dose continua exigindo pessoa, unidade e lote real com saldo disponível no banco
profissional do EasyVacc. O diretório municipal contém endereços; ele não constitui integração
com a agenda ou estoque oficial da Prefeitura. Não são criados lotes ou horários municipais
fictícios para liberar o botão. A confirmação local e a sincronização mantêm as regras de
reserva, validade e idempotência existentes.

## Verificações

- 81 verificações JVM aprovadas, incluindo reconhecimento de CEP com/sem máscara, bloqueio de
  consulta incompleta e prioridade de Bacaxá para o CEP residencial informado.
- Consulta real do CEP nas duas fontes HTTPS, concordando em cidade e bairro.
- Compilação do APK e testes instrumentados; mesma chave de assinatura das versões anteriores.
- Casos Android adicionados para abrir o agendamento pelo botão do portal e listar os postos
  pelo CEP 28994160 sem rede. **Esses casos foram compilados, mas não executados**, pois nenhum
  dispositivo Android foi detectado. Não representam evidência de navegação em celular.
- O servidor não foi alterado nesta versão; os resultados anteriores de 27 testes Django
  permanecem como evidência da versão 2.1.2.

Fontes: [ViaCEP](https://viacep.com.br/),
[consulta do CEP](https://viacep.com.br/ws/28994160/json/),
[BrasilAPI](https://brasilapi.com.br/api/cep/v1/28994160).
