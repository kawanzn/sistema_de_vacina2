package br.com.easyvacc.android;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Secrets {
    private Secrets() {}
    public static String hash(String password) {
        if (password == null || password.length() < 8 || password.length() > 128)
            throw new IllegalArgumentException("Use uma senha entre 8 e 128 caracteres.");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return hex(salt) + ":" + hex(derive(password, salt));
    }
    public static boolean matches(String password, String encoded) {
        try { String[] fields = encoded.split(":"); return fields.length == 2 && MessageDigest.isEqual(unhex(fields[1]), derive(password, unhex(fields[0]))); }
        catch (Exception e) { return false; }
    }
    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Não foi possível proteger a senha.", e); }
        finally { spec.clearPassword(); }
    }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for(byte b:bytes) s.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return s.toString(); }
    private static byte[] unhex(String s) { if(s.length()%2!=0) throw new IllegalArgumentException(); byte[] b=new byte[s.length()/2]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b; }
}
