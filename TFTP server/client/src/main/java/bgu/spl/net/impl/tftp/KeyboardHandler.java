package bgu.spl.net.impl.tftp;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class KeyboardHandler<T> implements Runnable {

    private final TftpProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private BufferedOutputStream out;

    public KeyboardHandler(BufferedOutputStream out, TftpEncoderDecoder reader, TftpProtocol protocol) {
        this.out = out;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {

        try{ 
            while (!TftpProtocol.shouldTerminate && !Thread.currentThread().isInterrupted()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String userInputString;
                while(!TftpProtocol.shouldTerminate && !Thread.currentThread().isInterrupted()){
                    while(protocol.shouldKeyboardBeLocked() == true){
                        synchronized(TftpProtocol.keyboardLock){
                            try{
                                TftpProtocol.keyboardLock.wait();
                            }catch(InterruptedException e){Thread.currentThread().interrupt();}
                        }
                    }
                    userInputString = reader.readLine();
                    byte[] userInputInPacket = handleRequest(userInputString);
                    if (userInputInPacket != null){ // Illegal input by user.
                        for (int i = 0; i < userInputInPacket.length; i++){
                            byte[] nextMessage = encdec.decodeNextByte((byte) userInputInPacket[i]);
                            if (nextMessage != null) {
                                byte[] msg = protocol.process(nextMessage);
                                try {
                                    if (msg != null) {
                                        out.write(msg);
                                        out.flush();
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                } 
                            }
                        }
                    }
                }  
            }
        }catch (IOException e){}
        System.out.println("Closing keyboard thread");
    }
    public TftpProtocol getProtocol(){
        return protocol;
    }

    /**
     * Handles the incoming request and generates the appropriate packet based on the request type.
     *
     * @param nextRequest The incoming request string.
     * @return The generated packet as a byte array.
     */
    public byte[] handleRequest(String nextRequest) {
        if(nextRequest == null || nextRequest.isEmpty()){
            return null;
        }
        byte[] packet = null;
        String opcodeString = "";
        for (char c : nextRequest.toCharArray()) {
            if (c != ' ') {
                opcodeString = opcodeString + c; // Stop reading if space or newline is encountered
            }
            else break;
        }
        // opcode cases:
        if (opcodeString.equals("RRQ")){
            byte[] prefix = new byte[]{0, 1};
            packet = createNamePacket(nextRequest, prefix, opcodeString.length());
        }
        else if (opcodeString.equals("WRQ")) {
            byte[] prefix = new byte[]{0, 2};
            packet = createNamePacket(nextRequest, prefix, opcodeString.length());
        }
        else if (opcodeString.equals("DIRQ")) {
            packet = new byte[]{0, 6};
        }
        else if (opcodeString.equals("LOGRQ")) {
            byte[] prefix = new byte[]{0, 7};
            packet = createNamePacket(nextRequest, prefix, opcodeString.length());
        }
        else if (opcodeString.equals("DELRQ")) {
            byte[] prefix = new byte[]{0, 8};
            packet = createNamePacket(nextRequest, prefix, opcodeString.length());
        }
        else if (opcodeString.equals("DISC")) {
            packet = new byte[]{0, 10};
        }
        else {
            System.out.println("Illegal input.");
        }
        return packet;
    }

    /**
     * Creates a packet including a name in it (file, username), with the provided prefix, file name, and delimiter.
     *
     * @param nextRequest        The incoming request string.
     * @param prefix             The prefix bytes of the packet.
     * @param opcodeStringLength The length of the opcode string.
     * @return The generated packet as a byte array.
     */
    public byte[] createNamePacket(String nextRequest, byte[] prefix, int opcodeStringLength) {
        byte[] packet;
        
        // Extracting file name
        String fileName = "";
        int position = opcodeStringLength + 1; // opcode + ' ' + ...
        while (position < nextRequest.length()) {
            fileName = fileName + nextRequest.charAt(position);
            position++;
        }
        byte[] fileNameBytes = fileName.getBytes();
        
        // Create packet
        packet = new byte[prefix.length + fileNameBytes.length + 1];
        System.arraycopy(prefix, 0, packet, 0, 2);
        System.arraycopy(fileNameBytes, 0, packet, 2, fileNameBytes.length);
        packet[packet.length - 1] = (byte) 0;

        return packet;
    }
    
}
