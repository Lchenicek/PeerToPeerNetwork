package resources;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

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
        String logEntry = getTime() + "Peer " +  id + " makes a connection to Peer " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    } 
    //log entry if we start the connection to another peer

    public void receiveConnection(int peer){
        String logEntry = getTime() + "Peer " +  id + " is connected from Peer " + Integer.toString(peer) + ".\n";
        writeEntry(logEntry);
    }
    //log entry if we got connection from another peer

    private static String getTime(){
        LocalDateTime now = LocalDateTime.now();    //get the current time
        String formattedDateTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);   //convert it to a string formatted in iso standard
        return "[" + formattedDateTime + "]: ";      //make it a little prettier
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
