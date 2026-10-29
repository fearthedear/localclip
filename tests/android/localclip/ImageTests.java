package localclip;
import android.app.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Exercises real Android decoders/encoders, including PNG expansion beyond the wire limit. */
public final class ImageTests extends Instrumentation {
    int assertions;
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception("FAILED "+label);assertions++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    static void chunk(DataOutputStream out,String name,byte[] bytes)throws Exception{
        byte[] type=name.getBytes(StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(type);crc.update(bytes);
        out.writeInt(bytes.length);out.write(type);out.write(bytes);out.writeInt((int)crc.getValue());
    }
    static byte[] indexedPNG(int w,int h)throws Exception{
        ByteArrayOutputStream result=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(result);
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(header);
        data.writeInt(w);data.writeInt(h);data.write(new byte[]{8,3,0,0,0});chunk(out,"IHDR",header.toByteArray());
        Random random=new Random(37);byte[] palette=new byte[768];random.nextBytes(palette);chunk(out,"PLTE",palette);
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(DeflaterOutputStream z=new DeflaterOutputStream(compressed,new Deflater(9))){byte[] row=new byte[w];for(int y=0;y<h;y++){random.nextBytes(row);z.write(0);z.write(row);}}
        chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);return result.toByteArray();
    }
    static byte[] noisyJPEG()throws Exception{
        Bitmap noise=Bitmap.createBitmap(2400,2000,Bitmap.Config.ARGB_8888);Random random=new Random(41);int[] pixels=new int[2400];
        for(int y=0;y<2000;y++){for(int x=0;x<pixels.length;x++)pixels[x]=0xff000000|random.nextInt(0x1000000);noise.setPixels(pixels,0,2400,0,y,2400,1);}
        ByteArrayOutputStream jpeg=new ByteArrayOutputStream();noise.compress(Bitmap.CompressFormat.JPEG,85,jpeg);noise.recycle();return jpeg.toByteArray();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        byte[] original=indexedPNG(3200,2200);check(original.length<Wire.MAX_CONTENT,"compact PNG fixture fits 8 MiB input limit");
        Bitmap bitmap=BitmapFactory.decodeByteArray(original,0,original.length);check(bitmap!=null,"compact PNG is decodable on Android");int expected=bitmap.getPixel(171,123);
        ByteArrayOutputStream expanded=new ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.PNG,100,expanded),"native PNG re-encoding succeeds without a size cap");bitmap.recycle();
        check(expanded.size()>Wire.MAX_CONTENT,"re-encoding expands a valid PNG beyond 8 MiB (original failure trigger)");
        Wire.Message message=Clips.image(original);check(Arrays.equals(message.body,original),"PNG sharing preserves exact bytes without re-encoding");
        Bitmap decoded=BitmapFactory.decodeByteArray(message.body,0,message.body.length);check(decoded.getWidth()==3200&&decoded.getHeight()==2200&&decoded.getPixel(171,123)==expected,"large PNG dimensions and pixels survive preparation");decoded.recycle();
        Bitmap small=Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888);small.eraseColor(0xff28a060);ByteArrayOutputStream jpg=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,95,jpg);small.recycle();
        Wire.Message converted=Clips.image(jpg.toByteArray());Bitmap photo=BitmapFactory.decodeByteArray(converted.body,0,converted.body.length);check(photo!=null&&photo.getWidth()==30&&photo.getHeight()==20,"non-PNG images still convert to valid PNG");photo.recycle();
        byte[] jpeg=noisyJPEG();check(jpeg.length<Wire.MAX_CONTENT,"conversion-overflow fixture fits source byte limit");
        Wire.Message fitted=Clips.image(jpeg);check(fitted.body.length<=Wire.MAX_CONTENT,"previously failing JPEG now fits the encrypted transfer limit");
        Bitmap resized=BitmapFactory.decodeByteArray(fitted.body,0,fitted.body.length);
        check(resized!=null&&resized.getWidth()<2400&&resized.getHeight()<2000,"oversized conversion becomes a smaller valid PNG");
        check(Math.abs((double)resized.getWidth()/resized.getHeight()-1.2)<0.002,"resizing preserves image aspect ratio");resized.recycle();
        check(fitted.resizedImage&&!message.resizedImage&&!converted.resizedImage,"sender UI metadata identifies only resized images");
        check(!Wire.Message.decode(fitted.encode()).resizedImage,"resize UI metadata does not change the network protocol");
        try(FileOutputStream file=getTargetContext().openFileOutput("resized-photo.png",android.content.Context.MODE_PRIVATE)){file.write(fitted.body);}
        byte[] bigPNG=indexedPNG(3600,2600);check(bigPNG.length>Wire.MAX_CONTENT&&bigPNG.length<Clips.MAX_IMAGE_SOURCE,"oversized PNG fixture fits the bounded source allowance");
        Wire.Message fittedPNG=Clips.image(bigPNG);check(fittedPNG.resizedImage&&fittedPNG.body.length<=Wire.MAX_CONTENT,"PNG source over 8 MiB is resized into a valid transfer");
        boolean bounded=false;try{Clips.boundedRead(new ByteArrayInputStream(new byte[2049]),2048);}catch(Exception e){bounded=true;}check(bounded,"image source reader enforces its configured byte bound");
        boolean invalid=false;try{Clips.image(new byte[]{1,2,3});}catch(Exception e){invalid=true;}check(invalid,"invalid image bytes are rejected");
        result.putString("stream","Android image preparation: "+assertions+" assertions passed on API "+Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
