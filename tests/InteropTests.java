import localclip.Wire;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class InteropTests {
    static String host;static int port;static byte[] secret;static int count;
    static void check(boolean b,String what){if(!b)throw new AssertionError(what);count++;}
    static final class Client implements AutoCloseable {
        Socket s;Wire.Session crypto;InputStream in;OutputStream out;
        Client()throws Exception{s=new Socket();s.connect(new InetSocketAddress(host,port),3000);s.setSoTimeout(4000);s.setTcpNoDelay(true);in=s.getInputStream();out=s.getOutputStream();crypto=Wire.client(in,out,secret);}
        void send(Wire.Message m,boolean fragmented)throws Exception{byte[] b=crypto.tx.seal(m.encode());if(fragmented){for(int i=0;i<b.length;i+=7){out.write(b,i,Math.min(7,b.length-i));}}else out.write(b);out.flush();}
        void verify(Wire.Message m)throws Exception{Wire.Message ack=crypto.rx.receive(in);Wire.Message echo=crypto.rx.receive(in);check(ack.type==4&&Arrays.equals(ack.id,m.id),"ack ID");check(echo.type==m.type&&Arrays.equals(echo.body,m.body),"cross-platform echo");}
        public void close()throws Exception{s.close();}
    }
    static void closed(Client c,String label)throws Exception{check(c.in.read()==-1,label);}
    public static void main(String[] args)throws Exception{
        host=args[0];port=Integer.parseInt(args[1]);secret=Wire.unhex(args[2]);
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
        long start=System.nanoTime();
        try(Client c=new Client()){
            Wire.Message unicode=new Wire.Message(1,"Mac ↔ Pixel • 日本語 🌏\nline two".getBytes(StandardCharsets.UTF_8));c.send(unicode,true);c.verify(unicode);
            Wire.Message image=new Wire.Message(2,png);c.send(image,true);c.verify(image);
            Wire.Message empty=new Wire.Message(1,new byte[0]);c.send(empty,false);c.verify(empty);
            Wire.Message large=new Wire.Message(1,new byte[Wire.MAX_CONTENT]);c.send(large,false);c.verify(large);
            for(int i=0;i<50;i++){Wire.Message m=new Wire.Message(1,("latency "+i).getBytes(StandardCharsets.UTF_8));c.send(m,false);c.verify(m);}
            Wire.Message m=new Wire.Message(1,"replay check".getBytes(StandardCharsets.UTF_8));byte[] frame=c.crypto.tx.seal(m.encode());c.out.write(frame);c.out.flush();c.verify(m);c.out.write(frame);c.out.flush();closed(c,"replay closes connection");
        }
        try(Client c=new Client()){byte[] frame=c.crypto.tx.seal(new Wire.Message(1,"tamper".getBytes(StandardCharsets.UTF_8)).encode());frame[frame.length-1]^=1;c.out.write(frame);c.out.flush();closed(c,"tamper closes connection");}
        try(Client c=new Client()){c.out.write(ByteBuffer.allocate(4).putInt(Wire.MAX_FRAME+1).array());c.out.flush();closed(c,"oversize closes connection");}
        try(Socket s=new Socket(host,port)){s.setSoTimeout(4000);Wire.read(s.getInputStream(),36);s.getOutputStream().write(new byte[64]);s.getOutputStream().flush();check(s.getInputStream().read()==-1,"wrong secret rejected");}
        try(Client c=new Client()){Wire.Message m=new Wire.Message(1,"reconnect".getBytes(StandardCharsets.UTF_8));c.send(m,false);c.verify(m);}
        System.out.println("Java ↔ Swift TCP integration: "+count+" assertions passed, "+String.format(Locale.ROOT,"%.2f",(System.nanoTime()-start)/1e9)+"s including 8 MiB payload, Unicode, PNG, fragmentation, replay, tamper, oversize, wrong secret and reconnect");
    }
}
