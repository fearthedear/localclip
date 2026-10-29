package localclip;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
public final class SourceActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);EditText text=new EditText(this);text.setText("Clipboard source for LocalClip tests");setContentView(text);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("share")||intent.hasExtra("copyImage"))onPostResume();if(hasWindowFocus())onWindowFocusChanged(true);}
    @Override protected void onPostResume(){super.onPostResume();
        Intent request=getIntent();
        if(request.getBooleanExtra("copyImage",false)){request.removeExtra("copyImage");new Handler(Looper.getMainLooper()).postDelayed(()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newUri(getContentResolver(),"Source image",android.net.Uri.parse("content://org.localclip.tests.images/image.png"))),350);}
        String command=request.getStringExtra("share");if(command==null)return;request.removeExtra("share");
        Intent share=new Intent(Intent.ACTION_SEND).setPackage("org.localclip.android");
        if("image".equals(command)||"screenshot".equals(command)||"photo".equals(command)){android.net.Uri uri=android.net.Uri.parse("content://org.localclip.tests.images/"+("photo".equals(command)?"photo.jpg":"screenshot".equals(command)?"screenshot.png":"image.png"));share.setType("photo".equals(command)?"image/jpeg":"image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newUri(getContentResolver(),"Source image",uri));}
        else share.setType("text/plain").putExtra(Intent.EXTRA_TEXT,command);
        startActivity(share);
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){String value=getIntent().getStringExtra("text");if(value!=null){getIntent().removeExtra("text");new Handler(Looper.getMainLooper()).postDelayed(()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(makeClip(value)),350);}}}
    ClipData makeClip(String value){ClipData clip=ClipData.newPlainText("Source fixture",value);if(getIntent().getBooleanExtra("sensitive",false)){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}return clip;}
}
