package localclip;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;

public final class LayoutTests extends Instrumentation {
    MainActivity main;int assertions;
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception("FAILED "+label);assertions++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    ScrollView scroll(){return (ScrollView)((android.view.ViewGroup)main.findViewById(android.R.id.content)).getChildAt(0);}
    void safe(String label)throws Exception{
        waitForIdleSync();Thread.sleep(500);final boolean[] good=new boolean[3];
        runOnMainSync(()->{ScrollView s=scroll();Insets safe=s.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            good[0]=safe.top>0&&s.getPaddingTop()>=safe.top;good[1]=s.getPaddingBottom()>=safe.bottom;
            LinearLayout root=(LinearLayout)s.getChildAt(0);int[] position=new int[2];root.getChildAt(0).getLocationOnScreen(position);good[2]=position[1]>=safe.top;
        });check(good[0],label+" content clears status bar and cutout");check(good[1],label+" viewport clears bottom system UI");check(good[2],label+" title stays below status bar");
    }
    void capture(String name)throws Exception{Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new Exception("No screenshot");try(FileOutputStream out=getTargetContext().openFileOutput(name,Context.MODE_PRIVATE)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));safe("Portrait");capture("layout-portrait.png");
        runOnMainSync(()->{main.addressFields.setVisibility(View.VISIBLE);main.address.requestFocus();main.getSystemService(InputMethodManager.class).showSoftInput(main.address,InputMethodManager.SHOW_IMPLICIT);});
        Thread.sleep(1200);final boolean[] ime={false};runOnMainSync(()->ime[0]=scroll().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));check(ime[0],"keyboard is visible for inset test");safe("Keyboard open");
        runOnMainSync(()->main.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(main.address.getWindowToken(),0));Thread.sleep(500);
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);runOnMainSync(()->main.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Activity rotated=monitor.waitForActivityWithTimeout(7000);check(rotated instanceof MainActivity,"landscape activity recreated");main=(MainActivity)rotated;removeMonitor(monitor);safe("Landscape");capture("layout-landscape.png");
        runOnMainSync(()->main.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        result.putString("stream","Android window insets: "+assertions+" assertions passed on API "+Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
