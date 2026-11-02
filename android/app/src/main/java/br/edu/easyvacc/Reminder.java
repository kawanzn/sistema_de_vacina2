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
    public final long at;
    public String state;
    public int boot;

    public Reminder(String id, String title, long at, String state, int boot) {
        this.id = id; this.title = title; this.at = at; this.state = state; this.boot = boot;
    }
    public static void validate(String title, long at, long now) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 80)
            throw new IllegalArgumentException("Informe um título com até 80 caracteres.");
        if (at <= now) throw new IllegalArgumentException("Escolha uma data e hora futuras.");
    }
    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("at", at).put("state", state).put("boot", boot);
    }
    static Reminder from(JSONObject json) throws JSONException {
        return new Reminder(json.getString("id"), json.getString("title"), json.getLong("at"), json.getString("state"), json.getInt("boot"));
    }
}
