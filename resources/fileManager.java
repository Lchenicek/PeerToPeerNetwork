package resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class fileManager {
    File file;
    String path;
    int pieceSize;
    int fileSize;
    logger Log;
    public byte[] bytes;

    public fileManager(String id, String fileName, int fileSize, int pieceSize, boolean hasFile) {
        path = id + "/" + fileName;

        this.pieceSize = pieceSize;
        this.fileSize = fileSize;
        this.Log = Log;
        bytes = new byte[fileSize];
        file = new File(path);
        if(file.exists() && !hasFile) {
            //If we shouldn't have the file but do
            file.delete();
            file = new File(path);
        }
        else if(!file.exists() && hasFile) {
            //If we should have the file, but don't
            System.err.println("Peer #" + id + " should have file named \"" + fileName + "\" but doesn't");
        }

        //Is purposefully not else if!
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                System.err.println("I don't even know what's up, man (file style)");
            }
        }
        else {
            try {
                FileInputStream in = new FileInputStream(file);
                in.read(bytes);
                in.close();
            } catch (Exception e) {
                System.err.println("I don't even know what's up, man (file style2)");
            }

        }
    }

    public byte[] readData(int startIndex, int piecesToRead) {
        //We multiply by pieceSize because we will only read/write by piece
        int startPos = startIndex * pieceSize;
        int endPos =  startPos + (piecesToRead * pieceSize);
        if (endPos > fileSize) {
            endPos = fileSize;
        }
        return Arrays.copyOfRange(bytes, startPos, endPos);
    }

    public void writeData(int pieceIndex, byte[] bytesToRead) {
        //We multiply by pieceSize because we will only read/write by piece
        int indexOffset = pieceIndex * pieceSize;
        for (int i = 0; i < bytesToRead.length; ++i) {
            bytes[i + indexOffset] = bytesToRead[i];
        }
    }

    public void writeToFile() {
        File file = new File(path);
        try {
            file.createNewFile();
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.flush();
            out.close();
        } catch (Exception e) {
            System.err.println("Error in writeToFile");
        }
    }

    public static void main(String[] args){
        fileManager readFromFile = new fileManager("1001", "thefile", 2167705, 16384, true);
        fileManager writeToFile = new fileManager("1002", "thefile", 2167705, 16384, false);
        int pieceCount = (int) Math.ceil((double) 2167705/(double) 16384);
        for (int i = 0; i < pieceCount; ++i) {
            byte[] temp = readFromFile.readData(i, 1);
            writeToFile.writeData(i, temp);
        }
        writeToFile.writeToFile();
    }
}
