package br.com.easyvacc.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Separate local database: reminders are never exported with clinical sync. */
public final class ReminderStore extends SQLiteOpenHelper {
    static final Object LOCK=new Object();
    public static final class Item {
        public int id;public String title,message,status;public long when,published;
    }
    public ReminderStore(Context context){this(context,"easyvacc_reminders.db");}
    ReminderStore(Context context,String name){super(context,name,null,1);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,message TEXT NOT NULL,when_at INTEGER NOT NULL,status TEXT NOT NULL,published INTEGER NOT NULL DEFAULT 0)");}
    public void onUpgrade(SQLiteDatabase db,int old,int current){}
    public List<Item> all(){synchronized(LOCK){List<Item> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,message,when_at,status,published FROM reminders ORDER BY when_at DESC,id DESC",null)){while(c.moveToNext()){Item i=new Item();i.id=c.getInt(0);i.title=c.getString(1);i.message=c.getString(2);i.when=c.getLong(3);i.status=c.getString(4);i.published=c.getLong(5);result.add(i);}}return result;}}
    public Item get(int id){for(Item item:all())if(item.id==id)return item;return null;}
    public int insert(String title,String message,long when){synchronized(LOCK){
        ReminderRules.validate(title,message,when,System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{ContentValues v=new ContentValues();v.put("title",Rules.text(title));v.put("message",Rules.text(message));v.put("when_at",when);v.put("status","Preparando");long id=db.insertOrThrow("reminders",null,v);if(id>Integer.MAX_VALUE)throw new IllegalStateException("Limite de identificadores de lembrete atingido.");db.setTransactionSuccessful();return (int)id;}finally{db.endTransaction();}
    }}
    public void status(int id,String status){synchronized(LOCK){ContentValues v=new ContentValues();v.put("status",status);if(status.equals("Publicada"))v.put("published",System.currentTimeMillis());getWritableDatabase().update("reminders",v,"id=?",new String[]{String.valueOf(id)});}}
    public void interrupted(){synchronized(LOCK){ContentValues v=new ContentValues();v.put("status","Reagendar após reinício");getWritableDatabase().update("reminders",v,"status IN ('Agendado','Preparando')",null);}}
}
