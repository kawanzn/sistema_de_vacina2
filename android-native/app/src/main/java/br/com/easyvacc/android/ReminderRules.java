package br.com.easyvacc.android;

/** Pure rules shared with executable JVM tests. */
public final class ReminderRules {
    private ReminderRules(){}
    public static void validate(String title,String message,long when,long now){
        Rules.required(title,"título do lembrete",80);
        Rules.required(message,"mensagem do lembrete",500);
        if(when<=now)throw new IllegalArgumentException("Escolha uma data e um horário futuros.");
    }
    public static boolean deliverable(String status,long when,long now){return "Agendado".equals(status)&&when<=now;}
}
