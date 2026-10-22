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
    private long citizen=0,selectedPost=0,editingPatient=0,rootCitizen=0;
    private String identifyTarget="card",searchStock="",stockFilter="Todos",searchAgenda="",agendaFilter="Todos",searchReport="";
    private String page="welcome",searchPosts="",searchPatients="",exportText="",shareToken="",shareNotice="";
    private int ids=1000;
    private ShareFlow share=new ShareFlow();
    private boolean observingSync=false,directoryRequested=false,directoryLoading=false;
    private String directoryNotice="";
    private LinearLayout directoryList;
    private Button directoryRefresh;
    private TextView directoryStatus;
    private final android.os.Handler cepHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private String cepRequested="";
    private final android.content.BroadcastReceiver syncReceiver=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context c,Intent i){if(!dirty&&!isFinishing()){if(citizen>0&&db.rows("SELECT id FROM patients WHERE id=?",String.valueOf(citizen)).isEmpty())citizen=0;render();}}};
    private final SimpleDateFormat timestamp=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","BR"));
    interface Task {void run() throws Exception;}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);db=new Db(this);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if(saved!=null){staff=saved.getBoolean("staff",false);citizen=saved.getLong("citizen",0);rootCitizen=saved.getLong("rootCitizen",0);editingPatient=saved.getLong("editingPatient",0);page=saved.getString("page","welcome");identifyTarget=saved.getString("identifyTarget","card");searchPatients=saved.getString("searchPatients","");searchStock=saved.getString("searchStock","");stockFilter=saved.getString("stockFilter","Todos");searchAgenda=saved.getString("searchAgenda","");agendaFilter=saved.getString("agendaFilter","Todos");searchReport=saved.getString("searchReport","");searchPosts=saved.getString("searchPosts","");shareToken=saved.getString("shareToken","");selectedPost=saved.getLong("post",0);share.restore(saved.getString("shareText",""),saved.getString("shareState","READY"));if(page.equals("share")&&!share.text().isEmpty())page="share";}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("staff",staff);out.putLong("citizen",citizen);out.putLong("rootCitizen",rootCitizen);out.putLong("editingPatient",editingPatient);out.putString("page",page);out.putString("identifyTarget",identifyTarget);out.putString("searchPatients",searchPatients);out.putString("searchStock",searchStock);out.putString("stockFilter",stockFilter);out.putString("searchAgenda",searchAgenda);out.putString("agendaFilter",agendaFilter);out.putString("searchReport",searchReport);out.putString("searchPosts",searchPosts);out.putString("shareToken",shareToken);out.putLong("post",selectedPost);out.putString("shareText",share.text());out.putString("shareState",share.state().name());}
    @Override protected void onResume(){super.onResume();if(!observingSync){android.content.IntentFilter filter=new android.content.IntentFilter("br.com.easyvacc.android.SYNC_UPDATED");if(Build.VERSION.SDK_INT>=33)registerReceiver(syncReceiver,filter,RECEIVER_NOT_EXPORTED);else registerReceiver(syncReceiver,filter);observingSync=true;}readShareResult();SyncJob.schedule(this);}
    @Override protected void onPause(){if(observingSync){unregisterReceiver(syncReceiver);observingSync=false;}super.onPause();}
    @Override protected void onDestroy(){cepHandler.removeCallbacksAndMessages(null);if(db!=null)db.close();super.onDestroy();}
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
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();SyncJob.schedule(this);}
    private void go(String next){dirty=false;page=next;render();}
    private boolean privatePage(){return Arrays.asList("home","patients","patientForm","dose","stock","stockForm","postForm","more","password","reports","sync").contains(page);}
    private void render(){
        if(citizen>0&&db.rows("SELECT id FROM patients WHERE id=?",String.valueOf(citizen)).isEmpty()){citizen=0;rootCitizen=0;}
        if(!staff&&privatePage())page="login";
        if(!staff&&citizen<=0&&Arrays.asList("card","agenda","history").contains(page)){identifyTarget=page;page="identify";}
        ids=1000;root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(22),dp(12),dp(16),dp(12));
        TextView brand=text("EasyVacc",22,NAVY,true);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text(staff?"PROFISSIONAL":"CIDADÃO",10,BLUE,true);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(bg(0xffe7eeff,20));bar.addView(badge);root.addView(bar);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(page){
            case "welcome":welcome();break;case "portal":portal();break;case "reports":reports();break;case "sync":syncPage();break;case "patientForm":patientForm(editingPatient);break;case "login":login();break;case "home":dashboard();break;
            case "patients":patients();break;case "stock":stocks();break;case "stockForm":stockForm();break;
            case "posts":posts();break;case "postForm":postForm();break;case "share":sharePage();break;
            case "dose":doseForm();break;case "identify":identify();break;case "card":patientCard(citizen);break;
            case "schedule":scheduleForm();break;case "agenda":agenda(false);break;case "history":agenda(true);break;
            case "more":more();break;case "help":help();break;case "password":password();break;
            default:page="welcome";welcome();
        }
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(8),dp(8),dp(8),dp(8));nav.setBackgroundColor(Color.WHITE);
        String[][] tabs=staff?new String[][]{{"Início","home"},{"Pessoas","patients"},{"Estoque","stock"},{"Agenda","agenda"},{"Mais","more"}}:new String[][]{{"Portal","portal"},{"Caderneta","identify"},{"Agenda","agenda"},{"Postos","posts"},{"Ajuda","help"}};
        for(String[] tab:tabs){TextView n=text(tab[0],12,page.equals(tab[1])?BLUE:MUTED,page.equals(tab[1]));n.setGravity(Gravity.CENTER);n.setPadding(0,dp(15),0,dp(15));n.setMinHeight(dp(48));nav.addView(n,new LinearLayout.LayoutParams(0,-2,1));n.setOnClickListener(v->{if(tab[1].equals("identify"))identifyTarget="card";if(tab[1].equals("agenda")&&!staff&&citizen<=0)identifyTarget="agenda";if(dirty)confirmDiscard(tab[1]);else go(tab[1]);});}
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
        heading("EasyVacc","Universidade de Vassouras · Sistema de Controle de Vacinas");
        LinearLayout professional=card(body);professional.setBackground(bg(BLUE,20));professional.addView(text("SERVIDOR PÚBLICO",11,0xffdce8ff,true));gap(professional,12);professional.addView(text("Painel profissional",25,Color.WHITE,true));gap(professional,8);professional.addView(text("Pacientes, aplicações, estoque e agendamentos em um só lugar.",15,Color.WHITE,false));button(professional,db.hasAccount()?"Entrar no painel":"Configurar acesso profissional",false,()->go("login"));
        LinearLayout portal=card(body);portal.setBackground(bg(GREEN,20));portal.addView(text("PORTAL DO CIDADÃO",11,0xffc9fff0,true));gap(portal,12);portal.addView(text("Sua saúde, à mão",25,Color.WHITE,true));gap(portal,8);portal.addView(text("Consulte a caderneta, encontre unidades e organize suas próximas doses.",15,Color.WHITE,false));button(portal,"Entrar no portal",false,()->{staff=false;citizen=0;rootCitizen=0;go("portal");});
        label("Trabalhe sem internet. No acesso profissional, conecte o servidor para sincronizar os registros quando houver conexão.");
    }
    private void portal(){
        heading("Portal do Cidadão","Caderneta digital, unidades de saúde e próximos atendimentos.");
        LinearLayout hero=card(body);hero.setBackground(bg(GREEN,20));hero.addView(text("Cuidar ficou mais simples.",25,Color.WHITE,true));gap(hero,10);hero.addView(text("Escolha abaixo o serviço que deseja acessar.",15,Color.WHITE,false));
        button(body,"Consultar caderneta",true,()->identifyFor("card"));button(body,"Agendar nova dose",false,()->go("schedule"));button(body,"Meus agendamentos",false,()->identifyFor("agenda"));button(body,"Buscar postos e vacinas disponíveis",false,()->go("posts"));button(body,"Conversar com o assistente",false,()->go("help"));button(body,"Trocar para acesso profissional",false,()->go("login"));
        label(new SyncEngine(this).status());
    }
    private void identifyFor(String target){identifyTarget=target;if(citizen>0)go(target);else go("identify");}
    private void section(String title){gap(body,18);body.addView(text(title,19,INK,true));gap(body,12);}
    private void badge(LinearLayout parent,String value,int color){TextView tag=text(value,12,color,true);tag.setPadding(dp(10),dp(6),dp(10),dp(6));tag.setBackground(bg(color==GREEN?0xffe5f7ef:color==BLUE?0xffeaf0ff:0xffffefea,10));parent.addView(tag);}
    private Spinner filter(LinearLayout parent,String title,String[] items,String selected){parent.addView(text(title,13,INK,true));Spinner spinner=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(a);spinner.setMinimumHeight(dp(52));for(int i=0;i<items.length;i++)if(items[i].equals(selected))spinner.setSelection(i);parent.addView(spinner);return spinner;}
    private final class LotPicker {
        final List<Db.Row> lots=new ArrayList<>();final Spinner lot;
        LotPicker(){
            List<Db.Row> posts=db.posts("");Spinner post=choice(body,"1. Unidade de saúde *",posts,"");lot=choice(body,"2. Vacina / lote *",lots,"stock");
            post.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){}public void onItemSelected(AdapterView<?>p,View v,int position,long id){
                lots.clear();if(position>0)lots.addAll(db.freeStock(posts.get(position-1).id()));
                ArrayList<String> labels=new ArrayList<>();labels.add(position==0?"Escolha a unidade primeiro":lots.isEmpty()?"Sem doses livres nesta unidade":"Selecione a vacina...");for(Db.Row r:lots)labels.add(r.s("name")+" · "+r.s("lot")+" · validade "+Rules.showDate(r.s("expiry"))+" · "+(r.n("qty")-r.n("reserved"))+" livres");
                ArrayAdapter<String>a=new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);lot.setAdapter(a);
            }});
        }
        long selected(){return MainActivity.this.selected(lot,lots);}
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
        heading("Painel de Controle","Olá, "+db.professionalName().split(" ")[0]+". Organize os atendimentos de hoje.");
        LinearLayout metric=card(body);metric.setBackground(bg(BLUE,20));metric.addView(text(db.count("patients")+" pacientes",28,Color.WHITE,true));gap(metric,10);metric.addView(text(db.count("doses")+" doses aplicadas · "+db.appointments(0,true).size()+" agendamentos",15,Color.WHITE,false));
        LinearLayout connection=card(body);connection.addView(text("Dados e sincronização",17,INK,true));connection.addView(text(new SyncEngine(this).status(),13,MUTED,false));button(connection,"Gerenciar conexão",false,()->go("sync"));
        button(body,"Registrar dose",true,()->go("dose"));button(body,"Cadastrar paciente",false,()->patientForm(0));button(body,"Agendar dose",false,()->go("schedule"));
        section("Relatório de aplicações");button(body,"Ver relatório completo e exportar",false,()->go("reports"));List<Db.Row> applied=db.applications();if(applied.isEmpty())label("Nenhuma aplicação registrada. Os registros aparecerão aqui após a confirmação.");for(Db.Row d:applied.subList(0,Math.min(5,applied.size())))applicationCard(body,d);
        section("Próximos atendimentos");List<Db.Row> pending=db.appointments(0,true);if(pending.isEmpty())label("Nenhum atendimento pendente.");for(Db.Row a:pending.subList(0,Math.min(4,pending.size()))){LinearLayout c=card(body);c.addView(text(a.s("patient_name"),17,INK,true));c.addView(text(a.s("vaccine")+"\n"+a.s("post_name")+"\n"+timestamp.format(new Date(a.n("when_at"))),14,MUTED,false));button(c,"Abrir agenda",false,()->go("agenda"));}
        if(db.count("posts")==0){section("Primeiros passos");label("Conecte o servidor para receber os registros existentes ou cadastre sua primeira unidade e um lote.");button(body,"Cadastrar unidade",false,()->go("postForm"));}
    }
    private void applicationCard(LinearLayout parent,Db.Row dose){LinearLayout c=card(parent);c.addView(text(dose.s("patient_name"),18,INK,true));gap(c,5);c.addView(text(dose.s("name")+" · lote "+dose.s("lot")+"\n"+timestamp.format(new Date(dose.n("applied_at")))+"\n"+dose.s("post_name"),14,MUTED,false));gap(c,10);badge(c,"Aplicada",GREEN);button(c,"Abrir caderneta",false,()->{citizen=dose.n("patient_id");go("card");});}
    private void reports(){
        heading("Relatório de Aplicações","Pesquise por paciente, CPF, vacina, lote ou unidade.");EditText query=input(body,"Buscar aplicações",searchReport,InputType.TYPE_CLASS_TEXT);LinearLayout list=column();button(body,"Buscar",false,()->{searchReport=value(query);fillReports(list);});button(body,"Exportar todas as aplicações (CSV)",false,()->export(db.report(),"text/csv","EasyVacc-aplicacoes.csv"));gap(body,14);body.addView(list);fillReports(list);
    }
    private void fillReports(LinearLayout list){list.removeAllViews();int count=0;for(Db.Row d:db.applications()){if(!Rules.matches(searchReport,d.s("patient_name"),d.s("cpf"),d.s("name"),d.s("lot"),d.s("post_name")))continue;count++;applicationCard(list,d);}if(count==0)list.addView(text("Nenhuma aplicação encontrada para esta busca.",15,MUTED,false));}
    private void patients(){
        heading("Pessoas","Pacientes e dependentes cadastrados neste aparelho.");button(body,"Cadastrar pessoa",true,()->patientForm(0));
        EditText query=input(body,"Buscar por nome, CPF ou SUS",searchPatients,InputType.TYPE_CLASS_TEXT);LinearLayout list=column();
        button(body,"Buscar",false,()->{searchPatients=value(query);fillPatients(list);});gap(body,16);body.addView(list);fillPatients(list);
    }
    private void fillPatients(LinearLayout list){list.removeAllViews();String q=Rules.key(searchPatients);int found=0;for(Db.Row p:db.rows("SELECT * FROM patients ORDER BY name COLLATE NOCASE")){if(!Rules.matches(searchPatients,p.s("name"),p.s("cpf"),p.s("sus")))continue;found++;LinearLayout c=card(list);c.addView(text(p.s("name"),18,INK,true));c.addView(text("Nascimento: "+Rules.showDate(p.s("dob"))+(p.n("guardian_id")>0?"\nDependente de "+db.row("patients",p.n("guardian_id")).s("name"):""),14,MUTED,false));button(c,"Ver caderneta",false,()->{citizen=p.id();go("card");});button(c,"Editar cadastro",false,()->patientForm(p.id()));}if(found==0)list.addView(text("Nenhuma pessoa encontrada.",15,MUTED,false));}
    private void patientForm(long id){
        editingPatient=id;dirty=true;page="patientForm";renderFormHeader(id>0?"Editar cadastro":"Nova pessoa","Campos com * são obrigatórios.");Db.Row p=id>0?db.row("patients",id):new Db.Row();
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
        heading("Identificação do cidadão","Informe nome completo, CPF ou cartão SUS e nascimento para continuar.");
        EditText identity=field("Nome completo, CPF ou cartão SUS *");EditText born=dateField(body,"Data de nascimento *","",false);
        button(body,"Consultar registros",true,()->{citizen=db.identify(value(identity),value(born)).id();rootCitizen=citizen;go(identifyTarget);});label("A consulta encontra apenas registros salvos neste aplicativo.");
    }
    private void patientCard(long id){
        if(id<=0){go("identify");return;}Db.Row p=db.row("patients",id);heading("Caderneta digital","Histórico de vacinação cadastrado no aplicativo.");LinearLayout person=card(body);person.addView(text(p.s("name"),23,NAVY,true));person.addView(text("Nascimento: "+Rules.showDate(p.s("dob"))+(p.s("cpf").isEmpty()?"":"\nCPF: "+p.s("cpf"))+(p.s("sus").isEmpty()?"":"\nSUS: "+p.s("sus")),14,MUTED,false));
        if(p.n("guardian_id")>0)person.addView(text("Responsável: "+db.row("patients",p.n("guardian_id")).s("name"),14,MUTED,false));if(!p.s("address").isEmpty())person.addView(text(p.s("address"),14,MUTED,false));button(body,"Agendar nova dose",true,()->go("schedule"));button(body,"Ver agendamentos",false,()->go("agenda"));if(rootCitizen>0&&rootCitizen!=id)button(body,"Voltar à caderneta do responsável",false,()->{citizen=rootCitizen;go("card");});
        List<Db.Row> doses=db.doses(id);gap(body,16);if(doses.isEmpty())label("Ainda não há doses registradas para esta pessoa.");
        for(Db.Row d:doses){LinearLayout c=card(body);c.addView(text(d.s("name"),18,BLUE,true));c.addView(text(timestamp.format(new Date(d.n("applied_at")))+"\nLote "+d.s("lot")+"\n"+d.s("post_name"),14,MUTED,false));gap(c,8);badge(c,"Aplicada",GREEN);}
        List<Db.Row> children=db.rows("SELECT * FROM patients WHERE guardian_id=? ORDER BY name",String.valueOf(id));if(!children.isEmpty()){gap(body,12);body.addView(text("Dependentes",18,INK,true));for(Db.Row child:children)button(body,child.s("name"),false,()->{citizen=child.id();go("card");});}
    }
    private void posts(){
        heading("Postos de Saquarema","Unidades da cidade disponíveis mesmo sem internet.");
        if(staff)button(body,"Cadastrar unidade",true,()->go("postForm"));
        PublicDirectory directory=new PublicDirectory(this);
        String updated=directory.updated();
        label(updated.isEmpty()?"Lista municipal incluída no aplicativo · conferida em 22/10/2026.":"Catálogo online recebido em "+updated+". Sem internet, a lista salva continua disponível.");
        directoryStatus=text(directoryNotice,13,MUTED,false);body.addView(directoryStatus);
        EditText query=input(body,"Seu CEP, nome, bairro ou rua",searchPosts,InputType.TYPE_CLASS_TEXT);
        query.setHint("Ex.: Bacaxá, Jaconé, Boqueirão ou Saquarema");
        LinearLayout list=column();directoryList=list;
        button(body,"Buscar unidades",true,()->{searchPosts=value(query);searchPostsNow(list);});
        button(body,"Mostrar todos os postos",false,()->{query.setText("");searchPosts="";fillPosts(list);});
        directoryRefresh=button(body,directoryLoading?"Atualizando lista...":"Atualizar lista pela internet",false,()->refreshDirectory(list));directoryRefresh.setEnabled(!directoryLoading);
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){}public void onTextChanged(CharSequence t,int start,int before,int count){searchPosts=t.toString();fillPosts(list);cepHandler.removeCallbacksAndMessages(null);if(Rules.fullCep(searchPosts))cepHandler.postDelayed(()->searchPostsNow(list),500);}public void afterTextChanged(android.text.Editable e){}});
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){searchPosts=value(query);searchPostsNow(list);return true;}return false;});
        gap(body,18);body.addView(list);fillPosts(list);
        if(!directoryRequested){directoryRequested=true;refreshDirectory(list);}
    }
    private void searchPostsNow(LinearLayout list){
        if(isFinishing()||isDestroyed()||!page.equals("posts"))return;
        fillPosts(list);if(!Rules.fullCep(searchPosts))return;
        String cep=Rules.digits(searchPosts);CepLookup lookup=new CepLookup(this);
        if(lookup.saved(cep)!=null){directoryStatus.setText("Região do CEP localizada. Consulta disponível sem internet.");fillPosts(list);return;}
        if(cep.equals(cepRequested))return;cepRequested=cep;directoryStatus.setText("Consultando a região do CEP...");
        new Thread(()->{String error="";try{lookup.resolve(cep);}catch(Exception e){error=e.getMessage()==null?"Não foi possível consultar o CEP. Busque pelo bairro.":e.getMessage();}
            final String message=error;runOnUiThread(()->{if(cep.equals(cepRequested))cepRequested="";if(!isFinishing()&&!isDestroyed()&&page.equals("posts")&&Rules.digits(searchPosts).equals(cep)){fillPosts(directoryList);directoryStatus.setText(message.isEmpty()?"Região do CEP localizada e salva para uso sem internet.":message);}});
        },"easyvacc-cep").start();
    }
    private void refreshDirectory(LinearLayout list){
        if(directoryLoading)return;directoryLoading=true;
        directoryRefresh.setText("Atualizando lista...");directoryRefresh.setEnabled(false);
        final PublicDirectory directory=new PublicDirectory(this);
        new Thread(()->{String result;try{directory.refresh();result="Lista atualizada. Os dados públicos ficam disponíveis sem login.";}catch(Exception e){result="Não foi possível atualizar online. Você pode consultar normalmente os postos salvos de Saquarema.";}
            final String message=result;runOnUiThread(()->{directoryLoading=false;directoryNotice=message;if(!isFinishing()&&!isDestroyed()&&page.equals("posts")&&directoryList!=null&&directoryList.isAttachedToWindow()){directoryRefresh.setText("Atualizar lista pela internet");directoryRefresh.setEnabled(true);directoryStatus.setText(message);fillPosts(directoryList);}});
        },"easyvacc-public-posts").start();
    }
    private void fillPosts(LinearLayout list){
        list.removeAllViews();List<Db.Row> posts=new PublicDirectory(this).all(db,searchPosts);
        org.json.JSONObject region=Rules.fullCep(searchPosts)?new CepLookup(this).saved(searchPosts):null;
        if(region!=null){list.addView(text("CEP "+Rules.digits(searchPosts)+" · "+region.optString("logradouro")+"\n"+region.optString("bairro")+" · "+region.optString("localidade")+" – "+region.optString("uf"),15,INK,true));list.addView(text("Postos da cidade, com os que citam o bairro primeiro. A lista não calcula distância nem define sua unidade de referência.",13,MUTED,false));gap(list,12);}
        else if(Rules.fullCep(searchPosts))list.addView(text("Para localizar a região deste CEP pela primeira vez, conecte à internet e toque em Buscar unidades. Depois a consulta fica salva para uso offline.",13,MUTED,false));
        list.addView(text(posts.size()+" unidade(s) encontrada(s)",14,BLUE,true));gap(list,12);
        if(posts.isEmpty())list.addView(text("Nenhum resultado para essa busca. Tente apenas o bairro ou toque em Mostrar todos os postos. Alguns CEPs não constam na fonte municipal; nesses casos, busque pelo nome ou bairro.",15,MUTED,false));
        for(Db.Row p:posts){
            LinearLayout c=card(list);c.addView(text(p.s("name"),20,INK,true));
            c.addView(text(p.s("address")+"\n"+p.s("district")+(p.s("city").isEmpty()?"":" · "+p.s("city")+" – RJ")+(p.s("zip").isEmpty()?"":" · CEP "+p.s("zip")),14,MUTED,false));
            if(!p.s("phone").isEmpty())button(c,"Ligar para a unidade",false,()->{try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Rules.digits(p.s("phone")))));}catch(ActivityNotFoundException e){throw new IllegalStateException("Este aparelho não possui aplicativo de telefone.");}});
            List<Db.Row> available=p.s("catalog").equals("true")?Collections.emptyList():db.rows("SELECT name,SUM(qty-reserved) AS free FROM stock WHERE post_id=? AND qty>reserved AND expiry>=? GROUP BY name",String.valueOf(p.id()),Rules.today());
            gap(c,12);
            if(available.isEmpty())c.addView(text(p.s("availability").isEmpty()?"Disponibilidade de vacinas não informada no aplicativo. Confirme com a unidade.":"Vacinas informadas na última atualização: "+p.s("availability")+". Confirme a disponibilidade com a unidade.",13,MUTED,false));
            for(Db.Row vaccine:available)c.addView(text(vaccine.s("name")+" · "+vaccine.n("free")+" doses livres registradas neste aparelho",13,GREEN,true));
            if(!p.s("note").isEmpty())c.addView(text(p.s("note"),13,MUTED,false));
            if(!p.s("source").isEmpty())c.addView(text(!p.s("catalog").equals("true")||!p.s("source").startsWith("https://")?"Endereço do cadastro profissional no EasyVacc.":"Fonte: Prefeitura de Saquarema · consulta em "+p.s("verified"),12,MUTED,false));
            button(c,"Compartilhar endereço",false,()->{selectedPost=p.id();share.preview(Rules.shareText(p.s("name"),p.s("address"),p.s("district")+(p.s("city").isEmpty()?"":" · "+p.s("city")+" – RJ"),p.s("zip")));shareNotice="";go("share");});
        }
    }
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
        heading("Controle de Estoque","Lotes, validade, doses disponíveis e reservas por unidade.");button(body,"Entrada de lote",true,()->go("stockForm"));EditText query=input(body,"Buscar vacina, lote, fornecedor ou unidade",searchStock,InputType.TYPE_CLASS_TEXT);Spinner status=filter(body,"Situação",new String[]{"Todos","Disponível","Reservado","Esgotado","Vencido"},stockFilter);button(body,"Aplicar filtros",false,()->{searchStock=value(query);stockFilter=String.valueOf(status.getSelectedItem());go("stock");});gap(body,16);
        List<Db.Row> lots=new ArrayList<>();for(Db.Row item:db.rows("SELECT s.*,p.name AS post_name FROM stock s JOIN posts p ON p.id=s.post_id ORDER BY s.expiry,s.name"))if(Rules.matches(searchStock,item.s("name"),item.s("lot"),item.s("supplier"),item.s("post_name"))&&(stockFilter.equals("Todos")||stockFilter.equals(Rules.stockStatus(item.n("qty"),item.n("reserved"),item.s("expiry"),Rules.today()))))lots.add(item);if(lots.isEmpty())label("Nenhum lote encontrado com estes filtros.");
        for(Db.Row s:lots){boolean expired=s.s("expiry").compareTo(Rules.today())<0;LinearLayout c=card(body);c.addView(text(s.s("name"),19,INK,true));c.addView(text("Lote "+s.s("lot")+" · "+s.s("post_name")+"\nValidade: "+Rules.showDate(s.s("expiry")),14,MUTED,false));gap(c,10);c.addView(text(expired?"LOTE VENCIDO":(s.n("qty")-s.n("reserved"))+" livres · "+s.n("reserved")+" reservadas · "+s.n("qty")+" totais",14,expired?0xffb42318:GREEN,true));if(!expired)button(c,"Adicionar doses ao lote",false,()->{LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);EditText n=input(form,"Quantidade de entrada *","",InputType.TYPE_CLASS_NUMBER);AlertDialog d=new AlertDialog.Builder(this).setTitle("Entrada de estoque").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{db.addStock(s.id(),value(n));d.dismiss();go("stock");toast("Estoque atualizado.");}catch(Exception e){error(e);}}));d.show();});}
    }
    private void stockForm(){
        heading("Novo lote","A validade e a unidade são obrigatórias.");dirty=true;List<Db.Row> posts=db.posts("");if(posts.isEmpty()){label("Cadastre uma unidade antes de incluir um lote.");button(body,"Cadastrar unidade",true,()->go("postForm"));return;}
        EditText name=field("Nome da vacina *"),lot=field("Lote *"),amount=input(body,"Quantidade inicial *","",InputType.TYPE_CLASS_NUMBER),expiry=dateField(body,"Validade *","",false),supplier=field("Fornecedor");gap(body,16);Spinner post=choice(body,"Unidade de saúde *",posts,"");
        button(body,"Salvar lote",true,()->{db.stock(value(name),value(lot),value(amount),value(expiry),value(supplier),selected(post,posts));toast("Lote cadastrado.");go("stock");});button(body,"Cancelar",false,()->confirmDiscard("stock"));
    }
    private void doseForm(){
        heading("Registrar Dose","Selecione paciente, unidade e lote antes de confirmar.");dirty=true;List<Db.Row> patients=db.rows("SELECT * FROM patients ORDER BY name");
        if(patients.isEmpty()||db.freeStock().isEmpty()){label("Cadastre um paciente e um lote vigente com dose livre, ou sincronize os registros do servidor.");button(body,"Cadastrar paciente",false,()->patientForm(0));button(body,"Ver estoque",true,()->go("stock"));return;}
        Spinner person=choice(body,"Paciente *",patients,"");LotPicker picker=new LotPicker();
        button(body,"Conferir aplicação",true,()->{long patient=selected(person,patients),lot=picker.selected();Db.Row p=db.row("patients",patient),stock=db.row("stock",lot);new AlertDialog.Builder(this).setTitle("Confirmar aplicação agora?").setMessage(p.s("name")+"\nNascimento: "+Rules.showDate(p.s("dob"))+"\n\n"+stock.s("name")+" · lote "+stock.s("lot")+"\n"+db.row("posts",stock.n("post_id")).s("name")+"\n\nRegistra a aplicação e baixa uma dose do lote.").setNegativeButton("Voltar",null).setPositiveButton("Confirmar aplicação",(dialog,w)->{try{db.dose(patient,lot);toast("Aplicação registrada. Sincronização agendada.");citizen=patient;go("card");}catch(Exception e){error(e);}}).show();});button(body,"Cancelar",false,()->confirmDiscard("home"));
    }
    private void scheduleForm(){
        heading("Agendar Nova Dose","Escolha unidade, vacina e horário. A reserva depende de estoque cadastrado no EasyVacc.");
        if(!staff&&citizen<=0){
            label("Para reservar uma dose, identifique primeiro uma pessoa cadastrada neste aparelho. A lista pública de postos não contém pacientes ou o estoque real das unidades.");
            button(body,"Identificar pessoa para agendar",true,()->{identifyTarget="schedule";go("identify");});
            button(body,"Consultar postos e telefones",false,()->go("posts"));
            button(body,"Acesso profissional / configurar cadastros",false,()->go("login"));
            button(body,"Voltar ao portal",false,()->go("portal"));return;
        }
        dirty=true;
        List<Db.Row> patients=staff?db.rows("SELECT * FROM patients ORDER BY name"):Collections.singletonList(db.row("patients",citizen));
        if(patients.isEmpty()){
            label("Não há pessoa cadastrada para agendar. Cadastre a pessoa ou receba os dados pela sincronização.");
            if(staff){button(body,"Cadastrar pessoa",true,()->patientForm(0));button(body,"Sincronizar cadastros",false,()->go("sync"));}return;
        }
        if(db.freeStock().isEmpty()){
            label("A tela de agendamento está disponível, mas não há lote vigente com doses livres registrado neste aparelho. Isso não indica falta de vacina na rede municipal.");
            if(staff){button(body,"Cadastrar estoque real",true,()->go("stockForm"));button(body,"Sincronizar estoque do servidor",false,()->go("sync"));}
            button(body,"Consultar postos e telefones",false,()->go("posts"));button(body,"Voltar",false,()->go(staff?"home":"portal"));return;
        }
        Spinner person=choice(body,"Identificação do cidadão *",patients,"");if(!staff){person.setSelection(1);person.setEnabled(false);}LotPicker picker=new LotPicker();EditText when=dateField(body,"3. Data e horário *","",true);
        button(body,"Conferir agendamento",true,()->{long patient=selected(person,patients),lot=picker.selected();String at=value(when);Db.Row stock=db.row("stock",lot);long time=Rules.date(at,true).getTime();Rules.appointment(time,System.currentTimeMillis(),stock.s("expiry"),new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time)));new AlertDialog.Builder(this).setTitle("Confirmar reserva?").setMessage(db.row("patients",patient).s("name")+"\n"+stock.s("name")+" · lote "+stock.s("lot")+"\n"+db.row("posts",stock.n("post_id")).s("name")+"\n"+at).setNegativeButton("Voltar e conferir",null).setPositiveButton("Confirmar agendamento",(d,w)->{try{db.schedule(patient,lot,at);toast("Agendamento salvo. A reserva será enviada na sincronização.");go("agenda");}catch(Exception e){error(e);}}).show();});button(body,"Cancelar",false,()->confirmDiscard(staff?"home":"portal"));
    }
    private void agenda(boolean history){
        if(!staff&&citizen<=0){identifyTarget=history?"history":"agenda";go("identify");return;}heading(history?"Histórico de agendamentos":"Agendamentos",history?"Atendimentos concluídos e cancelamentos.":"Confira os próximos atendimentos.");
        button(body,history?"Ver pendentes":"Novo agendamento",true,()->go(history?"agenda":"schedule"));if(!history)button(body,"Ver histórico",false,()->go("history"));gap(body,16);
        EditText query=input(body,"Buscar pessoa, vacina, lote ou posto",searchAgenda,InputType.TYPE_CLASS_TEXT);Spinner status=history?filter(body,"Situação",new String[]{"Todos","Concluído","Cancelado","Não compareceu"},agendaFilter):null;button(body,"Aplicar filtros",false,()->{searchAgenda=value(query);if(status!=null)agendaFilter=String.valueOf(status.getSelectedItem());go(history?"history":"agenda");});
        List<Db.Row> list=new ArrayList<>();for(Db.Row a:db.appointments(staff?0:citizen,!history))if(Rules.matches(searchAgenda,a.s("patient_name"),a.s("vaccine"),a.s("lot"),a.s("post_name"))&&(!history||agendaFilter.equals("Todos")||agendaFilter.equals(Rules.statusLabel(a.s("status")))))list.add(a);if(list.isEmpty())label("Nenhum agendamento encontrado com estes filtros.");
        for(Db.Row a:list){LinearLayout c=card(body);c.addView(text(a.s("patient_name"),18,INK,true));c.addView(text(a.s("vaccine")+" · lote "+a.s("lot")+"\n"+a.s("post_name")+"\n"+timestamp.format(new Date(a.n("when_at"))),14,MUTED,false));gap(c,8);c.addView(text(Rules.statusLabel(a.s("status")),12,BLUE,true));if(!a.s("reason").isEmpty())c.addView(text(a.s("reason"),13,MUTED,false));
            if(!history){if(staff)button(c,"Confirmar aplicação",true,()->new AlertDialog.Builder(this).setTitle("Registrar aplicação agora?").setMessage(a.s("patient_name")+"\n"+a.s("vaccine")+"\nA dose será registrada uma única vez e a reserva encerrada.").setNegativeButton("Voltar",null).setPositiveButton("Confirmar",(d,w)->{try{db.appointmentAction(a.id(),"concluido","");toast("Aplicação registrada.");go("agenda");}catch(Exception e){error(e);}}).show());button(c,"Cancelar agendamento",false,()->appointmentReason(a.id(),"cancelado"));if(staff)button(c,"Registrar não comparecimento",false,()->appointmentReason(a.id(),"ausente"));}
        }
    }
    private void appointmentReason(long id,String action){LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);EditText reason=input(form,"Justificativa *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(action.equals("ausente")?"Não comparecimento":"Cancelar agendamento").setView(form).setNegativeButton("Voltar",null).setPositiveButton("Confirmar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{db.appointmentAction(id,action,value(reason));dialog.dismiss();toast("Agendamento encerrado e reserva liberada.");go("agenda");}catch(Exception e){error(e);}}));dialog.show();}
    private void more(){
        heading("Mais recursos","Gerencie os dados salvos neste aparelho.");button(body,"Sincronização com o servidor",true,()->go("sync"));button(body,"Relatório de aplicações",false,()->go("reports"));button(body,"Unidades de saúde",false,()->go("posts"));button(body,"Exportar relatório CSV",false,()->export(db.report(),"text/csv","EasyVacc-relatorio.csv"));
        button(body,"Salvar backup dos registros",false,()->new AlertDialog.Builder(this).setTitle("Salvar backup").setMessage("O arquivo contém os cadastros e históricos de vacinação. Escolha um local privado para guardá-lo. A senha profissional não é incluída.").setNegativeButton("Cancelar",null).setPositiveButton("Escolher local",(d,w)->{try{export(db.backup(),"application/json","EasyVacc-backup.json");}catch(Exception e){error(e);}}).show());
        button(body,"Restaurar backup",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");startActivityForResult(i,IMPORT);});
        button(body,"Alterar senha",false,()->go("password"));button(body,"Ajuda e perguntas frequentes",false,()->go("help"));
        if(db.count("patients")==0&&db.count("posts")==0)button(body,"Carregar demonstração fictícia",false,()->new AlertDialog.Builder(this).setTitle("Carregar dados fictícios?").setMessage("Adiciona uma pessoa, uma unidade e um lote identificados como demonstração. Use somente para conhecer o aplicativo.").setNegativeButton("Cancelar",null).setPositiveButton("Carregar",(d,w)->{try{db.demo();toast("Demonstração carregada.");go("home");}catch(Exception e){error(e);}}).show());
        gap(body,18);label("EasyVacc Android 2.1.3\nModo offline · Sincronização HTTPS opcional\nProjeto acadêmico - Universidade de Vassouras\nPedro Marcos Quintanilha de Oliveira Nunes\nKawan Lopes Mangia");
        button(body,"Sair do acesso profissional",false,()->{staff=false;citizen=0;go("welcome");});
    }
    private void background(Button source,Task task){source.setEnabled(false);new Thread(()->{try{task.run();runOnUiThread(()->{if(!isFinishing()){source.setEnabled(true);if(page.equals("sync"))render();}});}catch(Exception e){runOnUiThread(()->{source.setEnabled(true);if(!isFinishing())error(e);});}},"easyvacc-connection").start();}
    private void syncPage(){
        heading("Sincronização","Conecte o mesmo servidor do site para compartilhar os registros entre aparelhos.");SyncEngine engine=new SyncEngine(this);LinearLayout state=card(body);state.addView(text(engine.configured()?"Conexão configurada":"Modo local",20,INK,true));gap(state,8);state.addView(text(engine.status(),14,MUTED,false));
        label("Use uma conta profissional do servidor. O acesso local deste aparelho é separado. A primeira sincronização combina os registros; não cria usuários no site.");
        if(!engine.configured()){
            EditText server=input(body,"Servidor HTTPS *",engine.server(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI),user=input(body,"Usuário do site *","",InputType.TYPE_CLASS_TEXT),pass=input(body,"Senha do site *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pass.setSaveEnabled(false);
            Button connect=button(body,"Conectar e sincronizar",true,()->{});connect.setOnClickListener(v->{String url=value(server),login=value(user),password=pass.getText().toString();pass.setText("");background(connect,()->{engine.connect(url,login,password);engine.sync();SyncJob.schedule(this);});});
        }else{
            Button now=button(body,"Sincronizar agora",true,()->{});now.setOnClickListener(v->background(now,()->{engine.sync();SyncJob.schedule(this);}));
            button(body,"Encerrar conexão",false,()->{engine.disconnect();go("sync");});
            Button recover=button(body,"Revisar conflito: usar a base do servidor",false,()->{});recover.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Substituir a base ativa deste aparelho?").setMessage("Os dados atuais, incluindo alterações não enviadas, serão guardados numa cópia de recuperação privada. A tela passará a mostrar a base do servidor. As pendências antigas não serão enviadas: exporte a cópia e confira com o administrador antes de registrar novamente. Nenhum dado do servidor será alterado nesta ação.").setNegativeButton("Continuar com meus dados",null).setPositiveButton("Guardar cópia e receber servidor",(d,w)->background(recover,()->{engine.useServerVersion();citizen=0;})).show());
        }
        button(body,"Exportar cópia de recuperação",false,()->{List<Db.Row> copies=db.rows("SELECT key,value FROM sync_state WHERE key LIKE 'recovery:%' ORDER BY key DESC");if(copies.isEmpty()){toast("Nenhuma cópia de recuperação criada.");return;}String[] names=new String[copies.size()];for(int i=0;i<names.length;i++)names[i]=timestamp.format(new Date(Long.parseLong(copies.get(i).s("key").substring(9))));new AlertDialog.Builder(this).setTitle("Escolha a cópia preservada").setItems(names,(d,which)->{try{export(copies.get(which).s("value"),"application/json","EasyVacc-recuperacao.json");}catch(Exception error){error(error);}}).show();});
        section("Como funciona");label("Sem internet, os registros continuam salvos no aparelho. Com conexão, o Android agenda o envio e recebe as alterações do servidor. A sincronização em segundo plano depende da bateria e das restrições do sistema; use Sincronizar agora quando precisar conferir o resultado.");label("Alterações simultâneas no mesmo cadastro ou lote exigem revisão para evitar perda de dados e doses duplicadas. Uma falha mantém os dados e a requisição pendente. O servidor precisa ter a integração Android publicada e as migrações aplicadas.");button(body,"Salvar backup antes de conectar",false,()->export(db.backup(),"application/json","EasyVacc-backup.json"));
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
            else if(q.contains("caderneta")||q.contains("historico")||q.contains("consult"))reply="Abra Caderneta e informe nome completo, CPF ou cartão SUS e data de nascimento. O histórico mostra as doses registradas neste aparelho. Os registros do servidor são recebidos após configurar a sincronização no acesso profissional.";
            else if(q.contains("backup")||q.contains("celular")||q.contains("internet"))reply="O aplicativo salva os dados no aparelho e funciona sem internet. Quando conectado ao servidor, sincroniza com os outros aparelhos. No acesso profissional, abra Mais para salvar e restaurar backups. Guarde o arquivo em local privado.";
            else if(q.contains("posto")||q.contains("endereco")||q.contains("compartilh"))reply="Abra Postos, pesquise nome, bairro ou CEP e toque em Compartilhar endereço. Confira a prévia antes de escolher o aplicativo de destino. Cancelar preserva a busca e o endereço.";
            else reply="Posso ajudar a usar a caderneta, os cadastros, o estoque e os agendamentos. Para saber quais vacinas, doses ou intervalos se aplicam a você, leve sua caderneta à unidade de saúde e converse com a equipe.";
            answer.setText(reply);
        });gap(chat,10);chat.addView(answer);
        String[][] faq={{"Como consultar minha caderneta?","Toque em Caderneta e informe o nome completo, CPF ou SUS e a data de nascimento cadastrada. Apenas registros deste aparelho aparecem."},{"Como cadastrar um dependente?","No acesso profissional, cadastre a pessoa e escolha um responsável já cadastrado. A caderneta do responsável permite abrir os dependentes."},{"Como agendar uma dose?","Após consultar a caderneta, toque em Agendar nova dose. Escolha um lote disponível e um horário futuro. O profissional confirma a aplicação no atendimento."},{"Como compartilhar o endereço do posto?","Abra Postos, busque a unidade e toque em Compartilhar endereço. Confira a prévia e escolha o aplicativo de destino. Para cancelar, volte sem selecionar um destino."},{"Qual vacina ou dose devo tomar?","O aplicativo registra os dados informados pelo profissional; não determina indicação, dose ou intervalo. Leve sua caderneta à unidade de saúde para receber orientação individual."},{"Funciona sem internet?","Sim. Cadastros, estoque, caderneta e agenda ficam disponíveis no aparelho. A sincronização espera a conexão. Compartilhar abre o menu do Android; o destino pode precisar de conexão para concluir o envio."},{"Meus dados aparecem em outro celular?","Sim, quando os aparelhos estão conectados ao mesmo servidor e a sincronização conclui. Sem conexão, as alterações ficam locais até o próximo envio."},{"Como exportar o relatório?","No painel profissional, abra Mais e toque em Exportar relatório CSV. Escolha onde salvar o arquivo. Cancelar a escolha não anuncia exportação concluída."}};
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
