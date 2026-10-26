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
        if(source.length>Wire.MAX_CONTENT)throw new Exception("Image exceeds 8 MiB");
        checkDimensions(source);Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length);if(bitmap==null)throw new Exception("Cannot decode image");
        try {
            // Screenshots already in PNG need no conversion. Re-encoding can expand a
            // valid compact PNG past the wire limit or change its color metadata.
            if(source.length>=8&&java.util.Arrays.equals(java.util.Arrays.copyOf(source,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return new Wire.Message(2,source);
            ImageOutput out=new ImageOutput();boolean encoded=bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            // Android's native encoder can turn an OutputStream exception into false.
            // Keep the overflow reason instead of misreporting it as an encoder failure.
            if(out.exceeded)throw new Exception("Converted image exceeds 8 MiB. Share a smaller image.");
            if(!encoded)throw new Exception("Cannot convert this image to PNG");return new Wire.Message(2,out.bytes.toByteArray());
        }finally{bitmap.recycle();}
    }
    private static final class ImageOutput extends OutputStream {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();boolean exceeded;
        @Override public void write(byte[] b,int off,int len)throws IOException{if(len>Wire.MAX_CONTENT-bytes.size()){exceeded=true;throw new IOException("PNG size limit");}bytes.write(b,off,len);}
        @Override public void write(int b)throws IOException{if(bytes.size()>=Wire.MAX_CONTENT){exceeded=true;throw new IOException("PNG size limit");}bytes.write(b);}
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
