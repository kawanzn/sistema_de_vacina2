package br.com.easyvacc.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BLUE=0xff2563eb,NAVY=0xff142447,INK=0xff17253f,MUTED=0xff66758d,BG=0xfff5f7fc,GREEN=0xff087f65;
    private static final int SHARE=201,EXPORT=202,IMPORT=203;
    private Db db;
    private LinearLayout root,body;
    private boolean staff=false,dirty=false;
    private long citizen=0,selectedPost=0;
    private String page="welcome",searchPosts="",searchPatients="",exportText="",shareToken="",shareNotice="";
    private int ids=1000;
    private ShareFlow share=new ShareFlow();
    private final SimpleDateFormat timestamp=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","BR"));
    interface Task {void run() throws Exception;}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);db=new Db(this);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if(saved!=null){searchPosts=saved.getString("searchPosts","");shareToken=saved.getString("shareToken","");selectedPost=saved.getLong("post",0);share.restore(saved.getString("shareText",""),saved.getString("shareState","READY"));if(!share.text().isEmpty())page="share";}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("searchPosts",searchPosts);out.putString("shareToken",shareToken);out.putLong("post",selectedPost);out.putString("shareText",share.text());out.putString("shareState",share.state().name());}
    @Override protected void onResume(){super.onResume();readShareResult();}
    private void readShareResult(){
        SharedPreferences prefs=getSharedPreferences("share_result",MODE_PRIVATE);
        if(!shareToken.isEmpty()&&shareToken.equals(prefs.getString("token",""))){
            if(share.state()!=ShareFlow.State.CHOOSER)share.restore(share.text(),"CHOOSER");share.selected();
            shareNotice="Aplicativo de destino selecionado. Confira a conclusão do envio no destino.";
            prefs.edit().clear().apply();if(page.equals("share"))render();
        }
    }
    @Override public void onBackPressed(){
        if(share.state()==ShareFlow.State.CHOOSER)return;
        if(dirty){new AlertDialog.Builder(this).setTitle("Sair deste formulário?").setMessage("As alterações ainda não salvas serão descartadas.").setNegativeButton("Continuar editando",null).setPositiveButton("Descartar",(d,w)->{dirty=false;go(staff?"home":"welcome");}).show();return;}
        if(page.equals("welcome")){super.onBackPressed();return;}
        if(page.equals("share")){go("posts");return;}go(staff?"home":"welcome");
    }
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void gap(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    private void label(String value){body.addView(text(value,15,MUTED,false));gap(body,10);}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(Color.WHITE,18));c.setElevation(dp(1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(c,p);return c;}
    private void heading(String title,String sub){body.addView(text(title,27,INK,true));gap(body,6);if(!sub.isEmpty())body.addView(text(sub,14,MUTED,false));gap(body,22);}
    private Button button(LinearLayout parent,String value,boolean primary,Task task){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(50));b.setPadding(dp(14),dp(7),dp(14),dp(7));b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(bg(primary?BLUE:0xffeaf0ff,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);parent.addView(b,p);b.setOnClickListener(v->{b.setEnabled(false);try{task.run();}catch(Exception e){error(e);}finally{b.setEnabled(true);}});return b;}
    private void error(Exception e){String m=e.getMessage();if(e instanceof android.database.sqlite.SQLiteConstraintException)m="Confira os dados: CPF, cartão SUS ou lote já cadastrado, agendamento duplicado ou vínculo inválido.";new AlertDialog.Builder(this).setTitle("Não foi possível concluir").setMessage(m==null?"Tente novamente. Nenhuma confirmação foi registrada.":m).setPositiveButton("Entendi",null).show();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void go(String next){dirty=false;page=next;render();}
    private boolean privatePage(){return Arrays.asList("home","patients","patientForm","dose","stock","stockForm","postForm","more","password").contains(page);}
    private void render(){
        if(!staff&&privatePage())page="login";
        if(!staff&&citizen<=0&&Arrays.asList("card","schedule","agenda","history").contains(page))page="identify";
        ids=1000;root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(22),dp(12),dp(16),dp(12));
        TextView brand=text("EasyVacc",22,NAVY,true);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text(staff?"PROFISSIONAL":"CIDADÃO",10,BLUE,true);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(bg(0xffe7eeff,20));bar.addView(badge);root.addView(bar);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(page){
            case "welcome":welcome();break;case "login":login();break;case "home":dashboard();break;
            case "patients":patients();break;case "stock":stocks();break;case "stockForm":stockForm();break;
            case "posts":posts();break;case "postForm":postForm();break;case "share":sharePage();break;
            case "dose":doseForm();break;case "identify":identify();break;case "card":patientCard(citizen);break;
            case "schedule":scheduleForm();break;case "agenda":agenda(false);break;case "history":agenda(true);break;
            case "more":more();break;case "help":help();break;case "password":password();break;
            default:page="welcome";welcome();
        }
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(8),dp(8),dp(8),dp(8));nav.setBackgroundColor(Color.WHITE);
        String[][] tabs=staff?new String[][]{{"Início","home"},{"Pessoas","patients"},{"Estoque","stock"},{"Agenda","agenda"},{"Mais","more"}}:new String[][]{{"Início","welcome"},{"Caderneta","identify"},{"Postos","posts"},{"Ajuda","help"}};
        for(String[] tab:tabs){TextView n=text(tab[0],12,page.equals(tab[1])?BLUE:MUTED,page.equals(tab[1]));n.setGravity(Gravity.CENTER);n.setPadding(0,dp(15),0,dp(15));n.setMinHeight(dp(48));nav.addView(n,new LinearLayout.LayoutParams(0,-2,1));n.setOnClickListener(v->{if(dirty)confirmDiscard(tab[1]);else go(tab[1]);});}
        root.addView(nav);setContentView(root);root.requestApplyInsets();
    }
    private void confirmDiscard(String target){new AlertDialog.Builder(this).setTitle("Descartar alterações?").setMessage("Você ainda não salvou este formulário.").setNegativeButton("Continuar",null).setPositiveButton("Descartar",(d,w)->go(target)).show();}
    private EditText input(LinearLayout parent,String title,String value,int type){TextView l=text(title,13,INK,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);parent.addView(l,lp);EditText e=new EditText(this);e.setId(ids++);e.setText(value);e.setTextSize(16);e.setTextColor(INK);e.setSingleLine(true);e.setInputType(type);e.setBackgroundTintList(ColorStateList.valueOf(BLUE));e.setPadding(dp(2),dp(10),dp(2),dp(10));e.setMinHeight(dp(48));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private EditText field(String title){return input(body,title,"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}
    private String value(EditText e){return e.getText().toString().trim();}
    private EditText dateField(LinearLayout parent,String label,String value,boolean time){
        EditText e=input(parent,label,value,InputType.TYPE_CLASS_DATETIME);e.setFocusable(false);e.setContentDescription(label+". Toque para escolher a data.");
        e.setOnClickListener(v->{Calendar c=Calendar.getInstance();try{if(!value(e).isEmpty())c.setTime(Rules.date(value(e),time));}catch(Exception ignored){}final Calendar chosen=(Calendar)c.clone();new DatePickerDialog(this,(picker,y,m,d)->{chosen.set(y,m,d);String date=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(chosen.getTime());if(time)new TimePickerDialog(this,(view,h,min)->e.setText(date+" "+String.format(Locale.ROOT,"%02d:%02d",h,min)),chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show();else e.setText(date);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});return e;
    }
    private Spinner choice(LinearLayout parent,String title,List<Db.Row> rows,String kind){parent.addView(text(title,13,INK,true));Spinner s=new Spinner(this);ArrayList<String> names=new ArrayList<>();names.add("Selecione...");for(Db.Row r:rows)names.add(kind.equals("stock")?r.s("name")+" · "+r.s("lot")+" · "+r.s("post_name")+" ("+(r.n("qty")-r.n("reserved"))+" livres)":r.s("name"));ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setId(ids++);s.setMinimumHeight(dp(56));parent.addView(s,new LinearLayout.LayoutParams(-1,-2));gap(parent,12);return s;}
    private long selected(Spinner s,List<Db.Row> rows){int i=s.getSelectedItemPosition()-1;if(i<0||i>=rows.size())throw new IllegalArgumentException("Escolha uma opção na lista.");return rows.get(i).id();}
    private void welcome(){
        heading("Sua vacinação,\nmais organizada.","Caderneta, postos e agendamentos neste aparelho.");
        LinearLayout hero=card(body);hero.setBackground(bg(NAVY,20));hero.addView(text("CUIDADO QUE ACOMPANHA VOCÊ",10,0xffb9ceff,true));gap(hero,14);hero.addView(text("Tudo à mão.\nMesmo sem conexão.",25,Color.WHITE,true));gap(hero,10);hero.addView(text("Consulte registros e organize atendimentos com os dados salvos no aplicativo.",14,0xffd1dcf3,false));
        button(body,"Consultar minha caderneta",true,()->{citizen=0;go("identify");});
        button(body,"Encontrar unidades de saúde",false,()->go("posts"));
        LinearLayout access=card(body);gap(access,4);access.addView(text("Acesso profissional",18,INK,true));access.addView(text("Cadastre pessoas, controle lotes e registre aplicações.",14,MUTED,false));button(access,db.hasAccount()?"Entrar no painel":"Configurar acesso profissional",false,()->go("login"));
        label("Os registros são locais. Use o backup para guardar uma cópia ou transferir os dados para outro aparelho.");
    }
    private void login(){
        boolean setup=!db.hasAccount();heading(setup?"Vamos começar":"Bem-vindo de volta",setup?"Crie o acesso profissional deste aparelho.":"Entre com o usuário configurado neste aplicativo.");dirty=true;
        EditText name=setup?field("Nome do profissional *"):null;
        EditText login=input(body,"Usuário *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText pass=input(body,"Senha *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText repeat=setup?input(body,"Repita a senha *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD):null;
        if(setup)label("Use pelo menos 8 caracteres. Guarde a senha: o aplicativo não envia recuperação por e-mail.");
        button(body,setup?"Criar acesso e entrar":"Entrar",true,()->{
            if(setup){if(!value(pass).equals(value(repeat)))throw new IllegalArgumentException("As senhas não coincidem.");db.setup(value(name),value(login),value(pass));}
            else if(!db.authenticate(value(login),value(pass)))throw new IllegalArgumentException("Usuário ou senha incorretos.");staff=true;citizen=0;go("home");
        });button(body,"Voltar",false,()->go("welcome"));
    }
    private void dashboard(){
        heading("Olá, "+db.professionalName().split(" ")[0]+".","Seu painel de vacinação.");
        LinearLayout metric=card(body);metric.addView(text(String.valueOf(db.count("patients")),34,BLUE,true));metric.addView(text("pessoas cadastradas",14,MUTED,false));
        long doses=db.rows("SELECT id FROM doses WHERE status='ativa'").size();long agenda=db.rows("SELECT id FROM appointments WHERE status='pendente'").size();
        metric.addView(text(doses+" doses registradas   ·   "+agenda+" agendamentos",14,INK,false));
        button(body,"Registrar aplicação de vacina",true,()->go("dose"));button(body,"Cadastrar pessoa",false,()->patientForm(0));button(body,"Novo agendamento",false,()->go("schedule"));
        gap(body,20);body.addView(text("Próximos atendimentos",18,INK,true));gap(body,10);List<Db.Row> pending=db.appointments(0,true);
        if(pending.isEmpty())label("Nenhum atendimento pendente.");else for(Db.Row a:pending.subList(0,Math.min(4,pending.size()))){LinearLayout c=card(body);c.addView(text(a.s("patient_name"),16,INK,true));c.addView(text(a.s("vaccine")+"\n"+timestamp.format(new Date(a.n("when_at"))),14,MUTED,false));}
        if(db.count("posts")==0){LinearLayout c=card(body);c.addView(text("Prepare seu primeiro atendimento",18,INK,true));c.addView(text("Comece cadastrando uma unidade e um lote de vacina.",14,MUTED,false));button(c,"Cadastrar unidade",false,()->go("postForm"));}
    }
    private void patients(){
        heading("Pessoas","Pacientes e dependentes cadastrados neste aparelho.");button(body,"Cadastrar pessoa",true,()->patientForm(0));
        EditText query=input(body,"Buscar por nome, CPF ou SUS",searchPatients,InputType.TYPE_CLASS_TEXT);LinearLayout list=column();
        button(body,"Buscar",false,()->{searchPatients=value(query);fillPatients(list);});gap(body,16);body.addView(list);fillPatients(list);
    }
    private void fillPatients(LinearLayout list){list.removeAllViews();String q=Rules.key(searchPatients);int found=0;for(Db.Row p:db.rows("SELECT * FROM patients ORDER BY name COLLATE NOCASE")){if(!q.isEmpty()&&!Rules.key(p.s("name")+" "+p.s("cpf")+" "+p.s("sus")).contains(q))continue;found++;LinearLayout c=card(list);c.addView(text(p.s("name"),18,INK,true));c.addView(text("Nascimento: "+Rules.showDate(p.s("dob"))+(p.n("guardian_id")>0?"\nDependente de "+db.row("patients",p.n("guardian_id")).s("name"):""),14,MUTED,false));button(c,"Ver caderneta",false,()->{citizen=p.id();go("card");});button(c,"Editar cadastro",false,()->patientForm(p.id()));}if(found==0)list.addView(text("Nenhuma pessoa encontrada.",15,MUTED,false));}
    private void patientForm(long id){
        dirty=true;page="patientForm";renderFormHeader(id>0?"Editar cadastro":"Nova pessoa","Campos com * são obrigatórios.");Db.Row p=id>0?db.row("patients",id):new Db.Row();
        EditText name=input(body,"Nome completo *",p.s("name"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText dob=dateField(body,"Data de nascimento *",Rules.showDate(p.s("dob")),false);
        EditText cpf=input(body,"CPF (opcional)",p.s("cpf"),InputType.TYPE_CLASS_NUMBER);
        EditText sus=input(body,"Cartão SUS (opcional)",p.s("sus"),InputType.TYPE_CLASS_NUMBER);
        EditText email=input(body,"E-mail",p.s("email"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone=input(body,"Telefone",p.s("phone"),InputType.TYPE_CLASS_PHONE);
        EditText address=input(body,"Endereço",p.s("address"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        List<Db.Row> parents=db.rows("SELECT * FROM patients WHERE guardian_id IS NULL AND id<>? ORDER BY name",String.valueOf(id));gap(body,14);Spinner guardian=choice(body,"Responsável (opcional)",parents,"");for(int i=0;i<parents.size();i++)if(parents.get(i).id()==p.n("guardian_id"))guardian.setSelection(i+1);
        button(body,"Salvar cadastro",true,()->{long g=guardian.getSelectedItemPosition()>0?selected(guardian,parents):0;db.patient(id,value(name),value(dob),value(cpf),value(sus),value(email),value(phone),value(address),g);toast("Cadastro salvo.");go("patients");});button(body,"Cancelar",false,()->confirmDiscard("patients"));
    }
    private void renderFormHeader(String title,String sub){
        // The form reuses the existing native shell without rebuilding its editable fields.
        if(body==null){page="home";render();}body.removeAllViews();ids=1100;heading(title,sub);
    }
    private void identify(){
        heading("Sua caderneta","Informe a identificação e a data de nascimento cadastradas.");
        EditText identity=field("Nome completo, CPF ou cartão SUS *");EditText born=dateField(body,"Data de nascimento *","",false);
        button(body,"Consultar registros",true,()->{citizen=db.identify(value(identity),value(born)).id();go("card");});label("A consulta encontra apenas registros salvos neste aplicativo.");
    }
    private void patientCard(long id){
        if(id<=0){go("identify");return;}Db.Row p=db.row("patients",id);heading("Caderneta digital","Histórico de vacinação cadastrado no aplicativo.");LinearLayout person=card(body);person.addView(text(p.s("name"),23,NAVY,true));person.addView(text("Nascimento: "+Rules.showDate(p.s("dob"))+(p.s("cpf").isEmpty()?"":"\nCPF: "+p.s("cpf"))+(p.s("sus").isEmpty()?"":"\nSUS: "+p.s("sus")),14,MUTED,false));
        button(body,"Agendar nova dose",true,()->go("schedule"));button(body,"Ver agendamentos",false,()->go("agenda"));
        List<Db.Row> doses=db.doses(id);gap(body,16);if(doses.isEmpty())label("Ainda não há doses registradas para esta pessoa.");
        for(Db.Row d:doses){LinearLayout c=card(body);c.addView(text(d.s("name"),18,BLUE,true));c.addView(text(timestamp.format(new Date(d.n("applied_at")))+"\nLote "+d.s("lot")+"\n"+d.s("post_name"),14,MUTED,false));}
        List<Db.Row> children=db.rows("SELECT * FROM patients WHERE guardian_id=? ORDER BY name",String.valueOf(id));if(!children.isEmpty()){gap(body,12);body.addView(text("Dependentes",18,INK,true));for(Db.Row child:children)button(body,child.s("name"),false,()->{citizen=child.id();go("card");});}
    }
    private void posts(){
        heading("Unidades de saúde","Encontre os postos cadastrados e compartilhe o endereço.");if(staff)button(body,"Cadastrar unidade",true,()->go("postForm"));
        EditText query=input(body,"Nome, bairro ou CEP",searchPosts,InputType.TYPE_CLASS_TEXT);LinearLayout list=column();button(body,"Buscar unidades",false,()->{searchPosts=value(query);fillPosts(list);});gap(body,18);body.addView(list);fillPosts(list);
    }
    private void fillPosts(LinearLayout list){list.removeAllViews();List<Db.Row> posts=db.posts(searchPosts);if(posts.isEmpty())list.addView(text("Nenhuma unidade encontrada. Confira a busca ou peça o cadastro ao profissional.",15,MUTED,false));for(Db.Row p:posts){LinearLayout c=card(list);c.addView(text(p.s("name"),20,INK,true));c.addView(text(p.s("address")+"\n"+p.s("district")+" · CEP "+p.s("zip"),14,MUTED,false));List<Db.Row> available=db.rows("SELECT name,SUM(qty-reserved) AS free FROM stock WHERE post_id=? AND qty>reserved AND expiry>=? GROUP BY name",String.valueOf(p.id()),Rules.today());gap(c,12);if(available.isEmpty())c.addView(text("Sem lotes disponíveis neste aparelho.",13,MUTED,false));for(Db.Row s:available)c.addView(text(s.s("name")+" · "+s.n("free")+" doses livres",13,GREEN,true));button(c,"Compartilhar endereço",false,()->{selectedPost=p.id();share.preview(Rules.shareText(p.s("name"),p.s("address"),p.s("district"),p.s("zip")));shareNotice="";go("share");});}}
    private void postForm(){
        heading("Nova unidade","Informe os dados públicos do posto de saúde.");dirty=true;EditText name=field("Nome da unidade *"),address=field("Endereço *"),district=field("Bairro *");EditText zip=input(body,"CEP *","",InputType.TYPE_CLASS_NUMBER);
        button(body,"Salvar unidade",true,()->{db.post(value(name),value(address),value(district),value(zip));toast("Unidade cadastrada.");go("posts");});button(body,"Cancelar",false,()->confirmDiscard("posts"));
    }
    private void sharePage(){
        heading("Compartilhar endereço","Confira o conteúdo antes de escolher um destino.");LinearLayout preview=card(body);preview.addView(text("PRÉVIA",11,BLUE,true));gap(preview,10);TextView details=text(share.text(),19,INK,true);details.setTextIsSelectable(true);preview.addView(details);
        if(!shareNotice.isEmpty()){label(shareNotice);}else label("Somente o nome e o endereço da unidade serão compartilhados.");
        Button send=button(body,"Escolher aplicativo",true,()->launchShare());send.setEnabled(share.state()!=ShareFlow.State.CHOOSER);
        button(body,"Cancelar e voltar à busca",false,()->{share.cancel();shareNotice="Compartilhamento cancelado.";go("posts");});
    }
    private void launchShare(){
        try{
            share.launch();shareToken=UUID.randomUUID().toString();getSharedPreferences("share_result",MODE_PRIVATE).edit().clear().commit();
            Intent callback=new Intent(this,ShareReceiver.class).putExtra("token",shareToken);
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent chosen=PendingIntent.getBroadcast(this,201,callback,flags);
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,share.text()).putExtra(Intent.EXTRA_SUBJECT,"Endereço da unidade de saúde");
            Intent chooser=Intent.createChooser(send,"Compartilhar endereço",chosen.getIntentSender());startActivityForResult(chooser,SHARE);
        }catch(ActivityNotFoundException|SecurityException e){share.fail();shareNotice="Não foi possível abrir o compartilhamento. O endereço foi preservado; tente novamente.";render();}
    }
    private void stocks(){
        heading("Estoque de vacinas","Lotes, validade e reservas em um só lugar.");button(body,"Cadastrar lote",true,()->go("stockForm"));gap(body,16);
        List<Db.Row> lots=db.rows("SELECT s.*,p.name AS post_name FROM stock s JOIN posts p ON p.id=s.post_id ORDER BY s.expiry,s.name");if(lots.isEmpty())label("Nenhum lote cadastrado.");
        for(Db.Row s:lots){boolean expired=s.s("expiry").compareTo(Rules.today())<0;LinearLayout c=card(body);c.addView(text(s.s("name"),19,INK,true));c.addView(text("Lote "+s.s("lot")+" · "+s.s("post_name")+"\nValidade: "+Rules.showDate(s.s("expiry")),14,MUTED,false));gap(c,10);c.addView(text(expired?"LOTE VENCIDO":(s.n("qty")-s.n("reserved"))+" livres · "+s.n("reserved")+" reservadas · "+s.n("qty")+" totais",14,expired?0xffb42318:GREEN,true));if(!expired)button(c,"Adicionar doses ao lote",false,()->{LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);EditText n=input(form,"Quantidade de entrada *","",InputType.TYPE_CLASS_NUMBER);AlertDialog d=new AlertDialog.Builder(this).setTitle("Entrada de estoque").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{db.addStock(s.id(),value(n));d.dismiss();go("stock");toast("Estoque atualizado.");}catch(Exception e){error(e);}}));d.show();});}
    }
    private void stockForm(){
        heading("Novo lote","A validade e a unidade são obrigatórias.");dirty=true;List<Db.Row> posts=db.posts("");if(posts.isEmpty()){label("Cadastre uma unidade antes de incluir um lote.");button(body,"Cadastrar unidade",true,()->go("postForm"));return;}
        EditText name=field("Nome da vacina *"),lot=field("Lote *"),amount=input(body,"Quantidade inicial *","",InputType.TYPE_CLASS_NUMBER),expiry=dateField(body,"Validade *","",false),supplier=field("Fornecedor");gap(body,16);Spinner post=choice(body,"Unidade de saúde *",posts,"");
        button(body,"Salvar lote",true,()->{db.stock(value(name),value(lot),value(amount),value(expiry),value(supplier),selected(post,posts));toast("Lote cadastrado.");go("stock");});button(body,"Cancelar",false,()->confirmDiscard("stock"));
    }
    private void doseForm(){
        heading("Registrar aplicação","Confira a pessoa e o lote antes de confirmar.");dirty=true;List<Db.Row> patients=db.rows("SELECT * FROM patients ORDER BY name"),lots=db.freeStock();
        if(patients.isEmpty()||lots.isEmpty()){label("É necessário ter uma pessoa cadastrada e um lote vigente com dose livre.");button(body,"Cadastrar pessoa",false,()->patientForm(0));button(body,"Ver estoque",true,()->go("stock"));return;}
        Spinner person=choice(body,"Pessoa *",patients,""),lot=choice(body,"Vacina / lote / unidade *",lots,"stock");
        button(body,"Conferir aplicação",true,()->{long p=selected(person,patients),s=selected(lot,lots);Db.Row patient=db.row("patients",p),stock=db.row("stock",s);new AlertDialog.Builder(this).setTitle("Confirmar aplicação agora?").setMessage(patient.s("name")+"\n"+stock.s("name")+" · lote "+stock.s("lot")+"\n\nA confirmação registra a dose e baixa uma unidade do estoque.").setNegativeButton("Voltar e conferir",null).setPositiveButton("Confirmar aplicação",(dialog,w)->{try{db.dose(p,s);toast("Aplicação registrada.");citizen=p;go("card");}catch(Exception e){error(e);}}).show();});button(body,"Cancelar",false,()->confirmDiscard("home"));
    }
    private void scheduleForm(){
        if(!staff&&citizen<=0){go("identify");return;}heading("Novo agendamento","Uma dose fica reservada até o atendimento ou cancelamento.");dirty=true;
        List<Db.Row> patients=staff?db.rows("SELECT * FROM patients ORDER BY name"):Collections.singletonList(db.row("patients",citizen));List<Db.Row> lots=db.freeStock();
        if(patients.isEmpty()||lots.isEmpty()){label("Nenhum lote vigente com dose livre está disponível. Procure o profissional responsável pelo cadastro.");return;}
        Spinner person=choice(body,"Pessoa *",patients,"");if(!staff){person.setSelection(1);person.setEnabled(false);}Spinner lot=choice(body,"Vacina / lote / unidade *",lots,"stock");EditText when=dateField(body,"Data e horário *","",true);
        button(body,"Confirmar agendamento",true,()->{db.schedule(selected(person,patients),selected(lot,lots),value(when));toast("Agendamento realizado e dose reservada.");go("agenda");});button(body,"Cancelar",false,()->confirmDiscard(staff?"home":"card"));
    }
    private void agenda(boolean history){
        if(!staff&&citizen<=0){go("identify");return;}heading(history?"Histórico de agendamentos":"Agendamentos",history?"Atendimentos concluídos e cancelamentos.":"Confira os próximos atendimentos.");
        button(body,history?"Ver pendentes":"Novo agendamento",true,()->go(history?"agenda":"schedule"));if(!history)button(body,"Ver histórico",false,()->go("history"));gap(body,16);
        List<Db.Row> list=db.appointments(staff?0:citizen,!history);if(list.isEmpty())label("Nenhum agendamento nesta lista.");
        for(Db.Row a:list){LinearLayout c=card(body);c.addView(text(a.s("patient_name"),18,INK,true));c.addView(text(a.s("vaccine")+" · lote "+a.s("lot")+"\n"+a.s("post_name")+"\n"+timestamp.format(new Date(a.n("when_at"))),14,MUTED,false));gap(c,8);c.addView(text(a.s("status").toUpperCase(new Locale("pt","BR")),12,BLUE,true));if(!a.s("reason").isEmpty())c.addView(text(a.s("reason"),13,MUTED,false));
            if(!history){if(staff)button(c,"Confirmar aplicação",true,()->new AlertDialog.Builder(this).setTitle("Registrar aplicação agora?").setMessage(a.s("patient_name")+"\n"+a.s("vaccine")+"\nA dose será registrada uma única vez e a reserva encerrada.").setNegativeButton("Voltar",null).setPositiveButton("Confirmar",(d,w)->{try{db.appointmentAction(a.id(),"concluido","");toast("Aplicação registrada.");go("agenda");}catch(Exception e){error(e);}}).show());button(c,"Cancelar agendamento",false,()->appointmentReason(a.id(),"cancelado"));if(staff)button(c,"Registrar não comparecimento",false,()->appointmentReason(a.id(),"ausente"));}
        }
    }
    private void appointmentReason(long id,String action){LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);EditText reason=input(form,"Justificativa *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(action.equals("ausente")?"Não comparecimento":"Cancelar agendamento").setView(form).setNegativeButton("Voltar",null).setPositiveButton("Confirmar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{db.appointmentAction(id,action,value(reason));dialog.dismiss();toast("Agendamento encerrado e reserva liberada.");go("agenda");}catch(Exception e){error(e);}}));dialog.show();}
    private void more(){
        heading("Mais recursos","Gerencie os dados salvos neste aparelho.");button(body,"Unidades de saúde",false,()->go("posts"));button(body,"Exportar relatório CSV",false,()->export(db.report(),"text/csv","EasyVacc-relatorio.csv"));
        button(body,"Salvar backup dos registros",false,()->new AlertDialog.Builder(this).setTitle("Salvar backup").setMessage("O arquivo contém os cadastros e históricos de vacinação. Escolha um local privado para guardá-lo. A senha profissional não é incluída.").setNegativeButton("Cancelar",null).setPositiveButton("Escolher local",(d,w)->{try{export(db.backup(),"application/json","EasyVacc-backup.json");}catch(Exception e){error(e);}}).show());
        button(body,"Restaurar backup",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");startActivityForResult(i,IMPORT);});
        button(body,"Alterar senha",false,()->go("password"));button(body,"Ajuda e perguntas frequentes",false,()->go("help"));
        if(db.count("patients")==0&&db.count("posts")==0)button(body,"Carregar demonstração fictícia",false,()->new AlertDialog.Builder(this).setTitle("Carregar dados fictícios?").setMessage("Adiciona uma pessoa, uma unidade e um lote identificados como demonstração. Use somente para conhecer o aplicativo.").setNegativeButton("Cancelar",null).setPositiveButton("Carregar",(d,w)->{try{db.demo();toast("Demonstração carregada.");go("home");}catch(Exception e){error(e);}}).show());
        gap(body,18);label("EasyVacc Android 2.0.0\nDados locais · Sem acesso à internet\nProjeto acadêmico - Universidade de Vassouras\nPedro Marcos Quintanilha de Oliveira Nunes\nKawan Lopes Mangia");
        button(body,"Sair do acesso profissional",false,()->{staff=false;citizen=0;go("welcome");});
    }
    private void password(){heading("Alterar senha","A alteração vale para o acesso profissional deste aparelho.");dirty=true;EditText old=input(body,"Senha atual *","",129),next=input(body,"Nova senha *","",129),again=input(body,"Repita a nova senha *","",129);button(body,"Salvar nova senha",true,()->{if(!value(next).equals(value(again)))throw new IllegalArgumentException("As novas senhas não coincidem.");db.changePassword(value(old),value(next));toast("Senha alterada.");go("more");});}
    private void help(){
        heading("Como podemos ajudar?","Respostas locais para usar o EasyVacc.");
        LinearLayout chat=card(body);chat.addView(text("Assistente local",19,BLUE,true));
        chat.addView(text("Tire dúvidas sobre caderneta, cadastros, agendamentos e estoque. As respostas são locais e não substituem avaliação de saúde.",14,MUTED,false));
        EditText question=input(chat,"Sua pergunta","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        TextView answer=text("",15,INK,false);button(chat,"Perguntar",false,()->{
            String q=Rules.key(Rules.required(value(question),"pergunta",500));String reply;
            if(q.contains("dependente")||q.contains("responsavel"))reply="No acesso profissional, abra Pessoas, cadastre o dependente e selecione um responsável já cadastrado. A caderneta do responsável mostra os dependentes vinculados.";
            else if(q.contains("agend")||q.contains("cancel"))reply="Consulte a caderneta e toque em Agendar nova dose. Escolha um lote disponível e um horário futuro. Em Agendamentos, você pode cancelar uma reserva pendente informando a justificativa. O profissional confirma a aplicação.";
            else if(q.contains("estoque")||q.contains("lote"))reply="No acesso profissional, abra Estoque para cadastrar lotes e registrar entradas. Doses vencidas ou reservadas não ficam livres para aplicação. O aplicativo baixa o estoque quando o profissional confirma a aplicação.";
            else if(q.contains("caderneta")||q.contains("historico")||q.contains("consult"))reply="Abra Caderneta e informe nome completo, CPF ou cartão SUS e data de nascimento. O histórico mostra as doses registradas neste aparelho. Os registros do site antigo não são importados automaticamente.";
            else if(q.contains("backup")||q.contains("celular")||q.contains("internet"))reply="Os dados ficam neste aparelho e o aplicativo funciona sem internet. No acesso profissional, abra Mais para salvar e restaurar backups. Guarde o arquivo em local privado.";
            else if(q.contains("posto")||q.contains("endereco")||q.contains("compartilh"))reply="Abra Postos, pesquise nome, bairro ou CEP e toque em Compartilhar endereço. Confira a prévia antes de escolher o aplicativo de destino. Cancelar preserva a busca e o endereço.";
            else reply="Posso ajudar a usar a caderneta, os cadastros, o estoque e os agendamentos. Para saber quais vacinas, doses ou intervalos se aplicam a você, leve sua caderneta à unidade de saúde e converse com a equipe.";
            answer.setText(reply);
        });gap(chat,10);chat.addView(answer);
        String[][] faq={{"Como consultar minha caderneta?","Toque em Caderneta e informe o nome completo, CPF ou SUS e a data de nascimento cadastrada. Apenas registros deste aparelho aparecem."},{"Como cadastrar um dependente?","No acesso profissional, cadastre a pessoa e escolha um responsável já cadastrado. A caderneta do responsável permite abrir os dependentes."},{"Como agendar uma dose?","Após consultar a caderneta, toque em Agendar nova dose. Escolha um lote disponível e um horário futuro. O profissional confirma a aplicação no atendimento."},{"Como compartilhar o endereço do posto?","Abra Postos, busque a unidade e toque em Compartilhar endereço. Confira a prévia e escolha o aplicativo de destino. Para cancelar, volte sem selecionar um destino."},{"Qual vacina ou dose devo tomar?","O aplicativo registra os dados informados pelo profissional; não determina indicação, dose ou intervalo. Leve sua caderneta à unidade de saúde para receber orientação individual."},{"Funciona sem internet?","Sim. Cadastros, estoque, caderneta e agenda são armazenados no aparelho. Compartilhar abre o menu do Android; o destino pode precisar de conexão para concluir o envio."},{"Meus dados aparecem em outro celular?","Não automaticamente. No acesso profissional, salve um backup e restaure o arquivo no outro aparelho. Guarde a cópia em local privado."},{"Como exportar o relatório?","No painel profissional, abra Mais e toque em Exportar relatório CSV. Escolha onde salvar o arquivo. Cancelar a escolha não anuncia exportação concluída."}};
        for(String[] item:faq){LinearLayout c=card(body);c.addView(text(item[0],17,INK,true));gap(c,8);c.addView(text(item[1],14,MUTED,false));}
    }
    private void export(String text,String mime,String name)throws Exception{try(OutputStream cache=openFileOutput("pending-export.tmp",MODE_PRIVATE)){cache.write(text.getBytes("UTF-8"));}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,EXPORT);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==SHARE){readShareResult();if(share.state()==ShareFlow.State.CHOOSER){share.cancel();shareNotice="Compartilhamento cancelado ou nenhum destino selecionado. O endereço foi preservado.";}if(page.equals("share"))render();return;}
        if(request==EXPORT){if(result!=RESULT_OK||data==null||data.getData()==null){deleteFile("pending-export.tmp");toast("Exportação cancelada. Os registros foram preservados.");return;}try(OutputStream stream=getContentResolver().openOutputStream(data.getData());InputStream cache=openFileInput("pending-export.tmp")){if(stream==null)throw new IllegalStateException("Não foi possível abrir o destino.");byte[] buffer=new byte[8192];int read;while((read=cache.read(buffer))!=-1)stream.write(buffer,0,read);stream.flush();deleteFile("pending-export.tmp");toast("Arquivo salvo no local escolhido.");}catch(Exception e){error(e);}return;}
        if(request==IMPORT){if(result!=RESULT_OK||data==null||data.getData()==null){toast("Restauração cancelada. Nada foi alterado.");return;}if(!staff){toast("Entre no acesso profissional para restaurar.");return;}try(InputStream stream=getContentResolver().openInputStream(data.getData())){if(stream==null)throw new IllegalStateException("Não foi possível ler o arquivo.");java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1){output.write(b,0,n);if(output.size()>10*1024*1024)throw new IllegalArgumentException("Backup excede o limite de 10 MB.");}String backup=output.toString("UTF-8");db.checkBackup(backup);new AlertDialog.Builder(this).setTitle("Substituir os registros locais?").setMessage("A restauração substituirá pacientes, postos, estoque, doses e agendamentos. O acesso profissional será mantido. Salve um backup atual antes de continuar.").setNegativeButton("Cancelar",null).setPositiveButton("Restaurar registros",(d,w)->{try{db.restore(backup);citizen=0;toast("Backup restaurado.");go("home");}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}}
    }
}
