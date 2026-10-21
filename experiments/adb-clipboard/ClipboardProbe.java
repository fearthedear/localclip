package localclip.probe;
import android.content.*;
import android.os.*;
import java.io.InputStream;

/** Research-only observer. No network, no keylogging, no raw clipboard output, no initial clipboard read. */
public final class ClipboardProbe {
    private static int events;
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Run this research probe as adb shell, not as root or an ordinary app");
        if(Build.VERSION.SDK_INT!=34)throw new UnsupportedOperationException("Research probe is validated only on API 34; inspect/adapt signatures before using another release");
        Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Object thread=activityThread.getMethod("systemMain").invoke(null);
        Context system=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        Context shell=new ContextWrapper(system.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY)){
            @Override public String getPackageName(){return "com.android.shell";}
            @Override public String getOpPackageName(){return "com.android.shell";}
            @Override public AttributionSource getAttributionSource(){return new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();}
        };
        System.out.println("PROBE_CONTEXT package="+shell.getPackageName()+" attribution="+shell.getOpPackageName()+" uid="+android.os.Process.myUid());
        if(args.length==1&&"--inspect".equals(args[0])){
            for(java.lang.reflect.Method m:Class.forName("android.app.IActivityManager").getMethods())if(m.getName().contains("ContentProviderExternal"))System.out.println(m.toString());
            for(java.lang.reflect.Method m:Class.forName("android.content.IContentProvider").getMethods())if(m.getName().equals("openTypedAssetFile"))System.out.println(m.toString());
            return;
        }
        ClipboardManager clipboard=(ClipboardManager)shell.getSystemService(Context.CLIPBOARD_SERVICE);
        java.lang.reflect.Field identity=ClipboardManager.class.getDeclaredField("mContext");identity.setAccessible(true);identity.set(clipboard,shell);
        clipboard.addPrimaryClipChangedListener(()->{
            try{
                ClipData clip=clipboard.getPrimaryClip();if(clip==null){System.out.println("EVENT unavailable");return;}
                if(clip.getDescription().getExtras()!=null&&clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE",false)){System.out.println("EVENT sensitive-skipped");return;}
                ClipData.Item item=clip.getItemAt(0);events++;
                if(item.getUri()!=null&&clip.getDescription().hasMimeType("image/*")){
                    int size=readImage(shell,item.getUri());
                    System.out.println("EVENT "+events+" image-readable bytes="+size);
                }else if(item.getText()!=null)System.out.println("EVENT "+events+" text-readable chars="+item.getText().length());
                else System.out.println("EVENT "+events+" unsupported");
            }catch(Exception e){System.out.println("EVENT_FAILED "+e.getClass().getSimpleName());}
        });
        System.out.println("READY shell clipboard listener; copy synthetic test text/image in another app");System.out.flush();Looper.loop();
    }
    private static int readImage(Context shell,android.net.Uri uri)throws Exception{
        // Exact API 34 signatures were inspected on the emulator. No guessed signature fallbacks.
        Class<?> serviceManager=Class.forName("android.os.ServiceManager"), activityApi=Class.forName("android.app.IActivityManager");
        IBinder binder=(IBinder)serviceManager.getMethod("getService",String.class).invoke(null,"activity");
        Object activity=Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        IBinder token=new Binder();String authority=uri.getAuthority();
        Object holder=activityApi.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(activity,authority,0,token,"localclip-clipboard-probe");
        if(holder==null)throw new java.io.IOException("Source provider unavailable");
        try{
            Object provider=Class.forName("android.app.ContentProviderHolder").getField("provider").get(holder);
            java.lang.reflect.Method open=Class.forName("android.content.IContentProvider").getMethod("openTypedAssetFile",AttributionSource.class,android.net.Uri.class,String.class,Bundle.class,Class.forName("android.os.ICancellationSignal"));
            android.content.res.AssetFileDescriptor file=(android.content.res.AssetFileDescriptor)open.invoke(provider,shell.getAttributionSource(),uri,"image/*",null,null);
            if(file==null)throw new java.io.IOException("No image file");
            int size=0;byte[] buffer=new byte[8192];
            try(android.content.res.AssetFileDescriptor ignored=file;InputStream in=file.createInputStream()){
                int n;while((n=in.read(buffer))!=-1){size+=n;if(size>8*1024*1024)throw new java.io.IOException("Image cap");}
            }return size;
        }finally{activityApi.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(activity,authority,token,0);}
    }

}
