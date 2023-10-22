import java.net.*;
import java.io.*;
import java.nio.*;
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
    boolean hasFile;
    int port;
    Vector<peerInfo> peers; //just for construction. not the actual sockets or anything
    //bitfield? we need one i just don't know what to do lmao
    //socket stuff

    private class peerInfo{
        public int id;
        public String hostname;
        public int port;

        public peerInfo(String _id, String _hostname, String _port){
            id = Integer.parseInt(_id);
            hostname = _hostname;
            port = Integer.parseInt(_port);
        }
    }       
    //simple little container for storing info until we make the connection. 
    //might be a bad way of going it, feel free to change
    //could be bad java but i'm bootlegging a struct

    private static class peerConnection extends Thread{
        private int id; //is having this many variables named "id" getting confusing?
        private Socket connection;
        private ObjectInputStream in;   //read to socket
        private ObjectOutputStream out; //write to socket. cribbing from sample code here

        public peerConnection(peerInfo info){
            id = info.id;
            try{
                connection = new Socket(info.hostname, info.port);
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();    //not sure why we need to flush it right away? sample does. guess it's good practive
                in = new ObjectInputStream(connection.getInputStream());
                System.out.println("Connected to peer " + Integer.toString(id) + " successfully!");
                //TODO: i'm not entirely sure. umm. the handshake? I don't think that should be in our constructor though

            } catch (ConnectException e){
                System.err.println("Connection refused. Server's not up. I think.");
                System.exit(-1); //is killing this on any error bad? I don't think so
            } catch (UnknownHostException e){
                System.err.println("Trying to connect to an unknown host");
                System.exit(-1); 
            } catch (Exception e){
                System.err.println("I don't even know what's up, man");
                System.exit(-1); 
            }
        }      //constructor for if this peer is connecting to another peer. we make the socket

        public peerConnection(Socket _connection, int _id){
            try{
                connection = _connection;
                id = _id;
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();    //not sure why we need to flush it right away? sample does. guess it's good practive
                in = new ObjectInputStream(connection.getInputStream());
                System.out.println("Connection received from peer " + Integer.toString(id) + " successfully!");
            } catch (IOException e){
                System.err.println("IO error on establising in/out streams");
                System.exit(-1);
            }
        }  //constructor for this peer got connection from another peer. we got the socket from the listener

    }

    public peerProcess(String _id){
        id = Integer.parseInt(_id);
        try (BufferedReader readerCfg = new BufferedReader(new FileReader("./Common.cfg"))){
            String line;
            while((line = readerCfg.readLine()) != null){
                String[] parsedLine = line.split(" ");
                String givenParameter = parsedLine[0];  
                String value = parsedLine[1];     
              
                if(givenParameter.equals("NumberOfPreferredNeighbors")) numberOfPreferredNeighbors = Integer.parseInt(value);
                else if(givenParameter.equals("UnchokingInterval")) unchokingInterval = Integer.parseInt(value);
                else if(givenParameter.equals("OptimisticUnchokingInterval")) optimisticUnchokingInterval = Integer.parseInt(value);
                else if(givenParameter.equals("FileName")) filename = value;
                else if(givenParameter.equals("FileSize")) fileSize = Integer.parseInt(value);
                else if(givenParameter.equals("PieceSize")) pieceSize = Integer.parseInt(value);
                /* i think the config files are always in the same order, but doing it like this ensures it doesn't matter if they are
                 * also just manually reading in every line and assinging them like that would make me feel like a caveman
                 * this might take longer than just manually doing it
                 * feel free to change it */

                else{
                    System.err.println("Invalid config file line " + givenParameter + " present in file");
                    System.exit(-1);
                }
                pieceCount = (int) Math.ceil((double) fileSize/(double) pieceSize);     //this many conversions is gross but i want to ensure it works
                //ceil because we can't have half a piece or whatever, one piece will just have some empty space   
            }
            readerCfg.close();
        } catch(Exception e){
            System.err.println("Config file Common.cfg not found");
            System.exit(-1);    //i think you go negative with an error. that's os knowledge. it might also be the direct opposite. oops
            //if we can't find the config file just kill it, since it isn't going to work
        }

        try  (BufferedReader readerPeer = new BufferedReader(new FileReader("./PeerInfo.cfg"))){
            String line;
            boolean encounteredSelf = false;
            int earlierPeers = 0;
            peers = new Vector<peerInfo>();
            /* my rationale for this is it's easier to read the whole file and then determine what to do from there,
             * rather than reading in one line, connecting, reading in the next, connecting, and so on.
             * This way our port can know it's own information before connection begins, and reading the file
             * won't be gummed up waiting for connections. Also, we know how many peers to expect */
            while((line = readerPeer.readLine()) != null){
                String[] parsedLine = line.split(" ");
                int peerId = Integer.parseInt(parsedLine[0]);    //first member of peerCfg is the peer id
                if(peerId == id){
                    encounteredSelf = true;
                    port = Integer.parseInt(parsedLine[2]);
                    if(Integer.parseInt(parsedLine[3]) == 1) hasFile = true;
                    else hasFile = false;
                }
                //get our port number from the file, if we have the file
                //we don't care about our hostname, we're running on this machine

                else{   //not us, learning about a peer
                    if(!encounteredSelf) earlierPeers++;    //counts how many we need to manually connect to, rather than wait for
                    peers.add(new peerInfo(parsedLine[0], parsedLine[1], parsedLine[2]));
                    //we might not actually need to remember this info for later peers, since they'll come to us. 
                    //it does make it easy to know how many to expect, so I think it's valid to keep it like this
                }
            }
            readerPeer.close();
        } catch(Exception e){
            System.err.println("Config file PeerInfo.cfg not found");
            System.exit(-1);
        }
        //TODO: read other config file to determine peers, if this has the file


    }
    public static void main(String[] args){
        if (args.length != 1){
            System.err.println("You must specify an id and nothing more");
            return;
        }
        peerProcess Peer = new peerProcess(args[0]);    //I think this is how to construct in java it has been a moment
        //TODO: the actual server stuff at the moment
        //will need to use threads (barf)
    }
}

