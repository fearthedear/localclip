package localclip;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.JSONObject;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

final class Config {
    final String host; final int port; final byte[] key;
    Config(String host,int port,byte[] key) { this.host=host;this.port=port;this.key=key; }
    static Config parse(String code) throws Exception {
        if(code.length()>2048||!code.startsWith("localclip1:")) throw new Exception("Paste a LocalClip pairing code from your Mac");
        JSONObject o=new JSONObject(new String(Base64.decode(code.substring(11),Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8));
        if(o.getInt("v")!=1) throw new Exception("Unsupported pairing version");
        String h=o.getString("host"); int p=o.getInt("port");
        if(h.isEmpty()||h.length()>253||h.contains("/")||h.matches(".*\\s.*")||p<1024||p>65535) throw new Exception("Invalid host or port");
        return new Config(h,p,Wire.unhex(o.getString("key")));
    }
    static SecretKey storageKey() throws Exception {
        KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);
        if(s.containsAlias("localclip-pairing")) return (SecretKey)s.getKey("localclip-pairing",null);
        KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder("localclip-pairing",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return g.generateKey();
    }
    static void save(Context c,String code) throws Exception {
        parse(code);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,storageKey());
        String encrypted=Base64.encodeToString(Wire.concat(cipher.getIV(),cipher.doFinal(code.getBytes(java.nio.charset.StandardCharsets.UTF_8))),Base64.NO_WRAP);
        if(!c.getSharedPreferences("pairing",0).edit().putString("encrypted",encrypted).commit()) throw new Exception("Cannot save pairing");
    }
    static Config load(Context c) throws Exception {
        String encoded=c.getSharedPreferences("pairing",0).getString("encrypted",null);if(encoded==null)return null;
        byte[] data=Base64.decode(encoded,Base64.NO_WRAP);if(data.length<28)throw new Exception("Invalid stored pairing");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,storageKey(),new GCMParameterSpec(128,java.util.Arrays.copyOf(data,12)));
        return parse(new String(cipher.doFinal(java.util.Arrays.copyOfRange(data,12,data.length)),java.nio.charset.StandardCharsets.UTF_8));
    }
    static void forget(Context c) throws Exception {
        c.getSharedPreferences("pairing",0).edit().clear().commit();KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);s.deleteEntry("localclip-pairing");
    }
}
