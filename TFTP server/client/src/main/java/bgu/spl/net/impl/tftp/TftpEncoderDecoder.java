package bgu.spl.net.impl.tftp;

import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int pos = 0;
    private byte packetType = 0; 
    private byte [] b_packet_size = new byte [2];
    private short short_packet_size = 512;


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (pos == 1)
            packetType = nextByte;

        switch (packetType) {
            case 1:
            case 2:
            case 7:
            case 8:
                if (nextByte == 0){
                    return popBytes();
                }
                break;

            case 5:
            if(pos == 2 || pos == 3)
                break;
            else if (nextByte == 0)
                return popBytes();
            break;

            case 9:
                if(pos == 2)
                    break;
                else if (nextByte == 0)
                    return popBytes();
                break;

            case 6:
            case 10:
                pushByte(nextByte);
                return popBytes();
                

            case 3:
                if(pos == 2)
                    b_packet_size[0] = nextByte;
                else if (pos == 3){
                    b_packet_size[1] = nextByte;
                    short_packet_size = ( short ) ((( short ) b_packet_size[0]) << 8 | ( short ) ( b_packet_size[1]) & 0xff );
                }
                else if(pos == 5 + short_packet_size) {                    
                    pushByte(nextByte);
                    return popBytes();
                }
                break;
            
            case 4:
                if(pos == 3){ //always 4 bytes
                    pushByte(nextByte);
                    return popBytes();
                }
                break;
        }
        
        pushByte(nextByte);
        return null; //not the end yet
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void pushByte(byte nextByte) {
        if (pos >= bytes.length) {
            bytes = Arrays.copyOf(bytes, pos * 2);
        }

        bytes[pos++] = nextByte;
    }

    private byte[] popBytes() {
        byte[] result = Arrays.copyOf(bytes, pos);
        pos = 0;
        packetType = 0;
        return result;
    }
}