package localclip;
import java.net.*;
import java.util.*;

/** Routing hints only; clipboard traffic still requires the fresh TCP handshake. */
final class TailnetDiscovery implements AutoCloseable {
    static final int PORT=47390;
    interface Found {void accept(String host,int port);}
    private final byte[] key;private final Found found;
    private final LinkedHashSet<String> nonces=new LinkedHashSet<>();
    private volatile boolean closed;private volatile DatagramSocket socket;
    private volatile String state="Waiting for paired Mac on Tailscale";
    TailnetDiscovery(byte[] key,Found found){this.key=key.clone();this.found=found;}
    String state(){return state;}
    void start(){new Thread(()->{
        try(DatagramSocket listener=new DatagramSocket(null)){
            socket=listener;listener.setReuseAddress(false);listener.bind(new InetSocketAddress(PORT));if(closed)return;
            while(!closed){
                DatagramPacket packet=new DatagramPacket(new byte[75],75);listener.receive(packet);
                try{String host=packet.getAddress().getHostAddress();int port=accept(host,Arrays.copyOf(packet.getData(),packet.getLength()));if(port==0)continue;
                    state="Paired Mac discovered on Tailscale: "+host+":"+port;if(!closed)found.accept(host,port);
                }catch(Exception ignored){/* Invalid or unpaired beacons never trigger a connection. */}
            }
        }catch(Exception e){if(!closed)state="Tailnet discovery unavailable; trying remembered addresses";}
        finally{socket=null;}
    },"localclip-tailnet-discovery").start();}
    synchronized int accept(String host,byte[] packet){
        if(closed||!Wire.tailnetIP(host))return 0;
        try{int port=Wire.verifyTailnetBeacon(packet,key,host);String nonce=Wire.hex(Arrays.copyOfRange(packet,4,36));
            if(!nonces.add(nonce))return 0;if(nonces.size()>128)nonces.remove(nonces.iterator().next());return port;
        }catch(Exception ignored){return 0;}
    }
    public void close(){closed=true;DatagramSocket current=socket;if(current!=null)current.close();}
}
