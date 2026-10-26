package localclip;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
/** Test-only, non-exported source app provider. Grants come only from the Share intent. */
public final class SourceImageProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode)||!("/image.png".equals(uri.getPath())||"/screenshot.png".equals(uri.getPath())))throw new FileNotFoundException();
        if("/screenshot.png".equals(uri.getPath()))try{File f=new File(getContext().getCacheDir(),"test-screenshot.png");try(FileOutputStream out=new FileOutputStream(f)){out.write(ImageTests.indexedPNG(3200,2200));}return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){throw new FileNotFoundException();}
        try{File f=new File(getContext().getCacheDir(),"test-source.png");Bitmap b=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);b.eraseColor(0xff00aaff);try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException e){throw new FileNotFoundException();}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
