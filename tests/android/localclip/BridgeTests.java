package localclip;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** End-to-end: unrelated source app -> shell observer -> encrypted loopback IPC -> encrypted Mac. */
public final class BridgeTests extends Instrumentation {
    Context c;MainActivity activity;int assertions;boolean externalHelper;
    interface Condition{boolean test()throws Exception;}
    void check(boolean b,String name)throws Exception{if(!b)throw new Exception("FAILED "+name+"; bridge="+AdbBridge.state+" detail="+App.detail);assertions++;Bundle r=new Bundle();r.putString("stream","PASS "+name+"\n");sendStatus(0,r);}
    void await(String name,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){if(condition.test()){check(true,name);return;}Thread.sleep(100);}check(false,name);}
    void shell(String command)throws Exception{
        ParcelFileDescriptor[] fds=getUiAutomation().executeShellCommandRw("sh");
        try(OutputStream out=new FileOutputStream(fds[1].getFileDescriptor())){out.write((command+"\nexit\n").getBytes(StandardCharsets.UTF_8));}
        // The helper is intentionally detached; don't wait for inherited pipe EOF.
        fds[0].close();fds[1].close();
    }
    void source(String value,boolean sensitive)throws Exception{c.startActivity(new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("text",value).putExtra("sensitive",sensitive).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1000);}
    void foreground()throws Exception{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("main screen focus",()->activity.hasWindowFocus());}
    @Override public void onCreate(Bundle args){super.onCreate(args);externalHelper=args!=null&&Boolean.parseBoolean(args.getString("externalHelper","false"));start();}
    @Override public void onStart(){Bundle result=new Bundle();
        try{
            c=getTargetContext();c.getSharedPreferences("settings",0).edit().putBoolean("automatic",false).putBoolean("adbBridge",true).commit();
            String code="localclip1:"+android.util.Base64.encodeToString("{\"v\":1,\"host\":\"10.0.2.2\",\"port\":47493,\"key\":\"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f\"}".getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
            Config.save(c,code);activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("main ready",()->activity.hasWindowFocus());
            String keyboard=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
            ((ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Before helper","must-not-send-initial"));
            runOnMainSync(()->c.startForegroundService(new Intent(c,SyncService.class)));
            await("encrypted Mac connection",()->App.service!=null&&App.service.connected());
            await("bridge waiting for shell",()->AdbBridge.state.startsWith("Waiting"));
            File tokenFile=new File(c.getFilesDir(),AdbBridge.TOKEN_FILE);String initialKey;
            try(BufferedReader reader=new BufferedReader(new FileReader(tokenFile))){initialKey=reader.readLine();}
            check(initialKey!=null&&initialKey.matches("[0-9a-f]{64}"),"independent temporary bridge key created");
            try(java.net.Socket impostor=new java.net.Socket("127.0.0.1",AdbBridge.PORT)){
                impostor.setSoTimeout(5000);boolean rejected=false;
                try{Wire.client(impostor.getInputStream(),impostor.getOutputStream(),Wire.random(32));}catch(Exception e){rejected=true;}
                check(rejected&&!AdbBridge.active,"wrong helper key rejected before clipboard events");
            }
            if(externalHelper){Bundle ready=new Bundle();ready.putString("stream","READY_EXTERNAL_HELPER\n");sendStatus(0,ready);}else
            shell("run-as org.localclip.android cat files/adb-bridge-token | CLASSPATH=/data/local/tmp/localclip-bridge.jar app_process /system/bin localclip.bridge.ClipboardBridge > /data/local/tmp/localclip-bridge-test.log 2>&1 &");
            await("shell helper authenticated and active",()->AdbBridge.active);
            App.detail="No initial clipboard sent";Thread.sleep(1200);check(App.detail.equals("No initial clipboard sent"),"helper does not read existing clipboard on startup");
            source("test:adb-auto 🌏 日本語",false);
            await("background copy delivered without Share",()->App.detail.startsWith("Received text"));check(!activity.hasWindowFocus()&&!App.focused,"copying leaves source app in front");
            foreground();ClipboardManager cb=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);check(cb.getPrimaryClip().getItemAt(0).getText().toString().equals("mac:test:adb-auto 🌏 日本語"),"exact Unicode text round trip");
            App.detail="Waiting for repeated copy";source("test:adb-auto 🌏 日本語",false);
            await("recopying identical text sends a new event",()->App.detail.startsWith("Received text"));
            String before=App.lastDigest;source("sensitive-must-not-send",true);Thread.sleep(1200);check(App.lastDigest.equals(before),"sensitive-marked background copy skipped");
            source(code,false);Thread.sleep(1200);check(App.lastDigest.equals(before),"pairing code background copy skipped");
            App.detail="Waiting for automatic image";
            c.startActivity(new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("copyImage",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("private-provider image copy automatically delivered",()->App.detail.startsWith("Received image"));check(!activity.hasWindowFocus(),"image copy requires no foreground LocalClip screen");
            foreground();Uri uri=cb.getPrimaryClip().getItemAt(0).getUri();byte[] png;try(InputStream in=c.getContentResolver().openInputStream(uri)){png=Clips.boundedRead(in);}
            Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length);check(image!=null&&image.getWidth()==4&&image.getHeight()==3&&image.getPixel(0,0)==0xff00aaff,"automatic image pixels survive Mac round trip");image.recycle();
            App.detail="Echo suppressed";Thread.sleep(2000);check(App.detail.equals("Echo suppressed"),"incoming Mac clipboard does not echo back through helper");
            check(keyboard.equals(Settings.Secure.getString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD)),"selected keyboard unchanged by helper: "+keyboard);
            check((activity.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)==0,"LocalClip screenshots permitted");
            Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"LocalClip screenshot captured");int light=0;for(int y=shot.getHeight()/4;y<shot.getHeight()*3/4;y+=40)for(int x=shot.getWidth()/4;x<shot.getWidth()*3/4;x+=40)if(Color.red(shot.getPixel(x,y))>50)light++;check(light>30,"LocalClip screenshot contains visible screen pixels");
            ByteArrayOutputStream screenshot=new ByteArrayOutputStream();shot.compress(Bitmap.CompressFormat.PNG,100,screenshot);int width=shot.getWidth(),height=shot.getHeight();shot.recycle();
            App.detail="Sending screen capture";App.service.send(Clips.image(screenshot.toByteArray()),null);
            await("full LocalClip screenshot round trip",()->App.detail.startsWith("Received image"));
            Uri screenUri=cb.getPrimaryClip().getItemAt(0).getUri();byte[] screenBytes;try(InputStream in=c.getContentResolver().openInputStream(screenUri)){screenBytes=Clips.boundedRead(in);}
            Bitmap screen=BitmapFactory.decodeByteArray(screenBytes,0,screenBytes.length);check(screen!=null&&screen.getWidth()==width&&screen.getHeight()==height,"full screenshot dimensions preserved");
            int visible=0;for(int y=height/4;y<height*3/4;y+=40)for(int x=width/4;x<width*3/4;x+=40)if(Color.red(screen.getPixel(x,y))>50)visible++;
            check(visible>30,"Mac round-trip screenshot is not black");screen.recycle();
            runOnMainSync(()->{c.getSharedPreferences("settings",0).edit().putBoolean("adbBridge",false).commit();App.service.refreshBridge();});
            await("disable option closes helper channel",()->!AdbBridge.active);check(!tokenFile.exists(),"disabling deletes the temporary bridge key");
            before=App.lastDigest;source("copy-after-disable",false);Thread.sleep(1200);check(App.lastDigest.equals(before),"no automatic sending after disabling helper");
            runOnMainSync(()->{c.getSharedPreferences("settings",0).edit().putBoolean("adbBridge",true).commit();App.service.refreshBridge();});
            await("reenabling waits for explicit helper restart",()->AdbBridge.state.startsWith("Waiting")&&!AdbBridge.active);
            try(BufferedReader reader=new BufferedReader(new FileReader(tokenFile))){check(!initialKey.equals(reader.readLine()),"restarting rotates the bridge key");}
            shell("run-as org.localclip.android cat files/adb-bridge-token | CLASSPATH=/data/local/tmp/localclip-bridge.jar app_process /system/bin localclip.bridge.ClipboardBridge > /data/local/tmp/localclip-bridge-test.log 2>&1 &");await("helper restart connects",()->AdbBridge.active);
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));await("Disconnect stops network and helper channel",()->App.service==null&&!AdbBridge.active);
            c.getSharedPreferences("settings",0).edit().putBoolean("adbBridge",false).commit();Config.forget(c);
            result.putString("stream","ADB bridge integration: "+assertions+" assertions passed on API "+Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
