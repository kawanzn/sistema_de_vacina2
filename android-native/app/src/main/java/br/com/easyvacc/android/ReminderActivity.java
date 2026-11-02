package br.com.easyvacc.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local reminders remain usable without a patient, professional login or internet. */
public final class ReminderActivity extends Activity {
    private EditText title,message;
    private Button date;
    private LinearLayout list;
    private TextView access,notice;
    private long chosen;
    private final SimpleDateFormat format=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","BR"));
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff17253f);t.setPadding(0,dp(8),0,dp(8));parent.addView(t);return t;}
    private EditText input(LinearLayout parent,String label,String initial,int id,int max){text(parent,label,15);EditText e=new EditText(this);e.setId(id);e.setText(initial);e.setTextSize(16);e.setMinHeight(dp(50));e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    interface Action{void run()throws Exception;}
    private Button button(LinearLayout parent,String label,Action action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(50));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{b.setEnabled(false);try{action.run();}catch(Exception error){new AlertDialog.Builder(this).setTitle("Não foi possível concluir").setMessage(error.getMessage()==null?"Tente novamente. Confira a lista antes de repetir.":error.getMessage()).setPositiveButton("Entendi",null).show();}finally{b.setEnabled(true);}});return b;}
    public void onCreate(Bundle saved){
        super.onCreate(saved);ReminderScheduler.channel(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff5f7fc);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        text(body,"Lembretes locais",27);
        text(body,"Lembre-se de consultar a caderneta ou de um compromisso. Funciona sem internet. Criar um lembrete não agenda atendimento nem reserva vacina.",15);
        text(body,"Evite CPF, nome de pacientes ou informações de saúde na mensagem. O conteúdo fica apenas neste aparelho; notificações podem ser vistas por outras pessoas.",13);
        access=text(body,"",14);notice=text(body,"",14);
        button(body,"Permitir notificações",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},301);}else{notice.setText(ReminderScheduler.allowed(this)?"Notificações permitidas. Preencha e toque em Agendar.":"Notificações bloqueadas nas configurações do app ou do canal. Abra Configurações de notificações.");}refresh();});
        button(body,"Configurações de notificações",()->{Intent i=Build.VERSION.SDK_INT>=26?new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(i);});
        button(body,"Testar notificação agora",()->{if(!ReminderScheduler.show(this,0,"Teste EasyVacc","Notificação local de teste. Toque para abrir os lembretes."))throw new IllegalStateException("Permita as notificações e verifique o canal antes de testar.");notice.setText("Notificação de teste enviada ao Android. Isso não comprova a entrega de um lembrete futuro.");});
        title=input(body,"Título *",saved==null?"Conferir caderneta":saved.getString("title",""),5101,80);
        message=input(body,"Mensagem *",saved==null?"Abra o EasyVacc para consultar seus compromissos.":saved.getString("message",""),5102,500);message.setSingleLine(false);message.setMinLines(2);
        chosen=saved==null?0:saved.getLong("when",0);
        date=button(body,"Escolher data e horário *",this::pick);date.setId(5103);updateDate();
        button(body,"Agendar lembrete",()->{ReminderRules.validate(title.getText().toString(),message.getText().toString(),chosen,System.currentTimeMillis());int id=ReminderScheduler.create(this,title.getText().toString(),message.getText().toString(),chosen);chosen=0;updateDate();notice.setText("Lembrete #"+id+" agendado para horário aproximado. O Android pode atrasar a entrega por economia de bateria.");refresh();});
        text(body,"Meus lembretes",22);button(body,"Atualizar lista",this::refresh);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        text(body,"Após reiniciar o celular ou atualizar o app, recrie os lembretes pendentes. Forçar parada pode impedir o disparo. Sem alarmes exatos, recorrência ou sincronização. Publicada significa enviada ao Android, não lida. Não use para alertas médicos ou emergências.",13);
        button(body,"Voltar ao EasyVacc",()->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        setContentView(root);root.requestApplyInsets();refresh();
    }
    private void updateDate(){date.setText(chosen==0?"Escolher data e horário *":format.format(new Date(chosen)));}
    private void pick(){Calendar c=Calendar.getInstance();if(chosen>0)c.setTimeInMillis(chosen);else c.add(Calendar.MINUTE,5);new DatePickerDialog(this,(view,y,m,d)->{Calendar candidate=(Calendar)c.clone();candidate.set(y,m,d);new TimePickerDialog(this,(clock,h,min)->{candidate.set(Calendar.HOUR_OF_DAY,h);candidate.set(Calendar.MINUTE,min);candidate.set(Calendar.SECOND,0);candidate.set(Calendar.MILLISECOND,0);chosen=candidate.getTimeInMillis();updateDate();},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void refresh(){if(list==null)return;access.setText(ReminderScheduler.allowed(this)?"Notificações permitidas neste aparelho.":"Notificações bloqueadas. Permitir não cria um lembrete automaticamente.");list.removeAllViews();try(ReminderStore store=new ReminderStore(this)){
        List<ReminderStore.Item> reminders=store.all();if(reminders.isEmpty())text(list,"Nenhum lembrete cadastrado.",15);
        int opened=getIntent().getIntExtra("reminder_id",-1);
        for(ReminderStore.Item item:reminders){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);list.addView(card,params);
            text(card,(item.id==opened?"ABERTO PELA NOTIFICAÇÃO · ":"")+"#"+item.id+" · "+item.title,18);text(card,item.message,15);text(card,format.format(new Date(item.when))+" · "+item.status,14);
            if(item.published>0)text(card,"Enviada ao Android em "+format.format(new Date(item.published)),13);
            if(item.status.equals("Agendado")&&item.when<System.currentTimeMillis())text(card,"Horário previsto já passou. A entrega pode estar atrasada ou impedida pelo sistema; não há confirmação de publicação.",13);
            if(!item.status.equals("Cancelado"))button(card,"Cancelar lembrete #"+item.id,()->new AlertDialog.Builder(this).setTitle("Cancelar lembrete?").setMessage("O histórico será mantido e o alarme será cancelado.").setNegativeButton("Voltar",null).setPositiveButton("Cancelar lembrete",(d,w)->{try{ReminderScheduler.cancel(this,item.id);notice.setText("Lembrete #"+item.id+" cancelado. Confira o status na lista.");refresh();}catch(Exception e){notice.setText("Falha ao cancelar: "+e.getMessage());}}).show());
            if(item.status.startsWith("Reagendar")||item.status.startsWith("Bloqueado")||item.status.startsWith("Falha"))button(card,"Preparar novo lembrete",()->{title.setText(item.title);message.setText(item.message);chosen=0;updateDate();notice.setText("Escolha uma nova data futura e toque em Agendar. O histórico anterior será mantido.");});
        }
    }}
    protected void onResume(){super.onResume();refresh();}
    protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("title",title.getText().toString());out.putString("message",message.getText().toString());out.putLong("when",chosen);}
    public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==301){notice.setText(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Permissão concedida. Toque em Agendar para criar o lembrete.":"Permissão não concedida. Nenhum lembrete foi criado. Você pode abrir as configurações.");refresh();}}
}
