import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Semaphore;

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
    fileManager myFileManager;
    //we should probably have a binary semaphore for writing to the file

    //Semaphors cuz threading
    ArrayList<Integer> peersInterested = new ArrayList<>(); //Peers interested in our data
    Semaphore semPeersInterested = new Semaphore(1); //Semaphor for above data

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
        private static Map<Integer, Boolean> handshakeSuccessStatus = new HashMap<Integer, Boolean>();; // Records whether a handshake has been successfully sent/received between connected peers.

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
                    //Add interested peer to our list (mostly for peers with completed sets)
                    semPeersInterested.acquire();
                    peersInterested.add(info.id);
                    semPeersInterested.release();

                    System.out.println("Peer interested");
                    Log.receiveInterestedMessage(peerId);
                }
                else {
                    System.out.println("Peer not interested");
                    Log.receiveNotInterestedMessage(peerId);
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
                    //Add interested peer to our list (mostly for peers with completed sets)
                    semPeersInterested.acquire();
                    peersInterested.add(_id);
                    semPeersInterested.release();

                    System.out.println("Peer interested");
                    Log.receiveInterestedMessage(peerId);
                }
                else {
                    System.out.println("Peer not interested");
                    Log.receiveNotInterestedMessage(peerId);
                }

            } catch (IOException e){
                System.err.println("IO error on establishing in/out streams");
                System.exit(-1);
            } catch (ClassNotFoundException e) {
                System.err.println("I could not add the handshake line unless I added this catch ¯\\_(ツ)_/¯");
                e.printStackTrace();
            } catch (InterruptedException e) {
                System.err.println("Semaphor related error most likely" + e);
            }
        }

        public peerConnection(Socket connection, peerInfo peer, boolean isServer) {
          this.connection = connection;
          this.peerId = peer.id;

          try {
            this.out = new ObjectOutputStream(connection.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(connection.getInputStream());

            // TODO(bllndalichako): Move to 'run()'?
            if (isServer) {
              Log.receiveConnection(peer.id);
              ReceiveHandshake();
              SendHandshake();
            } else {
              Log.startConnection(peer.id);
              SendHandshake();
              ReceiveHandshake();
            }
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }

        public void ValidateHandshake(String handshake) throws Exception {
          if (handshake.length() != 32) {
            throw new Exception("Wrong handshake length!\n\tExpected: 32 bytes\n\tReceived: " + handshake.length() + " bytes\n");
          }

          String handshakeHeader = handshake.substring(0, 18);
          if (!handshakeHeader.equals("P2PFILESHARINGPROJ")) { // TODO(bllndalichako): Store header in constant.
            throw new Exception("Wrong handshake header!\n\tExpected: P2PFILESHARINGPROJ\n\tReceived: " + handshakeHeader + "\n");
          }

          String handshakeZeros = handshake.substring(18, 28);
          if (!handshakeZeros.equals("0000000000")) {
            throw new Exception("Wrong handshake zero bits!\n\tExpected: 0000000000\n\tReceived: " + handshakeZeros + "\n");
          }

          String handshakePeerID = handshake.substring(28, 32);
          if (!handshakePeerID.equals(Integer.toString(this.peerId))) {
            throw new Exception("Wrong handshake peer ID received!\n\tExpected: " + this.peerId + "\n\tReceived: " + handshakePeerID + "\n");
          }

          System.out.println("Valid handshake received from peer " + this.peerId);
        }

        public synchronized void SendHandshake() throws Exception {
          synchronized (handshakeSuccessStatus) {
            try {
              // Create handshake.
              message handshake = new message(32, message.MessageType.handshake, Integer.toString(id));

              // Write and send handshake to peer.
              out.writeObject(handshake.getMessage());
              out.flush();
            } catch (Exception e) {
              System.err.println(e.getMessage());
            }

            // Record handshake sent.
            handshakeSuccessStatus.put(this.peerId, false);
            System.out.println("Success! Peer " + this.peerId + " sent handshake to peer " + id);
          }
        }

        public synchronized  void ReceiveHandshake() throws Exception {
          try {
            byte[] handshakeBytes = new byte[32];
            int bytesRead = in.read(handshakeBytes, 0, 32);

            if (bytesRead == -1) {
              throw new Exception("Handshake bytes not received. Read -1 bytes from input stream");
            }

            String handshakeStr = new String(handshakeBytes);
            ValidateHandshake(handshakeStr);

            // Record handshake receipt, if the client has a record of a previously sent handshake.
            int handshakePeerID = Integer.parseInt(handshakeStr.substring(28, 32));
            boolean peerHandshakeRecorded = handshakeSuccessStatus.containsKey(handshakePeerID);
            boolean peerHandshakeReceived = handshakeSuccessStatus.get(handshakePeerID);
          
            if (!peerHandshakeRecorded) {
              throw new Exception("Failure! Tried to received handshake from peer " + handshakePeerID + " without peer sending one first");
            }
            else if (peerHandshakeRecorded && peerHandshakeReceived) {
              throw new Exception("Failure! Received duplicate handshake from peer " + handshakePeerID);
            }

            // Record successful handshake receipt.
            handshakeSuccessStatus.put(handshakePeerID, true);
            System.out.println("Success! Peer " + this.peerId + " received handshake from peer " + handshakePeerID);
          } catch (Exception e) {
            System.err.println(e.getMessage());
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
                    myFileManager = new fileManager(Integer.toString(id), filename, fileSize, pieceSize, hasFile);
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

        try {
          ServerSocket listener = new ServerSocket(port); // Stores server socket.
          int numPeers = peers.size(); // Stores number of available peers.
          peerInfo currentPeer; // Stores current peer.
          boolean isEarlierPeer; // Flag for earlier peers.
          boolean isLaterPeer; // Flag for later peers.

          for (int i = 0; i < numPeers; i++) {
            currentPeer = peers.elementAt(i);
            isEarlierPeer = currentPeer.id < id;
            isLaterPeer = currentPeer.id > id; 

            // Create and start server connection with later peers.
            if (isLaterPeer) {
              peerConnection currentPeerConnection = new peerConnection(listener.accept(), currentPeer.id);
              currentPeerConnection.start();

              // Store connection among process' threads.
              threads.add(currentPeerConnection);
            }
            // Create and start client connection with earlier peers.
            else if (isEarlierPeer) {
              peerConnection currentPeerConnection = new peerConnection(currentPeer);
              currentPeerConnection.start();

              // Store connection among process' threads.
              threads.add(currentPeerConnection);
            }
          }

          // Close accepted server socket.
          listener.close(); // don't need any more server connections

        } catch (IOException e){
            System.err.println("Could not start listener");
            System.exit(-1);
        }   //it's kind of nice java doesn't let you leave exceptions unhandled but this is getting annoying

    }

    public peerConnection getPeerConnection(int peerId) throws Exception {
        for (int i = 0; i < threads.size(); ++i) {
            if (threads.get(i).peerId == peerId) {
                return threads.get(i);
            }
        }
        throw new Exception("Got peer that didn't exist: peer #" + peerId);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.err.println("You must specify an id and nothing more");
            return;
        }
        peerProcess Peer = new peerProcess(args[0]);    //I think this is how to construct in java it has been a moment
        //TODO: the actual server stuff at the moment (currently only executes once we have 3 peers, is this intended?)

        int selectedPeer = -1;
        if (Peer.hasFile) {
            //If we have the file, select neighbors randomly (should be # of connections, only selecting 1 for testing)
            Random rand = new Random();
            try {
                //Get the data
                Peer.semPeersInterested.acquire();
                ArrayList<Integer> peersInterested = Peer.peersInterested;
                Peer.semPeersInterested.release();

                int selectedPeerIndex = rand.nextInt(peersInterested.size());
                selectedPeer = peersInterested.get(selectedPeerIndex);
                System.out.println("This peer has the file. Begin transfer to peer #" + Integer.toString(selectedPeer));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        int i = 0;
        //Should go right before loop
        long lastRecalc = System.currentTimeMillis();
        while (true) {
            //I have broken this system. It worked before (and still mostly does) but the peer who does nothing right now is blocked by waiting for input
            if (System.currentTimeMillis() - lastRecalc > Peer.unchokingInterval * 1000L) {
                System.out.println("Recalculate top downloaders");
                lastRecalc = System.currentTimeMillis();
            }
            //FIXME: Currently just implemented to send one peer all the data
            /*
            I figure we can break up the sending data into three steps:
            1. Sending all the data to 1 peer
            2. Having all peers communicate with each other (1001 starts sending to 1002 and 1003 while 1002 and 1003 communicate too)
            3. Implement choking and peer download recalculation
            This is currently just step 1
             */
            //Peer who has file
            //FIXME (Post Checkin): move this into the threads
            if (Peer.hasFile && i < Peer.pieceCount) {
                //If we have the file, send some data to the peer we selected
                byte[] onePiece = Peer.myFileManager.readData(i,  1); //The one piece is real
                String msgPayload = new String(onePiece);   //can we get much higher?
                message pieceMsg = new message(msgPayload.length(), message.MessageType.piece, msgPayload);
                Peer.getPeerConnection(selectedPeer).out.writeObject(pieceMsg.getMessage());
                Peer.getPeerConnection(selectedPeer).out.flush();
                i = i + 1;
            } //Peer who doesn't have file
            else if (!Peer.hasFile && i < Peer.pieceCount) {
                String piece = (String) Peer.getPeerConnection(1001).in.readObject();
                String msgPayload = piece.substring(5);
                Peer.myFileManager.writeData(i, msgPayload.getBytes(StandardCharsets.UTF_8));
                Peer.Log.downloadPiece(1001, i, i);
                i = i + 1;
                if (i == Peer.pieceCount) {
                    //On the last iteration
                    Peer.myFileManager.writeToFile();
                    Peer.Log.completeDownload();
                }
            }
        }
        //will need to use threads (barf)
    }
}

