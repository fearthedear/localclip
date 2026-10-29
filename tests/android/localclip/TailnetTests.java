package localclip;
import android.app.*;
import android.content.*;
import android.os.*;
import java.net.*;
import java.util.*;

/** Emulator only: fixture pairing; 100.90.0.1/32 and .2/32 are test loopback aliases. */
public final class TailnetTests extends Instrumentation {
    int count;Context c;
    interface Condition {boolean ok()throws Exception;}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception("FAILED "+label+"; "+App.status+"; "+App.detail);count++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    void await(String label,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(!condition.ok()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);check(condition.ok(),label);}
    void offer(byte[] packet)throws Exception{try(DatagramSocket s=new DatagramSocket(new InetSocketAddress("100.90.0.1",0))){s.send(new DatagramPacket(packet,packet.length,InetAddress.getByName("100.90.0.2"),TailnetDiscovery.PORT));}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();DiscoveryTests.Proxy proxy=null;PeerDiscovery routes=null;
        try{
            c=getTargetContext();byte[] key=Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
            byte[] beacon=Wire.tailnetBeacon(key,Wire.random(32),"100.90.0.1",47493);
            TailnetDiscovery verifier=new TailnetDiscovery(key,(host,port)->{});
            check(verifier.accept("100.90.0.1",beacon)==47493,"paired tailnet beacon accepted");
            check(verifier.accept("100.90.0.1",beacon)==0,"recent duplicate nonce ignored");
            check(verifier.accept("100.90.0.2",Wire.tailnetBeacon(key,Wire.random(32),"100.90.0.1",47493))==0,"relayed beacon from another tailnet peer rejected");
            check(verifier.accept("192.168.1.10",Wire.tailnetBeacon(key,Wire.random(32),"100.90.0.1",47493))==0,"LAN source cannot advertise a tailnet route");
            byte[] wrong=key.clone();wrong[0]^=1;
            check(verifier.accept("100.90.0.1",Wire.tailnetBeacon(wrong,Wire.random(32),"100.90.0.1",47493))==0,"unpaired tailnet beacon rejected");
            verifier.close();check(verifier.accept("100.90.0.1",Wire.tailnetBeacon(key,Wire.random(32),"100.90.0.1",47493))==0,"closed discovery ignores future beacons");
            routes=new PeerDiscovery(c,key,()->{});Config config=new Config("192.0.2.10",47391,key,Arrays.asList("192.0.2.10","mac.tail123.ts.net"));
            check(routes.candidates(config).get(0).host.equals("mac.tail123.ts.net"),"saved MagicDNS route precedes Wi-Fi addresses");
            routes.offerTailnet("100.90.0.1",47493);check(routes.candidates(config).get(0).host.equals("100.90.0.1")&&routes.candidates(config).get(0).network==null,"fresh tailnet route wins and respects VPN routing");routes.close();routes=null;
            proxy=new DiscoveryTests.Proxy();int port=proxy.server.getLocalPort();
            org.json.JSONObject json=new org.json.JSONObject();json.put("v",1);json.put("host","127.0.0.1");json.put("port",port);json.put("key",Wire.hex(key));
            Config.save(c,"localclip1:"+android.util.Base64.encodeToString(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP));
            c.getSharedPreferences("settings",0).edit().putBoolean("automatic",false).putBoolean("adbBridge",false).commit();
            startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->c.startForegroundService(new Intent(c,SyncService.class)));
            await("fallback connection initially authenticates",()->App.service!=null&&App.service.connected());
            Thread.sleep(500);offer(Wire.tailnetBeacon(wrong,Wire.random(32),"100.90.0.1",port));Thread.sleep(400);
            check(App.service.connectedAddress().startsWith("127.0.0.1:"),"wrong-key UDP cannot replace the connected route");
            byte[] valid=Wire.tailnetBeacon(key,Wire.random(32),"100.90.0.1",port);offer(valid);
            await("real authenticated UDP promotes the tailnet route over the active fallback",()->App.service.connected()&&App.service.connectedAddress().equals("100.90.0.1:"+port));
            await("authenticated route remembered without replacing the pairing key",()->{Config saved=Config.load(c);return saved.host.equals("100.90.0.1")&&saved.port==port&&Arrays.equals(saved.key,key)&&saved.hosts.contains("127.0.0.1");});
            App.detail="Waiting for tailnet round trip";App.service.send(new Wire.Message(1,"test:tailnet-beacon".getBytes(java.nio.charset.StandardCharsets.UTF_8)),null);
            await("discovered route completes an encrypted Swift round trip",()->App.detail.startsWith("Received text"));
            offer(valid);Thread.sleep(300);check(App.service.connected(),"duplicate UDP does not disrupt the authenticated connection");
            Config saved=Config.load(c);Config.rememberTailnet(c,new Config(saved.host,saved.port,wrong),"100.90.0.2",port);check(Config.load(c).host.equals(saved.host),"old or mismatched pairing cannot overwrite a remembered route");
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));await("disconnect stops tailnet discovery",()->App.service==null);
            runOnMainSync(()->c.startForegroundService(new Intent(c,SyncService.class)));
            await("remembered tailnet route reconnects after restart without a new beacon",()->App.service!=null&&App.service.connected()&&App.service.connectedAddress().equals("100.90.0.1:"+port));
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));await("final disconnect completes",()->App.service==null);Config.forget(c);
            result.putString("stream","Android tailnet discovery: "+count+" assertions passed on API "+Build.VERSION.SDK_INT+" (synthetic loopback aliases, real UDP/TCP/Swift)\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(routes!=null)routes.close();if(proxy!=null)try{proxy.close();}catch(Exception ignored){}}
    }
}
