package resources;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Semaphore;

public class logger {
    String id;
    BufferedWriter logFile;

    public logger(int _id){
        id = Integer.toString(_id);
        String filepath = "./log_peer_" + id + ".log";  //while this file is a directory lower, it's called from 1 up so go in PWD
        try{
            logFile = new BufferedWriter(new FileWriter(filepath));
        }catch (IOException e){
            System.err.println("Could not open log file");
        }
    }

    public void startConnection(int peer){
        String logEntry = startEntry() + "makes a connection to Peer " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    } 
    //log entry if we start the connection to another peer

    public void receiveConnection(int peer){
        String logEntry = startEntry() + "is connected from Peer " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    }
    //log entry if we got connection from another peer

    public void changeNeighbors(Vector<Integer> peers){
        String logEntry = startEntry() + "has the preferred neighbors ";
        for(int p : peers){
            logEntry += Integer.toString(p) + ", ";
        }
        logEntry = logEntry.substring(0, logEntry.length() - 2);
        writeEntry(logEntry);
    }

    public void optimisticUnchoke(int peer){
        String logEntry = startEntry() + "has the optimistically unchoked neighbor " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    }

    public void isUnchoked(int peer){
        String logEntry = startEntry() + "is unchoked by " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);    
    }

    public void isChoked(int peer){
        String logEntry = startEntry() + "is choked by " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);        
    }

    public void receiveHaveMessage(int peer, int index){
        String logEntry = startEntry() + "received the \'have\' message from " + Integer.toString(peer) + " for the piece " + Integer.toString(index) + ".\n";
        writeEntry(logEntry);        
    }

    public void receiveInterestedMessage(int peer){
        String logEntry = startEntry() + "received the \'interested\' message from " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);        
    }

    public void receiveNotInterestedMessage(int peer){
        String logEntry = startEntry() + "received the \'not interested\' message from " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);        
    }

    public void downloadPiece(int peer, int index, int pieceCount){
        String logEntry = startEntry() + "has downloaded the piece " + Integer.toString(index) + " from " + Integer.toString(peer) + 
        ". Now the number of pieces it has is " + Integer.toString(pieceCount) + ".\n";
        writeEntry(logEntry);        
    }

    public void completeDownload(){
        String logEntry = startEntry() + "has downloaded the complete file.\n";
        writeEntry(logEntry);
    }

    public void recaclculatingDownloadSpeeds(ArrayList<Integer> neighborIds){
        String neighborList = "";
        for (int neighbor : neighborIds) {
            neighborList = neighborList + neighbor + ", ";
        }
        if (neighborList.length() > 1) {
            neighborList = neighborList.substring(0, neighborList.length() - 2);
            String logEntry = startEntry() + "has as the preferred neighbors " + neighborList + ".\n";
            writeEntry(logEntry);
        }
        else {
            String logEntry = startEntry() + "has no preferred neighbors.\n";
            writeEntry(logEntry);
        }
    }

    public void choked(int peer) {
        String logEntry = startEntry() + "is choked by " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    }

    public void unchoked(int peer) {
        String logEntry = startEntry() + "is unchoked by " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    }


    private String startEntry(){
        LocalDateTime now = LocalDateTime.now();    //get the current time
        String formattedDateTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);   //convert it to a string formatted in iso standard
        return "[" + formattedDateTime + "]: " + "Peer " + id + " ";      //make it a little prettier
    }
    //static so we can just grab this whenever we need it, makes individual log functions cleaner

    private void writeEntry(String entry){   
        try{
            logFile.write(entry);
            logFile.flush();        //won't actually write until we flush the buffer
            //we could close the file, but we don't want to because it'll be open and logging for a while
        } catch(IOException e){
            System.err.println("Issue logging");
        } 
    }
    //this is it's own function because if not I would have to handle the exception every time
    //and like absolutely not holy shit

    //TODO: destruct logger and close file
    //TODO: rest of logger methods
    //TODO(?):log wipes every run. should it be consistent?
}
