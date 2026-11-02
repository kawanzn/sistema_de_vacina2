package br.com.easyvacc.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

public final class ReminderScheduler {
    public static final String CHANNEL="easyvacc_local_reminders",ACTION="br.com.easyvacc.android.REMIND";
    private ReminderScheduler(){}
    public static void channel(Context c){if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"Lembretes locais",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("Lembretes pessoais de compromissos e caderneta. Horário aproximado.");channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);c.getSystemService(NotificationManager.class).createNotificationChannel(channel);}}
    public static boolean allowed(Context c){
        channel(c);
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=24&&!manager.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT==23){try{AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);if(ops.checkOpNoThrow("android:post_notification",android.os.Process.myUid(),c.getPackageName())!=AppOpsManager.MODE_ALLOWED)return false;}catch(RuntimeException e){return false;}}
        return Build.VERSION.SDK_INT<26||manager.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static PendingIntent intent(Context c,int id,int flags){Intent i=new Intent(c,ReminderReceiver.class).setAction(ACTION).setData(Uri.parse("easyvacc-reminder://local/"+id)).putExtra("id",id);return PendingIntent.getBroadcast(c,id,i,flags|PendingIntent.FLAG_IMMUTABLE);}
    public static int create(Context c,String title,String message,long when){synchronized(ReminderStore.LOCK){
        ReminderRules.validate(title,message,when,System.currentTimeMillis());if(!allowed(c))throw new IllegalStateException("As notificações estão bloqueadas. Permita o acesso ou abra as configurações. Depois toque em Agendar novamente.");
        try(ReminderStore store=new ReminderStore(c)){
            int id=store.insert(title,message,when);PendingIntent pending=intent(c,id,PendingIntent.FLAG_UPDATE_CURRENT);
            try{((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);store.status(id,"Agendado");return id;}
            catch(RuntimeException error){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending);pending.cancel();store.status(id,"Falha ao agendar");throw error;}
        }
    }}
    public static void cancel(Context c,int id){synchronized(ReminderStore.LOCK){try(ReminderStore store=new ReminderStore(c)){
        if(store.get(id)==null)throw new IllegalArgumentException("Lembrete não encontrado.");
        // State first: a waiting receiver will see cancellation before publishing.
        store.status(id,"Cancelado");PendingIntent pending=intent(c,id,PendingIntent.FLAG_NO_CREATE);if(pending!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending);pending.cancel();}
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
    }}}
    public static boolean show(Context c,int id,String title,String message){
        if(!allowed(c))return false;
        Intent open=new Intent(c,ReminderActivity.class).setAction("br.com.easyvacc.android.OPEN_REMINDER").setData(Uri.parse("easyvacc-reminder://open/"+id)).putExtra("reminder_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        Notification.Builder publicVersion=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        publicVersion.setSmallIcon(R.drawable.ic_reminder).setContentTitle("EasyVacc").setContentText("Você tem um lembrete local.");
        b.setSmallIcon(R.drawable.ic_reminder).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(content).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion.build()).setCategory(Notification.CATEGORY_REMINDER);
        if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_ALL);
        try{((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,b.build());return true;}catch(SecurityException error){return false;}
    }
}
