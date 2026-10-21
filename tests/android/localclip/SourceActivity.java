package localclip;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
public final class SourceActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);EditText text=new EditText(this);text.setText("Clipboard source for LocalClip tests");setContentView(text);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(hasWindowFocus())onWindowFocusChanged(true);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){String value=getIntent().getStringExtra("text");if(value!=null)new Handler(Looper.getMainLooper()).postDelayed(()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Source fixture",value)),350);}}
}
