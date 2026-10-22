package br.com.easyvacc.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Read-only public directory. Never inserts remote data into clinical sync tables. */
public final class PublicDirectory {
    private final SharedPreferences cache;
    private final Context context;
    private final String server;
    public PublicDirectory(Context context){
        this.context=context.getApplicationContext();
        server=new SyncEngine(context).server();
        cache=context.getSharedPreferences("public-directory",Context.MODE_PRIVATE);
    }
    private List<Db.Row> bundled(){
        List<Db.Row> rows=new ArrayList<>();
        for(String[] values:SaquaremaDirectory.UNITS){Db.Row row=new Db.Row();for(int i=0;i<values.length;i++)row.put(SaquaremaDirectory.FIELDS[i],values[i]);row.put("catalog","true");rows.add(row);}
        return rows;
    }
    public List<Db.Row> all(Db db,String query){
        Map<String,Db.Row> combined=new LinkedHashMap<>();
        for(Db.Row row:bundled())combined.put(Rules.postIdentity(row.s("name")),row);
        if(server.equals(cache.getString("server","")))try{
            for(Db.Row row:parse(cache.getString("json","")))combined.put(Rules.postIdentity(row.s("name")),row);
        }catch(Exception ignored){} // A broken cache cannot hide the bundled municipal directory.
        for(Db.Row row:db.posts("")){
            String key=Rules.postIdentity(row.s("name"));Db.Row known=combined.get(key);
            if(known!=null){for(String field:new String[]{"city","state","source","phone","note","verified","availability"})if(row.s(field).isEmpty())row.put(field,known.s(field));}
            combined.put(key,row);
        }
        List<Db.Row> result=new ArrayList<>();
        org.json.JSONObject region=Rules.fullCep(query)?new CepLookup(context).saved(query):null;
        for(Db.Row row:combined.values()){
            boolean exact=Rules.matchesPost(query,row.s("name"),row.s("district"),row.s("address"),row.s("zip"),row.s("city"));
            boolean sameCity=region!=null&&Rules.key(row.s("city")).equals(Rules.key(region.optString("localidade")))&&Rules.key(row.s("state")).equals(Rules.key(region.optString("uf")));
            if(exact||sameCity)result.add(row);
        }
        final String neighborhood=region==null?"":region.optString("bairro");
        Collections.sort(result,(a,b)->{int ar=Rules.regionRank(neighborhood,a.s("name")+" "+a.s("district")),br=Rules.regionRank(neighborhood,b.s("name")+" "+b.s("district"));return ar!=br?Integer.compare(ar,br):Rules.key(a.s("name")).compareTo(Rules.key(b.s("name")));});
        return result;
    }
    private List<Db.Row> parse(String text)throws Exception{
        JSONObject root=new JSONObject(text);if(root.getInt("protocol")!=1)throw new IOException("Formato de catálogo incompatível.");
        JSONArray units=root.getJSONArray("units");if(units.length()>5000)throw new IOException("Catálogo muito grande.");
        List<Db.Row> rows=new ArrayList<>();
        for(int i=0;i<units.length();i++){
            JSONObject item=units.getJSONObject(i);Db.Row row=new Db.Row();
            for(String field:SaquaremaDirectory.FIELDS)row.put(field,item.optString(field,""));
            Rules.required(row.s("name"),"nome da unidade",100);Rules.required(row.s("address"),"endereço",255);
            row.put("catalog","true");row.put("availability",item.optString("availability",""));rows.add(row);
        }
        return rows;
    }
    public String updated(){return server.equals(cache.getString("server",""))?cache.getString("updated",""):"";}
    public void refresh()throws Exception{
        URL url=new URL(server+"/api/android/posts/");if(!url.getProtocol().equals("https"))throw new IOException("Servidor precisa usar HTTPS.");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("O catálogo online está indisponível. A lista salva continua disponível.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream stream=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>2*1024*1024)throw new IOException("Catálogo excedeu o tamanho permitido.");}}
            String text=bytes.toString("UTF-8");parse(text);
            String now=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","BR")).format(new Date());
            if(!cache.edit().putString("server",server).putString("json",text).putString("updated",now).commit())throw new IOException("Não foi possível salvar a atualização.");
        }finally{connection.disconnect();}
    }
}
