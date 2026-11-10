package br.edu.easyvacc;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ReminderRepository {
    private final SharedPreferences prefs;
    private final Context context;
    public ReminderRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("easyvacc_reminders", Context.MODE_PRIVATE);
    }
    public int boot() { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0); }
    public List<Reminder> list() {
        try {
            JSONArray json = new JSONArray(prefs.getString("items", "[]"));
            List<Reminder> items = new ArrayList<>();
            for (int i = 0; i < json.length(); i++) {
                Reminder item = Reminder.from(json.getJSONObject(i));
                if (Reminder.SCHEDULED.equals(item.state) && item.boot != boot()) item.state = Reminder.PAUSED;
                items.add(item);
            }
            return items;
        } catch (JSONException error) { throw new IllegalStateException("Não foi possível ler a lista de lembretes.", error); }
    }
    private void save(List<Reminder> items) {
        try {
            JSONArray json = new JSONArray();
            for (Reminder item : items) json.put(item.json());
            if (!prefs.edit().putString("items", json.toString()).commit())
                throw new IllegalStateException("Não foi possível salvar os lembretes.");
        } catch (JSONException error) { throw new IllegalStateException("Falha ao salvar os lembretes.", error); }
    }
    public Reminder create(String title, String message, long at, long now) {
        Reminder.validate(title, message, at, now);
        List<Reminder> items = list();
        if (items.size() >= 100) throw new IllegalStateException("Limite de 100 lembretes neste aparelho.");
        Reminder item = new Reminder(UUID.randomUUID().toString(), title.trim(), message.trim(), at, Reminder.CREATED, boot());
        items.add(item); save(items); return item;
    }
    public Reminder find(String id) {
        for (Reminder item : list()) if (item.id.equals(id)) return item;
        throw new IllegalArgumentException("Lembrete não encontrado.");
    }
    public void update(Reminder item) {
        List<Reminder> items = list();
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(item.id)) {
            items.set(i, item); save(items); return;
        }
        throw new IllegalArgumentException("Lembrete não encontrado.");
    }
}
