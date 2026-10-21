package localclip;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

public final class App extends Application {
    static volatile String status="Not connected", detail="No transfers yet", lastDigest="";
    enum Phase { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, PAIRING_REJECTED }
    static volatile Phase phase=Phase.DISCONNECTED;
    static volatile SyncService service;
    static volatile boolean focused=false;
    static final ExecutorService contentWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),new ThreadPoolExecutor.AbortPolicy());
    private static final java.util.Map<String,Wire.Message> handoffs=new java.util.HashMap<>();
    static final Handler main=new Handler(Looper.getMainLooper());
    static void handoffShared(Context c,Wire.Message message)throws Exception{
        String token=java.util.UUID.randomUUID().toString();
        synchronized(handoffs){if(handoffs.size()>=2)throw new Exception("Sharing busy");handoffs.put(token,message);}
        try{c.startForegroundService(new Intent(c,SyncService.class).setAction("localclip.SHARED").putExtra("handoff",token));}
        catch(Exception e){takeShared(token);throw e;}
        main.postDelayed(()->takeShared(token),30000);
    }
    static Wire.Message takeShared(String token){synchronized(handoffs){return handoffs.remove(token);}}
    static boolean unlocked(Context c) { return !((KeyguardManager)c.getSystemService(KEYGUARD_SERVICE)).isDeviceLocked(); }
    static boolean automatic(Context c) { return c.getSharedPreferences("settings",0).getBoolean("automatic",false); }
    static void send(Context context,ClipData clip,boolean explicit) {
        if(!unlocked(context)){ detail="Unlock the phone before sending";return; }
        if(clip==null){detail="Clipboard unavailable. Open LocalClip or use Share.";return;}
        CharSequence label=clip.getDescription().getLabel();
        if(!explicit && label!=null && ("LocalClip".contentEquals(label)||"LocalClip image".contentEquals(label)))return;
        final Context c=context.getApplicationContext();
        try { contentWorker.execute(()->{
            try {
                Wire.Message m=Clips.read(c,clip);
                String hash=Wire.fingerprint(m.type,m.body);if(!explicit&&hash.equals(lastDigest))return;
                SyncService s=service;if(s==null)throw new Exception("Tap Connect first");
                s.send(m,null);lastDigest=hash;
            } catch(Exception e){ detail=e.getMessage()==null?"Cannot read clipboard":e.getMessage(); }
        }); }catch(RejectedExecutionException e){detail="Clipboard busy; send again";}
    }
}
