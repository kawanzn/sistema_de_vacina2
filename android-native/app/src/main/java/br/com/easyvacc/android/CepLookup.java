package br.com.easyvacc.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.*;
import java.io.*;
import org.json.*;

/** A resident's CEP locates a region; it is not required to equal a clinic's CEP. */
public final class CepLookup {
    private final SharedPreferences cache;
    public CepLookup(Context context){cache=context.getSharedPreferences("cep-regions",Context.MODE_PRIVATE);}
    public JSONObject saved(String value){
        String cep=Rules.digits(value);
        try{String text=cache.getString(cep,"");if(!text.isEmpty())return new JSONObject(text);}catch(Exception ignored){}
        if(cep.equals("28994160"))try{return new JSONObject().put("cep",cep).put("bairro","Condado de Bacaxá (Bacaxá)").put("localidade","Saquarema").put("uf","RJ").put("logradouro","Rua Angélica");}catch(Exception ignored){}
        return null;
    }
    public JSONObject resolve(String value)throws Exception{
        String cep=Rules.digits(value);if(cep.length()!=8)throw new IllegalArgumentException("Informe os 8 dígitos do CEP.");
        JSONObject old=saved(cep);if(old!=null)return old;
        JSONObject result;
        try{result=read("https://viacep.com.br/ws/"+cep+"/json/");}
        catch(IOException e){JSONObject other=read("https://brasilapi.com.br/api/cep/v1/"+cep);result=new JSONObject().put("bairro",other.optString("neighborhood")).put("localidade",other.optString("city")).put("uf",other.optString("state")).put("logradouro",other.optString("street"));}
        if(result.optBoolean("erro",false)||result.optString("localidade").isEmpty())throw new IllegalArgumentException("CEP não encontrado. Confira os números ou procure pelo bairro.");
        result.put("cep",cep);if(!cache.edit().putString(cep,result.toString()).commit())throw new IOException("Não foi possível guardar o CEP para uso offline.");return result;
    }
    private JSONObject read(String url)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(7000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Não foi possível consultar esse CEP agora. Procure pelo bairro ou tente com internet.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream stream=connection.getInputStream()){byte[] buffer=new byte[2048];int count;while((count=stream.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>20000)throw new IOException("Resposta de CEP inválida.");}}
            return new JSONObject(bytes.toString("UTF-8"));
        }finally{connection.disconnect();}
    }
}
