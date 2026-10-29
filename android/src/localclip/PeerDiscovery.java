package localclip;
import android.content.Context;
import android.net.Network;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Advertisements suggest routes only. The existing encrypted handshake authenticates every connection. */
final class PeerDiscovery implements AutoCloseable {
    static final String TYPE="_localclip._tcp.";
    static final class Endpoint {
        final String host;final int port;final Network network;
        Endpoint(String host,int port,Network network){this.host=host;this.port=port;this.network=network;}
        String label(){return host+":"+port;}
    }
    private final NsdManager manager;private final String id;private final Runnable changed;
    private final Map<String,List<Endpoint>> found=new LinkedHashMap<>();
    private final Map<String,NsdManager.ServiceInfoCallback> callbacks=new HashMap<>();
    private boolean closed,started;
    private final Set<String> resolving=new HashSet<>();
    private Endpoint tailnet;
    private volatile String state="Looking for your paired Mac on Wi-Fi…";
    PeerDiscovery(Context c,byte[] key,Runnable changed)throws Exception{manager=c.getSystemService(NsdManager.class);id=Wire.discoveryID(key);this.changed=changed;}
    String state(){return state;}
    private boolean matches(NsdServiceInfo s){String n=s.getServiceName(),expected="LocalClip-"+id;return n!=null&&(n.equals(expected)||n.startsWith(expected+" ("))&&(s.getServiceType().equals(TYPE)||s.getServiceType().equals("_localclip._tcp"));}
    private final NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
        public void onDiscoveryStarted(String type){synchronized(PeerDiscovery.this){started=true;if(closed)stopDiscovery();}}
        public void onStartDiscoveryFailed(String type,int code){state="Wi-Fi discovery unavailable; using saved address / Tailscale";}
        public void onStopDiscoveryFailed(String type,int code){state="Wi-Fi discovery stopped";}
        public void onDiscoveryStopped(String type){synchronized(PeerDiscovery.this){started=false;}}
        public void onServiceFound(NsdServiceInfo s){if(matches(s))watch(s);}
        public void onServiceLost(NsdServiceInfo s){lost(s.getServiceName());}
    };
    void start(){try{manager.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception e){state="Wi-Fi discovery unavailable; using saved address / Tailscale";}}
    private synchronized void lost(String name){if(found.remove(name)!=null){state="Looking for your paired Mac on Wi-Fi…";changed.run();}}
    private synchronized void watch(NsdServiceInfo service){
        if(closed)return;String name=service.getServiceName();
        if(Build.VERSION.SDK_INT>=34){
            if(callbacks.containsKey(name))return;
            NsdManager.ServiceInfoCallback callback=new NsdManager.ServiceInfoCallback(){
                public void onServiceInfoCallbackRegistrationFailed(int code){synchronized(PeerDiscovery.this){callbacks.remove(name);}lost(name);}
                public void onServiceInfoCallbackUnregistered(){}
                public void onServiceLost(){lost(name);}
                public void onServiceUpdated(NsdServiceInfo info){update(info);}
            };
            callbacks.put(name,callback);
            try{manager.registerServiceInfoCallback(service,Runnable::run,callback);}catch(Exception e){callbacks.remove(name);state="Could not resolve Wi-Fi service; using saved address";}
        }else{
            if(!resolving.add(name))return;
            manager.resolveService(service,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo s,int code){synchronized(PeerDiscovery.this){resolving.remove(name);}}
                public void onServiceResolved(NsdServiceInfo s){synchronized(PeerDiscovery.this){resolving.remove(name);}update(s);}
            });
        }
    }
    private synchronized void update(NsdServiceInfo info){
        if(closed||!matches(info))return;
        Map<String,byte[]> txt=info.getAttributes();
        if(!Arrays.equals(txt.get("id"),id.getBytes(StandardCharsets.US_ASCII))||!Arrays.equals(txt.get("v"),new byte[]{'1'})||info.getPort()<1024||info.getPort()>65535)return;
        List<InetAddress> addresses=Build.VERSION.SDK_INT>=34?info.getHostAddresses():info.getHost()==null?Collections.emptyList():Collections.singletonList(info.getHost());
        List<Endpoint> routes=new ArrayList<>();
        // Prefer IPv4 on home Wi-Fi; retain the scoped IPv6 address when available.
        for(int family=0;family<2;family++)for(InetAddress address:addresses){
            if(routes.size()>=4)break;if(address.isAnyLocalAddress()||address.isMulticastAddress())continue;
            if((address instanceof Inet4Address)!=(family==0))continue;
            routes.add(new Endpoint(address.getHostAddress(),info.getPort(),Build.VERSION.SDK_INT>=33?info.getNetwork():null));
        }
        if(!routes.isEmpty()){found.put(info.getServiceName(),routes);state="Discovered Mac: "+String.join(", ",routes.stream().map(Endpoint::label).toArray(String[]::new))+"\nWaiting for an encrypted connection";changed.run();}
    }
    synchronized List<Endpoint> candidates(Config config){
        LinkedHashMap<String,Endpoint> routes=new LinkedHashMap<>();
        if(tailnet!=null)routes.put(tailnet.label(),tailnet);
        for(String host:config.hosts)if(Wire.tailnetHost(host)){Endpoint e=new Endpoint(host,config.port,null);routes.putIfAbsent(e.label(),e);}
        for(List<Endpoint> group:found.values())for(Endpoint e:group)if(routes.size()<4)routes.put(e.label(),e);
        for(String host:config.hosts){Endpoint e=new Endpoint(host,config.port,null);if(routes.size()<8)routes.putIfAbsent(e.label(),e);}
        return new ArrayList<>(routes.values());
    }
    synchronized boolean offerTailnet(String host,int port){
        if(closed||!Wire.tailnetIP(host)||port<1024||port>65535)return false;
        if(tailnet!=null&&tailnet.host.equals(host)&&tailnet.port==port)return false;
        tailnet=new Endpoint(host,port,null);changed.run();return true;
    }
    private void stopDiscovery(){try{manager.stopServiceDiscovery(listener);}catch(IllegalArgumentException ignored){}}
    @Override public synchronized void close(){
        if(closed)return;closed=true;if(started)stopDiscovery();
        if(Build.VERSION.SDK_INT>=34)for(NsdManager.ServiceInfoCallback callback:callbacks.values())try{manager.unregisterServiceInfoCallback(callback);}catch(IllegalArgumentException ignored){}
        callbacks.clear();found.clear();resolving.clear();
    }
}
