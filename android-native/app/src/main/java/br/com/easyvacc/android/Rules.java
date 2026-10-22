package br.com.easyvacc.android;

import java.text.Normalizer;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared pure-Java domain rules, also executed by the JVM regression tests. */
public final class Rules {
    private Rules() {}
    public static String text(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    public static String digits(String value) { return text(value).replaceAll("[^0-9]", ""); }
    public static String key(String value) {
        return Normalizer.normalize(text(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    public static String postKey(String value) {
        return key(value).replaceAll("[^a-z0-9]+", " ").trim();
    }
    public static String postIdentity(String name) {
        return postKey(name).replaceAll("^(esf|psf|ubs) +", "").replace("correa", "correia");
    }
    public static boolean matchesPost(String query,String name,String district,String address,String zip,String city) {
        String q=postKey(query), all=postKey(name+" "+district+" "+address+" "+city+" RJ");
        if(q.isEmpty())return true;
        // Numeric queries are CEPs only when they contain five or more digits.
        if(q.matches("[0-9 ]+")&&digits(q).length()>=5)return !digits(zip).isEmpty()&&digits(zip).startsWith(digits(q));
        all=all.replace("d areia","dareia").replace("correa","correia");
        q=q.replace("d areia","dareia").replace("correa","correia");
        for(String word:q.split(" +")) {
            if(word.matches("posto|postos|unidade|unidades|saude|esf|psf|ubs|de|da|do|das|dos|em"))continue;
            if(!all.contains(word))return false;
        }
        return true;
    }
    public static String required(String value, String label, int max) {
        String v = text(value);
        if (v.isEmpty()) throw new IllegalArgumentException("Preencha " + label + ".");
        if (v.length() > max) throw new IllegalArgumentException(label + ": máximo de " + max + " caracteres.");
        return v;
    }
    public static int quantity(String value, boolean allowZero) {
        try {
            int n = Integer.parseInt(text(value));
            if (n < (allowZero ? 0 : 1) || n > 1000000) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Informe uma quantidade inteira entre " + (allowZero ? "0" : "1") + " e 1.000.000."); }
    }
    public static Date date(String value, boolean withTime) {
        String v = text(value);
        SimpleDateFormat f = new SimpleDateFormat(withTime ? "dd/MM/yyyy HH:mm" : "dd/MM/yyyy", Locale.ROOT);
        f.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date result = f.parse(v, position);
        if (result == null || position.getIndex() != v.length() || !f.format(result).equals(v))
            throw new IllegalArgumentException(withTime ? "Use data e hora válidas: dd/mm/aaaa hh:mm." : "Use uma data válida: dd/mm/aaaa.");
        return result;
    }
    public static String iso(String value) { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date(value, false)); }
    public static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    public static String showDate(String iso) {
        if (iso == null || iso.length() != 10) return "";
        return iso.substring(8,10) + "/" + iso.substring(5,7) + "/" + iso.substring(0,4);
    }
    public static String cpf(String value) {
        String v = digits(value);
        if (v.isEmpty()) return "";
        if (v.length() != 11 || v.matches("([0-9])\\1{10}")) throw new IllegalArgumentException("CPF inválido. Confira os 11 dígitos.");
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int i = 0; i < size; i++) sum += (v.charAt(i)-'0') * (size + 1 - i);
            int check = (sum * 10) % 11; if (check == 10) check = 0;
            if (v.charAt(size)-'0' != check) throw new IllegalArgumentException("CPF inválido. Confira os dígitos verificadores.");
        }
        return v;
    }
    public static boolean available(int quantity, int reserved, String expiry, String date) {
        return quantity >= 0 && reserved >= 0 && quantity > reserved && expiry != null && expiry.compareTo(date) >= 0;
    }
    public static boolean matches(String query,String... fields) {
        String q=key(query);if(q.isEmpty())return true;
        StringBuilder all=new StringBuilder();for(String field:fields)all.append(' ').append(key(field));
        if(all.toString().contains(q))return true;
        String number=digits(query);
        if(!number.isEmpty()&&q.matches("[0-9 .()/+-]+"))for(String field:fields)if(digits(field).contains(number))return true;
        return false;
    }
    public static String stockStatus(long qty,long reserved,String expiry,String today) {
        if(expiry.compareTo(today)<0)return "Vencido";
        if(qty==0)return "Esgotado";
        if(qty<=reserved)return "Reservado";
        return "Disponível";
    }
    public static String statusLabel(String value){
        if("pendente".equals(value))return "Pendente";
        if("concluido".equals(value))return "Concluído";
        if("cancelado".equals(value))return "Cancelado";
        if("ausente".equals(value))return "Não compareceu";
        return value;
    }
    public static void appointment(long when, long now, String expiry, String scheduledDay) {
        if (when <= now) throw new IllegalArgumentException("Escolha um horário futuro.");
        if (expiry.compareTo(scheduledDay) < 0) throw new IllegalArgumentException("O lote vence antes do agendamento. Escolha outro lote.");
    }
    public static String csv(String v) {
        String s = v == null ? "" : v;
        int first = 0;
        while (first < s.length() && Character.isWhitespace(s.charAt(first))) first++;
        if (first < s.length() && "=+@-".indexOf(s.charAt(first)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    public static String shareText(String name, String address, String district, String zip) {
        return required(name, "nome do posto", 100) + "\n" + required(address, "endereço", 255)
            + "\n" + text(district) + (text(zip).isEmpty() ? "" : " - CEP " + text(zip));
    }
}
