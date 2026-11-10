package br.edu.easyvacc;

import static org.junit.Assert.*;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReminderTests {
    Application app;
    ReminderRepository repository;
    ReminderScheduler scheduler;
    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("easyvacc_reminders",0).edit().clear().commit();
        Settings.Global.putInt(app.getContentResolver(),Settings.Global.BOOT_COUNT,1);
        repository = new ReminderRepository(app); scheduler = new ReminderScheduler(app);
    }
    Reminder create() { return repository.create("Vacinação fictícia","Mensagem fictícia",System.currentTimeMillis()+3600000,System.currentTimeMillis()); }
    @Test public void createAndPersistList() {
        Reminder item = create();
        assertEquals(item.id,new ReminderRepository(app).list().get(0).id);
        assertEquals(Reminder.CREATED,repository.find(item.id).state);
    }
    @Test public void rejectPastDateAndEmptyTitle() {
        assertThrows(IllegalArgumentException.class,() -> repository.create("","Mensagem",200,100));
        assertThrows(IllegalArgumentException.class,() -> repository.create("Vacina","Mensagem",100,100));
        assertTrue(repository.list().isEmpty());
    }
    @Test public void rejectEmptyMessageAndKeepDatabaseEmpty() {
        assertThrows(IllegalArgumentException.class,() -> repository.create("Vacina","  ",200,100));
        assertTrue(repository.list().isEmpty());
    }
    @Test public void immediateTestDoesNotCreateRecordOrAlarm() {
        AlarmReceiver.publish(app,"test","Teste","Mensagem");
        assertTrue(repository.list().isEmpty());
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class)).getScheduledAlarms().isEmpty());
        assertEquals(1,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void scheduleUsesAlarmManager() {
        Reminder item=create(); scheduler.schedule(item.id);
        assertEquals(Reminder.SCHEDULED,repository.find(item.id).state);
        assertEquals(1,Shadows.shadowOf(app.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        assertThrows(IllegalStateException.class,() -> scheduler.schedule(item.id));
    }
    @Test public void cancelPreventsDelivery() {
        Reminder item=create(); scheduler.schedule(item.id); scheduler.cancel(item.id);
        new AlarmReceiver().onReceive(app,new Intent().putExtra("id",item.id));
        assertEquals(Reminder.CANCELLED,repository.find(item.id).state);
        assertEquals(0,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class)).getScheduledAlarms().isEmpty());
    }
    @Test public void deliverOnceAndCancelNotification() {
        Reminder item=create(); scheduler.schedule(item.id);
        AlarmReceiver receiver=new AlarmReceiver(); Intent intent=new Intent().putExtra("id",item.id);
        receiver.onReceive(app,intent);
        assertEquals(0,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
        AlarmReceiver.deliver(app,item.id,item.at); AlarmReceiver.deliver(app,item.id,item.at);
        assertEquals(item.at,repository.find(item.id).notifiedAt);
        assertEquals("Mensagem fictícia",app.getSystemService(NotificationManager.class).getActiveNotifications()[0].getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT));
        assertEquals(Reminder.SENT,repository.find(item.id).state);
        assertEquals(1,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
        scheduler.cancel(item.id);
        assertEquals(0,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void rebootPausesWithoutRestoringAlarms() {
        Reminder item=create(); scheduler.schedule(item.id);
        Settings.Global.putInt(app.getContentResolver(),Settings.Global.BOOT_COUNT,2);
        assertEquals(Reminder.PAUSED,repository.find(item.id).state);
    }
    @Test public void unknownIdentifierIsRejected() { assertThrows(IllegalArgumentException.class,() -> scheduler.schedule("missing")); }
    @Test @Config(sdk = 33) public void permissionDenialDoesNotSchedule() {
        Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        Reminder item=create();
        assertThrows(IllegalStateException.class,() -> scheduler.schedule(item.id));
        assertEquals(Reminder.CREATED,repository.find(item.id).state);
    }
}
