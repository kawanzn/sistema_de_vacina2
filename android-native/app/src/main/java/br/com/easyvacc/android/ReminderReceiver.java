package br.com.easyvacc.android;

import android.content.*;

/** Short offline work; a stale or canceled alarm cannot publish from Intent extras. */
public final class ReminderReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        synchronized(ReminderStore.LOCK){try(ReminderStore store=new ReminderStore(c)){
            if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){store.interrupted();return;}
            if(!ReminderScheduler.ACTION.equals(intent.getAction()))return;
            ReminderStore.Item item=store.get(intent.getIntExtra("id",-1));
            if(item==null||!ReminderRules.deliverable(item.status,item.when,System.currentTimeMillis()))return;
            boolean posted=ReminderScheduler.show(c,item.id,item.title,item.message);store.status(item.id,posted?"Publicada":"Bloqueado na entrega");
        }}
    }
}
