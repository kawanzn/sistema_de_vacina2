package br.com.easyvacc.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.Date;

/** Device tests operate exclusively on instrumentation_test.db, never on user records. */
public final class NativeInstrumentation extends Instrumentation {
    private Db db; private StringBuilder report=new StringBuilder();private int count=0;
    interface Check {void run()throws Exception;}
    private void check(String label,Check action)throws Exception{action.run();count++;report.append("APROVADO ").append(label).append('\n');}
    private void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
    private void rejects(Check action){try{action.run();}catch(Exception expected){return;}throw new AssertionError("A operação deveria ser recusada.");}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            getTargetContext().deleteDatabase("instrumentation_test.db");db=new Db(getTargetContext(),"instrumentation_test.db");
            check("DB vazia",()->eq(db.count("patients"),0));
            check("Senha protegida e login local",()->{db.setup("Profissional fictício","teste","Ficticia-2026");if(!db.authenticate("teste","Ficticia-2026")||db.authenticate("teste","errada"))throw new AssertionError();});
            long post=db.post("Unidade TESTE-001","Rua Fictícia, 10","Centro","28990000");
            long patient=db.patient(0,"Paciente TESTE-001","01/01/2000","11144477735","","","","",0);
            long child=db.patient(0,"Dependente TESTE-002","01/01/2020","","","","","",patient);
            long lot=db.stock("Vacina TESTE","LOTE-001","5","31/12/2099","Fictício",post);
            check("Paciente e dependente persistidos",()->{eq(db.count("patients"),2);eq(db.row("patients",child).n("guardian_id"),patient);});
            check("CPF duplicado rejeitado",()->rejects(()->db.patient(0,"Outra pessoa fictícia","01/01/2000","11144477735","","","","",0)));
            check("Edição de cadastro inexistente não anuncia salvamento",()->rejects(()->db.patient(999,"Pessoa removida","01/01/2000","","","","","",0)));
            check("Consulta por identificação e nascimento",()->eq(db.identify("111.444.777-35","01/01/2000").id(),patient));
            check("Nascimento divergente rejeitado",()->rejects(()->db.identify("11144477735","02/01/2000")));
            check("Bairro e CEP formatado",()->{eq(db.posts("centro").size(),1);eq(db.posts("28990-000").size(),1);eq(db.posts("inexistente").size(),0);});
            check("Dose baixa estoque e aparece na caderneta",()->{db.dose(patient,lot);eq(db.row("stock",lot).n("qty"),4);eq(db.doses(patient).size(),1);});
            String future=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.ROOT).format(new Date(System.currentTimeMillis()+86400000L));
            long appointment=db.schedule(patient,lot,future);
            check("Agendamento reserva sem baixar quantidade total",()->{eq(db.row("stock",lot).n("qty"),4);eq(db.row("stock",lot).n("reserved"),1);});
            check("Agendamento duplicado não duplica reserva",()->{rejects(()->db.schedule(patient,lot,future));eq(db.row("stock",lot).n("reserved"),1);});
            check("Confirmar agendamento registra dose uma vez",()->{db.appointmentAction(appointment,"concluido","");eq(db.row("stock",lot).n("qty"),3);eq(db.row("stock",lot).n("reserved"),0);eq(db.doses(patient).size(),2);});
            check("Repetir confirmação não altera estoque",()->{rejects(()->db.appointmentAction(appointment,"concluido",""));eq(db.row("stock",lot).n("qty"),3);eq(db.doses(patient).size(),2);});
            long cancel=db.schedule(patient,lot,future);
            check("Cancelar libera somente a reserva",()->{db.appointmentAction(cancel,"cancelado","Teste de cancelamento");eq(db.row("stock",lot).n("reserved"),0);eq(db.row("stock",lot).n("qty"),3);});
            check("Repetir cancelamento não reduz reserva",()->{rejects(()->db.appointmentAction(cancel,"cancelado","Teste"));eq(db.row("stock",lot).n("reserved"),0);});
            check("Lote totalmente reservado não permite dose livre",()->{db.getWritableDatabase().execSQL("UPDATE stock SET reserved=qty WHERE id=?",new Object[]{lot});rejects(()->db.dose(patient,lot));eq(db.row("stock",lot).n("qty"),3);db.getWritableDatabase().execSQL("UPDATE stock SET reserved=0 WHERE id=?",new Object[]{lot});});
            check("Lote vencido bloqueia aplicação",()->{db.getWritableDatabase().execSQL("UPDATE stock SET expiry='2000-01-01' WHERE id=?",new Object[]{lot});rejects(()->db.dose(patient,lot));eq(db.row("stock",lot).n("qty"),3);db.getWritableDatabase().execSQL("UPDATE stock SET expiry='2099-12-31' WHERE id=?",new Object[]{lot});});
            String backup=db.backup();
            check("Restaurar CPF e CEP formatados mantém consulta e busca",()->{JSONObject formatted=new JSONObject(backup);formatted.getJSONArray("posts").getJSONObject(0).put("zip","28990-000");for(int i=0;i<formatted.getJSONArray("patients").length();i++){JSONObject p=formatted.getJSONArray("patients").getJSONObject(i);if(p.getLong("id")==patient)p.put("cpf","111.444.777-35");}db.restore(formatted.toString());eq(db.identify("11144477735","01/01/2000").id(),patient);eq(db.posts("28990000").size(),1);db.restore(backup);});
            check("Backup e restauração preservam vínculos e login",()->{db.restore(backup);eq(db.count("patients"),2);eq(db.doses(patient).size(),2);eq(db.row("patients",child).n("guardian_id"),patient);if(!db.authenticate("teste","Ficticia-2026"))throw new AssertionError();});
            check("Backup inválido não destrói registros",()->{JSONObject corrupt=new JSONObject(backup);corrupt.getJSONArray("stock").getJSONObject(0).put("qty","-1");rejects(()->db.restore(corrupt.toString()));eq(db.count("patients"),2);eq(db.row("stock",lot).n("qty"),3);});
            check("Relatório CSV contém histórico",()->{String csv=db.report();if(!csv.startsWith("\ufeffPaciente;")||!csv.contains("LOTE-001"))throw new AssertionError();});
            check("Nova instância mantém dados",()->{db.close();db=new Db(getTargetContext(),"instrumentation_test.db");eq(db.count("patients"),2);eq(db.count("doses"),2);});
            check("Fila de sync persiste na mesma transação SQLite",()->{db.syncValue("pending","requisição-fictícia");db.close();db=new Db(getTargetContext(),"instrumentation_test.db");if(!db.syncValue("pending").equals("requisição-fictícia"))throw new AssertionError();});
            check("Novos registros usam IDs distintos entre instalações",()->{if(patient<1000000000000L||post<1000000000000L)throw new AssertionError();});
            check("Vacinas são filtradas por unidade",()->{eq(db.freeStock(post).size(),1);eq(db.freeStock(post+1).size(),0);});
            JSONObject base=SyncEngine.normalized(new JSONObject(db.backup()));
            check("Merge recebe edição remota e mantém nova edição local independente",()->{JSONObject local=new JSONObject(base.toString()),remote=new JSONObject(base.toString());local.getJSONArray("patients").getJSONObject(0).put("address","Rua offline fictícia");remote.getJSONArray("posts").getJSONObject(0).put("name","Unidade editada");JSONObject merged=SyncEngine.merge(base,local,remote);if(!merged.getJSONArray("patients").getJSONObject(0).getString("address").equals("Rua offline fictícia")||!merged.getJSONArray("posts").getJSONObject(0).getString("name").equals("Unidade editada"))throw new AssertionError();});
            check("Merge recusa duas alterações incompatíveis no mesmo lote",()->{JSONObject local=new JSONObject(base.toString()),remote=new JSONObject(base.toString());local.getJSONArray("stock").getJSONObject(0).put("qty","2");remote.getJSONArray("stock").getJSONObject(0).put("qty","1");rejects(()->SyncEngine.merge(base,local,remote));});
            Activity main=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            check("Tela inicial é nativa e abre sem site",()->{if(!hasText(main.getWindow().getDecorView(),"EasyVacc"))throw new AssertionError("Marca não encontrada.");});
            check("CEP residencial 28994160 encontra postos sem internet",()->{PublicDirectory directory=new PublicDirectory(getTargetContext());java.util.List<Db.Row> units=directory.all(db,"28994160");if(units.size()<21||!Rules.key(units.get(0).s("name")).contains("bacaxa"))throw new AssertionError("Região de Bacaxá não localizada.");});
            runOnMainSync(()->clickText(main.getWindow().getDecorView(),"Entrar no portal"));waitForIdleSync();
            runOnMainSync(()->clickText(main.getWindow().getDecorView(),"Agendar nova dose"));waitForIdleSync();
            check("Botão do portal abre tela própria de agendamento",()->{if(!hasText(main.getWindow().getDecorView(),"Agendar Nova Dose"))throw new AssertionError("Agendamento não abriu.");});
            runOnMainSync(main::finish);
            report.append("TOTAL: ").append(count).append(" verificações Android aprovadas.\n");result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
        }catch(Throwable error){report.append("FALHA: ").append(error.toString()).append('\n');result.putString("stream",report.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{if(db!=null)db.close();getTargetContext().deleteDatabase("instrumentation_test.db");}
    }
    private boolean hasText(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(hasText(g.getChildAt(i),value))return true;}return false;}
    private boolean clickText(View view,String value){if(view instanceof android.widget.Button&&((TextView)view).getText().toString().equals(value)){view.performClick();return true;}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(clickText(g.getChildAt(i),value))return true;}return false;}
}
