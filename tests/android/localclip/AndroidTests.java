package localclip;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.graphics.*;
import android.provider.Settings;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AndroidTests extends Instrumentation {
    Context c;ClipboardManager cb;MainActivity activity;int assertions=0;
    interface Condition{boolean test()throws Exception;}
    void check(boolean b,String label)throws Exception{if(!b)throw new Exception("FAILED: "+label+"; status="+App.status+" detail="+App.detail);assertions++;Bundle result=new Bundle();result.putString("stream","PASS "+label+"\n");sendStatus(0,result);}
    void await(String label,Condition condition)throws Exception{long until=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<until){if(condition.test()){check(true,label);return;}Thread.sleep(100);}check(false,label);}
    String text(){ClipData clip=cb.getPrimaryClip();return clip==null||clip.getItemCount()==0||clip.getItemAt(0).getText()==null?"":clip.getItemAt(0).getText().toString();}
    void foreground()throws Exception{Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);if(activity==null)activity=(MainActivity)startActivitySync(intent);else c.startActivity(intent);await("LocalClip has focus",()->activity.hasWindowFocus());}
    void source(String text)throws Exception{Intent i=new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("text",text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);Thread.sleep(1200);}
    void shell(String command)throws Exception{try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand(command);FileInputStream in=new FileInputStream(p.getFileDescriptor())){Clips.boundedRead(in);}}
    void capture(String state)throws Exception{
        check((activity.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)==0,"main screen permits screenshots");
        Thread.sleep(1200);Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new Exception("No test screenshot");
        try(FileOutputStream out=c.openFileOutput("status-"+state+".png",Context.MODE_PRIVATE)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            c=getTargetContext();c.getSharedPreferences("settings",0).edit().putBoolean("automatic",false).commit();cb=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
            String code="localclip1:"+android.util.Base64.encodeToString(("{\"v\":1,\"host\":\"10.0.2.2\",\"port\":47493,\"key\":\"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f\"}").getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
            Config.save(c,code);Config conf=Config.load(c);check(conf.host.equals("10.0.2.2")&&conf.port==47493&&conf.key.length==32,"pairing Keystore save/load");
            boolean invalid=false;try{Config.parse("invalid");}catch(Exception e){invalid=true;}check(invalid,"invalid pairing rejected");
            foreground();runOnMainSync(()->c.startForegroundService(new Intent(c,SyncService.class)));
            await("authenticated Mac connection",()->App.service!=null&&App.service.connected());
            runOnMainSync(()->{cb.setPrimaryClip(ClipData.newPlainText("Test","test:unicode 🌏 日本語"));activity.sendClipboard();});
            await("text round trip through OS clipboard",()->text().equals("mac:test:unicode 🌏 日本語"));
            // Share-sheet intent reaches the real Activity and uses the same authenticated service.
            Intent share=new Intent(c,ShareActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"test:share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(share);await("Share text round trip",()->text().equals("mac:test:share"));
            Intent selected=new Intent(c,ShareActivity.class).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT,"test:selected").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(selected);await("selected text round trip",()->text().equals("mac:test:selected"));
            Bitmap b=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);b.eraseColor(0xff00aaff);ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);b.recycle();
            Wire.Message image=new Wire.Message(2,bytes.toByteArray());runOnMainSync(()->{try{Clips.apply(c,image);App.detail="Sending test image";activity.sendClipboard();}catch(Exception e){throw new RuntimeException(e);}});
            await("image round trip acknowledgement",()->App.detail.startsWith("Received image"));
            ClipData clip=cb.getPrimaryClip();Uri imageUri=clip.getItemAt(0).getUri();check(imageUri!=null&&clip.getDescription().hasMimeType("image/png"),"received image uses PNG content URI");
            byte[] received;try(InputStream in=c.getContentResolver().openInputStream(imageUri)){received=Clips.boundedRead(in);}check(Arrays.equals(received,image.body),"image provider exact PNG bytes");
            // PNG source bytes are preserved by the production send path.
            Bitmap decoded=BitmapFactory.decodeByteArray(received,0,received.length);check(decoded!=null&&decoded.getWidth()==4&&decoded.getHeight()==3&&decoded.getPixel(0,0)==0xff00aaff,"PNG pixels and dimensions preserved");decoded.recycle();
            App.detail="Waiting for shared image";
            Intent shareImage=new Intent(c,ShareActivity.class).setAction(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,imageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(shareImage);await("Share image round trip",()->App.detail.startsWith("Received image"));
            ClipData sensitive=ClipData.newPlainText("Private","do-not-send");PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);sensitive.getDescription().setExtras(extras);
            boolean skipped=false;try{Clips.read(c,sensitive);}catch(Exception e){skipped=true;}check(skipped,"sensitive clipboard excluded");
            boolean pairSkipped=false;try{Clips.read(c,ClipData.newPlainText("Pairing",code));}catch(Exception e){pairSkipped=true;}check(pairSkipped,"pairing code excluded from sync");
            boolean oversize=false;try{Clips.boundedRead(new ByteArrayInputStream(new byte[Wire.MAX_CONTENT+1]));}catch(Exception e){oversize=true;}check(oversize,"image input limit");
            runOnMainSync(()->{cb.setPrimaryClip(ClipData.newPlainText("Background test","test:background"));activity.sendClipboard();});
            Thread.sleep(500);source("foreign:focused");Thread.sleep(2500);
            foreground();await("background Mac to Android clipboard write",()->text().equals("mac:background"));
            // Observe Android's actual foreground read restriction with an unrelated app in focus.
            source("foreign:restriction");check(cb.getPrimaryClip()==null,"background clipboard read denied without default IME");
            Intent resolved=new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(c.getPackageName());
            check(c.getPackageManager().resolveActivity(resolved,0).activityInfo.name.equals("localclip.ShareActivity"),"share sheet resolves to transparent receiver");
            ActivityMonitor receiver=addMonitor(ShareActivity.class.getName(),null,false);
            Intent foreignShare=new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("share","test:background-share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.detail="Waiting for external text share";c.startActivity(foreignShare);
            Activity handler=receiver.waitForActivityWithTimeout(5000);check(handler!=null,"external text share reaches receiver");
            await("share receiver closes itself",()->handler.isFinishing()||handler.isDestroyed());
            await("external text share delivers in background",()->App.detail.startsWith("Received text"));
            check(!activity.hasWindowFocus()&&!App.focused,"sharing does not open MainActivity");
            removeMonitor(receiver);
            receiver=addMonitor(ShareActivity.class.getName(),null,false);
            Intent foreignImage=new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("share","image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.detail="Waiting for external image share";c.startActivity(foreignImage);
            Activity imageHandler=receiver.waitForActivityWithTimeout(5000);check(imageHandler!=null,"external image share reaches receiver");
            await("image share receiver closes after staging bytes",()->imageHandler.isFinishing()||imageHandler.isDestroyed());
            await("private source URI image delivered after receiver closes",()->App.detail.startsWith("Received image"));
            check(!activity.hasWindowFocus(),"external image share leaves main screen in background");removeMonitor(receiver);
            byte[] screenshot=ImageTests.indexedPNG(3200,2200);String screenshotDigest=Wire.fingerprint(2,screenshot);
            App.detail="Waiting for large PNG share";Intent largeShare=new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("share","screenshot").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(largeShare);
            await("large compact PNG share completes encrypted Swift round trip",()->App.detail.startsWith("Received image")&&App.lastDigest.equals(screenshotDigest));
            check(!activity.hasWindowFocus(),"large PNG share keeps MainActivity in background");
            String defaultKeyboard=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
            foreground();
            runOnMainSync(()->{activity.renderConnection();if(!activity.status.getText().toString().contains("Connected to Mac"))throw new AssertionError("Connected badge");});
            check(true,"prominent connected badge after authentication");
            capture("connected");
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));
            await("Disconnect stops connection service",()->App.service==null);
            runOnMainSync(()->{activity.renderConnection();if(!activity.status.getText().toString().contains("Disconnected"))throw new AssertionError("Disconnected badge");if(!activity.sendButton.getText().toString().equals("Connect & send clipboard"))throw new AssertionError("Offline send action");});
            check(true,"disconnected badge and reconnect send action");capture("disconnected");
            // Sharing while disconnected must reconnect and deliver without a second Send tap.
            App.detail="Waiting for disconnected external share";
            ActivityMonitor reconnectReceiver=addMonitor(ShareActivity.class.getName(),null,false);
            Intent reconnectShare=new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("share","test:share-reconnect").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(reconnectShare);Activity reconnectHandler=reconnectReceiver.waitForActivityWithTimeout(5000);
            check(reconnectHandler!=null,"disconnected external share reaches receiver");
            await("disconnected share receiver closes",()->reconnectHandler.isFinishing()||reconnectHandler.isDestroyed());
            await("Share automatically reconnects and delivers in background",()->App.detail.startsWith("Received text"));
            check(!activity.hasWindowFocus(),"reconnecting share leaves previous app in front");removeMonitor(reconnectReceiver);
            foreground();check(text().equals("mac:test:share-reconnect"),"reconnected share applied exact text");
            // The main clipboard button does the same without changing the selected keyboard.
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));
            await("Second disconnect completed",()->App.service==null);
            runOnMainSync(()->{cb.setPrimaryClip(ClipData.newPlainText("Reconnect copy","test:clipboard-reconnect"));activity.sendClipboard();});
            await("clipboard button reconnects and delivers",()->text().equals("mac:test:clipboard-reconnect"));
            check(defaultKeyboard.equals(Settings.Secure.getString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD)),"selected keyboard remains unchanged");
            android.content.pm.PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_SERVICES);
            boolean hasKeyboard=false;for(android.content.pm.ServiceInfo service:info.services)if("android.permission.BIND_INPUT_METHOD".equals(service.permission))hasKeyboard=true;
            check(!hasKeyboard,"app does not register a replacement keyboard");
            runOnMainSync(()->c.getSharedPreferences("settings",0).edit().putBoolean("automatic",true).commit());
            runOnMainSync(()->cb.setPrimaryClip(ClipData.newPlainText("Focused automatic","test:focused-auto")));
            await("focused automatic sending with normal keyboard",()->text().equals("mac:test:focused-auto"));
            Thread.sleep(1500);check(text().equals("mac:test:focused-auto"),"incoming clipboard echo suppressed");
            runOnMainSync(()->c.getSharedPreferences("settings",0).edit().putBoolean("automatic",false).commit());
            runOnMainSync(()->c.stopService(new Intent(c,SyncService.class)));
            await("Disconnect before removing pairing",()->App.service==null);
            Config.forget(c);
            runOnMainSync(()->{activity.updatePeer();activity.renderConnection();if(!activity.status.getText().toString().contains("Pair with a Mac")||activity.pairingFields.getVisibility()!=android.view.View.VISIBLE)throw new AssertionError("Unpaired state");});
            check(true,"unpaired state exposes pairing without a connected badge");
            ActivityMonitor unpairedReceiver=addMonitor(ShareActivity.class.getName(),null,false);
            c.startActivity(new Intent().setComponent(new ComponentName("org.localclip.tests","localclip.SourceActivity")).putExtra("share","test:unpaired").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity unpairedHandler=unpairedReceiver.waitForActivityWithTimeout(5000);check(unpairedHandler!=null,"unpaired share reaches receiver");
            await("unpaired share closes without opening main screen",()->unpairedHandler.isFinishing()||unpairedHandler.isDestroyed());
            check(!activity.hasWindowFocus()&&App.service==null,"unpaired share does not launch main or a connection");removeMonitor(unpairedReceiver);
            result.putString("stream","Android clipboard integration: "+assertions+" assertions passed on API "+Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
