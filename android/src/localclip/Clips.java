package localclip;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.PersistableBundle;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class Clips {
    static final int MAX_PIXELS=16_000_000;
    static Wire.Message read(Context c,ClipData clip) throws Exception {
        PersistableBundle extras=clip.getDescription().getExtras();
        if(extras!=null&&extras.getBoolean("android.content.extra.IS_SENSITIVE",false))throw new Exception("Sensitive clipboard skipped");
        if(clip.getItemCount()<1)throw new Exception("Clipboard is empty");
        ClipData.Item item=clip.getItemAt(0);Uri uri=item.getUri();
        if(uri!=null && clip.getDescription().hasMimeType("image/*")) {
            if(!"content".equals(uri.getScheme()))throw new Exception("Only content-provider images are supported");
            byte[] source;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Image permission unavailable");source=boundedRead(in);}
            return image(source);
        }
        CharSequence text=item.getText();if(text==null)throw new Exception("Copy text or share an image to LocalClip");
        if(text.toString().startsWith("localclip1:"))throw new Exception("Pairing codes are never synced");
        return new Wire.Message(1,text.toString().getBytes(StandardCharsets.UTF_8));
    }
    static byte[] boundedRead(InputStream in) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>Wire.MAX_CONTENT)throw new Exception("Image exceeds 8 MiB");out.write(b,0,n);}return out.toByteArray();
    }
    static void checkDimensions(byte[] b) throws Exception {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(b,0,b.length,options);
        if(options.outWidth<=0||options.outHeight<=0||options.outWidth>16384||options.outHeight>16384||(long)options.outWidth*options.outHeight>MAX_PIXELS)throw new Exception("Image must be at most 16 megapixels");
    }
    static Wire.Message image(byte[] source) throws Exception {
        checkDimensions(source);Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length);if(bitmap==null)throw new Exception("Cannot decode image");
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream(){@Override public synchronized void write(byte[] b,int off,int len){if(count+len>Wire.MAX_CONTENT)throw new IllegalArgumentException("PNG exceeds 8 MiB");super.write(b,off,len);} @Override public synchronized void write(int b){if(count>=Wire.MAX_CONTENT)throw new IllegalArgumentException("PNG exceeds 8 MiB");super.write(b);}};
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Cannot encode PNG");return new Wire.Message(2,out.toByteArray());
        }finally{bitmap.recycle();}
    }
    static void apply(Context c,Wire.Message m) throws Exception {
        if(!App.unlocked(c))throw new Exception("Phone locked; unlock it and send again from Mac");
        ClipboardManager cb=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip;
        if(m.type==1)clip=ClipData.newPlainText("LocalClip",new String(m.body,StandardCharsets.UTF_8));
        else if(m.type==2){
            checkDimensions(m.body);File dir=new File(c.getCacheDir(),"received");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Cannot create image cache");
            File f=new File(dir,Wire.hex(m.id)+".png");try(FileOutputStream out=new FileOutputStream(f)){out.write(m.body);}
            clip=ClipData.newUri(c.getContentResolver(),"LocalClip image",Uri.parse("content://org.localclip.android.images/"+f.getName()));
            File[] files=dir.listFiles((d,name)->!name.equals(f.getName()));if(files!=null){java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(int i=3;i<files.length;i++)files[i].delete();}
        }else throw new Exception("Invalid clipboard type");
        App.lastDigest=Wire.fingerprint(m.type,m.body);cb.setPrimaryClip(clip);App.detail="Received "+(m.type==1?"text":"image")+" from Mac — ready to paste";
    }
}
