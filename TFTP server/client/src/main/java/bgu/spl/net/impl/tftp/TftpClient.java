package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;



public class TftpClient {

    public static void main(String[] args){
        if(args.length == 0){
            args = new String[] {"127.0.0.1"};
        }
        try (Socket sock = new Socket(args[0], 7777);
                BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());) {
            System.out.println("Connected to server");
            Thread keyboardThread;
            Thread ListeningThread;
            ListeningHandler<byte[]> L_handler = new ListeningHandler<>(out, in, new TftpEncoderDecoder(), new TftpProtocol());
            KeyboardHandler<byte[]> K_handler = new KeyboardHandler<>(out, new TftpEncoderDecoder(), new TftpProtocol());
            
            keyboardThread = new Thread(K_handler);
            ListeningThread = new Thread(L_handler);
            
            keyboardThread.start();
            ListeningThread.start();

            try {
                ListeningThread.join();
                keyboardThread.interrupt();
                keyboardThread.join();
            } catch (InterruptedException e) {}
            
        }catch(IOException ex){}  
    }
}
