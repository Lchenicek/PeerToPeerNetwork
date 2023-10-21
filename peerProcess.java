import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;  //you'd think the above would handle this, but i'm getting an error without it
import java.nio.channels.*;
import java.nio.file.Files;
import java.util.*;

public class peerProcess{

    int id;
    int numberOfPreferredNeighbors; //I'm not sure if this should include the optimistic or not
    int unchokingInterval;  //seconds
    int optimisticUnchokingInterval;    //seconds
    String filename;
    int fileSize;   //bytes
    int pieceSize;  //bytes
    int pieceCount;     //we could infer this from the above two but I think it's cleaner to do it like this
    
    public peerProcess(String _id){
        id = Integer.parseInt(_id);
        try (BufferedReader reader = new BufferedReader(new FileReader("./Common.cfg"))){
            String line;
            while((line = reader.readLine()) != null){
                int beginningOfInteger = line.indexOf(" ");
                String givenParameter = line.substring(0, beginningOfInteger);  //get the line up to the space (parameter)
                String value = line.substring(beginningOfInteger + 1);              //get the line after the space (value)
                if(givenParameter.equals("NumberOfPreferredNeighbors")) numberOfPreferredNeighbors = Integer.parseInt(value);
                else if(givenParameter.equals("UnchokingInterval")) unchokingInterval = Integer.parseInt(value);
                else if(givenParameter.equals("OptimisticUnchokingInterval")) optimisticUnchokingInterval = Integer.parseInt(value);
                else if(givenParameter.equals("FileName")) filename = value;
                else if(givenParameter.equals("FileSize")) fileSize = Integer.parseInt(value);
                else if(givenParameter.equals("PieceSize")) pieceSize = Integer.parseInt(value);
                //i think the config files are always in the same order, but doing it like this ensures it doesn't matter if they are
                //also just manually reading in every line and assinging them like that would make me feel like a caveman
                else{
                    System.out.println("Invalid config file line " + givenParameter + " present in file");
                    System.exit(-1);
                }
                pieceCount = (int) Math.ceil((double) fileSize/(double) pieceSize);     //this many conversions is gross but i want to ensure it works
                //ceil because we can't have half a piece or whatever, one piece will just have some empty space

                //TODO: read other config file to determine peers, if this has the file
            }
        } catch(Exception e){
            System.out.println("Config file Common.cfg not found");
            System.exit(-1);
            //if we can't find the config file just kill it, since it isn't going to work
        }

    }
    public static void main(String[] args){
        if (args.length != 1){
            System.out.println("You must specify an id and nothing more");
            return;
        }
        peerProcess Peer = new peerProcess(args[0]);    //I think this is how to construct in java it has been a moment
        //TODO: the actual server stuff at the moment
    }
}

