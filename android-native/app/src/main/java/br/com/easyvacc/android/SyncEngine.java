package br.com.easyvacc.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** HTTPS, durable retry request, and conservative three-way merge on the device. */
public final class SyncEngine {
    private static final String[] TABLES={"posts","patients","stock","appointments","doses"};
    private static boolean running;
    private final Context context;
    private final SharedPreferences prefs;
    public SyncEngine(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("sync",Context.MODE_PRIVATE);}
    public boolean configured(){return !prefs.getString("token","").isEmpty();}
    public String status(){return prefs.getString("status","Sincronização não configurada.");}
    public String server(){return prefs.getString("server","https://sistema-vacina.vercel.app");}
    public void message(String value){prefs.edit().putString("status",value).commit();context.sendBroadcast(new android.content.Intent("br.com.easyvacc.android.SYNC_UPDATED").setPackage(context.getPackageName()));}
    public void connect(String url,String user,String password)throws Exception{
        URL parsed=new URL(url.trim());
        if(!parsed.getProtocol().equals("https")||parsed.getHost().isEmpty()||parsed.getUserInfo()!=null||parsed.getQuery()!=null||parsed.getRef()!=null||!(parsed.getPath().isEmpty()||parsed.getPath().equals("/")))throw new IllegalArgumentException("Informe somente o endereço HTTPS do servidor, por exemplo https://sistema-vacina.vercel.app.");
        String normalized="https://"+parsed.getAuthority();
        if(!server().equals(normalized)){
            try(Db local=new Db(context)){if(configured()||!local.syncValue("base").isEmpty()||!local.syncValue("pending").isEmpty())throw new IllegalArgumentException("Este banco já está vinculado a outro servidor. Preserve o backup e use uma instalação separada para outro banco.");}
        }
        JSONObject credentials=new JSONObject().put("username",Rules.required(user,"usuário do servidor",150)).put("password",password);
        JSONObject result=request(normalized+"/api/android/login/",credentials,"");
        if(!prefs.edit().putString("server",normalized).putString("token",result.getString("token")).putString("status","Conectado. Aguardando sincronização.").commit())throw new IOException("Não foi possível salvar a conexão.");
    }
    public void disconnect()throws Exception{
        synchronized(SyncEngine.class){if(running)throw new IllegalStateException("Aguarde a sincronização terminar.");}
        // Preserve the base and pending request so reconnecting never repeats an offline edit.
        prefs.edit().remove("token").putString("status","Conexão encerrada. Dados e pendências preservados.").commit();
    }
    private JSONObject request(String endpoint,JSONObject body,String token)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        try{
            byte[] bytes=body.toString().getBytes("UTF-8");if(bytes.length>10*1024*1024)throw new IOException("Dados excedem o limite de sincronização de 10 MB.");connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int code=connection.getResponseCode();InputStream stream=code<400?connection.getInputStream():connection.getErrorStream();
            ByteArrayOutputStream output=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){output.write(b,0,n);if(output.size()>15*1024*1024)throw new IOException("Resposta do servidor excedeu o limite.");}}
            JSONObject result;try{result=new JSONObject(output.toString("UTF-8"));}catch(JSONException e){throw new IOException("Servidor indisponível ou integração Android ainda não publicada (HTTP "+code+"). Registros locais preservados.");}
            if(code<200||code>=300)throw new IOException(result.optString("error","Falha HTTP "+code));
            return result;
        }finally{connection.disconnect();}
    }
    static JSONObject normalized(JSONObject data)throws Exception{
        JSONObject result=new JSONObject().put("format","EasyVaccNative").put("version",1);
        for(String table:TABLES){JSONArray rows=data.getJSONArray(table);TreeMap<Long,JSONObject> sorted=new TreeMap<>();for(int i=0;i<rows.length();i++){JSONObject row=new JSONObject(rows.getJSONObject(i).toString());if(table.equals("patients"))row.put("name_key","");sorted.put(row.getLong("id"),row);}JSONArray out=new JSONArray();for(JSONObject row:sorted.values())out.put(row);result.put(table,out);}return result;
    }
    private static boolean equal(JSONObject a,JSONObject b)throws Exception{
        if(a==null||b==null)return a==b;
        if(a.length()!=b.length())return false;
        Iterator<String> keys=a.keys();while(keys.hasNext()){String key=keys.next();if(!b.has(key)||!String.valueOf(a.get(key)).equals(String.valueOf(b.get(key))))return false;}return true;
    }
    private static Map<String,JSONObject> index(JSONObject source,String table)throws Exception{Map<String,JSONObject> map=new TreeMap<>();JSONArray rows=source.getJSONArray(table);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);map.put(row.getString("id"),row);}return map;}
    static JSONObject merge(JSONObject base,JSONObject current,JSONObject remote)throws Exception{
        base=normalized(base);current=normalized(current);remote=normalized(remote);JSONObject result=new JSONObject().put("format","EasyVaccNative").put("version",1);
        for(String table:TABLES){Map<String,JSONObject>b=index(base,table),l=index(current,table),r=index(remote,table);Set<String>ids=new TreeSet<>();ids.addAll(b.keySet());ids.addAll(l.keySet());ids.addAll(r.keySet());JSONArray rows=new JSONArray();for(String id:ids){JSONObject before=b.get(id),here=l.get(id),there=r.get(id),chosen;
            if(equal(here,before))chosen=there;else if(equal(there,before)||equal(here,there))chosen=here;else throw new IOException("Alterações simultâneas em "+table+". Exporte seu backup e revise com o administrador. Dados locais preservados.");if(chosen!=null)rows.put(chosen);
        }result.put(table,rows);}return result;
    }
    public boolean sync(){
        synchronized(SyncEngine.class){if(running)return false;running=true;}
        Db db=new Db(context);
        try{
            if(!configured())return false;
            JSONObject pending;SQLiteDatabase capture=db.getWritableDatabase();capture.beginTransaction();
            try{
                String stored=db.syncValue("pending");
                if(stored.isEmpty()){
                    String base=db.syncValue("base");pending=new JSONObject().put("request_id",UUID.randomUUID().toString()).put("local",normalized(new JSONObject(db.backup())));
                    if(base.isEmpty())pending.put("base",JSONObject.NULL);else{JSONObject prior=new JSONObject(base);pending.put("base",prior.getJSONObject("snapshot")).put("revision",prior.getString("revision"));}
                    db.syncValue("pending",pending.toString());
                }else pending=new JSONObject(stored);
                capture.setTransactionSuccessful();
            }finally{capture.endTransaction();}
            JSONObject result=request(server()+"/api/android/sync/",pending,prefs.getString("token",""));
            SQLiteDatabase database=db.getWritableDatabase();database.beginTransaction();
            try{
                JSONObject merged=merge(pending.getJSONObject("local"),new JSONObject(db.backup()),result.getJSONObject("snapshot"));
                db.restore(merged.toString());
                db.syncValue("base",result.toString());db.syncValue("pending","");
                database.setTransactionSuccessful();
            }finally{database.endTransaction();}
            prefs.edit().putLong("last",System.currentTimeMillis()).commit();message("Sincronizado em "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date())+". Novas alterações serão enviadas automaticamente.");return true;
        }catch(Exception error){message("Sincronização pendente: "+(error.getMessage()==null?"sem conexão. Dados locais preservados.":error.getMessage()));return false;}
        finally{db.close();synchronized(SyncEngine.class){running=false;}}
    }
    public void useServerVersion()throws Exception{
        synchronized(SyncEngine.class){if(running)throw new IllegalStateException("Aguarde a sincronização terminar.");running=true;}
        try(Db db=new Db(context)){
            if(!configured())throw new IllegalStateException("Conecte a conta profissional primeiro.");
            JSONObject result=request(server()+"/api/android/sync/",new JSONObject().put("request_id",UUID.randomUUID().toString()).put("pull_only",true),prefs.getString("token",""));
            SQLiteDatabase transaction=db.getWritableDatabase();transaction.beginTransaction();
            try{db.syncValue("recovery:"+System.currentTimeMillis(),db.backup());db.restore(result.getJSONObject("snapshot").toString());db.syncValue("base",result.toString());db.syncValue("pending","");transaction.setTransactionSuccessful();}finally{transaction.endTransaction();}
            message("Base do servidor recebida. A cópia local anterior foi guardada em Cópias de recuperação para conferência.");
        }finally{synchronized(SyncEngine.class){running=false;}}
    }
}
