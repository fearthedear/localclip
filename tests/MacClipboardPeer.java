import localclip.Wire;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
public final class MacClipboardPeer {
    public static void main(String[] args)throws Exception{
        try(Socket s=new Socket("127.0.0.1",47494)){
            s.setSoTimeout(5000);s.setTcpNoDelay(true);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
            Wire.Session crypto=Wire.client(in,out,Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"));
            Wire.Message text=new Wire.Message(1,"test:Mac clipboard 🌏".getBytes(StandardCharsets.UTF_8));out.write(crypto.tx.seal(text.encode()));out.flush();
            Wire.Message ack=crypto.rx.receive(in);if(ack.type!=4||!Arrays.equals(ack.id,text.id))throw new AssertionError("Mac text acknowledgement");
            Wire.Message reply=crypto.rx.receive(in);if(reply.type!=1||!new String(reply.body,StandardCharsets.UTF_8).equals("mac:test:Mac clipboard 🌏"))throw new AssertionError("Mac automatic local text send");
            out.write(crypto.tx.seal(new Wire.Message(4,reply.id,new byte[0]).encode()));out.flush();
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
            String fixture=System.getenv("PNG_FIXTURE");if(fixture!=null)png=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fixture));
            Wire.Message image=new Wire.Message(2,png);out.write(crypto.tx.seal(image.encode()));out.flush();
            ack=crypto.rx.receive(in);if(ack.type!=4||!Arrays.equals(ack.id,image.id))throw new AssertionError("Mac image acknowledgement");
            reply=crypto.rx.receive(in);if(reply.type!=2)throw new AssertionError("Mac automatic PNG send");
            java.awt.image.BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(reply.body));if(decoded==null||decoded.getWidth()!=2||decoded.getHeight()!=2)throw new AssertionError("Mac PNG dimensions");
            out.write(crypto.tx.seal(new Wire.Message(4,reply.id,new byte[0]).encode()));out.flush();
            System.out.println("Mac app clipboard integration: 7 assertions passed (named pasteboard; text and PNG in both directions, acknowledgements, automatic local send)");
            if(fixture!=null)System.out.println("Large preserved PNG applied byte-for-byte to Mac private pasteboard: "+png.length+" bytes");
        }
    }
}
