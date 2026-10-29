package localclip;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.concurrent.RejectedExecutionException;

/** Android requires an Activity share target. This transparent receiver never opens MainActivity. */
public final class ShareActivity extends Activity {
    private boolean started;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        overridePendingTransition(0,0);
    }
    @Override protected void onPostResume(){
        super.onPostResume();if(started)return;started=true;
        try{
            final ClipData clip=sharedClip(getIntent());
            if(!App.unlocked(this))throw new Exception("Unlock the phone before sharing");
            try{App.contentWorker.execute(()->{
                try{
                    Context c=getApplicationContext();
                    if(Config.load(c)==null)throw new Exception("Pair with a Mac in LocalClip first");
                    // Read/convert URI data before finishing: the source's grant belongs to this Activity.
                    Wire.Message message=Clips.read(c,clip);
                    App.main.post(()->{
                        if(isFinishing()||isDestroyed())return;
                        try{App.handoffShared(this,message);done(message.resizedImage?"Large image resized to fit · sending to Mac":"Sending to Mac in the background");}
                        catch(Exception e){done("Could not start sharing. Open LocalClip and try again.");}
                    });
                }catch(Exception e){App.main.post(()->done(e.getMessage()==null?"Could not read shared item":e.getMessage()));}
            });}catch(RejectedExecutionException e){throw new Exception("LocalClip is busy. Share again in a moment.");}
        }catch(Exception e){done(e.getMessage()==null?"Nothing available to share":e.getMessage());}
    }
    static ClipData sharedClip(Intent intent)throws Exception{
        ClipData result;
        if(Intent.ACTION_SEND.equals(intent.getAction())){
            if(intent.getType()!=null&&intent.getType().startsWith("image/")){
                Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(uri==null||!"content".equals(uri.getScheme()))throw new Exception("Share an image from an Android content provider");
                result=new ClipData("Shared image",new String[]{intent.getType()},new ClipData.Item(uri));
            }else{
                CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if(text==null)throw new Exception("No text was shared");result=ClipData.newPlainText("Shared text",text);
            }
        }else if(Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())){
            CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if(text==null)throw new Exception("No selected text was shared");result=ClipData.newPlainText("Selected text",text);
        }else throw new Exception("Unsupported share action");
        ClipData original=intent.getClipData();
        if(original!=null&&original.getDescription().getExtras()!=null)result.getDescription().setExtras(original.getDescription().getExtras());
        return result;
    }
    private void done(String message){
        if(isDestroyed())return;
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);finish();overridePendingTransition(0,0);
    }
}
