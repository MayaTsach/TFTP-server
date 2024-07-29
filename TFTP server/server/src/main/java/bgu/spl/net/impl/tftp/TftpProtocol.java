package bgu.spl.net.impl.tftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private int connectionId;
    private ConnectionsImpl<byte[]> connections;
    private String username = null; // null indicates not logged
    private byte[] entireData = new byte[0];
    private byte[] currFileNameBytes = new byte[0];
    private boolean shouldTerminate = false;
    String serverFilesDirectoryPath = System.getProperty("user.dir") + "/Flies/";
    
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<byte[]>)connections;
    }

    @Override
    public void process(byte[] message) {
        // Creates a response, does something if needed and then executes 'send' of connections
        byte packetType = message[1]; // What kind of packet we got
        byte[] ack_0 = {0, 4, 0, 0}; //generic answer of ACK 0

        if(packetType == 1){ // RRQ
            byte[] byteFileName = Arrays.copyOfRange(message, 2, message.length);
            String stringFileName = new String(byteFileName, 0, byteFileName.length, StandardCharsets.UTF_8);
            // Checks if the file exists- if not sends an error
            if(!doesFileExist(serverFilesDirectoryPath, stringFileName)){
                connections.send(connectionId, createErrPacket((byte)1, "File not found"));
                return;
            }
            
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }

            // Import the file into byte[]
            this.entireData = createDataByteArray(stringFileName); 
            // Retrive the first block to be sent 
            byte[] firstBlock = nextDataBlock(0);
            connections.send(connectionId, blockToDataPacket(firstBlock, 0));
        }

        else if (packetType == 2){ // WRQ
            // Needs to check if the exists - if not ACK_0, if yes- error_5
            this.currFileNameBytes = Arrays.copyOfRange(message, 2, message.length);
            String stringFileName = new String(currFileNameBytes, 0, currFileNameBytes.length, StandardCharsets.UTF_8);
            // Checks if the file exists- if not sends an error
            if (doesFileExist(serverFilesDirectoryPath, stringFileName)) {
                connections.send(connectionId, createErrPacket((byte)5, "File already exists."));
                return;
            }
            
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }
            
            else { // File doesn't exist - sends ACK 0
                connections.send(this.connectionId, ack_0);
            }
        }

        else if (packetType == 3){ //DATA
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }
            byte[] b_blockSize = Arrays.copyOfRange(message, 2, 4);
            int blockSize = (int)byteToShort(b_blockSize);

            byte[] b_blockNumber = Arrays.copyOfRange(message, 4, 6);
            int blockNumber = (int)byteToShort(b_blockNumber);

            byte[] data = Arrays.copyOfRange(message, 6, message.length);

            if(blockNumber == 1) // If it's the first packet
                this.entireData = data;
            
            else
                this.entireData = mergeArrays(this.entireData, data);
            
            byte[] answer = {0, 4};
            answer = mergeArrays(answer, b_blockNumber);
            connections.send(connectionId, answer);

            if (blockSize < 512) { // File Upload completed - create file and send BCAST to all logged-in users
                createFile();
                connections.sendBcast(createBcastPacket((byte)1, this.currFileNameBytes)); 
            }
        }


        else if (packetType == 4){ // ACK
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }
            short blockNumber = byteToShort(Arrays.copyOfRange(message, 2, message.length)); // ACK packet always consists 4 bytes.
            if (blockNumber > 0) { 
                // Retrive the next block to be sent 
                byte[] nextBlock = nextDataBlock(blockNumber);
                if(nextBlock == null){ // if null - all data was sent.
                    return;
                }
                connections.send(connectionId, blockToDataPacket(nextBlock, blockNumber));
            }
            else {
                connections.send(connectionId, createErrPacket((byte) 0, "Cannot send this ACK packet."));
            }
            
        }
   

        else if (packetType == 6){ // DIRQ
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }
            
            
            // Get a list of all file names in the directory
            List<String> fileNames = getFileNames(this.serverFilesDirectoryPath);
            
            // Convert each file name to a byte array and append to the data packet
            byte[] dataPacket = DIRQDataPacket(fileNames);
            
            this.entireData = dataPacket;
            byte[] firstBlock = nextDataBlock(0);
            connections.send(connectionId, blockToDataPacket(firstBlock, 0));
        }

        else if (packetType == 7){ // LOGRQ
            byte[] byteUserName = Arrays.copyOfRange(message, 2, message.length);
            String temp_username = new String(byteUserName, 0, byteUserName.length, StandardCharsets.UTF_8);
            if(isLoggedIn(temp_username)){ // He's already logged in
                // Send an error packet
                connections.send(connectionId, createErrPacket((byte)7, "User already logged in"));
                return;
            }
            
            // Not logged in 
            username = temp_username;
            connections.logIn(connectionId, username); 
            connections.send(connectionId, ack_0);
            return;
        }

        else if (packetType == 8){ // DELRQ
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
            }

            byte[] fileNameBytes = Arrays.copyOfRange(message, 2, message.length); // File name in bytes
            String fileName = new String(fileNameBytes); // Convert byte array to string

            // Delete the file if it exists in the directory
            File file = new File(serverFilesDirectoryPath + fileName);

            if (file.exists()) {
                // Attempt to delete the file
                file.delete();
                connections.send(connectionId, ack_0); // ACK 0 - notify success to client
                // create and send BCAST to all logged in users.
                connections.sendBcast(createBcastPacket((byte)0, fileNameBytes));
            } 
            
            else { // File is not found
                // Create and send error packet.
                String ErrMsg = "File not found - RRQ or DELRQ of non-existing file.";
                byte[] errPckt = createErrPacket((byte) 1, ErrMsg);
                connections.send(connectionId, errPckt);
            }
        }

        else if (packetType == 10){ // DISC
            // Check if he's logged in
            if(!isLoggedIn(username)){
                // Send an error packet - not logged in
                connections.send(connectionId, createErrPacket((byte)6, "User not logged in"));
                return;
            }
            // He's logged in
            username = null;
            connections.send(this.connectionId, ack_0);
            connections.disconnect(connectionId);
            shouldTerminate = true;  
        } 

        else { // for type 5 (ERROR), type 9 (BCAST), or any other number
            connections.send(connectionId, createErrPacket((byte)0, "Not a valid packet type"));
        }

    }


    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    /**
     * Creates an error packet with the specified error code and error message.
     * @param errCode error code to include in the error packet. (elaborated in a table in assignment doc).
     * @param strErrMsg error message to include in the error packet.
     * @return Error packet ready to be sent to client
     */
    public byte[] createErrPacket(byte errCode, String strErrMsg) {
        byte[] errPckt = null;
        byte[] byteErrMsg = strErrMsg.getBytes();
        errPckt = new byte[4 + byteErrMsg.length + 1];
        errPckt[0] = 0;
        errPckt[1] = 5;
        errPckt[2] = 0;
        errPckt[3] = errCode;
        System.arraycopy(byteErrMsg, 0, errPckt, 4, byteErrMsg.length);
        errPckt[errPckt.length - 1] = 0;
        return errPckt;
    }
    

    /**
     * A function that turns a file into a byte array (the whole file, no matter what size)
     * @param packetNumber - the packet number
     * @param stringFileName - the file name
     */
    public byte[] createDataByteArray(String stringFileName){
        String filePath_ = this.serverFilesDirectoryPath + stringFileName;
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
        int length = Math.min(512, this.entireData.length - startIndex); // Determine the length of the destination array
        if(length <= 0){ // no more data to send.
            return null;
        }
        byte[] nextBlock = new byte[length]; // Returned array

        // Copy elements from source array to destination array
        System.arraycopy(this.entireData, startIndex, nextBlock, 0, length);

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

    /**
     * Gets all files' names from "Flies" directory to a list.
     * 
     * @param directoryPath directory path
     * @return List of files' names.
     */
    public List<String> getFileNames(String directoryPath) {
        List<String> fileNames = new ArrayList<>();

        // Create a File object for the directory
        File directory = new File(directoryPath);

        // Check if the directory exists and is a directory
        if (directory.exists() && directory.isDirectory()) {
            // Get a list of all files in the directory
            File[] files = directory.listFiles();
            if (files != null) {
                // Add the name of each file to the list
                for (File file : files) {
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }
        }
        return fileNames;
    }

    /**
     * Convert each file name to a byte array to be given to the 
     * @param fileNames file names list.
     * @return Array of bytes to append to the full data packet.
     */
    public byte[] DIRQDataPacket(List<String> fileNames) {
        List<Byte> packetList = new ArrayList<>();

        // Append each file name to the packet, separated by byte 0
        for (String fileName : fileNames) {
            byte[] fileNameBytes = fileName.getBytes();
            for (byte b : fileNameBytes) {
                packetList.add(b);
            }
            packetList.add((byte) 0); // Add the delimiter byte
        }

        // Convert the list of bytes to a byte array
        byte[] packetBytes = new byte[packetList.size()];
        for (int i = 0; i < packetList.size(); i++) {
            packetBytes[i] = packetList.get(i);
        }

        return packetBytes;
    }

    public boolean isLoggedIn(String userName){
        return connections.isLoggedIn(userName, connectionId);
    }

    /**
     * Checks if a file with the specified name exists in the specified directory.
     *
     * @param directoryPath The path to the directory where the file is to be checked.
     * @param fileName      The name of the file to be checked.
     * @return {@code true} if a file with the same name exists in the directory, {@code false} otherwise.
     *         Returns {@code false} if the directory does not exist, is not a directory, or no file with the same name exists.
     */
    public boolean doesFileExist(String directoryPath, String fileName) {
        File file = new File(directoryPath + fileName);
        // Check if the directory exists and is a directory
        return(file.exists());
    }

    /**
     * Creates a broadcast packet with the specified state and file name bytes.
     * The packet consists of a prefix, the file name bytes, and a single null byte.
     *
     * @param state          The state byte (deleted (0), added (1)) to include in the broadcast packet.
     * @param fileNameBytes  The bytes representing the file name to include in the broadcast packet.
     * @return The broadcast packet as a byte array, consisting of the packet prefix, file name bytes, and a null byte.
     */
    public byte[] createBcastPacket(byte state, byte[] fileNameBytes) {
        // Create the prefix portion of the broadcast packet
        byte[] packetPrefix = new byte[3];
        packetPrefix[0] = 0;
        packetPrefix[1] = 9;
        packetPrefix[2] = state;

        // Calculate the length of the broadcast packet
        int packetLength = packetPrefix.length + fileNameBytes.length + 1; // 1 for the null byte

        // Create the broadcast packet byte array
        byte[] bcastPacket = new byte[packetLength];

        // Copy the prefix, file name bytes, and null byte into the broadcast packet array
        System.arraycopy(packetPrefix, 0, bcastPacket, 0, packetPrefix.length); // Copy prefix
        System.arraycopy(fileNameBytes, 0, bcastPacket, packetPrefix.length, fileNameBytes.length); // Copy file name bytes
        bcastPacket[packetLength - 1] = 0; // Set last byte to null

        return bcastPacket; // Return the broadcast packet array
    }

    public void createFile() {
        try {
            // Create a File object with the directory path and file name
            String fileName = new String(this.currFileNameBytes); // Convert byte[] to String
            File file = new File(this.serverFilesDirectoryPath, fileName);

            // Create FileOutputStream to write data to the file
            FileOutputStream fos = new FileOutputStream(file);

            // Write data to the file
            fos.write(this.entireData);

            // Close the FileOutputStream
            fos.close();
        } catch (IOException e) {}
    }


}