package bgu.spl.net.impl.tftp;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import bgu.spl.net.api.MessagingProtocol;

public class TftpProtocol implements MessagingProtocol<byte[]>{

    protected volatile static boolean shouldTerminate = false;
    private static byte[] entireData = new byte[0];
    private static String currFileName = null;
    private static String clientFilesDirectoryPath = System.getProperty("user.dir");
    private static int sentPacketType;
    private static volatile boolean isKeyboardLocked = false;
    protected static Object keyboardLock = new Object();


    @Override
    public byte[] process(byte[] message) {
        byte packetType = message[1]; // What kind of packet we got
        
        //Keyboard thread scenarios:

        if(packetType == 1){ //RRQ
            byte[] b_fileName = Arrays.copyOfRange(message, 2, message.length);
            String fileName = new String(b_fileName, StandardCharsets.UTF_8);
            Path filePath = Paths.get(clientFilesDirectoryPath, fileName);
            if(Files.exists(filePath)){ // The file exists already
                System.out.println("file already exists");
                return null;
            }

            sentPacketType = 1;
            currFileName = fileName;
            try {// Create the file (doesn't exists)
                Files.createFile(filePath);
            } catch (IOException e) {}
            
            isKeyboardLocked = true; // So it will wait for response
            return addZero(message);
        }

        if(packetType == 2){ //WRQ
            byte[] b_fileName = Arrays.copyOfRange(message, 2, message.length);
            String fileName = new String(b_fileName, StandardCharsets.UTF_8);
            Path filePath = Paths.get(System.getProperty("user.dir"), fileName);
            if(!Files.exists(filePath)){ // The file doesn't exists 
                System.out.println("file does not exists");
                return null;
            }

            sentPacketType = 2;
            currFileName = fileName;
            isKeyboardLocked = true; // So it will wait for response
            return addZero(message);
        }

        if(packetType == 6){  //DIRQ
            sentPacketType = 6;
            currFileName = "DIRQ";
            isKeyboardLocked = true; // So it will wait for response
            return message;
        }

        if(packetType == 7){ //LOGRQ
            sentPacketType = 7;
            isKeyboardLocked = true; // So it will wait for response
            return addZero(message);
        }

        if(packetType == 8){ //DELRQ
            sentPacketType = 8;
            isKeyboardLocked = true; // So it will wait for response
            return addZero(message);
        }

        if(packetType == 10){ //DISC
            sentPacketType = 10;
            isKeyboardLocked = true; // So it will wait for response
            return message;
        }



        //Listening thread scenarios:

        if(packetType == 3){ //DATA
            //When received a DATA packet save the data to a file or a buffer depending if we are in RRQ Command or DIRQ
            //Command and send an ACK packet in return with the corresponding block number written in the DATA packet.
            
            byte[] b_blockSize = Arrays.copyOfRange(message, 2, 4);
            int blockSize = (int)byteToShort(b_blockSize);

            byte[] b_blockNumber = Arrays.copyOfRange(message, 4, 6);
            int blockNumber = (int)byteToShort(b_blockNumber);

            byte[] data = Arrays.copyOfRange(message, 6, message.length);

            if(blockNumber == 1) // If it's the first packet
                entireData = data;
            
            else
                entireData = mergeArrays(entireData, data);
            
            byte[] answer = {0, 4};
            answer = mergeArrays(answer, b_blockNumber);
            
            if (blockSize < 512) { // File Upload completed - create file
                createFile();    
                if(!currFileName.equals("DIRQ")){
                    System.out.println("RRQ " + currFileName + " complete");
                }
                else printDIRQ();  
                isKeyboardLocked = false;
            }
            return answer;
        }

        if(packetType == 4){ //ACK
            // Print to the terminal the following:
            // ACK <block number>
            byte[] b_ACK_number = Arrays.copyOfRange(message, 2, 4);
            int ACK_number = (int)byteToShort(b_ACK_number);
            System.out.println("ACK " + ACK_number);

            if(ACK_number == 0){ // Got ACK_0
                if(sentPacketType == 7 || sentPacketType == 8){ //LOGRQ or DELRQ finished succesfully
                    isKeyboardLocked = false;
                    return null;
                }

                else if(sentPacketType == 10){ //DISC
                    shouldTerminate = true; // The client can disconnect now
                    return null;
                }
    
                else if(sentPacketType == 2){ // WRQ -the client wanted to write a file to the server, so send the first block
                    // Import the file into byte[]
                    entireData = createDataByteArray(currFileName); 
                    // Retrive the first block to be sent 
                    byte[] firstBlock = nextDataBlock(0);
                    return blockToDataPacket(firstBlock, 0);
                }
                System.out.println("got to an illegal option");

            }
            
            // The ACK was because of a data packet that the client sent 
            // Send next data
            else{ 
                // Retrive the next block to be sent 
                byte[] nextBlock = nextDataBlock(ACK_number);
                if(nextBlock == null){ // If null - all data was sent.
                    isKeyboardLocked = false;
                    System.out.println("WRQ " + currFileName + " complete");
                    return null; // Will not send anything
                }
                return blockToDataPacket(nextBlock, ACK_number);
            }
        }

        if(packetType == 5){ //ERROR
            // Print to the terminal the following:
            // Error <Error number> <Error Message if exist>
            byte[] b_errorNum = Arrays.copyOfRange(message, 2, 4);
            int errorNum = (int)byteToShort(b_errorNum);
            
            byte[] b_errorTxt = Arrays.copyOfRange(message, 2, message.length);
            String errorTxt = "";
            errorTxt = new String(b_errorTxt, StandardCharsets.UTF_8);
            System.out.println("ERROR " + errorNum +" "+ errorTxt);
            if(sentPacketType == 1 && errorNum == 1){
                // Delete the file if it exists in the directory
                File file = new File(clientFilesDirectoryPath +"/"+ currFileName);
                if (file.exists()) {
                    // Attempt to delete the file
                    file.delete();
                } 
            }

            if(sentPacketType == 10 && errorNum == 6){ // The client wanted to disconnect but wasn't logged in
                shouldTerminate = true;
            }
            
            isKeyboardLocked = false;
            return null;
        }

        if(packetType == 9){ //BCAST
            //Print to the terminal the following:
            //BCAST <del/add> <file name>
            byte b_whatHappened = message[2];
            byte[] b_fileName = Arrays.copyOfRange(message, 3, message.length);
            String bcastFileName = new String(b_fileName, StandardCharsets.UTF_8);
            if(b_whatHappened == 0) // File deleted
                System.out.println("BCAST del " + bcastFileName);
            if(b_whatHappened == 1) // File added
                System.out.println("BCAST add " + bcastFileName);
            
            return null;
        }
        
        System.out.println("got to an illegal point");
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }


