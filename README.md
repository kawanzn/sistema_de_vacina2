# EasyVacc Android

[Baixar o APK Android 2.1.4](releases/EasyVacc-Android-2.1.4.apk).

Telas nativas para Servidor Público e Portal do Cidadão, cadastros, caderneta, estoque,
aplicações, agenda, relatórios, assistente de uso e backup. Funciona com dados locais sem internet.
A versão 2.1.4 acrescenta a integração HTTPS para compartilhar o banco do site entre aparelhos.

Instale por cima da versão Android 2.0.0 para preservar os dados. O pacote e a assinatura permanecem
os mesmos. O arquivo `EasyVacc.apk` da raiz também corresponde à versão 2.1.4.

**Busca de postos corrigida:** diretório de 21 unidades de Saquarema incluído no APK,
sem login e disponível offline. Pesquisa por nome, bairro, rua e cidade; atualização pública
pela internet. Consulte [fontes e limites](android-native/docs/postos-saquarema-2.1.2.md).
Não há estoque municipal fictício; confirme a vacina com a unidade.

**Sincronização:** servidor publicado em https://easyvacc-android-servidor.vercel.app, com
banco persistente Neon gratuito. Configure Mais > Sincronização com as credenciais entregues
privadamente. O acesso local do APK e a conta do servidor são separados. O novo banco começa
sem os cadastros antigos; eles precisam de backup ou importação.

- [Instalação, funções e limites do APK](android-native/README.md)
- [Publicação da integração no servidor](docs/sincronizacao-android.md)
- [Plano de testes adicional](android-native/docs/plano-2.1.md)
- [Evidências de validação](android-native/docs/evidencias/)

Compilação e assinatura verificadas. Testes de regras JVM e API Django foram executados localmente.
O servidor publicado passou em 16 verificações HTTPS. Ainda falta validar a interface e o
retorno à conexão em Android físico/emulador; nenhum dispositivo foi detectado nesta entrega. A pasta `android-native` contém o projeto Android; `core` e `vacinas` contêm o servidor
compartilhado com a versão web. Nenhuma tela do APK é WebView.

## CEP e agendamento na versão 2.1.4

O CEP residencial localiza a cidade e prioriza postos que citam o bairro. O CEP 28994-160
(Condado de Bacaxá, Saquarema) funciona offline desde a instalação; outros CEPs ficam salvos
após a primeira consulta com internet. O botão de agendamento abre sua própria tela e explica
os cadastros necessários. Confirmar reserva exige paciente e estoque real no EasyVacc; a lista
pública de unidades não informa a agenda ou o estoque oficial da Prefeitura.

Detalhes e limites: [CEP e agendamento](android-native/docs/cep-agendamento-2.1.3.md).

## Selecionar a unidade e agendar — 2.1.4

O botão Ligar para a unidade foi removido. Cada posto tem Selecionar e agendar. O nome e
o endereço escolhidos aparecem no agendamento e a seleção é preservada durante a identificação
e a recriação da tela. Trocar unidade retorna à busca. Quando existe cadastro profissional
correspondente, a unidade fica pré-selecionada e somente seus lotes podem ser reservados.
Sem estoque registrado naquela unidade, o aplicativo informa o impedimento e não usa doses
de outro posto. A reserva continua dependendo de paciente e estoque real no EasyVacc, sem
integração com a agenda oficial municipal. Compilação e assinatura verificadas; execução
da interface em Android continua pendente.
