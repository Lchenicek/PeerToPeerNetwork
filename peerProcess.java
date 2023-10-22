import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.util.*;
import resources.*;


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
    Vector<peerConnection> threads; //store all threads
    logger Log;
    bitfield myBitfield;
    //file IO
    //we should probably have a binary semaphore for writing to the file

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

    private class peerConnection extends Thread{     //threads so 1 socket doesn't block connections to other peers

        private int peerId; //is having this many variables named "id" getting confusing?
        private Socket connection;
        private ObjectInputStream in;   //read to socket
        private ObjectOutputStream out; //write to socket. cribbing from sample code here

        public peerConnection(peerInfo info){ //constructor for if this peer is connecting to another peer. we make the socket
            peerId = info.id;
            try{
                connection = new Socket(info.hostname, info.port);  //connect to peer's server/listener socket
                Log.startConnection(peerId);
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();    //not sure why we need to flush it right away? sample does. guess it's good practive
                in = new ObjectInputStream(connection.getInputStream());
                //TODO: i'm not entirely sure. umm. the handshake? I don't think that should be in our constructor though

                //Create handshake
                message handshake = new message(32, message.MessageType.handshake, Integer.toString(id));
                //Send message
                out.writeObject(handshake.getMessage());
                out.flush();
                String response = (String) in.readObject();
                System.out.println("Recieved handshake response: " + response);

                //Verify handshake
                if (!message.isValidHandshake(response, info.id)) {
                    //FIXME: what to do when handshake is invalid?
                    System.out.println("Invalid handshake!");
                }

                //Next send bitfield
                String bitfieldPayload = myBitfield.getMessagePayload();
                message bitfieldMsg = new message(bitfieldPayload.length(), message.MessageType.bitfield,  bitfieldPayload);
                out.writeObject(bitfieldMsg.getMessage());
                response = (String) in.readObject();
                System.out.println("Recieved bitfield response: " + response);

                //Process bitfield response
                ArrayList<Integer> desiredPieces = myBitfield.processBitfieldMessage(response);
                //TODO: I assume we'll have to keep track of desiredBits (it'd probably need a semaphor)

                //Send interest message
                //TODO: do something with level of interest
                if (desiredPieces.isEmpty()) {
                    message notInterestedMsg = new message(0, message.MessageType.notInterested);
                    out.writeObject(notInterestedMsg.getMessage());
                }
                else {
                    message interestedMsg = new message(0, message.MessageType.interested);
                    out.writeObject(interestedMsg.getMessage());
                }

                //Receive interest response
                String interestResponse = (String) in.readObject();
                if (interestResponse.charAt(4) == '2') {
                    System.out.println("Peer interested");
                }
                else {
                    System.out.println("Peer not interested");
                }

            } catch (ConnectException e){
                System.err.println("Connection refused. Server's not up. I think.");
                System.exit(-1); //is killing this on any error bad? I don't think so
            } catch (UnknownHostException e){
                System.err.println("Trying to connect to an unknown host");
                System.exit(-1); 
            } catch (IOException e){
                System.err.println("IOException. idk what to tell you");
                System.exit(-1); 
            } catch (Exception e){
                System.err.println("I don't even know what's up, man");
                System.exit(-1); 
            }
        }      

        public peerConnection(Socket _connection, int _id){ //constructor for this peer got connection from another peer. we got the socket from the listener
            try{
                connection = _connection;   //get socket from listener
                peerId = _id;
                Log.receiveConnection(peerId);
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();    
                in = new ObjectInputStream(connection.getInputStream());
                System.out.println("Connection received from peer " + Integer.toString(id) + " successfully!");

                //Handshake
                String handshake = (String) in.readObject();
                System.out.println("Received handshake: " + handshake);
                message handshakeResponse = new message(32, message.MessageType.handshake, Integer.toString(id));
                out.writeObject(handshakeResponse.getMessage());

                //Verify handshake
                if (!message.isValidHandshake(handshake, _id)) {
                    //FIXME: what to do when handshake is invalid?
                    System.out.println("Invalid handshake!");
                }

                //Bitfield
                String bitfieldMsg = (String) in.readObject();
                System.out.println("Received bitfield: " + bitfieldMsg);
                String bitfieldPayload = myBitfield.getMessagePayload();
                message bitfieldResponse = new message(bitfieldPayload.length(), message.MessageType.bitfield,  bitfieldPayload);
                out.writeObject(bitfieldResponse.getMessage());

                //Process bitfield response
                ArrayList<Integer> desiredPieces = myBitfield.processBitfieldMessage(bitfieldMsg);
                //TODO: I assume we'll have to keep track of desiredBits (it'd probably need a semaphor)

                //Send interest message
                //TODO: do something with level of interest
                if (desiredPieces.isEmpty()) {
                    message notInterestedMsg = new message(0, message.MessageType.notInterested);
                    out.writeObject(notInterestedMsg.getMessage());
                }
                else {
                    message interestedMsg = new message(0, message.MessageType.interested);
                    out.writeObject(interestedMsg.getMessage());
                }

                //Receive interest response
                String interestResponse = (String) in.readObject();
                if (interestResponse.charAt(4) == '2') {
                    System.out.println("Peer interested");
                }
                else {
                    System.out.println("Peer not interested");
                }

            } catch (IOException e){
                System.err.println("IO error on establishing in/out streams");
                System.exit(-1);
            } catch (ClassNotFoundException e) {
                System.err.println("I could not add the handshake line unless I added this catch ¯\\_(ツ)_/¯");
                e.printStackTrace();
            }
        }  

        public void run(){  //gets called when we do .start() on the thread
            while(true){}
        }
        //doesn't do anything right now, but without this here the process just dies as soon as it makes its last connection
    }

    public peerProcess(String _id){
        id = Integer.parseInt(_id);
        Log = new logger(id);
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

        int earlierPeers = 0;   //outside of try cause we need it for the for loop later
        try  (BufferedReader readerPeer = new BufferedReader(new FileReader("./PeerInfo.cfg"))){
            String line;
            boolean encounteredSelf = false;
            peers = new Vector<peerInfo>();

            /* my rationale for this is it's easier to read the whole file and then determine what to do from there,
             * rather than reading in one line, connecting, reading in the next, connecting, and so on.
             * This way our port can know it's own information before connection begins, and reading the file
             * won't be gummed up waiting for connections. Also, we know how many peers to expect */

            while((line = readerPeer.readLine()) != null){
                String[] parsedLine = line.split(" ");
                int peerId = Integer.parseInt(parsedLine[0]);    //first member of peerCfg is the peer id
                //If we found ourselves, initialize and set up our values
                if(peerId == id){
                    encounteredSelf = true;
                    port = Integer.parseInt(parsedLine[2]);
                    if(Integer.parseInt(parsedLine[3]) == 1) hasFile = true;
                    else hasFile = false;
                    //Set up bitfield, if hasFile, then all values are 1.
                    myBitfield = new bitfield(fileSize, pieceSize, hasFile);
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

        threads = new Vector<peerConnection>();

        for(int i = 0; i < earlierPeers; i++){
            peerConnection peerConn = new peerConnection(peers.elementAt(i));     //make the thread to connect to the earlier peers
            peerConn.start();
            threads.add(peerConn);
        }

        try{
            ServerSocket listener = new ServerSocket(port);
            for(int i = 0; i < peers.size() - earlierPeers; i++){   //we're awaiting connections from total peers - earlier peers others
                peerConnection peerConn = new peerConnection(listener.accept(), peers.elementAt(i + earlierPeers).id);   //this assumes peers connect in order, they might not. fix?
                peerConn.start();
                threads.add(peerConn);
            }
            listener.close();   //don't need any more server connections

        } catch (IOException e){
            System.err.println("Could not start listener");
            System.exit(-1);
        }   //it's kind of nice java doesn't let you leave exceptions unhandled but this is getting annoying

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

