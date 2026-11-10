package br.edu.easyvacc;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

public final class ReminderScheduler {
    public static final String CHANNEL = "vaccination_reminders";
    private final Context context;
    private final ReminderRepository repository;
    public ReminderScheduler(Context context) {
        this.context = context.getApplicationContext();
        repository = new ReminderRepository(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Lembretes de vacinação", NotificationManager.IMPORTANCE_DEFAULT));
    }
    public boolean permitted() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private PendingIntent pending(String id, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class).setData(Uri.parse("easyvacc://reminder/" + id)).putExtra("id", id);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }
    public void schedule(String id) {
        Reminder item = repository.find(id);
        if (!permitted()) throw new IllegalStateException("Permita as notificações do EasyVacc e do canal de lembretes nas configurações do Android.");
        if (!(Reminder.CREATED.equals(item.state) || Reminder.PAUSED.equals(item.state) || Reminder.FAILED.equals(item.state)))
            throw new IllegalStateException("Este lembrete não pode ser agendado novamente.");
        Reminder.validate(item.title, item.message, item.at, System.currentTimeMillis());
        item.state = Reminder.SCHEDULED; item.boot = repository.boot();
        repository.update(item);
        try {
            context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at,
                pending(item.id, PendingIntent.FLAG_UPDATE_CURRENT));
        } catch (RuntimeException error) {
            item.state = Reminder.FAILED; repository.update(item); throw error;
        }
    }
    public void cancel(String id) {
        Reminder item = repository.find(id);
        item.state = Reminder.CANCELLED; repository.update(item);
        PendingIntent pending = pending(id, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            context.getSystemService(AlarmManager.class).cancel(pending);
            pending.cancel();
        }
        context.getSystemService(NotificationManager.class).cancel(id, 0);
    }
}
