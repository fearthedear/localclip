package localclip;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;
public final class SendTile extends TileService {
    @Override public void onClick(){super.onClick();
        unlockAndRun(()->{Intent intent=new Intent(this,MainActivity.class).setAction("localclip.SEND").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,2,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));else startActivityAndCollapse(intent);
        });
    }
}
