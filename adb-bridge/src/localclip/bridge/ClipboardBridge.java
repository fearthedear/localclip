package localclip.bridge;
import android.content.*;
import android.net.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import localclip.Wire;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Rootless, opt-in shell helper. Only encrypted loopback traffic; no Mac pairing key or clipboard log. */
public final class ClipboardBridge {
    static final int MAX=8*1024*1024;
    static final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),new ThreadPoolExecutor.DiscardPolicy());
    static final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    static DataOutputStream output;
    static Socket socket;
    static Wire.Session session;
    static Context shell;
    static long lastTimestamp=Long.MIN_VALUE;
    static Object activity;
    static Method acquire,release,open;
    static Field providerField;
    static void frame(int type,byte[] body)throws Exception{
        if(body.length>MAX)throw new IOException("Clipboard exceeds limit");
        synchronized(output){output.write(session.tx.seal(Wire.concat(new byte[]{(byte)type},Wire.random(16),body)));output.flush();}
    }
    static void stop(){try{socket.close();}catch(Exception ignored){}System.exit(0);}
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Run as adb shell UID 2000, without root");
        String token=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.US_ASCII)).readLine();
        byte[] key=Wire.unhex(token==null?"":token);
        if(Build.VERSION.SDK_INT<34)throw new UnsupportedOperationException("Android 14 or newer required");
        Looper.prepareMainLooper();
        Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
        Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
        shell=new ContextWrapper(system.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY)){
            @Override public String getPackageName(){return "com.android.shell";}
            @Override public String getOpPackageName(){return "com.android.shell";}
            @Override public AttributionSource getAttributionSource(){return new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();}
        };
        // Exact signatures are checked once. Fail visibly on an incompatible Android build.
        Class<?> api=Class.forName("android.app.IActivityManager");
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"activity");
        activity=Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        acquire=api.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class);
        release=api.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class);
        providerField=Class.forName("android.app.ContentProviderHolder").getField("provider");
        open=Class.forName("android.content.IContentProvider").getMethod("openTypedAssetFile",AttributionSource.class,Uri.class,String.class,Bundle.class,Class.forName("android.os.ICancellationSignal"));
        ClipboardManager clipboard=(ClipboardManager)shell.getSystemService(Context.CLIPBOARD_SERVICE);
        Field identity=ClipboardManager.class.getDeclaredField("mContext");identity.setAccessible(true);identity.set(clipboard,shell);
        socket=new Socket();socket.connect(new InetSocketAddress("127.0.0.1",47392),4000);socket.setSoTimeout(15000);socket.setTcpNoDelay(true);
        InputStream input=socket.getInputStream();output=new DataOutputStream(socket.getOutputStream());
        session=Wire.client(input,output,key);java.util.Arrays.fill(key,(byte)0);
        frame(6,ByteBuffer.allocate(4).putInt(Build.VERSION.SDK_INT).array());
        clipboard.addPrimaryClipChangedListener(()->{
            try{
                android.app.KeyguardManager lock=(android.app.KeyguardManager)shell.getSystemService(Context.KEYGUARD_SERVICE);
                if(lock.isDeviceLocked())return;
                ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()<1)return;
                ClipDescription desc=clip.getDescription();
                if(desc.getExtras()!=null&&desc.getExtras().getBoolean("android.content.extra.IS_SENSITIVE",false))return;
                CharSequence label=desc.getLabel();if(label!=null&&("LocalClip".contentEquals(label)||"LocalClip image".contentEquals(label)))return;
                long timestamp=desc.getTimestamp();if(timestamp==lastTimestamp)return;lastTimestamp=timestamp;
                ClipData.Item item=clip.getItemAt(0);
                if(item.getUri()!=null&&desc.hasMimeType("image/*")){
                    Uri uri=item.getUri();if(!"content".equals(uri.getScheme()))return;
                    worker.execute(()->{try{frame(2,readImage(uri));}catch(Exception e){skip();}});
                }else if(item.getText()!=null){
                    String text=item.getText().toString();if(text.startsWith("localclip1:"))return;
                    if(text.length()>MAX){skip();return;}
                    byte[] body=text.getBytes(StandardCharsets.UTF_8);if(body.length>MAX){skip();return;}
                    worker.execute(()->{try{frame(1,body);}catch(Exception e){stop();}});
                }
            }catch(Exception e){skip();}
        });
        new Thread(()->{try{while(session.rx.receive(input).type==4){}}catch(Exception ignored){}stop();},"localclip-bridge-ack").start();
        heartbeat.scheduleAtFixedRate(()->{try{frame(3,new byte[0]);}catch(Exception e){stop();}},0,5,TimeUnit.SECONDS);
        System.out.println("Automatic copying active on Android API "+Build.VERSION.SDK_INT+". Keep your keyboard. No clipboard contents are logged.");
        Looper.loop();
    }
    static void skip(){try{frame(5,new byte[0]);}catch(Exception e){stop();}}
    static byte[] readImage(Uri uri)throws Exception{
        IBinder token=new Binder();String authority=uri.getAuthority();int user=0;
        // This MVP supports the main Android user only; launcher rejects work/secondary profiles.
        Object holder=acquire.invoke(activity,authority,user,token,"localclip-adb");if(holder==null)throw new IOException("Provider unavailable");
        try{
            Object provider=providerField.get(holder);
            android.content.res.AssetFileDescriptor file=(android.content.res.AssetFileDescriptor)open.invoke(provider,shell.getAttributionSource(),uri,"image/*",null,null);
            if(file==null)throw new IOException("Image unavailable");
            try(android.content.res.AssetFileDescriptor ignored=file;InputStream in=file.createInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX)throw new IOException("Image exceeds limit");out.write(b,0,n);}return out.toByteArray();
            }
        }finally{release.invoke(activity,authority,token,user);}
    }
}
