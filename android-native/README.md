# EasyVacc Android 2.0.0

Aplicativo Android nativo, em Java, com telas do Android e banco SQLite privado no aparelho.
Não usa WebView, Trusted Web Activity, site, APIs remotas ou permissão INTERNET.

## Instalar e começar
1. Baixe `EasyVacc-Android-2.0.0.apk` e abra o arquivo no Android 6 ou superior.
2. Quando o Android solicitar, autorize a instalação para o aplicativo que abriu o arquivo.
3. Abra EasyVacc. Em Acesso profissional, crie seu nome de usuário e senha de pelo menos 8 caracteres.
4. Cadastre uma unidade, um lote e uma pessoa. Depois registre uma aplicação ou um agendamento.
5. Para demonstrar sem dados reais: no painel profissional, abra Mais > Carregar demonstração fictícia.
   A opção aparece somente com pessoas e postos vazios.

O pacote novo é `br.com.easyvacc.android`, versão 2.0.0, versionCode 20000, minSdk 23 e targetSdk 35.
Ele pode coexistir com o aplicativo antigo, porque tem identificador e assinatura próprios.
A nova assinatura é necessária porque a chave original não foi fornecida.

## Funções implementadas
- Configuração do acesso profissional, login, logout e alteração de senha; hash PBKDF2 com sal aleatório.
- Cadastro e edição de pessoas com CPF, SUS, nascimento, endereço, telefone, e-mail e responsável.
- Caderneta individual e acesso aos dependentes vinculados.
- Cadastro de unidades e busca local por nome, bairro ou CEP, sem diferenciar acentos.
- Lotes, validade, quantidade total, reservas, saldo livre e entrada de doses.
- Aplicação com confirmação e baixa de estoque em transação SQLite.
- Agendamento com reserva; confirmação, cancelamento justificado, ausência e histórico.
- Compartilhamento nativo de endereço com prévia, cancelamento, falha e seleção do destino.
- Relatório CSV com seletor de arquivo do Android.
- Backup JSON e restauração validada em transação, preservando o acesso profissional.
- Assistente local e perguntas frequentes sobre o uso do aplicativo. Não usa IA remota nem calcula conduta médica.

## Dados e funcionamento local
Os cadastros e históricos ficam neste aparelho. O novo APK começa sem os registros do site antigo,
porque o APK recebido era apenas um cliente TWA e não continha aquele banco de dados.
Não há sincronização entre aparelhos. Para transferir os registros desta versão, use Salvar backup
e Restaurar backup. O arquivo de backup inclui dados pessoais; escolha um local privado.
A restauração pede confirmação antes de substituir cadastros, mantém a conta profissional e desfaz
a operação se encontrar vínculos, quantidade ou formato inconsistentes.
O acesso profissional é uma conta local por instalação. Guarde sua senha.

## Primeiro fluxo e atividade
RFN01: compartilhar somente o nome e endereço públicos de uma unidade pelo menu nativo do Android.
Aceite: conferir a prévia, abrir o menu após toque e distinguir destino selecionado de cancelamento/falha.
Selecionar um aplicativo não comprova que uma mensagem foi enviada ou entregue.
Não há solicitação de câmera, localização, contatos ou armazenamento amplo neste fluxo.
Os exemplos de câmera da atividade foram adaptados ao recurso nativo de compartilhamento.
Para arquivos, o Storage Access Framework concede acesso somente ao arquivo escolhido pelo usuário.

RFN02: estoque e aplicações com saldo livre e validade. RFN03: agendamento futuro e reservas.
RFN04: pessoas, caderneta e busca local. RFN05: exportação. RFN06: autenticação profissional.
Casos CTN01-CTN18 e técnicas TN01-TN03 estão em `docs/aula-2-android.md` e na planilha do projeto.
O histórico da antiga versão web permanece identificado como histórico, sem ser contado como teste Android.

## O que foi verificado
- Testes JVM: 35 verificações de regras e estados aprovadas; não executam a interface nem o SQLite Android.
- Compilação Java, geração de DEX, empacotamento, alinhamento e assinatura do APK.
- Verificação da assinatura v1, v2 e v3 e inspeção do manifesto/pacote.
- Inspeção do DEX para confirmar ausência de referência WebView/TWA e do endereço do site antigo.
- Um runner de instrumentação cobre banco, reservas, backup e abertura da tela, usando banco de teste separado.

Ainda não houve execução em Android físico ou emulador. O adb do ambiente falhou ao criar sua pasta
de credenciais e não foi possível obter um ambiente Android acessível. Portanto, navegação, renderização,
teclado, seletor de arquivos e menu de compartilhamento continuam pendentes de validação no aparelho.
Os logs e hashes ficam em `docs/evidencias/`. Compilar e assinar não prova funcionamento completo em dispositivo.

## Compilar novamente
Opção Android Studio: abra esta pasta, use JDK 17, Gradle 8.9 e Android SDK 35. O plugin configurado é 8.7.3.
O projeto não precisa de bibliotecas externas na aplicação. O Android Studio pode baixar o plugin de compilação.

Opção independente de Gradle (PowerShell, JDK 17 e SDK Android com Build Tools 36):
```powershell
.\test.ps1 -JavaHome 'C:\caminho\jdk-17'
.\build.ps1 -JavaHome 'C:\caminho\jdk-17' -AndroidSdk 'C:\caminho\Sdk' `
  -SigningStore 'C:\privado\easyvacc-native.p12' `
  -SigningPasswordFile 'C:\privado\password.txt' -Platform android-35
```
A compilação desta entrega usou o android.jar 37.0 disponível no ambiente, com targetSdk 35 e minSdk 23.
O arquivo `build.ps1` também aceita `-BuildDeviceTests` para gerar o APK de instrumentação com a mesma assinatura.
Para executar os testes de dispositivo em aparelho de teste conectado:
```text
adb install -r EasyVacc-Android-2.0.0.apk
adb install -r EasyVacc-Android-Testes.apk
adb shell am instrument -w br.com.easyvacc.android.tests/br.com.easyvacc.android.NativeInstrumentation
```
O runner usa `instrumentation_test.db`, sem substituir o banco dos registros do aplicativo.

## Assinatura e próximas versões
A chave e a senha foram entregues separadamente em um arquivo privado. Não publique esses arquivos no GitHub.
Use a mesma chave e aumente versionCode para atualizar esta versão sem perder seus dados.

## Equipe
Pedro Marcos Quintanilha de Oliveira Nunes - 202312595.
Kawan Lopes Mangia - 202312633.
Material: Laboratório de Desenvolvimento de Aplicativos Nativos, Aula 2, prof. Lucas Hermenegildo do Nascimento.
Data da entrega: 20/10/2026.
