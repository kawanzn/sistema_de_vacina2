package br.edu.easyvacc;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    public static final String REFRESH = "br.edu.easyvacc.REFRESH";
    @Override public void onReceive(Context context, Intent intent) {
        ReminderRepository repository = new ReminderRepository(context);
        Reminder item;
        try { item = repository.find(intent.getStringExtra("id")); }
        catch (RuntimeException ignored) { return; }
        if (!Reminder.SCHEDULED.equals(item.state)) return;
        try {
            ReminderScheduler scheduler = new ReminderScheduler(context);
            if (!scheduler.permitted()) { item.state = Reminder.FAILED; }
            else {
                PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notification = new Notification.Builder(context, ReminderScheduler.CHANNEL)
                    .setSmallIcon(br.edu.easyvacc.R.drawable.ic_vaccine)
                    .setContentTitle("EasyVacc - lembrete")
                    .setContentText("Confira seu compromisso de vacinação no EasyVacc.")
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open).build();
                context.getSystemService(NotificationManager.class).notify(item.id, 0, notification);
                item.state = Reminder.SENT;
            }
        } catch (RuntimeException error) { item.state = Reminder.FAILED; }
        try { repository.update(item); } catch (RuntimeException ignored) { /* Não travar o processo de recepção. */ }
        context.sendBroadcast(new Intent(REFRESH).setPackage(context.getPackageName()));
    }
}
