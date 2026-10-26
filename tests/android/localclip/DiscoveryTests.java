package localclip;
import android.app.*;
import android.content.*;
import android.net.nsd.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real DNS-SD registration/resolution plus encrypted Swift round trips behind changing routes. */
public final class DiscoveryTests extends Instrumentation {
    Context c;NsdManager nsd;MainActivity main;int assertions;byte[] key;
    interface Condition{boolean ok()throws Exception;}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception("FAILED "+label+"; "+App.status+"; "+App.detail);assertions++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    void await(String label,Condition condition)throws Exception{long until=SystemClock.elapsedRealtime()+20000;while(!condition.ok()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);check(condition.ok(),label);}
    String code(String host,int port)throws Exception{return "localclip1:"+android.util.Base64.encodeToString(("{\"v\":1,\"host\":\""+host+"\",\"port\":"+port+",\"key\":\""+Wire.hex(key)+"\"}").getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    final class Publication implements AutoCloseable,NsdManager.RegistrationListener {
        volatile boolean registered,removed;final NsdServiceInfo info;
        Publication(int port,String id)throws Exception{info=new NsdServiceInfo();info.setServiceName("LocalClip-"+Wire.discoveryID(key));info.setServiceType(PeerDiscovery.TYPE);info.setPort(port);info.setAttribute("id",id);info.setAttribute("v","1");nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,this);}
        public void onServiceRegistered(NsdServiceInfo s){registered=true;}
        public void onRegistrationFailed(NsdServiceInfo s,int error){}
        public void onServiceUnregistered(NsdServiceInfo s){removed=true;}
        public void onUnregistrationFailed(NsdServiceInfo s,int error){}
        public void close(){nsd.unregisterService(this);}
    }
    static final class Proxy implements AutoCloseable {
        final ServerSocket server;volatile boolean closed;final Set<Socket> sockets=java.util.concurrent.ConcurrentHashMap.newKeySet();
        Proxy()throws Exception{server=new ServerSocket(0);new Thread(()->{while(!closed)try{Socket incoming=server.accept(),outgoing=new Socket("10.0.2.2",47493);sockets.add(incoming);sockets.add(outgoing);pump(incoming,outgoing);pump(outgoing,incoming);}catch(IOException e){if(!closed)throw new RuntimeException(e);}},"test-route").start();}
        void pump(Socket from,Socket to){new Thread(()->{try{InputStream in=from.getInputStream();OutputStream out=to.getOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){out.write(b,0,n);out.flush();}}catch(IOException ignored){}finally{try{from.close();to.close();}catch(IOException ignored){}}},"test-copy").start();}
        public void close()throws Exception{closed=true;server.close();for(Socket s:sockets)s.close();}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Proxy first=null,second=null;Publication publication=null;
        try{
            c=getTargetContext();nsd=c.getSystemService(NsdManager.class);key=Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
            android.net.Network[] available=c.getSystemService(android.net.ConnectivityManager.class).getAllNetworks();
            check(available.length>0,"test device has a network for local discovery");android.net.Network active=available[0];
            check(SyncService.routeNetwork(active,false)==active,"discovered Wi-Fi routing retained without a VPN");
            check(SyncService.routeNetwork(active,true)==null,"active VPN uses normal routing instead of forbidden underlay binding");
            c.getSharedPreferences("settings",0).edit().putBoolean("automatic",false).putBoolean("adbBridge",false).commit();
            int oldPort;try(ServerSocket stale=new ServerSocket(0)){oldPort=stale.getLocalPort();}
            Config.save(c,code("127.0.0.2",oldPort));
            main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->c.startForegroundService(new Intent(c,SyncService.class)));
            await("connection service starts with stale saved address",()->App.service!=null);Thread.sleep(800);
            first=new Proxy();publication=new Publication(first.server.getLocalPort(),"wrong-identity");Publication wrong=publication;
            await("DNS-SD fixture registered",()->wrong.registered);Thread.sleep(2000);
            check(!App.service.connected(),"matching name with wrong TXT identity is ignored");publication.close();await("wrong advertisement removed",()->wrong.removed);
            publication=new Publication(oldPort,Wire.discoveryID(key));Publication unreachable=publication;
            await("unreachable paired Mac advertisement registered",()->unreachable.registered);
            await("discovery reports its resolved address separately from connection",()->App.service.discoveryState().contains(":"+oldPort)&&App.service.connectionDiagnostics().contains(":"+oldPort));
            check(!App.service.connected(),"discovery alone does not mark the Mac connected");
            check(App.service.connectionDiagnostics().contains("Connection refused or unreachable"),"failed discovered route exposes the socket failure");
            runOnMainSync(main::renderConnection);
            check(main.networkHint.getText().toString().contains("Last connection attempts:"),"connection card displays failure diagnostics");
            publication.close();await("unreachable advertisement removed",()->unreachable.removed);
            publication=new Publication(first.server.getLocalPort(),Wire.discoveryID(key));Publication valid=publication;int port1=first.server.getLocalPort();
            await("correct paired Mac advertisement registered",()->valid.registered);
            await("stale address automatically replaced by discovered route",()->App.service.connected()&&App.service.connectedAddress().endsWith(":"+port1));
            check(Config.load(c).host.equals("127.0.0.2"),"legacy pairing stays intact; no re-pair required");
            check(App.service.connectionDiagnostics().isEmpty(),"authenticated connection clears old failures");
            runOnMainSync(main::renderConnection);
            check(main.peer.getText().toString().endsWith(":"+port1)&&!main.peer.getText().toString().contains("127.0.0.2"),"connection card shows the authenticated address instead of the stale backup");
            check(!main.networkHint.getText().toString().contains("Waiting"),"connected card does not show a pending discovery status");
            App.detail="Waiting for discovery text";App.service.send(new Wire.Message(1,"test:discovered-first".getBytes(StandardCharsets.UTF_8)),null);
            await("discovered route completes encrypted Swift clipboard round trip",()->App.detail.startsWith("Received text"));
            ClipboardManager cb=c.getSystemService(ClipboardManager.class);check(cb.getPrimaryClip().getItemAt(0).getText().toString().equals("mac:test:discovered-first"),"discovered peer applies exact text");
            publication.close();await("first advertisement withdrawn",()->valid.removed);first.close();first=null;
            second=new Proxy();int port2=second.server.getLocalPort();publication=new Publication(port2,Wire.discoveryID(key));Publication moved=publication;
            await("paired Mac reappears at changed endpoint",()->moved.registered);
            await("automatic reconnect follows changed DNS-SD endpoint",()->App.service.connected()&&App.service.connectedAddress().endsWith(":"+port2));
            App.detail="Waiting after route change";App.service.send(new Wire.Message(1,"test:discovered-moved".getBytes(StandardCharsets.UTF_8)),null);
            await("clipboard transfer succeeds after endpoint changes",()->App.detail.startsWith("Received text"));
            check(cb.getPrimaryClip().getItemAt(0).getText().toString().equals("mac:test:discovered-moved"),"changed endpoint retains pairing identity");
            byte[] original=Config.load(c).key;Config.updateHost(c,"mac.tail1234.ts.net");Config updated=Config.load(c);
            check(updated.host.equals("mac.tail1234.ts.net")&&Arrays.equals(original,updated.key),"MagicDNS address edit preserves encrypted pairing key");
            org.json.JSONObject multi=new org.json.JSONObject();multi.put("v",1);multi.put("host","mac.tail1234.ts.net");multi.put("port",oldPort);multi.put("key",Wire.hex(key));multi.put("hosts",new org.json.JSONArray().put("mac.tail1234.ts.net").put("192.0.2.20"));
            Config parsed=Config.parse("localclip1:"+android.util.Base64.encodeToString(multi.toString().getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP));
            check(parsed.hosts.size()==2&&parsed.hosts.get(1).equals("192.0.2.20"),"new pairing codes retain explicit alternate addresses");
            boolean whitespace=false;try{Config.updateHost(c,"mac tailnet.ts.net");}catch(Exception e){whitespace=true;}check(whitespace,"address editor rejects whitespace");
            boolean withPort=false;try{Config.updateHost(c,"192.0.2.20:47391");}catch(Exception e){withPort=true;}check(withPort,"address editor rejects host-and-port input");
            boolean invalid=false;try{Config.updateHost(c,"https://example.com");}catch(Exception e){invalid=true;}check(invalid,"URL rejected by address editor");
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));await("Disconnect shuts down service and discovery",()->App.service==null);
            Config.forget(c);result.putString("stream","Android DNS-SD integration: "+assertions+" assertions passed on API "+Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(publication!=null)try{publication.close();}catch(Exception ignored){}if(first!=null)try{first.close();}catch(Exception ignored){}if(second!=null)try{second.close();}catch(Exception ignored){}}
    }
}
