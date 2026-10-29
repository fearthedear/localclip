package localclip;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.JSONObject;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

final class Config {
    final String host; final int port; final byte[] key;final java.util.List<String> hosts;
    Config(String host,int port,byte[] key) { this(host,port,key,java.util.Collections.singletonList(host)); }
    Config(String host,int port,byte[] key,java.util.List<String> hosts) { this.host=host;this.port=port;this.key=key;this.hosts=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(hosts)); }
    static boolean validHost(String h){return !h.isEmpty()&&h.length()<=253&&!h.contains("/")&&h.chars().noneMatch(ch->Character.isWhitespace(ch)||Character.isISOControl(ch))&&(h.indexOf(':')<0||h.indexOf(':')!=h.lastIndexOf(':'));}
    static Config parse(String code) throws Exception {
        if(code.length()>2048||!code.startsWith("localclip1:")) throw new Exception("Paste a LocalClip pairing code from your Mac");
        JSONObject o=new JSONObject(new String(Base64.decode(code.substring(11),Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8));
        if(o.getInt("v")!=1) throw new Exception("Unsupported pairing version");
        String h=o.getString("host"); int p=o.getInt("port");
        if(!validHost(h)||p<1024||p>65535) throw new Exception("Invalid host or port");
        java.util.List<String> hosts=new java.util.ArrayList<>();hosts.add(h);
        if(o.has("hosts")){org.json.JSONArray a=o.getJSONArray("hosts");if(a.length()>6)throw new Exception("Too many Mac addresses");for(int i=0;i<a.length();i++){String v=a.getString(i);if(!validHost(v))throw new Exception("Invalid Mac address");if(!hosts.contains(v))hosts.add(v);}}
        return new Config(h,p,Wire.unhex(o.getString("key")),hosts);
    }
    static void updateHost(Context c,String host)throws Exception{
        Config current=load(c);if(current==null)throw new Exception("Pair with your Mac first");
        if(!validHost(host))throw new Exception("Enter a Mac IP address or Tailscale MagicDNS name, without a port");
        JSONObject json=new JSONObject();json.put("v",1);json.put("host",host);json.put("port",current.port);json.put("key",Wire.hex(current.key));
        save(c,"localclip1:"+Base64.encodeToString(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),Base64.NO_WRAP));
    }
    static void rememberTailnet(Context c,Config connected,String host,int port)throws Exception{
        if(!Wire.tailnetIP(host))return;Config current=load(c);
        if(current==null||!java.util.Arrays.equals(current.key,connected.key))return;
        java.util.List<String> hosts=new java.util.ArrayList<>();hosts.add(host);
        for(String old:current.hosts)if(!hosts.contains(old)&&hosts.size()<6)hosts.add(old);
        if(current.host.equals(host)&&current.hosts.equals(hosts))return;
        JSONObject json=new JSONObject();json.put("v",1);json.put("host",host);json.put("hosts",new org.json.JSONArray(hosts));json.put("port",port);json.put("key",Wire.hex(current.key));
        save(c,"localclip1:"+Base64.encodeToString(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),Base64.NO_WRAP));
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
