# EasyVacc Android

[Baixar o APK Android 2.1.1](releases/EasyVacc-Android-2.1.1.apk).

Telas nativas para Servidor Público e Portal do Cidadão, cadastros, caderneta, estoque,
aplicações, agenda, relatórios, assistente de uso e backup. Funciona com dados locais sem internet.
A versão 2.1.1 acrescenta a integração HTTPS para compartilhar o banco do site entre aparelhos.

Instale por cima da versão Android 2.0.0 para preservar os dados. O pacote e a assinatura permanecem
os mesmos. O arquivo `EasyVacc.apk` da raiz também corresponde à versão 2.1.1.

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
