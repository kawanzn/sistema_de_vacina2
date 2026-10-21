package br.com.easyvacc.android;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public final class ShareReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ComponentName target = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);
        String token = intent.getStringExtra("token");
        if (target != null && token != null) {
            context.getSharedPreferences("share_result", Context.MODE_PRIVATE).edit()
                .putString("token", token).putString("target", target.getPackageName()).commit();
        }
    }
}
