package br.edu.easyvacc;

import org.json.JSONException;
import org.json.JSONObject;

public final class Reminder {
    public static final String CREATED = "Criado";
    public static final String SCHEDULED = "Agendado";
    public static final String SENT = "Notificado";
    public static final String CANCELLED = "Cancelado";
    public static final String PAUSED = "Pausado após reinício";
    public static final String FAILED = "Falha";
    public final String id;
    public final String title;
    public final String message;
    public long notifiedAt;
    public final long at;
    public String state;
    public int boot;

    public Reminder(String id, String title, String message, long at, String state, int boot) {
        this.id = id; this.title = title; this.message = message; this.at = at; this.state = state; this.boot = boot;
    }
    public static void validate(String title, String message, long at, long now) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 80)
            throw new IllegalArgumentException("Informe um título com até 80 caracteres.");
        if (at <= now) throw new IllegalArgumentException("Escolha uma data e hora futuras.");
        if (message == null || message.trim().isEmpty() || message.trim().length() > 500)
            throw new IllegalArgumentException("Informe uma mensagem com até 500 caracteres.");
    }
    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("message", message).put("at", at).put("state", state).put("boot", boot).put("notifiedAt", notifiedAt);
    }
    static Reminder from(JSONObject json) throws JSONException {
        Reminder item = new Reminder(json.getString("id"), json.getString("title"), json.optString("message", "Confira seu compromisso no EasyVacc."), json.getLong("at"), json.getString("state"), json.getInt("boot"));
        item.notifiedAt = json.optLong("notifiedAt", 0);
        return item;
    }
}
