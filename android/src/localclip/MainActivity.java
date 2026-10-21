package localclip;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    static final int BLUE=0xff2458da, INK=0xff182237, MUTED=0xff526078;
    TextView status,detail,peer,connectionHelp;EditText pairing;CheckBox auto;TextView bridgeStatus;
    Button connectionButton,sendButton,pairToggle;
    LinearLayout pairingFields;
    final Handler handler=new Handler(Looper.getMainLooper());
    ClipData shared;boolean sendOnFocus=false;long sharedAt;
    Config config;
    ClipboardManager clipboard;ClipboardManager.OnPrimaryClipChangedListener listener;
    final Runnable refresh=new Runnable(){public void run(){
        if(shared!=null&&SystemClock.elapsedRealtime()-sharedAt>30000){shared=null;App.detail="Shared item expired. Share it to LocalClip again.";}
        if(shared!=null&&hasWindowFocus()&&isConnected()){ClipData clip=shared;shared=null;App.send(MainActivity.this,clip,true);}
        renderConnection();bridgeStatus.setText(AdbBridge.active&&!isConnected()?"Helper active · Mac offline; copies are not queued":AdbBridge.enabled(MainActivity.this)&&App.service==null?"Connect to Mac, then start the ADB helper":AdbBridge.state);bridgeStatus.setTextColor(AdbBridge.active&&isConnected()?0xff137447:MUTED);detail.setText(App.detail);handler.postDelayed(this,400);
    }};
    boolean isConnected(){SyncService s=App.service;return s!=null&&s.connected();}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xfff3f5fa);getWindow().setNavigationBarColor(0xfff3f5fa);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff3f5fa);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(18),dp(20),dp(32));scroll.addView(root);setContentView(scroll);
        TextView title=label(root,"LocalClip",30);title.setTypeface(null,Typeface.BOLD);
        label(root,"Mac ↔ Android · Keep using Gboard",15).setTextColor(MUTED);

        LinearLayout connection=card(root);
        status=label(connection,"Disconnected",23);status.setTypeface(null,Typeface.BOLD);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        peer=label(connection,"",14);peer.setTextColor(MUTED);
        connectionHelp=label(connection,"",14);
        connectionButton=button(connection,"Connect to Mac",()->{
            if(App.service!=null){stopService(new Intent(this,SyncService.class));shared=null;App.detail="Sharing paused. Tap Connect when you want to resume.";}
            else connect();
        },false);

        LinearLayout sending=card(root);
        label(sending,"Send manually",21).setTypeface(null,Typeface.BOLD);
        label(sending,"Copy text or an image in any app. Open LocalClip, then tap the blue button below. Gboard can stay selected.",15);
        sendButton=button(sending,"Send clipboard to Mac",this::sendClipboard,true);
        button(sending,"Choose a photo to send",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,10);},false);
        label(sending,"Or send directly from another app",16).setTypeface(null,Typeface.BOLD);
        label(sending,"1. Select text or open a photo.\n2. Tap Share (not just Copy).\n3. Choose LocalClip in the Android share sheet.\n\nSharing sends in the background and returns to your previous app. You can also use Share on Android’s clipboard popup after copying. If no Share action is available, use Copy and the blue button above.",15);
        auto=new CheckBox(this);auto.setText("Send new clipboard changes while LocalClip is open");auto.setTextColor(INK);auto.setChecked(App.automatic(this));auto.setOnCheckedChangeListener((v,checked)->getSharedPreferences("settings",0).edit().putBoolean("automatic",checked).apply());sending.addView(auto);
        label(sending,"Without the optional ADB helper, Android allows clipboard reading only while LocalClip is on screen.",13).setTextColor(MUTED);
        LinearLayout bridgeCard=card(root);
        label(bridgeCard,"Automatic copying with Gboard",21).setTypeface(null,Typeface.BOLD);
        CheckBox bridge=new CheckBox(this);bridge.setText("Allow automatic copying through the ADB helper");bridge.setTextColor(INK);bridge.setChecked(AdbBridge.enabled(this));bridge.setEnabled(Build.VERSION.SDK_INT>=34);bridgeCard.addView(bridge);
        bridgeStatus=label(bridgeCard,AdbBridge.state,15);bridgeStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        label(bridgeCard,"1. Connect to your Mac above.\n2. Enable this option.\n3. On your Mac, run Start automatic copying.command from the ADB bridge folder.\n\nRequires USB or wireless debugging for setup. Copying then sends automatically while connected and unlocked, with Gboard still selected. Restart the helper after a phone reboot or disconnecting LocalClip. Sensitive-marked items and pairing codes are skipped.",14).setTextColor(MUTED);
        bridge.setOnCheckedChangeListener((v,checked)->{getSharedPreferences("settings",0).edit().putBoolean("adbBridge",checked).apply();SyncService service=App.service;if(service!=null)service.refreshBridge();else AdbBridge.state=checked?"Connect to Mac, then start the ADB helper":"Automatic copying off";});

        root.removeView(bridgeCard);root.addView(bridgeCard,3);
        LinearLayout transfer=card(root);
        label(transfer,"Last transfer",16).setTypeface(null,Typeface.BOLD);detail=label(transfer,App.detail,15);detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        LinearLayout pairingCard=card(root);
        pairToggle=button(pairingCard,"Pair with a Mac",()->pairingFields.setVisibility(pairingFields.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE),false);
        pairingFields=new LinearLayout(this);pairingFields.setOrientation(LinearLayout.VERTICAL);pairingCard.addView(pairingFields);
        label(pairingFields,"On the Mac: Start sharing → Copy pairing code. Paste that code below.",15);
        pairing=new EditText(this);pairing.setHint("Paste localclip1:… pairing code");pairing.setTextColor(INK);pairing.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);pairing.setSingleLine(true);pairing.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);pairingFields.addView(pairing);
        button(pairingFields,"Save pairing & connect",()->{
            try{if(App.service!=null){App.detail="Disconnect before changing the paired Mac.";return;}
                Config.save(this,pairing.getText().toString().trim());pairing.setText("");updatePeer();App.detail="Pairing saved. Connecting to your Mac…";connect();
            }catch(Exception e){App.detail=e.getMessage();}
        },true);
        button(pairingFields,"Forget this Mac",()->{
            shared=null;stopService(new Intent(this,SyncService.class));
            try{Config.forget(this);App.lastDigest="";updatePeer();App.detail="Pairing removed. Pair with a Mac to send again.";}catch(Exception e){App.detail="Could not remove pairing";}
        },false);
        label(root,"Mac → Android arrives automatically while connected and unlocked. Keep the connection notification running and the Mac awake. No cloud or telemetry. Images: up to 8 MiB / 16 megapixels; pasting depends on the destination app. Build 0.3.0.",13).setTextColor(MUTED);
        clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        listener=()->{if(hasWindowFocus()&&!AdbBridge.active&&App.automatic(this)&&isConnected())App.send(this,clipboard.getPrimaryClip(),false);};
        updatePeer();handleIntent(getIntent());renderConnection();
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout card(LinearLayout root){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(12),dp(18),dp(16));box.setBackground(background(Color.WHITE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);root.addView(box,p);return box;}
    TextView label(LinearLayout root,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(INK);t.setPadding(0,dp(6),0,dp(6));root.addView(t);return t;}
    Button button(LinearLayout root,String title,Runnable action,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(52));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setTextColor(primary?Color.WHITE:BLUE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?BLUE:0xffeaf0ff));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);root.addView(b,p);return b;}
    void updatePeer(){
        try{config=Config.load(this);peer.setText(config==null?"No Mac paired":config.host+":"+config.port);pairToggle.setText(config==null?"Pair with a Mac":"Change Mac pairing");pairingFields.setVisibility(config==null?View.VISIBLE:View.GONE);}
        catch(Exception e){config=null;peer.setText("Pairing storage unavailable. Pair again.");pairingFields.setVisibility(View.VISIBLE);}
    }
    void renderConnection(){
        SyncService service=App.service;boolean connected=service!=null&&service.connected();
        boolean checking=connected&&service.responseAgeMillis()>25000;
        String title,help;int color;
        if(config==null){title="○  Pair with a Mac";help="Add your Mac pairing code below to get started.";color=MUTED;}
        else if(connected&&!checking){title="●  Connected to Mac";color=0xff137447;help="Encrypted connection · Last Mac response "+service.responseAgeMillis()/1000+"s ago.\nMac copies arrive automatically. Use automatic copying or send manually below.";}
        else if(checking){title="◌  Checking connection…";color=0xff9b6500;help="No recent Mac response. Keep both devices online; LocalClip will reconnect if needed.";}
        else if(service==null){title="○  Disconnected";color=0xffa72a35;help="No clipboard is being shared. Tap Connect to Mac, or send an item to reconnect.";}
        else if(App.phase==App.Phase.PAIRING_REJECTED){title="!  Pairing rejected";color=0xffa72a35;help="Disconnect, then copy and save a fresh pairing code from your Mac.";}
        else{title=App.phase==App.Phase.RECONNECTING?"◌  Reconnecting…":"◌  Connecting…";color=0xff9b6500;help=App.status+"\nCheck that the Mac app is sharing and reachable on Wi-Fi or Tailscale.";}
        status.setText(title);status.setTextColor(color);connectionHelp.setText(help);
        connectionButton.setText(service==null?"Connect to Mac":connected?"Disconnect / pause sharing":"Cancel connection");connectionButton.setEnabled(config!=null);
        sendButton.setText(connected?"Send clipboard to Mac":"Connect & send clipboard");sendButton.setAlpha(config==null?0.5f:1f);
    }
    void connect(){
        try{if(config==null){App.detail="Pair with a Mac below before sending.";pairingFields.setVisibility(View.VISIBLE);return;}
            if(App.service!=null)return;
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("settings",0).getBoolean("notificationAsked",false)){
                getSharedPreferences("settings",0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
            }
            App.phase=App.Phase.CONNECTING;App.status="Opening encrypted connection…";startForegroundService(new Intent(this,SyncService.class));
        }catch(Exception e){App.detail="Could not start connection: "+e.getMessage();}
    }
    void queue(ClipData clip){
        if(clip==null){App.detail="Nothing available to send. Copy text or share a photo to LocalClip.";return;}
        shared=clip;sharedAt=SystemClock.elapsedRealtime();
        App.detail=isConnected()?"Sending shared item to Mac…":"Connecting, then sending your item…";
        if(hasWindowFocus())connect();
    }
    void sendClipboard(){
        if(!hasWindowFocus()){sendOnFocus=true;return;}
        if(config==null){connect();return;}
        ClipData clip=clipboard.getPrimaryClip();if(isConnected())App.send(this,clip,true);else queue(clip);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);}
    void handleIntent(Intent intent){
        if("localclip.SEND".equals(intent.getAction())){sendOnFocus=true;if(hasWindowFocus()){sendOnFocus=false;sendClipboard();}return;}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);App.focused=focus;if(focus){if(sendOnFocus){sendOnFocus=false;handler.postDelayed(this::sendClipboard,200);}if(shared!=null)connect();}}
    @Override protected void onResume(){super.onResume();clipboard.addPrimaryClipChangedListener(listener);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);clipboard.removePrimaryClipChangedListener(listener);App.focused=false;super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null)queue(ClipData.newUri(getContentResolver(),"Selected image",data.getData()));}
}
