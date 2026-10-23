package localclip;
import android.app.*;
import android.content.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncService extends Service {
    private AdbBridge bridge;
    private PeerDiscovery discovery;
    private final Object retryLock=new Object();private long routeRevision;
    private volatile String connectedAddress="";
    String connectedAddress(){return connectedAddress;}
    String discoveryState(){return discovery==null?"":discovery.state();}
    void routesChanged(){synchronized(retryLock){routeRevision++;retryLock.notifyAll();}}
    void reconnect(){Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}routesChanged();}
    static final String CHANNEL="localclip.connection";
    private final AtomicBoolean running=new AtomicBoolean();
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService writer=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),new ThreadPoolExecutor.AbortPolicy());
    private final Object outputLock=new Object();
    private final ConcurrentHashMap<String,Long> pending=new ConcurrentHashMap<>();
    private volatile Socket socket;
    private volatile Connection connection;
    private volatile long lastResponse;
    private final Object sharedLock=new Object();
    private Wire.Message sharedItem;
    private final java.util.Set<String> sharedDeliveries=ConcurrentHashMap.newKeySet();
    static final class Connection {
        final Socket socket;final Wire.Session crypto; final OutputStream out;
        Connection(Socket socket,Wire.Session crypto,OutputStream out){this.socket=socket;this.crypto=crypto;this.out=out;}
    }
    @Override public void onCreate(){
        super.onCreate();App.phase=App.Phase.CONNECTING;App.status="Connecting to your Mac…";App.service=this;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Clipboard connection",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification("Connecting to your Mac"),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    }
    Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent sendIntent=new Intent(this,MainActivity.class).setAction("localclip.SEND");
        PendingIntent send=PendingIntent.getActivity(this,1,sendIntent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_share).setContentTitle("LocalClip").setContentText(text).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Send clipboard",send).build()).setOngoing(true).setVisibility(Notification.VISIBILITY_SECRET).build();
    }
    void status(App.Phase phase,String text){App.phase=phase;App.status=text;getSystemService(NotificationManager.class).notify(1,notification(text));}
    public long responseAgeMillis(){return Math.max(0,SystemClock.elapsedRealtime()-lastResponse);}
    public boolean connected(){return connection!=null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"localclip.SHARED".equals(intent.getAction())){
            Wire.Message message=App.takeShared(intent.getStringExtra("handoff"));
            if(message!=null)queueShared(message);
        }
        refreshBridge();
        if(running.compareAndSet(false,true)){
            try{Config config=Config.load(this);if(config!=null){discovery=new PeerDiscovery(this,config.key,this::routesChanged);discovery.start();}}catch(Exception e){App.detail="Wi-Fi discovery unavailable; using saved address";}
            new Thread(this::connectLoop,"localclip-connection").start();
            heartbeat.scheduleAtFixedRate(()->{
                if(connection!=null)try{send(new Wire.Message(3,new byte[0]),null);}catch(Exception ignored){}
                long now=SystemClock.elapsedRealtime();for(String key:pending.keySet()){Long time=pending.get(key);if(time!=null&&now-time>12000){pending.remove(key);App.detail="No Mac delivery confirmation. Send again after reconnecting.";if(sharedDeliveries.remove(key))transferNotice("Not confirmed. Share again after reconnecting.");}}
            },10,10,TimeUnit.SECONDS);
        }return START_NOT_STICKY;
    }
    synchronized void refreshBridge(){
        if(AdbBridge.enabled(this)){if(bridge==null){bridge=new AdbBridge(this);bridge.start();}}
        else if(bridge!=null){bridge.close();bridge=null;}
    }
    private void connectLoop(){
        int delay=1;
        while(running.get()){
            long revision;synchronized(retryLock){revision=routeRevision;}
            Socket s=null;
            try{
                Config c=Config.load(this);if(c==null){status(App.Phase.DISCONNECTED,"Pair with your Mac first");break;}
                Exception last=null;Connection established=null;
                java.util.List<PeerDiscovery.Endpoint> routes=discovery==null?java.util.Collections.singletonList(new PeerDiscovery.Endpoint(c.host,c.port,null)):discovery.candidates(c);
                for(PeerDiscovery.Endpoint endpoint:routes){
                    if(!running.get())break;
                    Socket attempt=new Socket();socket=attempt;
                    try{
                        status(App.Phase.CONNECTING,"Connecting to "+endpoint.label());
                        if(endpoint.network!=null)endpoint.network.bindSocket(attempt);
                        attempt.connect(new InetSocketAddress(endpoint.host,endpoint.port),2500);attempt.setTcpNoDelay(true);attempt.setSoTimeout(4000);
                        Wire.Session crypto=Wire.client(attempt.getInputStream(),attempt.getOutputStream(),c.key);
                        established=new Connection(attempt,crypto,attempt.getOutputStream());connectedAddress=endpoint.label();s=attempt;break;
                    }catch(Exception e){last=e;try{attempt.close();}catch(IOException ignored){}}
                }
                if(established==null)throw last==null?new IOException("No reachable Mac address"):last;
                InputStream in=s.getInputStream();
                if(!running.get())break;
                s.setSoTimeout(45000);Connection active=established;lastResponse=SystemClock.elapsedRealtime();connection=active;delay=1;status(App.Phase.CONNECTED,"Connected to Mac · encrypted");flushShared();
                while(running.get()&&connection==active){
                    Wire.Message message=active.crypto.rx.receive(in);lastResponse=SystemClock.elapsedRealtime();
                    if(message.type==4){if(pending.remove(Wire.hex(message.id))!=null){App.detail="Mac clipboard updated — ready to paste";if(sharedDeliveries.remove(Wire.hex(message.id)))transferNotice("Shared item delivered to Mac");}}
                    else if(message.type==3)send(new Wire.Message(4,message.id,new byte[0]),null);
                    else {
                        try{Clips.apply(this,message);send(new Wire.Message(4,message.id,new byte[0]),null);}
                        catch(Exception e){App.detail=e.getMessage()==null?"Could not apply received clipboard":e.getMessage();}
                    }
                }
            }catch(Exception e){connection=null;if(running.get())status(e instanceof java.security.GeneralSecurityException?App.Phase.PAIRING_REJECTED:App.Phase.RECONNECTING,e instanceof java.security.GeneralSecurityException?"Pairing rejected. Check the Mac pairing code.":"Mac unavailable · searching Wi-Fi and saved addresses · retrying in "+delay+"s");}
            finally{connection=null;socket=null;if(s!=null)try{s.close();}catch(IOException ignored){}}
            if(running.get())synchronized(retryLock){try{if(routeRevision==revision)retryLock.wait(delay*1000L);}catch(InterruptedException ignored){}delay=Math.min(15,delay*2);}
        }
        if(running.get())App.main.post(this::stopSelf);
    }
    private void transferNotice(String text){
        getSystemService(NotificationManager.class).notify(1,notification(text));
    }
    private void queueShared(Wire.Message message){
        synchronized(sharedLock){sharedItem=message;}
        App.detail="Shared item queued — waiting for Mac";
        App.main.postDelayed(()->{
            boolean expired=false;synchronized(sharedLock){if(sharedItem==message){sharedItem=null;expired=true;}}
            if(expired){App.detail="Shared item expired. Share again when the Mac is available.";if(running.get())transferNotice("Mac unavailable. Shared item expired; share again.");}
        },30000);
        flushShared();
    }
    private void flushShared(){
        synchronized(sharedLock){
            if(sharedItem==null||connection==null)return;
            Wire.Message message=sharedItem;String id=Wire.hex(message.id);sharedDeliveries.add(id);
            try{send(message,null);sharedItem=null;}
            catch(Exception e){sharedDeliveries.remove(id);sharedItem=null;App.detail="Could not send shared item. Share again.";transferNotice("Could not send. Share again.");}
        }
    }
    public void send(Wire.Message message,Runnable sent)throws Exception{
        final Connection active=connection;if(active==null)throw new Exception("Mac offline. Connect and send again.");
        try{writer.execute(()->{
            if(connection!=active||!running.get()){App.detail="Connection changed. Send again.";if(sharedDeliveries.remove(Wire.hex(message.id)))transferNotice("Connection changed. Share again.");return;}
            // Socket writes have no Java write timeout. Close a stalled connection after 12 seconds.
            ScheduledFuture<?> deadline=heartbeat.schedule(()->{try{active.socket.close();}catch(IOException ignored){}},12,TimeUnit.SECONDS);
            try{
                if(message.type<=2){pending.put(Wire.hex(message.id),SystemClock.elapsedRealtime());App.detail="Sent "+(message.type==1?"text":"image")+" · waiting for Mac confirmation";}
                synchronized(outputLock){active.out.write(active.crypto.tx.seal(message.encode()));active.out.flush();}
                if(sent!=null)sent.run();
            }catch(Exception e){if(message.type<=2){pending.remove(Wire.hex(message.id));App.detail="Send failed. Reconnect and send again.";if(sharedDeliveries.remove(Wire.hex(message.id)))transferNotice("Send failed. Share again after reconnecting.");}try{active.socket.close();}catch(IOException ignored){}
            }finally{deadline.cancel(false);}
        });}catch(RejectedExecutionException e){throw new Exception("Connection busy. Send again.");}
    }
    @Override public void onDestroy(){running.set(false);routesChanged();if(discovery!=null)discovery.close();synchronized(this){if(bridge!=null){bridge.close();bridge=null;}}running.set(false);connection=null;Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}heartbeat.shutdownNow();writer.shutdownNow();pending.clear();sharedDeliveries.clear();synchronized(sharedLock){sharedItem=null;}if(App.service==this)App.service=null;App.phase=App.Phase.DISCONNECTED;App.status="Disconnected";super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
