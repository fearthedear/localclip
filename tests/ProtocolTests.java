import localclip.Wire;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ProtocolTests {
    static int count;
    interface Action {void run()throws Exception;}
    static void check(boolean b,String what){if(!b)throw new AssertionError(what);count++;}
    static void rejects(Action a,String what)throws Exception{boolean failed=false;try{a.run();}catch(Exception e){failed=true;}check(failed,what);}
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32],s=new byte[32],c=new byte[32];for(int i=0;i<32;i++){key[i]=(byte)i;s[i]=(byte)(i+32);c[i]=(byte)(i+64);}
        String expected=args[0];check(Wire.hex(Wire.mac(key,"client",s,c)).equals(expected),"HMAC vector");
        check(Wire.discoveryID(key).equals("4ce9b50ef959bd12074a57de1566bab0"),"stable domain-separated discovery ID");
        byte[] beacon=Wire.tailnetBeacon(key,s,"100.90.0.1",47391);
        check(Wire.hex(beacon).equals("4c434431202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3fb91f645a00014ae97f413010f955352de245664b69962a48d2318d12ce4e651997a193e40f47"),"tailnet beacon independent HMAC vector");
        check(Wire.verifyTailnetBeacon(beacon,key,"100.90.0.1")==47391,"tailnet beacon verifies listener port");
        byte[] corrupt=beacon.clone();corrupt[36]^=1;rejects(()->Wire.verifyTailnetBeacon(corrupt,key,"100.90.0.1"),"beacon endpoint tampering rejected");
        rejects(()->Wire.verifyTailnetBeacon(beacon,new byte[32],"100.90.0.1"),"unpaired beacon rejected");
        rejects(()->Wire.verifyTailnetBeacon(Arrays.copyOf(beacon,73),key,"100.90.0.1"),"short beacon rejected");
        check(Wire.tailnetIP("100.64.0.1")&&Wire.tailnetIP("100.127.255.254")&&!Wire.tailnetIP("100.63.0.1")&&!Wire.tailnetIP("100.128.0.1")&&!Wire.tailnetIP("100.80.999.1"),"tailnet source range strictly checked");
        rejects(()->Wire.verifyTailnetBeacon(beacon,key,"100.90.0.2"),"relayed beacon source rejected");
        byte[] msg=new Wire.Message(1,"hello 🌏\ntext".getBytes(StandardCharsets.UTF_8)).encode();
        Wire.Frames tx=new Wire.Frames(key),rx=new Wire.Frames(key);byte[] frame=tx.seal(msg);
        check(Arrays.equals(rx.open(frame),msg),"AES roundtrip");
        rejects(()->rx.open(frame),"replay rejected");
        byte[] changed=frame.clone();changed[changed.length-1]^=1;rejects(()->new Wire.Frames(key).open(changed),"tamper rejected");
        byte[] wrong=key.clone();wrong[0]^=1;rejects(()->new Wire.Frames(wrong).open(frame),"wrong key rejected");
        rejects(()->Wire.frameLength(new byte[]{127,-1,-1,-1}),"oversize header");
        rejects(()->Wire.frameLength(new byte[]{0,0,0,32}),"short header");
        rejects(()->Wire.Message.decode(new byte[16]),"truncated message");
        rejects(()->new Wire.Message(9,new byte[0]),"unsupported type");
        rejects(()->new Wire.Message(1,new byte[]{(byte)255}),"invalid UTF8");
        rejects(()->new Wire.Message(2,new byte[12]),"invalid PNG signature");
        rejects(()->new Wire.Message(3,new byte[]{1}),"invalid ping");
        rejects(()->new Wire.Message(1,new byte[Wire.MAX_CONTENT+1]),"content limit");
        rejects(()->Wire.unhex("not-a-secret"),"invalid key");
        check(Arrays.equals(Wire.unhex(Wire.hex(key)),key),"hex roundtrip");
        Wire.Frames orderedTx=new Wire.Frames(key);byte[] first=orderedTx.seal(msg),second=orderedTx.seal(msg);
        rejects(()->new Wire.Frames(key).open(second),"out of order");
        Wire.Frames orderedRx=new Wire.Frames(key);orderedRx.open(first);check(Arrays.equals(orderedRx.open(second),msg),"ordered sequence");
        check(!Arrays.equals(Wire.mac(key,"c2s",s,c),Wire.mac(key,"s2c",s,c)),"direction keys distinct");
        System.out.println("Java protocol tests: "+count+" passed");
    }
}
