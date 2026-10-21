# EasyVacc Android

[Baixar o APK Android 2.1.0](releases/EasyVacc-Android-2.1.0.apk).

Telas nativas para Servidor Público e Portal do Cidadão, cadastros, caderneta, estoque,
aplicações, agenda, relatórios, assistente de uso e backup. Funciona com dados locais sem internet.
A versão 2.1.0 acrescenta a integração HTTPS para compartilhar o banco do site entre aparelhos.

Instale por cima da versão Android 2.0.0 para preservar os dados. O pacote e a assinatura permanecem
os mesmos. O arquivo `EasyVacc.apk` da raiz também corresponde à versão 2.1.0.

**Ativação da sincronização:** o serviço que atende o domínio precisa publicar esta revisão,
usar banco persistente, configurar SECRET_KEY segura e executar as migrações. O endereço atual
ainda não confirmou a integração. Sem ela, as tentativas ficam pendentes e os dados locais
continuam disponíveis. No APK, configure Mais > Sincronização com uma conta profissional do site.

- [Instalação, funções e limites do APK](android-native/README.md)
- [Publicação da integração no servidor](docs/sincronizacao-android.md)
- [Plano de testes adicional](android-native/docs/plano-2.1.md)
- [Evidências de validação](android-native/docs/evidencias/)

Compilação e assinatura verificadas. Testes de regras JVM e API Django foram executados localmente.
Ainda falta validar interface e sincronização completa em Android físico/emulador e no servidor
publicado. A pasta `android-native` contém o projeto Android; `core` e `vacinas` contêm o servidor
compartilhado com a versão web. Nenhuma tela do APK é WebView.
