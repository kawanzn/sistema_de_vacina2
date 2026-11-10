package br.edu.easyvacc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.json.*;

public final class PostsActivity extends Activity {
    private JSONArray units;
    private LinearLayout results;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void text(LinearLayout root,String value,int size){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);
        view.setPadding(0,dp(8),0,dp(8));root.addView(view);
    }
    private Button button(LinearLayout root,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());root.addView(b);return b;
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);
        root.setPadding(dp(20),dp(16),dp(20),dp(20));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),
                dp(20)+insets.getSystemWindowInsetRight(),dp(20)+insets.getSystemWindowInsetBottom());return insets;
        });
        text(root,"Postos de Saquarema",24);
        text(root,"Diretório municipal disponível sem internet. Consulte a unidade para confirmar o atendimento.",14);
        button(root,"Voltar aos lembretes",this::finish);
        EditText search=new EditText(this);search.setId(R.id.post_search);search.setSingleLine(true);
        search.setHint("Nome, bairro ou endereço");search.setContentDescription("Buscar postos");root.addView(search);
        button(root,"Mostrar todos os postos",()->search.setText(""));
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
        try(InputStream input=getAssets().open("postos-saquarema.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int size;
            while((size=input.read(buffer))!=-1)bytes.write(buffer,0,size);
            units=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }catch(Exception e){text(results,"Não foi possível carregar o diretório de postos.",15);}
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render(s.toString());}
            public void afterTextChanged(Editable s){}
        });
        render("");setContentView(scroll);root.requestApplyInsets();
    }
    static String key(String text){return Normalizer.normalize(text,Normalizer.Form.NFD)
        .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    static boolean matches(String search,JSONObject item){
        String target=key(item.optString("name")+" "+item.optString("district")+" "+item.optString("address")+" Saquarema RJ");
        for(String word:key(search).split(" "))if(!target.contains(word))return false;
        return true;
    }
    static String address(JSONObject item){return item.optString("name")+"\n"+item.optString("address")+"\n"+item.optString("district")+" · Saquarema/RJ";}
    private void render(String search){
        if(units==null)return;
        results.removeAllViews();int found=0;
        for(int i=0;i<units.length();i++){JSONObject item=units.optJSONObject(i);if(item==null||!matches(search,item))continue;
            found++;text(results,address(item),16);button(results,"Compartilhar endereço de "+item.optString("name"),()->preview(item));
        }
        if(found==0)text(results,"Nenhuma unidade encontrada. Tente outro bairro ou mostre todos os postos.",15);
    }
    private void preview(JSONObject item){
        String publicAddress=address(item);
        new AlertDialog.Builder(this).setTitle("Conferir endereço público").setMessage(publicAddress)
            .setNegativeButton("Voltar à busca",null)
            .setPositiveButton("Escolher aplicativo",(dialog,which)->startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,publicAddress),"Compartilhar endereço")))
            .show();
    }
}
