package br.edu.easyvacc;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        deliver(context, intent.getStringExtra("id"), System.currentTimeMillis());
    }
    static void deliver(Context context, String id, long now) {
        ReminderRepository repository = new ReminderRepository(context);
        Reminder item;
        try { item = repository.find(id); }
        catch (RuntimeException ignored) { return; }
        if (!Reminder.SCHEDULED.equals(item.state) || now < item.at) return;
        try {
            ReminderScheduler scheduler = new ReminderScheduler(context);
            if (!scheduler.permitted()) { item.state = Reminder.FAILED; }
            else { publish(context, item.id, item.title, item.message); item.state = Reminder.SENT; item.notifiedAt = now; }
        } catch (RuntimeException error) { item.state = Reminder.FAILED; }
        try { repository.update(item); } catch (RuntimeException ignored) { /* Não travar o processo de recepção. */ }
    }
    static void publish(Context context, String id, String title, String message) {
        Intent intent = new Intent(context, MainActivity.class).setData(Uri.parse("easyvacc://notification/" + id))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion = new Notification.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_vaccine).setContentTitle("EasyVacc").setContentText("Você tem um lembrete local.").build();
        Notification notification = new Notification.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_vaccine).setContentTitle(title).setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message)).setPublicVersion(publicVersion)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open).build();
        context.getSystemService(NotificationManager.class).notify(id, 0, notification);
    }
}
