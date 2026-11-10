package br.com.easyvacc.android;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** One foreground request; position is never persisted. */
final class ForegroundLocation implements LocationListener {
    interface Result { void finish(Location location,String message); }
    private final LocationManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->finish(this.best,"Não foi possível obter a localização. Confira o GPS e tente novamente.");
    private Location best;
    private Result result;
    ForegroundLocation(Context context){manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);}
    void start(boolean precise,Result result){
        stop();this.result=result;
        boolean listening=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            if(!precise&&LocationManager.GPS_PROVIDER.equals(provider))continue;
            try{
                if(!manager.isProviderEnabled(provider))continue;
                Location recent=manager.getLastKnownLocation(provider);
                if(recent!=null&&SystemClock.elapsedRealtimeNanos()-recent.getElapsedRealtimeNanos()<20_000_000_000L)
                    prefer(recent);
                manager.requestLocationUpdates(provider,1000L,0f,this,Looper.getMainLooper());
                listening=true;
            }catch(SecurityException|IllegalArgumentException ignored){}
        }
        if(best!=null&&best.hasAccuracy()&&best.getAccuracy()<=100){finish(best,"");return;}
        if(listening)handler.postDelayed(timeout,12000);
        else finish(best,"Ative a localização do aparelho e tente novamente.");
    }
    void stop(){
        handler.removeCallbacks(timeout);
        if(result!=null)manager.removeUpdates(this);
        result=null;best=null;
    }
    private void finish(Location location,String message){
        Result callback=result;stop();if(callback!=null)callback.finish(location,message);
    }
    private void prefer(Location location){
        if(best==null||(location.hasAccuracy()&&(!best.hasAccuracy()||location.getAccuracy()<best.getAccuracy())))best=location;
    }
    @Override public void onLocationChanged(Location location){
        if(result==null)return;prefer(location);
        if(best.hasAccuracy()&&best.getAccuracy()<=100)finish(best,"");
    }
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
}
