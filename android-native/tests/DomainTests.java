package br.com.easyvacc.android;

/** Run using a regular JDK. These tests do not pretend to be device executions. */
public final class DomainTests {
    private static int passed;
    interface Check {void run() throws Exception;}
    private static void test(String name,Check check)throws Exception {check.run();passed++;System.out.println("APROVADO "+name);}
    private static void eq(Object a,Object b){if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    private static void rejects(Check check){try{check.run();}catch(Exception expected){return;}throw new AssertionError("Era esperada rejeição.");}
    public static void main(String[] args)throws Exception {
        test("Busca ignora acentos",()->eq(Rules.matches("sao", "São Pedro"),true));
        test("Busca por CPF formatado",()->eq(Rules.matches("111.444.777-35", "11144477735"),true));
        test("Busca por nome com dígitos não vira CPF",()->eq(Rules.matches("lote 10", "10"),false));
        test("Busca sem resultado",()->eq(Rules.matches("teste", "Outro nome"),false));
        test("Busca vazia mostra registros",()->eq(Rules.matches("", "Unidade"),true));
        test("Estoque vencido prevalece sobre saldo",()->eq(Rules.stockStatus(10,0,"2026-10-19","2026-10-20"),"Vencido"));
        test("Estoque totalmente reservado",()->eq(Rules.stockStatus(2,2,"2026-10-21","2026-10-20"),"Reservado"));
        test("Estoque esgotado",()->eq(Rules.stockStatus(0,0,"2026-10-21","2026-10-20"),"Esgotado"));
        test("Estoque vigente disponível",()->eq(Rules.stockStatus(2,1,"2026-10-20","2026-10-20"),"Disponível"));
        test("Não comparecimento tem descrição legível",()->eq(Rules.statusLabel("ausente"),"Não compareceu"));
        test("CTN01 - prévia contém apenas endereço público",()->{ShareFlow f=new ShareFlow();String s=Rules.shareText("Unidade TESTE-001","Rua Fictícia, 10","Centro","28990000");f.preview(s);eq(f.state(),ShareFlow.State.PREVIEW);eq(f.text(),"Unidade TESTE-001\nRua Fictícia, 10\nCentro - CEP 28990000");});
        test("CTN02 - cancelar preserva texto",()->{ShareFlow f=new ShareFlow();f.preview("Endereço fictício");f.cancel();eq(f.state(),ShareFlow.State.CANCELLED);eq(f.text(),"Endereço fictício");});
        test("CTN03 - cancelar seletor não significa sucesso",()->{ShareFlow f=new ShareFlow();f.preview("Endereço");f.launch();f.cancel();eq(f.state(),ShareFlow.State.CANCELLED);});
        test("CTN04 - seleção do destino distingue entrega",()->{ShareFlow f=new ShareFlow();f.preview("Endereço");f.launch();f.selected();eq(f.state(),ShareFlow.State.SELECTED);});
        test("CTN05 - falha permite recuperação",()->{ShareFlow f=new ShareFlow();f.preview("Endereço");f.launch();f.fail();eq(f.text(),"Endereço");f.launch();eq(f.state(),ShareFlow.State.CHOOSER);});
        test("CTN06 - bloqueia lançamento duplicado",()->{ShareFlow f=new ShareFlow();f.preview("Endereço");f.launch();rejects(()->f.launch());});
        test("CTN07 - preserva estado após recriação",()->{ShareFlow a=new ShareFlow();a.preview("Rua Fictícia");a.launch();ShareFlow b=new ShareFlow();b.restore(a.text(),a.state().name());eq(b.text(),a.text());eq(b.state(),ShareFlow.State.CHOOSER);});
        test("CTN08 - quantidade zero não é dose disponível",()->eq(Rules.available(0,0,"2030-01-01","2026-10-20"),false));
        test("CTN08 - reserva total bloqueia dose livre",()->eq(Rules.available(1,1,"2030-01-01","2026-10-20"),false));
        test("CTN08 - lote livre permite dose",()->eq(Rules.available(2,1,"2030-01-01","2026-10-20"),true));
        test("CTN09 - validade ontem bloqueia",()->eq(Rules.available(1,0,"2026-10-19","2026-10-20"),false));
        test("CTN09 - validade hoje é aceita",()->eq(Rules.available(1,0,"2026-10-20","2026-10-20"),true));
        test("CTN09 - validade amanhã é aceita",()->eq(Rules.available(1,0,"2026-10-21","2026-10-20"),true));
        test("CTN10 - agendamento passado bloqueado",()->rejects(()->Rules.appointment(999,1000,"2030-01-01","2026-10-20")));
        test("CTN10 - horário igual ao atual bloqueado",()->rejects(()->Rules.appointment(1000,1000,"2030-01-01","2026-10-20")));
        test("CTN10 - horário futuro aceito",()->Rules.appointment(1001,1000,"2030-01-01","2026-10-20"));
        test("CTN10 - lote vence antes do atendimento",()->rejects(()->Rules.appointment(1001,1000,"2026-10-19","2026-10-20")));
        test("CTN11 - CPF normalizado e validado",()->eq(Rules.cpf("111.444.777-35"),"11144477735"));
        test("CTN11 - CPF repetido rejeitado",()->rejects(()->Rules.cpf("11111111111")));
        test("CTN11 - CPF com dígito incorreto rejeitado",()->rejects(()->Rules.cpf("11144477736")));
        test("CTN11 - CPF ausente permitido",()->eq(Rules.cpf(""),""));
        test("CTN12 - data impossível rejeitada",()->rejects(()->Rules.date("31/02/2026",false)));
        test("CTN12 - ano bissexto válido",()->eq(Rules.iso("29/02/2024"),"2024-02-29"));
        test("CTN12 - dados após data rejeitados",()->rejects(()->Rules.date("01/01/2026 xyz",false)));
        test("CTN13 - quantidade negativa rejeitada",()->rejects(()->Rules.quantity("-1",true)));
        test("CTN13 - quantidade fracionária rejeitada",()->rejects(()->Rules.quantity("1.5",true)));
        test("CTN13 - limite superior aceito",()->eq(Rules.quantity("1000000",true),1000000));
        test("CTN13 - acima do limite rejeitado",()->rejects(()->Rules.quantity("1000001",true)));
        test("CTN14 - CSV impede fórmula",()->eq(Rules.csv("=2+2"),"\"'=2+2\""));
        test("CTN14 - CSV protege fórmula com quebra de linha",()->eq(Rules.csv("  =\n2+2"),"\"'  =\n2+2\""));
        test("CTN14 - CSV escapa aspas",()->eq(Rules.csv("Lote \"A\""),"\"Lote \"\"A\"\"\""));
        test("CTN15 - senha não é armazenada em texto",()->{String hash=Secrets.hash("Ficticia-2026");eq(hash.contains("Ficticia"),false);eq(Secrets.matches("Ficticia-2026",hash),true);eq(Secrets.matches("Errada",hash),false);});
        test("CTN15 - hashes usam sal único",()->eq(Secrets.hash("Ficticia-2026").equals(Secrets.hash("Ficticia-2026")),false));
        test("CTN15 - senha curta rejeitada",()->rejects(()->Secrets.hash("123")));
        test("CTN16 - busca normaliza acentos",()->eq(Rules.key("  São   João "),"sao joao"));
        test("Busca de posto normaliza acento",()->eq(Rules.matchesPost("bacaxa","ESF Bacaxá","Bacaxá","Rua Alfredo Menezes, 980","","Saquarema"),true));
        test("Busca combina bairro e cidade",()->eq(Rules.matchesPost("Saquarema RJ Jacone","ESF Jaconé","Jaconé","Rua 97","","Saquarema"),true));
        test("Busca aceita posto de saúde e UBS como termos genéricos",()->eq(Rules.matchesPost("posto de saude bacaxa","ESF Bacaxá","Bacaxá","Rua Alfredo","","Saquarema"),true));
        test("Busca ignora ordem dos termos",()->eq(Rules.matchesPost("980 Alfredo","ESF Bacaxá","Bacaxá","Rua Alfredo Menezes, 980","","Saquarema"),true));
        test("Busca Rio d'Areia normaliza apóstrofo",()->eq(Rules.matchesPost("rio dareia","ESF Rio D’Areia","Rio D’Areia","Estrada Latino Melo","","Saquarema"),true));
        test("Busca CEP com máscara",()->eq(Rules.matchesPost("28990-000","Posto","Centro","Rua","28990000",""),true));
        test("CEP desconhecido não inventa resultado",()->eq(Rules.matchesPost("28990000","ESF Bacaxá","Bacaxá","Rua","","Saquarema"),false));
        test("Bairro desconhecido não retorna toda a cidade",()->eq(Rules.matchesPost("inexistente","ESF Bacaxá","Bacaxá","Rua","","Saquarema"),false));
        test("Diretório cobre 21 unidades",()->eq(SaquaremaDirectory.UNITS.length,21));
        for(String[] unit:SaquaremaDirectory.UNITS){final String[] u=unit;
            test("Unidade municipal pesquisável offline: "+u[0],()->{eq(Rules.matchesPost(u[1],u[0],u[1],u[2],u[4],u[5]),true);eq(Rules.matchesPost("Saquarema",u[0],u[1],u[2],u[4],u[5]),true);});
        }
        System.out.println("TOTAL: "+passed+" verificações aprovadas; 0 falhas. Ambiente: JVM; não é execução Android.");
    }
}
