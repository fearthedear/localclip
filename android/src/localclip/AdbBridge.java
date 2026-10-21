package localclip;
import android.os.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Arrays;

/** Ephemeral-key authenticated/encrypted localhost channel. Mac pairing keys never leave the APK. */
final class AdbBridge implements AutoCloseable {
    static final int PORT=47392;
    static final String TOKEN_FILE="adb-bridge-token";
    static volatile boolean active;
    static volatile String state="Automatic copying off";
    private static volatile AdbBridge owner;
    private final SyncService service;
    private volatile boolean closed;
    private ServerSocket server;
    private volatile Socket client;
    static boolean enabled(android.content.Context c){return c.getSharedPreferences("settings",0).getBoolean("adbBridge",false);}
    AdbBridge(SyncService service){this.service=service;}
    void status(String value,boolean connected){if(owner==this){state=value;active=connected;}}
    void start(){owner=this;new Thread(this::run,"localclip-adb").start();}
    static byte[] receive(Wire.Session session,InputStream in)throws Exception{
        byte[] header=Wire.read(in,4);return session.rx.open(Wire.concat(header,Wire.read(in,Wire.frameLength(header))));
    }
    private void run(){
        String failure=null;
        try{
            byte[] key=Wire.random(32);
            synchronized(this){
                if(closed)return;server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress("127.0.0.1",PORT),2);
                try(OutputStream out=service.openFileOutput(TOKEN_FILE,android.content.Context.MODE_PRIVATE)){out.write((Wire.hex(key)+"\n").getBytes(StandardCharsets.US_ASCII));}
            }
            status("Waiting for ADB helper · run Start automatic copying on your Mac",false);
            while(!closed){
                Socket s=server.accept();
                try{
                    synchronized(this){if(closed){s.close();break;}client=s;}
                    s.setTcpNoDelay(true);s.setSoTimeout(4000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                    Wire.Session session=Wire.server(in,out,key);s.setSoTimeout(15000);
                    byte[] hello=receive(session,in);
                    if(hello.length!=21||hello[0]!=6)throw new IOException("Wrong bridge protocol");
                    int sdk=ByteBuffer.wrap(hello,17,4).getInt();if(sdk<34)throw new IOException("Android 14 or newer required");
                    status("Automatic copying active · Android API "+sdk,true);
                    while(!closed){
                        byte[] frame=receive(session,in);if(frame.length<17)throw new IOException("Invalid bridge frame");
                        int type=frame[0]&255;byte[] body=Arrays.copyOfRange(frame,17,frame.length);
                        if(type==3){if(body.length!=0)throw new IOException("Invalid heartbeat");}
                        else if(type==1||type==2){
                            if(App.unlocked(service))try{
                                Wire.Message message;
                                if(type==1){
                                    String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
                                    if(text.startsWith("localclip1:"))throw new IOException("Pairing code rejected");
                                    message=new Wire.Message(1,body);
                                }else message=Clips.image(body);
                                try{service.send(message,null);App.lastDigest=Wire.fingerprint(message.type,message.body);}
                                catch(Exception e){App.detail="Automatic copy not sent: "+e.getMessage();}
                            }catch(Exception e){App.detail="Automatic copy skipped: unsupported text or image";}
                        }else if(type==5){
                            if(body.length!=0)throw new IOException("Invalid skip notice");App.detail="Automatic copy skipped: image unavailable, unsupported, or too large";
                        }else throw new IOException("Unknown bridge event");
                        out.write(session.tx.seal(new Wire.Message(4,new byte[0]).encode()));out.flush();
                    }
                }catch(Exception e){if(!closed)status("Waiting for ADB helper · restart it on your Mac",false);}
                finally{status(closed?"Automatic copying off":"Waiting for ADB helper · restart it on your Mac",false);s.close();client=null;}
            }
        }catch(Exception e){if(!closed)failure="ADB bridge unavailable · reconnect LocalClip";}
        finally{close();if(failure!=null)status(failure,false);}
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        try{if(client!=null)client.close();}catch(IOException ignored){}
        try{if(server!=null)server.close();}catch(IOException ignored){}
        if(owner==this){service.deleteFile(TOKEN_FILE);status("Automatic copying off",false);}
    }
}
