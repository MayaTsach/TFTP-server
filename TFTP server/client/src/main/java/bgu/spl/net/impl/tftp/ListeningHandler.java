package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class ListeningHandler<T> implements Runnable{

    private final MessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private BufferedOutputStream out;
    private BufferedInputStream in;

    public ListeningHandler(BufferedOutputStream out, BufferedInputStream in, MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol) {
        this.out = out;
        this.in = in;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try{
            int read;
            while (!TftpProtocol.shouldTerminate && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    T msg = protocol.process(nextMessage);
                    try {
                        if (msg != null) {
                            out.write(encdec.encode(msg));
                            out.flush();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } 
                }
                synchronized(TftpProtocol.keyboardLock){
                    TftpProtocol.keyboardLock.notifyAll();
                }
            } 
        }catch (IOException e) {}
        System.out.println("Closing listening thread");
    }


    public MessagingProtocol<T> getProtocol(){
        return protocol;
    }


}
