package br.com.easyvacc.android;

import android.app.job.*;
import android.content.*;

public final class SyncJob extends JobService {
    public static void schedule(Context context){
        if(!new SyncEngine(context).configured())return;
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName component=new ComponentName(context,SyncJob.class);
        jobs.schedule(new JobInfo.Builder(701,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build());
        jobs.schedule(new JobInfo.Builder(702,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1000).setOverrideDeadline(60*1000).build());
    }
    @Override public boolean onStartJob(JobParameters params){new Thread(()->{boolean ok=new SyncEngine(this).sync();jobFinished(params,!ok&&params.getJobId()==702);},"easyvacc-sync").start();return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
