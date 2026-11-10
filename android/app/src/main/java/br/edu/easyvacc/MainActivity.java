package br.edu.easyvacc;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;

public final class MainActivity extends Activity {
    private ReminderRepository repository;
    private ReminderScheduler scheduler;
    private LinearLayout list;
    private TextView feedback;
    private EditText title;
    private Button dateButton;
    private long selected;
    private EditText message;
    private final int teal = Color.rgb(0,136,121);
    private final int navy = Color.rgb(18,48,71);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        repository = new ReminderRepository(this); scheduler = new ReminderScheduler(this);
        selected = saved == null ? System.currentTimeMillis()+3600000 : saved.getLong("selected");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244,247,250));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(18),dp(20),dp(24));
        scroll.addView(root);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(dp(20),top+dp(18),dp(20),bottom+dp(24)); return insets;
        });
        root.addView(text("EasyVacc",30,true));
        root.addView(button("Buscar e compartilhar postos",() -> startActivity(new Intent(this,PostsActivity.class))));
        root.addView(text("Lembretes de vacinação",21,true));
        root.addView(text("Seus lembretes ficam somente neste aparelho. Não informe CPF ou dados de saúde no título ou na mensagem.",14,false));
        TextView warning = text("Versão didática: alarmes não exatos podem atrasar. Após reiniciar o aparelho, agende novamente os itens pausados. Não utilize para alertas críticos.",14,false);
        warning.setPadding(dp(12),dp(12),dp(12),dp(12)); warning.setBackground(card(Color.rgb(255,244,209))); root.addView(warning);

        LinearLayout form = panel(); root.addView(form);
        form.addView(text("Novo lembrete",19,true));
        title = new EditText(this); title.setHint("Conferir agendamento de vacinação"); title.setSingleLine(true); title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        title.setContentDescription("Título do lembrete"); form.addView(title);
        if (saved != null) title.setText(saved.getString("title", ""));
        message = new EditText(this); message.setHint("Abra o EasyVacc para conferir o compromisso");
        message.setContentDescription("Mensagem do lembrete");
        message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setMinLines(2); message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        form.addView(message); if (saved != null) message.setText(saved.getString("message", ""));
        dateButton = button("",this::chooseDate); updateDate(); form.addView(dateButton);
        form.addView(button("Permitir notificações",this::permission));
        form.addView(button("Configurações de notificações",() -> safely(() -> startActivity(
            new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())))));
        form.addView(button("Agendar lembrete",() -> safely(() -> {
            String heading=title.getText().toString(), body=message.getText().toString();
            long now=System.currentTimeMillis();
            Reminder.validate(heading,body,selected,now); requirePermission();
            Reminder item=repository.create(heading,body,selected,now);
            scheduler.schedule(item.id);
            title.setText(""); message.setText(""); render();
            say("Lembrete agendado. O Android pode atrasar a entrega.",false);
        })));
        form.addView(button("Testar notificação agora",() -> safely(() -> {
            requirePermission();
            AlarmReceiver.publish(this,"test","Teste EasyVacc","Toque para abrir os lembretes. Este teste não agenda um alarme.");
            say("Notificação de teste enviada ao Android.",false);
        })));
        feedback = text("",14,false); feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(feedback);
        root.addView(text("Meus lembretes",21,true));
        root.addView(button("Atualizar lista",this::render));
        root.addView(text("Cancelar aqui remove somente o lembrete. Sua consulta ou reserva no sistema permanece inalterada.",13,false));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        setContentView(scroll); render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putLong("selected",selected); out.putString("title",title.getText().toString());
        out.putString("message",message.getText().toString());
    }
    @Override protected void onResume() {
        super.onResume();
        render();
    }
    private void chooseDate() {
        Calendar calendar = Calendar.getInstance(); calendar.setTimeInMillis(selected);
        new DatePickerDialog(this,(view,year,month,day) -> {
            calendar.set(year,month,day);
            new TimePickerDialog(this,(clock,hour,minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY,hour); calendar.set(Calendar.MINUTE,minute); calendar.set(Calendar.SECOND,0); calendar.set(Calendar.MILLISECOND,0);
                selected = calendar.getTimeInMillis(); updateDate();
            },calendar.get(Calendar.HOUR_OF_DAY),calendar.get(Calendar.MINUTE),true).show();
        },calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH),calendar.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void updateDate() { dateButton.setText("Data e hora: " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(selected)); }
    private void requirePermission() {
        if (!scheduler.permitted()) throw new IllegalStateException("Permita as notificações e confira o canal nas configurações. Depois toque no botão novamente.");
    }
    private void permission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        else say(scheduler.permitted() ? "Notificações permitidas." : "Abra as configurações para habilitar o aplicativo e o canal.",!scheduler.permitted());
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request == 10) say(scheduler.permitted() ? "Permissão concedida. Toque em Agendar para criar o lembrete." :
            "Notificações não permitidas. Nenhum lembrete foi criado por esta ação.",!scheduler.permitted());
    }
    private void render() {
        if (list == null) return;
        list.removeAllViews();
        try {
            List<Reminder> items = repository.list();
            if (items.isEmpty()) list.addView(text("Nenhum lembrete criado.",15,false));
            for (Reminder item : items) {
                LinearLayout row = panel(); row.addView(text(item.title,18,true));
                row.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(item.at),14,false));
                row.addView(text(item.message,14,false));
                row.addView(text(item.state,15,true));
                if(item.notifiedAt>0)row.addView(text("Enviada ao Android: "+DateFormat.getDateTimeInstance().format(item.notifiedAt),13,false));
                if (Reminder.CREATED.equals(item.state) || Reminder.PAUSED.equals(item.state) || Reminder.FAILED.equals(item.state))
                    row.addView(button("Agendar",() -> safely(() -> {requirePermission();scheduler.schedule(item.id);render();say("Lembrete agendado.",false);})));
                if (!Reminder.CANCELLED.equals(item.state)) row.addView(button("Cancelar lembrete",() -> safely(() -> {
                    scheduler.cancel(item.id); say("Lembrete cancelado. Alarme e notificação removidos.",false); render();
                })));
                list.addView(row);
            }
        } catch (RuntimeException error) { say(error.getMessage(),true); }
    }
    private void safely(Runnable action) { try { action.run(); } catch (RuntimeException error) { say(error.getMessage(),true); render(); } }
    private void say(String message,boolean error) { feedback.setText(message); feedback.setTextColor(error ? Color.rgb(163,34,34) : teal); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size,boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(navy);
        view.setPadding(0,dp(7),0,dp(7)); if (bold) view.setTypeface(null,Typeface.BOLD); return view;
    }
    private Button button(String label,Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextColor(teal); button.setAllCaps(false);
        button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); return button;
    }
    private GradientDrawable card(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(14)); return shape; }
    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(14),dp(10),dp(14),dp(10)); panel.setBackground(card(Color.WHITE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(14),0,dp(10)); panel.setLayoutParams(params); return panel;
    }
}
