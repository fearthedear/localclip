package localclip;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Platform-neutral protocol shared by the Android app and desktop test client. */
public final class Wire {
    public static final int MAX_CONTENT = 8 * 1024 * 1024;
    public static final int MAX_FRAME = MAX_CONTENT + 33;
    public static final byte[] MAGIC = new byte[]{76,67,48,49};
    public static byte[] random(int size) { byte[] b = new byte[size]; new SecureRandom().nextBytes(b); return b; }
    public static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); for (byte[] p : parts) b.write(p); return b.toByteArray();
    }
    public static byte[] mac(byte[] secret, String label, byte[] server, byte[] client) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(secret,"HmacSHA256"));
        return m.doFinal(concat(("LC01/"+label).getBytes(StandardCharsets.US_ASCII), server, client));
    }
    public static String discoveryID(byte[] key)throws Exception { return hex(Arrays.copyOf(mac(key,"discovery",new byte[0],new byte[0]),16)); }
    public static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT,"%02x",b & 255)); return s.toString(); }
    public static byte[] unhex(String s) throws IOException {
        if (!s.matches("[0-9a-fA-F]{64}")) throw new IOException("Secret must be 64 hex characters");
        byte[] b = new byte[32]; for (int i=0;i<32;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b;
    }
    public static byte[] read(InputStream in, int size) throws IOException { byte[] b = new byte[size]; new DataInputStream(in).readFully(b); return b; }
    public static int frameLength(byte[] header) throws IOException {
        if (header.length != 4) throw new IOException("Invalid frame header");
        int n = ByteBuffer.wrap(header).getInt(); if(n<33 || n>MAX_FRAME) throw new IOException("Frame outside size limit"); return n;
    }
    public static String fingerprint(int type, byte[] data) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(concat(new byte[]{(byte)type},data))); }
    public static final class Message {
        public final int type; public final byte[] id, body;
        public Message(int type, byte[] body) throws Exception { this(type,random(16),body); }
        public Message(int type, byte[] id, byte[] body) throws Exception {
            if(type<1||type>4||id.length!=16||body.length>MAX_CONTENT) throw new IOException("Unsupported or oversized clipboard");
            if(type==1) StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body));
            if(type==2 && (body.length<8 || !Arrays.equals(Arrays.copyOf(body,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))) throw new IOException("Expected PNG image");
            if(type>=3 && body.length!=0) throw new IOException("Control message has data");
            this.type=type; this.id=id.clone(); this.body=body.clone();
        }
        public byte[] encode() throws IOException { return concat(new byte[]{(byte)type},id,body); }
        public static Message decode(byte[] b) throws Exception {
            if(b.length<17) throw new IOException("Truncated message");
            return new Message(b[0]&255,Arrays.copyOfRange(b,1,17),Arrays.copyOfRange(b,17,b.length));
        }
    }
    public static final class Frames {
        private final byte[] key; private long sequence=0;
        public Frames(byte[] key) { this.key=key.clone(); }
        private byte[] nonce() throws IOException { if(sequence<0||sequence==Long.MAX_VALUE) throw new IOException("Session exhausted"); return ByteBuffer.allocate(12).putInt(0).putLong(sequence).array(); }
        public synchronized byte[] seal(byte[] plain) throws Exception {
            if(plain.length<17||plain.length>MAX_CONTENT+17) throw new IOException("Invalid payload size");
            byte[] header=ByteBuffer.allocate(4).putInt(plain.length+16).array(), iv=nonce();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            c.updateAAD(concat(MAGIC,header,iv)); byte[] result=concat(header,c.doFinal(plain)); sequence++; return result;
        }
        public byte[] open(byte[] frame) throws Exception {
            if(frame.length<4) throw new IOException("Missing header");
            byte[] header=Arrays.copyOf(frame,4); if(frameLength(header)+4!=frame.length) throw new IOException("Truncated frame");
            byte[] iv=nonce(); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            c.updateAAD(concat(MAGIC,header,iv)); byte[] plain=c.doFinal(Arrays.copyOfRange(frame,4,frame.length)); sequence++; return plain;
        }
        public Message receive(InputStream in) throws Exception { byte[] header=read(in,4); return Message.decode(open(concat(header,read(in,frameLength(header))))); }
    }
    public static final class Session {
        public final Frames tx, rx;
        public Session(Frames tx, Frames rx) { this.tx=tx; this.rx=rx; }
    }
    public static Session client(InputStream in, OutputStream out, byte[] secret) throws Exception {
        if(!MessageDigest.isEqual(read(in,4),MAGIC)) throw new IOException("Wrong protocol version");
        byte[] server=read(in,32), client=random(32);
        out.write(concat(client,mac(secret,"client",server,client))); out.flush();
        if(!MessageDigest.isEqual(read(in,32),mac(secret,"server",server,client))) throw new GeneralSecurityException("Peer authentication failed");
        return new Session(new Frames(mac(secret,"c2s",server,client)),new Frames(mac(secret,"s2c",server,client)));
    }
    public static Session server(InputStream in, OutputStream out, byte[] secret) throws Exception {
        byte[] server=random(32);out.write(concat(MAGIC,server));out.flush();
        byte[] client=read(in,32),proof=read(in,32);
        if(!MessageDigest.isEqual(proof,mac(secret,"client",server,client)))throw new GeneralSecurityException("Peer authentication failed");
        out.write(mac(secret,"server",server,client));out.flush();
        return new Session(new Frames(mac(secret,"s2c",server,client)),new Frames(mac(secret,"c2s",server,client)));
    }

}