    public byte[] shortToByte(short s){
        byte[] s_bytes = new byte[]{(byte)(s >> 8) , (byte)(s & 0xff)};
        return s_bytes;
    }

    public short byteToShort(byte[] b){
        short b_short = (short) (((short) b[0]) << 8 | (short) (b[1]) & 0xff);
        return b_short;
    }


    /**
     * Merges two byte arrays into a single byte array.
     *
     * @param array1 The first byte array to be merged.
     * @param array2 The second byte array to be merged.
     * @return A new byte array containing the elements of {@code array1} followed by the elements of {@code array2}.
     */
    public byte[] mergeArrays(byte[] array1, byte[] array2) {
        byte[] mergedArray = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, mergedArray, 0, array1.length);
        System.arraycopy(array2, 0, mergedArray, array1.length, array2.length);
        return mergedArray;
    }


    public void createFile() {
             Path filePath = Paths.get(clientFilesDirectoryPath, currFileName);
             try (FileOutputStream fos = new FileOutputStream(filePath.toString())) {
                fos.write(entireData);
            } catch (IOException e) {}
    }


    public static void printDIRQ() {
        String str = new String(entireData);

        // Split the file names
        String[] strings = str.split("\0");

        // Print each file name
        for (String s : strings) {
            System.out.println(s);
        }
    }


    /**
     * Retrieves the next data block from the entire data array based on the specified block number.
     * If the block number is n, it retrieves the nth block of data from the entire data array.
     * Each block contains up to 512 bytes of data.
     * @param blockNumber The block number indicating which block of data to retrieve.
     *
     * @return The next data block as a byte array. If the block number is invalid or exceeds the length of the data array,
     *         an empty byte array is returned.
     */
    public byte[] nextDataBlock(int blockNumber) {
        int startIndex = blockNumber * 512;
        int length = Math.min(512, entireData.length - startIndex); // Determine the length of the destination array
        if(length <= 0){ // no more data to send.
            return null;
        }
        byte[] nextBlock = new byte[length]; // Returned array

        // Copy elements from source array to destination array
        System.arraycopy(entireData, startIndex, nextBlock, 0, length);

        return nextBlock; // Return the destination array

    }


    /**
     * A function that gets the data to be sent as a data packet and adds to it the first 6 bytes of information.
     * Note: block numerating starts in 1.
     * @param data - byte array of the data
     * @param blockNumber - the number of block (got from ACK)
     * @return An array of bytes of the entire file.
     */
    public byte[] blockToDataPacket(byte[] data, int blockNumber){
        byte[] first6Bytes = new byte[6];
        first6Bytes[0] = 0;
        first6Bytes[1] = 3;
        
        //insert the data size
        short size = (short)data.length;
        byte[] size_bytes = shortToByte(size);
        first6Bytes[2] = size_bytes[0];
        first6Bytes[3] = size_bytes[1];
        
        //insert the block number
        byte[] byte_blockNumber = shortToByte((short)(blockNumber + 1));  // when sending from ack, block number must be [currAck + 1].
        first6Bytes[4] = byte_blockNumber[0];
        first6Bytes[5] = byte_blockNumber[1];

        //merge the arrays into the packet
        byte[] Packet = mergeArrays(first6Bytes, data);
       
        return Packet;
    }


    /**
     * A function that turns a file into a byte array (the whole file, no matter what size)
     * @param packetNumber - the packet number
     * @param stringFileName - the file name
     */
    public byte[] createDataByteArray(String stringFileName){
        String filePath_ = clientFilesDirectoryPath + "/" + stringFileName;
        byte[] fileData = null;
        try (FileInputStream fis = new FileInputStream(filePath_);
            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024]; 
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                // Write the bytes read from the file to the ByteArrayOutputStream
                baos.write(buffer, 0, bytesRead);
            }

            // Get the byte array containing all the file data
            fileData = baos.toByteArray();
            
        } catch (IOException e) {}
        return fileData;
    }

    public boolean shouldKeyboardBeLocked(){
        return isKeyboardLocked;
    }
    

    public byte[] addZero(byte[] almostPacket){
        return mergeArrays(almostPacket, new byte[]{0});
    }

}
