package br.com.easyvacc.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Db extends SQLiteOpenHelper {
    public static final class Row extends LinkedHashMap<String,String> {
        public String s(String key) { String v=get(key); return v==null ? "" : v; }
        public long id() { return n("id"); }
        public long n(String key) { try { return Long.parseLong(s(key)); } catch(Exception e) { return 0; } }
        @Override public String toString() { return s("name"); }
    }
    public Db(Context context) { this(context,"easyvacc_native.db"); }
    Db(Context context,String name) { super(context,name,null,2); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sync_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE account(id INTEGER PRIMARY KEY CHECK(id=1),name TEXT NOT NULL,login TEXT NOT NULL UNIQUE,password TEXT NOT NULL)");
        db.execSQL("CREATE TABLE patients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,name_key TEXT NOT NULL,dob TEXT NOT NULL,cpf TEXT UNIQUE,sus TEXT UNIQUE,email TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',guardian_id INTEGER REFERENCES patients(id),CHECK(guardian_id IS NULL OR guardian_id<>id))");
        db.execSQL("CREATE TABLE posts(id INTEGER PRIMARY KEY,name TEXT NOT NULL,address TEXT NOT NULL,district TEXT NOT NULL,zip TEXT NOT NULL)");
        db.execSQL("CREATE TABLE stock(id INTEGER PRIMARY KEY,name TEXT NOT NULL,lot TEXT NOT NULL,qty INTEGER NOT NULL CHECK(qty>=0),reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0 AND reserved<=qty),expiry TEXT NOT NULL,supplier TEXT NOT NULL,post_id INTEGER NOT NULL REFERENCES posts(id),UNIQUE(post_id,name,lot))");
        db.execSQL("CREATE TABLE appointments(id INTEGER PRIMARY KEY,patient_id INTEGER NOT NULL REFERENCES patients(id),stock_id INTEGER NOT NULL REFERENCES stock(id),when_at INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('pendente','concluido','cancelado','ausente')),reason TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE UNIQUE INDEX one_pending_lot ON appointments(patient_id,stock_id) WHERE status='pendente'");
        db.execSQL("CREATE TABLE doses(id INTEGER PRIMARY KEY,patient_id INTEGER NOT NULL REFERENCES patients(id),stock_id INTEGER NOT NULL REFERENCES stock(id),post_id INTEGER NOT NULL REFERENCES posts(id),name TEXT NOT NULL,lot TEXT NOT NULL,applied_at INTEGER NOT NULL,appointment_id INTEGER UNIQUE REFERENCES appointments(id),status TEXT NOT NULL DEFAULT 'ativa' CHECK(status IN ('ativa','anulada')),reason TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX patient_doses ON doses(patient_id,applied_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int current) { if(old<2)db.execSQL("CREATE TABLE IF NOT EXISTS sync_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)"); }
    public String syncValue(String key){List<Row> found=rows("SELECT value FROM sync_state WHERE key=?",key);return found.isEmpty()?"":found.get(0).s("value");}
    public void syncValue(String key,String value){ContentValues data=new ContentValues();data.put("key",key);data.put("value",value);getWritableDatabase().insertWithOnConflict("sync_state",null,data,SQLiteDatabase.CONFLICT_REPLACE);}
    private long insertRecord(SQLiteDatabase db,String table,ContentValues values){values.put("id",1000000000000L+(new java.security.SecureRandom().nextLong()&0x000fffffffffffffL));return db.insertOrThrow(table,null,values);}
    public List<Row> rows(String sql, String... args) {
        ArrayList<Row> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {
            while(c.moveToNext()) { Row r=new Row(); for(int i=0;i<c.getColumnCount();i++) r.put(c.getColumnName(i),c.isNull(i)?null:c.getString(i)); list.add(r); }
        } return list;
    }
    public Row row(String table,long id) {
        if(!table.matches("patients|posts|stock|appointments|doses")) throw new IllegalArgumentException();
        List<Row> list=rows("SELECT * FROM "+table+" WHERE id=?",String.valueOf(id));
        if(list.isEmpty()) throw new IllegalArgumentException("Registro não encontrado."); return list.get(0);
    }
    public long count(String table) {
        if(!table.matches("patients|posts|stock|appointments|doses|account")) throw new IllegalArgumentException();
        return DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM "+table,null);
    }
    public boolean hasAccount() { return count("account")>0; }
    public void setup(String name,String login,String password) {
        if(hasAccount()) throw new IllegalArgumentException("O acesso profissional já foi configurado.");
        ContentValues v=new ContentValues(); v.put("id",1); v.put("name",Rules.required(name,"nome",100));
        v.put("login",Rules.key(Rules.required(login,"usuário",60))); v.put("password",Secrets.hash(password));
        getWritableDatabase().insertOrThrow("account",null,v);
    }
    public boolean authenticate(String login,String password) {
        List<Row> a=rows("SELECT * FROM account WHERE login=?",Rules.key(login));
        return !a.isEmpty() && Secrets.matches(password,a.get(0).s("password"));
    }
    public String professionalName() { List<Row> a=rows("SELECT name FROM account LIMIT 1"); return a.isEmpty()?"Profissional":a.get(0).s("name"); }
    public void changePassword(String current,String next) {
        Row a=rows("SELECT * FROM account LIMIT 1").get(0);
        if(!Secrets.matches(current,a.s("password"))) throw new IllegalArgumentException("Senha atual incorreta.");
        ContentValues v=new ContentValues();v.put("password",Secrets.hash(next)); getWritableDatabase().update("account",v,"id=1",null);
    }
    public long patient(long id,String name,String dob,String cpf,String sus,String email,String phone,String address,long guardian) {
        ContentValues v=new ContentValues(); String n=Rules.required(name,"nome",200);
        v.put("name",n);v.put("name_key",Rules.key(n)); String born=Rules.iso(dob);
        if(born.compareTo(Rules.today())>0 || born.compareTo("1900-01-01")<0) throw new IllegalArgumentException("Confira a data de nascimento (1900 até hoje).");
        v.put("dob",born); String c=Rules.cpf(cpf); if(c.isEmpty()) v.putNull("cpf"); else v.put("cpf",c);
        String s=Rules.digits(sus); if(!s.isEmpty() && s.length()!=15) throw new IllegalArgumentException("O cartão SUS deve ter 15 dígitos.");
        if(s.isEmpty())v.putNull("sus");else v.put("sus",s);
        if(!Rules.text(email).isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(Rules.text(email)).matches()) throw new IllegalArgumentException("Confira o e-mail.");
        v.put("email",Rules.text(email));v.put("phone",Rules.text(phone));v.put("address",Rules.text(address));
        if(guardian>0) {
            Row parent=row("patients",guardian);
            if(guardian==id || parent.n("guardian_id")>0) throw new IllegalArgumentException("Escolha um responsável sem vínculo de dependência.");
            if(id>0 && !rows("SELECT id FROM patients WHERE guardian_id=?",String.valueOf(id)).isEmpty()) throw new IllegalArgumentException("Uma pessoa com dependentes não pode ser vinculada como dependente.");
            v.put("guardian_id",guardian);
        } else v.putNull("guardian_id");
        if(id>0) { getWritableDatabase().update("patients",v,"id=?",new String[]{String.valueOf(id)}); return id; }
        return insertRecord(getWritableDatabase(),"patients",v);
    }
    public long post(String name,String address,String district,String zip) {
        ContentValues v=new ContentValues();v.put("name",Rules.required(name,"nome da unidade",100));v.put("address",Rules.required(address,"endereço",255));
        v.put("district",Rules.required(district,"bairro",100));String z=Rules.digits(zip);if(z.length()!=8)throw new IllegalArgumentException("O CEP deve ter 8 dígitos.");v.put("zip",z);
        return insertRecord(getWritableDatabase(),"posts",v);
    }
    public long stock(String name,String lot,String amount,String expiry,String supplier,long post) {
        row("posts",post);ContentValues v=new ContentValues();v.put("name",Rules.required(name,"nome da vacina",100));v.put("lot",Rules.required(lot,"lote",50));
        v.put("qty",Rules.quantity(amount,true));v.put("reserved",0);String expires=Rules.iso(expiry);
        if(expires.compareTo(Rules.today())<0)throw new IllegalArgumentException("Não cadastre um lote vencido.");
        v.put("expiry",expires);v.put("supplier",Rules.text(supplier));v.put("post_id",post);
        return insertRecord(getWritableDatabase(),"stock",v);
    }
    public void addStock(long id,String amount) {
        Row lot=row("stock",id);int n=Rules.quantity(amount,false);
        if(lot.n("qty")+n>1000000)throw new IllegalArgumentException("O lote excederia 1.000.000 de doses.");
        if(lot.s("expiry").compareTo(Rules.today())<0)throw new IllegalArgumentException("Lote vencido. Cadastre um novo lote vigente.");
        getWritableDatabase().execSQL("UPDATE stock SET qty=qty+? WHERE id=?",new Object[]{n,id});
    }
    public List<Row> posts(String query) {
        ArrayList<Row> result=new ArrayList<>();String q=Rules.key(query);String z=Rules.digits(query);
        for(Row p:rows("SELECT * FROM posts ORDER BY name COLLATE NOCASE"))
            if(q.isEmpty() || Rules.key(p.s("name")+" "+p.s("district")+" "+p.s("address")).contains(q) || (!z.isEmpty() && p.s("zip").contains(z)))result.add(p);
        return result;
    }
    public List<Row> freeStock() {
        return rows("SELECT s.*,p.name AS post_name FROM stock s JOIN posts p ON p.id=s.post_id WHERE s.qty>s.reserved AND s.expiry>=? ORDER BY s.name,s.expiry",Rules.today());
    }
    public List<Row> freeStock(long post) {
        return rows("SELECT s.*,p.name AS post_name FROM stock s JOIN posts p ON p.id=s.post_id WHERE s.post_id=? AND s.qty>s.reserved AND s.expiry>=? ORDER BY s.name,s.expiry",String.valueOf(post),Rules.today());
    }
    public List<Row> applications(){
        return rows("SELECT d.*,p.name AS patient_name,p.cpf,o.name AS post_name FROM doses d JOIN patients p ON p.id=d.patient_id JOIN posts o ON o.id=d.post_id ORDER BY d.applied_at DESC,d.id DESC");
    }
    private long insertDose(SQLiteDatabase db,long patient,Row lot,Long appointment) {
        ContentValues v=new ContentValues();v.put("patient_id",patient);v.put("stock_id",lot.id());v.put("post_id",lot.n("post_id"));v.put("name",lot.s("name"));v.put("lot",lot.s("lot"));v.put("applied_at",System.currentTimeMillis());
        if(appointment!=null)v.put("appointment_id",appointment);return insertRecord(db,"doses",v);
    }
    public long dose(long patient,long stock) {
        row("patients",patient);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Row lot=row("stock",stock);
            if(!Rules.available((int)lot.n("qty"),(int)lot.n("reserved"),lot.s("expiry"),Rules.today()))throw new IllegalArgumentException("Lote vencido ou sem doses livres.");
            ContentValues v=new ContentValues();v.put("qty",lot.n("qty")-1);
            db.update("stock",v,"id=?",new String[]{String.valueOf(stock)});long id=insertDose(db,patient,lot,null);db.setTransactionSuccessful();return id;
        } finally {db.endTransaction();}
    }
    public long schedule(long patient,long stock,String when) {
        row("patients",patient);long time=Rules.date(when,true).getTime();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Row lot=row("stock",stock);String day=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date(time));
            Rules.appointment(time,System.currentTimeMillis(),lot.s("expiry"),day);
            if(!Rules.available((int)lot.n("qty"),(int)lot.n("reserved"),lot.s("expiry"),Rules.today()))throw new IllegalArgumentException("Não há doses livres neste lote.");
            ContentValues a=new ContentValues();a.put("patient_id",patient);a.put("stock_id",stock);a.put("when_at",time);a.put("status","pendente");long id=insertRecord(db,"appointments",a);
            db.execSQL("UPDATE stock SET reserved=reserved+1 WHERE id=?",new Object[]{stock});db.setTransactionSuccessful();return id;
        } finally {db.endTransaction();}
    }
    public void appointmentAction(long id,String action,String reason) {
        if(!action.matches("concluido|cancelado|ausente"))throw new IllegalArgumentException("Ação inválida.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Row a=row("appointments",id);if(!a.s("status").equals("pendente"))throw new IllegalArgumentException("Este agendamento já foi encerrado.");
            Row lot=row("stock",a.n("stock_id"));if(lot.n("reserved")<1)throw new IllegalArgumentException("Reserva inconsistente. Confira o estoque.");
            if(action.equals("concluido")) {
                if(lot.n("qty")<1 || lot.s("expiry").compareTo(Rules.today())<0)throw new IllegalArgumentException("Lote vencido ou sem doses. Cancele a reserva e agende outro lote.");
                db.execSQL("UPDATE stock SET qty=qty-1,reserved=reserved-1 WHERE id=?",new Object[]{lot.id()});insertDose(db,a.n("patient_id"),lot,id);
            } else {reason=Rules.required(reason,"justificativa",500);db.execSQL("UPDATE stock SET reserved=reserved-1 WHERE id=?",new Object[]{lot.id()});}
            ContentValues v=new ContentValues();v.put("status",action);v.put("reason",Rules.text(reason));db.update("appointments",v,"id=?",new String[]{String.valueOf(id)});db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public Row identify(String identity,String birthday) {
        String dob=Rules.iso(birthday);String q=Rules.required(identity,"nome, CPF ou cartão SUS",200);List<Row> found;
        if(!Rules.digits(q).isEmpty() && Rules.digits(q).length()>=11)found=rows("SELECT * FROM patients WHERE (cpf=? OR sus=?) AND dob=?",Rules.digits(q),Rules.digits(q),dob);
        else found=rows("SELECT * FROM patients WHERE name_key=? AND dob=?",Rules.key(q),dob);
        if(found.size()!=1)throw new IllegalArgumentException(found.size()>1?"Há nomes iguais. Use CPF ou cartão SUS.":"Pessoa não encontrada. Confira a identificação e a data de nascimento.");return found.get(0);
    }
    public List<Row> doses(long patient) {return rows("SELECT d.*,p.name AS post_name FROM doses d JOIN posts p ON p.id=d.post_id WHERE d.patient_id=? ORDER BY d.applied_at DESC",String.valueOf(patient));}
    public List<Row> appointments(long patient,boolean pending) {
        String sql="SELECT a.*,p.name AS patient_name,s.name AS vaccine,s.lot,o.name AS post_name FROM appointments a JOIN patients p ON p.id=a.patient_id JOIN stock s ON s.id=a.stock_id JOIN posts o ON o.id=s.post_id WHERE "+(pending?"a.status='pendente'":"a.status<>'pendente'");
        if(patient>0)sql+=" AND a.patient_id="+patient;return rows(sql+" ORDER BY a.when_at "+(pending?"ASC":"DESC"));
    }
    public String report() {
        StringBuilder b=new StringBuilder("\ufeffPaciente;CPF;Vacina;Lote;Data de aplicação;Unidade;Situação\r\n");
        for(Row r:rows("SELECT d.*,p.name AS patient_name,p.cpf,o.name AS post_name FROM doses d JOIN patients p ON p.id=d.patient_id JOIN posts o ON o.id=d.post_id ORDER BY d.applied_at DESC")) {
            String[] fields={r.s("patient_name"),r.s("cpf"),r.s("name"),r.s("lot"),new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.ROOT).format(new java.util.Date(r.n("applied_at"))),r.s("post_name"),r.s("status")};
            for(int i=0;i<fields.length;i++){if(i>0)b.append(';');b.append(Rules.csv(fields[i]));}b.append("\r\n");
        } return b.toString();
    }
    public String backup() throws Exception {
        JSONObject result=new JSONObject();result.put("format","EasyVaccNative");result.put("version",1);result.put("created_at",System.currentTimeMillis());
        for(String table:new String[]{"posts","patients","stock","appointments","doses"}) {
            JSONArray values=new JSONArray();for(Row r:rows("SELECT * FROM "+table)) {JSONObject o=new JSONObject();for(Map.Entry<String,String> e:r.entrySet())o.put(e.getKey(),e.getValue()==null?JSONObject.NULL:e.getValue());values.put(o);}result.put(table,values);
        }return result.toString(2);
    }
    public void checkBackup(String text) throws Exception {
        JSONObject root=new JSONObject(text);
        if(!root.optString("format").equals("EasyVaccNative")||root.optInt("version")!=1)
            throw new IllegalArgumentException("Este arquivo não é um backup compatível do EasyVacc Android.");
        int total=0;
        for(String table:new String[]{"posts","patients","stock","appointments","doses"}) {
            JSONArray a=root.getJSONArray(table);total+=a.length();
            if(total>50000)throw new IllegalArgumentException("Backup excede o limite de 50.000 registros.");
            java.util.HashSet<Long> ids=new java.util.HashSet<>();
            for(int i=0;i<a.length();i++){JSONObject row=a.getJSONObject(i);long id=Long.parseLong(row.getString("id"));if(id<1||!ids.add(id))throw new IllegalArgumentException("Backup com identificadores inválidos ou duplicados.");}
        }
    }
    public void restore(String text) throws Exception {
        checkBackup(text);JSONObject data=new JSONObject(text);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(String t:new String[]{"doses","appointments","stock","patients","posts"})db.delete(t,null,null);
            String[][] columns={
                {"id","name","address","district","zip"},
                {"id","name","name_key","dob","cpf","sus","email","phone","address","guardian_id"},
                {"id","name","lot","qty","reserved","expiry","supplier","post_id"},
                {"id","patient_id","stock_id","when_at","status","reason"},
                {"id","patient_id","stock_id","post_id","name","lot","applied_at","appointment_id","status","reason"}
            };
            String[] tables={"posts","patients","stock","appointments","doses"};
            for(int t=0;t<tables.length;t++){
                JSONArray array=data.getJSONArray(tables[t]);
                for(int i=0;i<array.length();i++){
                    JSONObject r=array.getJSONObject(i);ContentValues values=new ContentValues();
                    for(String col:columns[t]){if(!r.has(col))throw new IllegalArgumentException("Backup incompleto: "+tables[t]+"/"+col);if(r.isNull(col)||col.equals("guardian_id"))values.putNull(col);else values.put(col,r.getString(col));}
                    if(t==0){Rules.required(r.getString("name"),"nome da unidade",100);Rules.required(r.getString("address"),"endereço",255);if(Rules.digits(r.getString("zip")).length()!=8)throw new IllegalArgumentException("CEP inválido no backup.");}
                    if(t==1){Rules.required(r.getString("name"),"nome da pessoa",200);String born=r.getString("dob");if(!Rules.iso(Rules.showDate(born)).equals(born)||born.compareTo(Rules.today())>0||born.compareTo("1900-01-01")<0)throw new IllegalArgumentException("Nascimento inválido no backup.");values.put("name_key",Rules.key(r.getString("name")));if(!r.isNull("cpf"))Rules.cpf(r.getString("cpf"));if(!r.isNull("sus")&&Rules.digits(r.getString("sus")).length()!=15)throw new IllegalArgumentException("SUS inválido no backup.");}
                    if(t==2){Rules.required(r.getString("name"),"vacina",100);Rules.required(r.getString("lot"),"lote",50);Rules.quantity(r.getString("qty"),true);Rules.quantity(r.getString("reserved"),true);String expiry=r.getString("expiry");if(!Rules.iso(Rules.showDate(expiry)).equals(expiry))throw new IllegalArgumentException("Validade inválida no backup.");}
                    if(t==3&&Long.parseLong(r.getString("when_at"))<=0)throw new IllegalArgumentException("Data de agendamento inválida no backup.");
                    if(t==4&&Long.parseLong(r.getString("applied_at"))<=0)throw new IllegalArgumentException("Data de aplicação inválida no backup.");
                    db.insertOrThrow(tables[t],null,values);
                }
            }
            JSONArray people=data.getJSONArray("patients");
            for(int i=0;i<people.length();i++){JSONObject p=people.getJSONObject(i);if(!p.isNull("guardian_id")){long parent=Long.parseLong(p.getString("guardian_id"));Row g=row("patients",parent);JSONObject original=null;for(int j=0;j<people.length();j++)if(people.getJSONObject(j).getLong("id")==parent)original=people.getJSONObject(j);if(parent==p.getLong("id")||g.n("guardian_id")>0||original==null||!original.isNull("guardian_id"))throw new IllegalArgumentException("Responsável inválido no backup.");ContentValues v=new ContentValues();v.put("guardian_id",parent);db.update("patients",v,"id=?",new String[]{p.getString("id")});}}
            if(!rows("PRAGMA foreign_key_check").isEmpty())throw new IllegalArgumentException("Backup com vínculos inexistentes.");
            for(Row s:rows("SELECT id,reserved FROM stock")){long count=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM appointments WHERE stock_id=? AND status='pendente'",new String[]{String.valueOf(s.id())});if(count!=s.n("reserved"))throw new IllegalArgumentException("Reservas de estoque inconsistentes no backup.");}
            if(!rows("SELECT a.id FROM appointments a LEFT JOIN doses d ON d.appointment_id=a.id WHERE a.status='concluido' AND d.id IS NULL").isEmpty())throw new IllegalArgumentException("Agendamento concluído sem aplicação no backup.");
            if(!rows("SELECT d.id FROM doses d JOIN appointments a ON a.id=d.appointment_id WHERE a.status<>'concluido' OR a.patient_id<>d.patient_id OR a.stock_id<>d.stock_id").isEmpty())throw new IllegalArgumentException("Aplicação incompatível com agendamento no backup.");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public void demo(){
        if(count("patients")>0||count("posts")>0)throw new IllegalArgumentException("A demonstração só pode ser carregada com cadastros vazios.");
        SQLiteDatabase data=getWritableDatabase();data.beginTransaction();
        try {long post=post("Unidade de Demonstração","Rua Exemplo, 100","Centro","28990000");patient(0,"Paciente de Demonstração","01/01/2000","","","","","Endereço fictício",0);stock("Vacina de demonstração","DEMO-001","10","31/12/2030","Dados fictícios",post);data.setTransactionSuccessful();}
        finally {data.endTransaction();}
    }
}
