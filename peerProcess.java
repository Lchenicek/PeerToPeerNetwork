import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Semaphore;

import javax.crypto.spec.PBEKeySpec;

import resources.*;

public class peerProcess {

  int id;
  int numberOfPreferredNeighbors; // I'm not sure if this should include the optimistic or not
  int unchokingInterval; // seconds
  int optimisticUnchokingInterval; // seconds
  String filename;
  int fileSize; // bytes
  int pieceSize; // bytes
  int pieceCount; // we could infer this from the above two but I think it's cleaner to do it like
                  // this
  boolean hasFile;
  int port;
  Vector<peerInfo> peers; // just for construction. not the actual sockets or anything
  HashMap<Integer, peerConnection> peerConnections;
  logger Log;
  bitfield myBitfield;
  private static byte[] processOwnerBitfield; // Bitfield of pieces contained by the process owner.
  fileManager myFileManager;
  Semaphore fileManagerSemaphor = new Semaphore(1);
  // we should probably have a binary semaphore for writing to the file

  ArrayList<Integer> outstandingPieceRequests = new ArrayList<>(); //Represents pieces we've already requested

  // Semaphors cuz threading
  ArrayList<Integer> peersInterested = new ArrayList<>(); // Peers interested in our data
  Semaphore semPeersInterested = new Semaphore(1); // Semaphor for above data

  private class peerInfo {
    public int id;
    public String hostname;
    public int port;

    public peerInfo(String _id, String _hostname, String _port) {
      id = Integer.parseInt(_id);
      hostname = _hostname;
      port = Integer.parseInt(_port);
    }
  }

  // simple little container for storing info until we make the connection.
  // might be a bad way of going it, feel free to change
  // could be bad java but i'm bootlegging a struct
  private class peerConnection {

    public peerConnectionSend send;
    public peerConnectionReceive recv;

    private int peerId;
    private Socket connection;
    ArrayList<Integer> iDesiredPieces; // Indices of pieces that the process owner needs.

    // Records whether a handshake has been successfully sent/received between
    // connected peers.
    private Map<Integer, Boolean> handshakeSuccessStatus = new HashMap<Integer, Boolean>();
    String bitfieldMsg; // Bitfield of pieces contained by the connected peer.

    // Client connectipon
    public peerConnection(peerInfo info) { // constructor for if this peer is connecting to another peer. we make the
                                           // socket
      // even though these are just for sending, we send and receive in here, since I
      // don't want to refactor and it's all set up already
      // but it might be a good idea
      peerId = info.id;
      try {
        connection = new Socket(info.hostname, info.port); // connect to peer's server/listener socket
        Log.startConnection(peerId);
        send = new peerConnectionSend(connection);
        recv = new peerConnectionReceive(connection);

        // Handshake exchange.
        SendHandshake();
        ReceiveHandshake();

        // Bitfield exchange.
        SendBitfield();
        ReceiveBitfield();

        // Send interest message
        // TODO: do something with level of interest
        if (iDesiredPieces.isEmpty()) {
          SendNotInterested();

        } else {
          SendInterested();

        }

        // Receive & process interest response
        ProcessInterestResponse();

        send.start();
        recv.start();

      } catch (ConnectException e) {
        System.err.println("Connection refused. Server's not up. I think.");
        System.exit(-1); // is killing this on any error bad? I don't think so
      } catch (UnknownHostException e) {
        System.err.println("Trying to connect to an unknown host");
        System.exit(-1);
      } catch (IOException e) {
        System.err.println("IOException. idk what to tell you");
        System.exit(-1);
      } catch (Exception e) {
        System.err.println("I don't even know what's up, man");
        e.printStackTrace();
        System.exit(-1);
      }
    }

    public peerConnection(Socket _connection, int _id) { // constructor for this peer got connection from another peer.
                                                         // we got the socket from the listener
      try {
        connection = _connection; // get socket from listener
        peerId = _id;
        Log.receiveConnection(peerId);
        send = new peerConnectionSend(connection);
        recv = new peerConnectionReceive(connection);
        System.out.println("Connection received from peer " + Integer.toString(peerId) + " successfully!");

        // Handshake exchange.
        ReceiveHandshake();
        SendHandshake();

        // Bitfield exchange.
        ReceiveBitfield();
        SendBitfield();

        // Send interest message
        // TODO: do something with level of interest
        if (iDesiredPieces.isEmpty()) {
          SendNotInterested();
        } else {
          SendInterested();
        }

        // Receive & process interest response.
        ProcessInterestResponse();

        send.start();
        recv.start();

      } catch (Exception e) {
        e.printStackTrace();;
      }
    }

    public void SendHandshake() {
      try {
        // Create handshake message.
        message handshakeResponse = new message(32, message.MessageType.handshake, Integer.toString(id));

        // Send handshake message to connected peer.
        send.write(handshakeResponse);

        // Record handshake sent.
        // handshakeSuccessStatus.put(peerId, false);
        // System.out.println("Success! Peer " + id + " sent handshake to peer " +
        // peerId);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void ReceiveHandshake() {
      try {
        // Receive handshake from connected peer.
        String handshake = recv.read();

        // Print handshake for debugging.
        System.out.println("Received handshake: " + handshake);

        // Validate handshake.
        if (!message.isValidHandshake(handshake, this.peerId)) {
          // FIXME: what to do when handshake is invalid?
          // Threw exception for now.
          throw new Exception(
              "Invalid handshake! Received " + handshake + " from " + Integer.toString(this.peerId) + "\n");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void SendBitfield() {
      try {
        // Get process owner's bitfield.
        String bitfieldPayload = myBitfield.getMessagePayload();

        // Create bitfield message.
        message bitfieldMsg = new message(bitfieldPayload.length(), message.MessageType.bitfield, bitfieldPayload);

        // Send bitfield message to connected peer.
        send.write(bitfieldMsg);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void ReceiveBitfield() {
      try {
        // Read bitfield message from connected peer.
        bitfieldMsg = recv.read();

        // Print message for debugging.
        System.out.println("Received bitfield: " + bitfieldMsg);

        // TODO: I assume we'll have to keep track of desiredBits (it'd probably need a
        // semaphor)
        // Determine if connected peer has pieces that the process owner needs.
        /*
         * NOTE: Due to implementation of "processBitfieldMessage()",
         * The indices are relative to entire bitfield message.
         * Therefore, index 5 corresponds to the index of first piece
         */
        iDesiredPieces = myBitfield.processBitfieldMessage(bitfieldMsg);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void SendInterested() {
      try {
        // Create interested message.
        message interestedMsg = new message(0, message.MessageType.interested);

        // Send interested message to connected peer.
        send.write(interestedMsg);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void SendNotInterested() {
      try {
        // Create not interested message.
        message notInterestedMsg = new message(0, message.MessageType.notInterested);

        // Send not interested message to connected peer.
        send.write(notInterestedMsg);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void ProcessInterestResponse() {
      try {
        String interestResponse = recv.read();
        if (interestResponse.charAt(4) == '2') {
          // Add interested peer to our list (mostly for peers with completed sets)
          semPeersInterested.acquire();
          peersInterested.add(this.peerId);
          semPeersInterested.release();

          System.out.println("Peer interested");
          Log.receiveInterestedMessage(peerId);

        } else if (interestResponse.charAt(4) == '3') {
          System.out.println("Peer not interested");
          Log.receiveNotInterestedMessage(peerId);

        } else {
          // Received neither "interested" nor "not interested" message.
          throw new Exception("Invalid response from " + peerId + ".\n Expected interested or not interested message.\n Received: " + interestResponse);

        }

      } catch (InterruptedException e) {
        e.printStackTrace();
        System.err.println("Semaphor related error most likely" + e);
      } catch (Exception e) {
        e.printStackTrace();
      } 
    }

    private class peerConnectionSend extends Thread { // threads so 1 socket doesn't block connections to other peers

      private ObjectOutputStream out; // write to socket. cribbing from sample code here

      public peerConnectionSend(Socket connection) {
        try {
          out = new ObjectOutputStream(connection.getOutputStream());
          out.flush(); // not sure why we need to flush it right away? sample does. guess it's good
                       // practive
        } catch (Exception e) {
          System.err.println(e.getMessage());
        }
      }

      public void write(message m) {
        try {
          out.writeObject(m.getMessage());
          out.flush();
        } catch (Exception e) {
          System.err.println(e.getMessage());
        }
      }
      // used when something else tells this socket to write a message
      // not sure how much it'll come up, but we need it for the constructor at least

      public void sendMessage(message m) {
        try {
          out.writeObject(m.getMessage());
          out.flush();
        } catch (Exception e) {
          System.err.println(e.getMessage());
        }
      }

      /*
       * public peerConnectionSend(Socket connection, peerInfo peer, boolean isServer)
       * {
       * this.connection = connection;
       * this.peerId = peer.id;
       * 
       * try {
       * this.out = new ObjectOutputStream(connection.getOutputStream());
       * this.out.flush();
       * this.in = new ObjectInputStream(connection.getInputStream());
       * 
       * // TODO(bllndalichako): Move to 'run()'?
       * if (isServer) {
       * Log.receiveConnection(peer.id);
       * ReceiveHandshake();
       * SendHandshake();
       * } else {
       * Log.startConnection(peer.id);
       * SendHandshake();
       * ReceiveHandshake();
       * }
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * }
       * }
       */

      public void ValidateHandshake(String handshake) throws Exception {
        if (handshake.length() != 32) {
          throw new Exception(
              "Wrong handshake length!\n\tExpected: 32 bytes\n\tReceived: " + handshake.length() + " bytes\n");
        }

        String handshakeHeader = handshake.substring(0, 18);
        if (!handshakeHeader.equals("P2PFILESHARINGPROJ")) { // TODO(bllndalichako): Store header in constant.
          throw new Exception(
              "Wrong handshake header!\n\tExpected: P2PFILESHARINGPROJ\n\tReceived: " + handshakeHeader + "\n");
        }

        String handshakeZeros = handshake.substring(18, 28);
        if (!handshakeZeros.equals("0000000000")) {
          throw new Exception(
              "Wrong handshake zero bits!\n\tExpected: 0000000000\n\tReceived: " + handshakeZeros + "\n");
        }

        String handshakePeerID = handshake.substring(28, 32);
        if (!handshakePeerID.equals(Integer.toString(peerId))) {
          throw new Exception(
              "Wrong handshake peer ID received!\n\tExpected: " + peerId + "\n\tReceived: " + handshakePeerID + "\n");
        }

        System.out.println("Valid handshake received from peer " + peerId);
      }

      /*
       * public synchronized void ReceiveHandshake() throws Exception {
       * try {
       * byte[] handshakeBytes = new byte[32];
       * int bytesRead = in.read(handshakeBytes, 0, 32);
       * 
       * if (bytesRead == -1) {
       * throw new
       * Exception("Handshake bytes not received. Read -1 bytes from input stream");
       * }
       * 
       * String handshakeStr = new String(handshakeBytes);
       * ValidateHandshake(handshakeStr);
       * 
       * // Record handshake receipt, if the client has a record of a previously sent
       * handshake.
       * int handshakePeerID = Integer.parseInt(handshakeStr.substring(28, 32));
       * boolean peerHandshakeRecorded =
       * handshakeSuccessStatus.containsKey(handshakePeerID);
       * boolean peerHandshakeReceived = handshakeSuccessStatus.get(handshakePeerID);
       * 
       * if (!peerHandshakeRecorded) {
       * throw new Exception("Failure! Tried to received handshake from peer " +
       * handshakePeerID + " without peer sending one first");
       * }
       * else if (peerHandshakeRecorded && peerHandshakeReceived) {
       * throw new Exception("Failure! Received duplicate handshake from peer " +
       * handshakePeerID);
       * }
       * 
       * // Record successful handshake receipt.
       * handshakeSuccessStatus.put(handshakePeerID, true);
       * System.out.println("Success! Peer " + this.peerId +
       * " received handshake from peer " + handshakePeerID);
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * }
       * }
       * 
       * public synchronized void SendBitfield() {
       * try {
       * int payloadLength = processOwnerBitfield.length;
       * 
       * // Create bitfield message.
       * message bitfieldMsg = new message(payloadLength,
       * message.MessageType.bitfield, processOwnerBitfield);
       * 
       * // Send bitfield message to peer.
       * out.write(bitfieldMsg.getMessageBytes());
       * out.flush();
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * }
       * }
       * 
       * public synchronized void ReadPeerBitfield() {
       * try {
       * byte[] msgLengthBytes = new byte[4]; // Stores message length bytes.
       * byte[] msgTypeBytes = new byte[1]; // Stores message type bytes.
       * 
       * // Read message length bytes.
       * int bytesRead = in.read(msgLengthBytes);
       * 
       * // Validate message length bytes.
       * if (bytesRead == -1) {
       * throw new
       * Exception("Bitfield message bytes not received. Read -1 bytes from input stream"
       * );
       * } else if (bytesRead != 4) {
       * throw new Exception("Wrong bitfield message length received. Read " +
       * bytesRead + " bytes from input stream");
       * }
       * 
       * // Convert message length bytes to integer.
       * int msgLength = ByteBuffer.wrap(msgLengthBytes).getInt();
       * 
       * // Read message type bytes.
       * bytesRead = in.read(msgTypeBytes);
       * 
       * // Validate message type bytes.
       * if (bytesRead == -1) {
       * throw new
       * Exception("Bitfield message bytes not received. Read -1 bytes from input stream"
       * );
       * } else if (bytesRead != 1) {
       * throw new Exception("Wrong bitfield message type received. Read " + bytesRead
       * + " bytes from input stream");
       * } else if (msgTypeBytes[0] != (byte) message.MessageType.bitfield.value) {
       * throw new Exception("Wrong bitfield message type received. Expected: " +
       * message.MessageType.bitfield.value + " Received: " + msgTypeBytes[0]);
       * }
       * 
       * // Read message payload bytes.
       * int payloadLength = msgLength - 1; // -1 For message type byte.
       * byte[] msgPayloadBytes = new byte[payloadLength];
       * int numUnreadPayloadBytes = payloadLength;
       * 
       * // Read the available bytes from the input stream.
       * // There may be more input bytes than we need to read, hence the while loop.
       * // All bytes may not be readily available in input stream, hence the
       * continuos check for available bytes inside the while loop.
       * while (numUnreadPayloadBytes > 0) {
       * int availableBytes = in.available();
       * byte[] tempReadBytes = new byte[Math.min(numUnreadPayloadBytes,
       * availableBytes)];
       * 
       * if (Math.min(numUnreadPayloadBytes, availableBytes) > 0) {
       * bytesRead = in.read(tempReadBytes);
       * 
       * // Validate message payload bytes.
       * if (bytesRead == -1) {
       * throw new
       * Exception("Bitfield message bytes not received. Read -1 bytes from input stream"
       * );
       * } else if (bytesRead != Math.min(numUnreadPayloadBytes, availableBytes)) {
       * throw new Exception("Wrong bitfield message payload length received. Read " +
       * bytesRead + " bytes from input stream. Expected "+
       * Math.min(numUnreadPayloadBytes, availableBytes) + " bytes.");
       * }
       * 
       * // Store bytes read into message payload bytes.
       * System.arraycopy(tempReadBytes, 0, msgPayloadBytes, payloadLength -
       * numUnreadPayloadBytes, Math.min(numUnreadPayloadBytes, availableBytes));
       * numUnreadPayloadBytes -= Math.min(numUnreadPayloadBytes, availableBytes);
       * }
       * }
       * 
       * // Store connected peer's bitfield.
       * connectedPeerBitfield = msgPayloadBytes;
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * }
       * }
       * 
       * // TODO(bndalichako): Function below may have issues b/c connectedBitField
       * may be empty.
       * public synchronized boolean PeerIsInterested() {
       * // Determine if connected peer has pieces that the process owner needs.
       * // If so, peer is interested.
       * // If not, peer is not interested.
       * boolean isInterested = false;
       * 
       * for (int i = 0; i < processOwnerBitfield.length; i++) {
       * 
       * if ((processOwnerBitfield[i] & connectedPeerBitfield[i]) !=
       * processOwnerBitfield[i]) {
       * 
       * isInterested = true;
       * 
       * break;
       * }
       * }
       * 
       * return isInterested;
       * }
       * 
       * // Sends "Interested" message.
       * public synchronized void SendInterestedMessage() {
       * int payloadLength = 0; // "Interested" messages have no payload
       * byte[] msgPayload = new byte[payloadLength];
       * 
       * message interestedMsg = new message(payloadLength,
       * message.MessageType.interested, msgPayload);
       * 
       * try {
       * out.write(interestedMsg.getMessageBytes());
       * out.flush();
       * 
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * 
       * }
       * }
       * 
       * // Sends "Not Interested" message.
       * public synchronized void SendNotInterestedMessage() {
       * int payloadLength = 0; // "Not Interested" messages have no payload
       * byte[] msgPayload = new byte[payloadLength];
       * 
       * message notInterestedMsg = new message(payloadLength,
       * message.MessageType.notInterested, msgPayload);
       * 
       * try {
       * out.write(notInterestedMsg.getMessageBytes());
       * out.flush();
       * 
       * } catch (Exception e) {
       * System.err.println(e.getMessage());
       * }
       * }
       * 
       * public synchronized byte[] ReadBytesFromInputStream(InputStream inputStream,
       * int numBytesToRead) throws Exception {
       * byte[] msgBytes = new byte[numBytesToRead];
       * int numUnreadBytes = numBytesToRead;
       * 
       * // Read the available bytes from the input stream.
       * // There may be more input bytes than we need to read, hence the while loop.
       * // All bytes may not be readily available in input stream, hence the
       * continuos check for available bytes inside the while loop.
       * while (numUnreadBytes > 0) {
       * int availableBytes = inputStream.available();
       * byte[] tempReadBytes = new byte[Math.min(numUnreadBytes, availableBytes)];
       * 
       * if (Math.min(numUnreadBytes, availableBytes) > 0) {
       * int bytesRead = inputStream.read(tempReadBytes);
       * 
       * // Validate message payload bytes.
       * if (bytesRead == -1) {
       * throw new
       * Exception("Message bytes not received. Read -1 bytes from input stream");
       * } else if (bytesRead != Math.min(numUnreadBytes, availableBytes)) {
       * throw new Exception("Wrong number of Bytes received. Read " + bytesRead +
       * " bytes from input stream. Expected "+ Math.min(numUnreadBytes,
       * availableBytes) + " bytes.");
       * }
       * 
       * // Store bytes read into message payload bytes.
       * System.arraycopy(tempReadBytes, 0, msgBytes, numBytesToRead - numUnreadBytes,
       * Math.min(numUnreadBytes, availableBytes));
       * numUnreadBytes -= Math.min(numUnreadBytes, availableBytes);
       * }
       * }
       * 
       * return msgBytes;
       * }
       */

      // This is a huge block of commented out code, but since I'm refactoring I'm
      // just focused on getting it all working
      // I'm pretty sure it's all duplicate

      public void run() { // gets called when we do .start() on the thread
        // Don't send bitfield if the process owner doesn't have the file.
        /*
         * if (hasFile) {
         * SendBitfield();
         * }
         * 
         * ReadPeerBitfield();
         * 
         * // If peer is interested, send "interested" message
         * // Otherwise, send "not interested" message
         * if (PeerIsInterested()) {
         * SendInterestedMessage();
         * } else {
         * SendNotInterestedMessage();
         * }
         * 
         * try {
         * // Read message length from peer.
         * byte[] msgLengthBytes = ReadBytesFromInputStream(in, 4);
         * 
         * // Convert message length bytes to integer.
         * int msgLength = ByteBuffer.wrap(msgLengthBytes).getInt();
         * 
         * // Read message type bytes.
         * byte[] msgTypeBytes = ReadBytesFromInputStream(in, 1);
         * 
         * // Extract message type from "message type" byte.
         * message.MessageType msgType = message.ExtractMessageType(msgTypeBytes);
         * 
         * switch (msgType) {
         * case choke:
         * break;
         * case unchoke:
         * break;
         * case interested:
         * break;
         * case notInterested:
         * break;
         * case have:
         * break;
         * case bitfield:
         * break;
         * case request:
         * break;
         * case piece:
         * break;
         * default:
         * break;
         * }
         * 
         * } catch (Exception e) {
         * System.err.println(e.getMessage());
         * }
         */
        while (true) {
        }
      }
      // doesn't do anything right now, but without this here the process just dies as
      // soon as it makes its last connection
    }

    private class peerConnectionReceive extends Thread {
      private ObjectInputStream in;
      // no outstream because this is just a reader

      public peerConnectionReceive(Socket _connection) {
        try {
          in = new ObjectInputStream(connection.getInputStream());
        } catch (Exception e) {

          System.err.println("Contructor error");
        }
      }

      public String read() {
        try {
          return (String) in.readObject();
        } catch (Exception e) {
          System.err.println(e.getMessage());
          return ""; // need a return type always
        }
      }

      public void sendPieceToPeer(int piece) {
        try {
          // getPeerConnection(peer).send.sendMessage("test");
          // If we have the file, send some data to the peer we selected
          fileManagerSemaphor.acquire();
          byte[] onePiece = myFileManager.readData(piece, 1); // The one piece is real
          fileManagerSemaphor.release();
          String msgPayload = new String(onePiece);
          String indexBinary = Integer.toString(piece);
          for (int i = indexBinary.length(); i < 4; ++i) {
            indexBinary = "0" + indexBinary;
          }
          msgPayload = indexBinary + msgPayload;
          message pieceMsg = new message(msgPayload.length(), message.MessageType.piece, msgPayload);
          send.sendMessage(pieceMsg);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      public void requestPieceFromPeer() {
        try {
          Random rand = new Random();
          System.out.println(iDesiredPieces);
          int piece = iDesiredPieces.get(rand.nextInt(iDesiredPieces.size()));

          message pieceRequest = new message(32, message.MessageType.request, Integer.toString(piece));
          send.sendMessage(pieceRequest);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      public void run() {
        System.out.println("run begins");
        while(true){
            try {
                //Process only piece messages in order rn
                String piece = read();

                //converts it to an int
                int msgType = piece.charAt(4) - '0';
                switch (msgType) {
                    case 0:
                        System.out.println("Received choke");
                        break;
                    case 1:
                        System.out.println("Received unchoke");
                        break;
                    case 2:
                        System.out.println("Received interested");
                        break;
                    case 3:
                        System.out.println("Received not interested");
                        break;
                    case 4:
                        System.out.println("Received have");
                        break;
                    case 5:
                        System.out.println("Received bitfield");
                        break;
                    case 6:
                        System.out.println("Received request");
                        //Process request
                        String requestPieceString = piece.substring(5);
                        sendPieceToPeer(Integer.parseInt(requestPieceString));
                        break;
                    case 7:
                        System.out.println("Received piece");

                        fileManagerSemaphor.acquire();
                        String pieceIndexString = piece.substring(5, 9);
                        int pieceIndex = Integer.parseInt(pieceIndexString);
                        String msgPayload = piece.substring(9);
                        myFileManager.writeData(pieceIndex, msgPayload.getBytes(StandardCharsets.UTF_8));
                        myBitfield.addPiece(pieceIndex);
                        iDesiredPieces = myBitfield.processBitfieldMessage(bitfieldMsg);
                        outstandingPieceRequests.remove(Integer.valueOf(pieceIndex));
                        Log.downloadPiece(1001, pieceIndex, pieceIndex);
                        if (myBitfield.fileComplete()) {
                            //On the last iteration
                            myFileManager.writeToFile();
                            Log.completeDownload();
                            System.out.println("Finished Reading File");
                        }
                        else {
                            //If not done, request another piece
                            System.out.println("Requesting New Piece");
                            requestPieceFromPeer();
                        }
                        fileManagerSemaphor.release();


                        break;

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
          }
        }
      }
    }

  public peerProcess(String _id) {
    id = Integer.parseInt(_id);
    Log = new logger(id);
    try (BufferedReader readerCfg = new BufferedReader(new FileReader("./Common.cfg"))) {
      String line;
      while ((line = readerCfg.readLine()) != null) {
        String[] parsedLine = line.split(" ");
        String givenParameter = parsedLine[0];
        String value = parsedLine[1];

        if (givenParameter.equals("NumberOfPreferredNeighbors"))
          numberOfPreferredNeighbors = Integer.parseInt(value);
        else if (givenParameter.equals("UnchokingInterval"))
          unchokingInterval = Integer.parseInt(value);
        else if (givenParameter.equals("OptimisticUnchokingInterval"))
          optimisticUnchokingInterval = Integer.parseInt(value);
        else if (givenParameter.equals("FileName"))
          filename = value;
        else if (givenParameter.equals("FileSize"))
          fileSize = Integer.parseInt(value);
        else if (givenParameter.equals("PieceSize"))
          pieceSize = Integer.parseInt(value);
        /*
         * i think the config files are always in the same order, but doing it like this
         * ensures it doesn't matter if they are
         * also just manually reading in every line and assinging them like that would
         * make me feel like a caveman
         * this might take longer than just manually doing it
         * feel free to change it
         */

        else {
          System.err.println("Invalid config file line " + givenParameter + " present in file");
          System.exit(-1);
        }
        pieceCount = (int) Math.ceil((double) fileSize / (double) pieceSize); // this many conversions is gross but i
                                                                              // want to ensure it works
        // ceil because we can't have half a piece or whatever, one piece will just have
        // some empty space
      }
      readerCfg.close();

    } catch (Exception e) {
      System.err.println("Config file Common.cfg not found");
      System.exit(-1); // i think you go negative with an error. that's os knowledge. it might also be
                       // the direct opposite. oops
      // if we can't find the config file just kill it, since it isn't going to work
    }

    int earlierPeers = 0; // outside of try cause we need it for the for loop later
    try (BufferedReader readerPeer = new BufferedReader(new FileReader("./PeerInfo.cfg"))) {
      String line;
      boolean encounteredSelf = false;
      peers = new Vector<peerInfo>();

      /*
       * my rationale for this is it's easier to read the whole file and then
       * determine what to do from there,
       * rather than reading in one line, connecting, reading in the next, connecting,
       * and so on.
       * This way our port can know it's own information before connection begins, and
       * reading the file
       * won't be gummed up waiting for connections. Also, we know how many peers to
       * expect
       */

      while ((line = readerPeer.readLine()) != null) {
        String[] parsedLine = line.split(" ");
        int peerId = Integer.parseInt(parsedLine[0]); // first member of peerCfg is the peer id
        // If we found ourselves, initialize and set up our values
        if (peerId == id) {
          encounteredSelf = true;
          port = Integer.parseInt(parsedLine[2]);
          if (Integer.parseInt(parsedLine[3]) == 1)
            hasFile = true;
          else
            hasFile = false;
          // Set up bitfield, if hasFile, then all values are 1.
          myBitfield = new bitfield(fileSize, pieceSize, hasFile);
          myFileManager = new fileManager(Integer.toString(id), filename, fileSize, pieceSize, hasFile);

          // Initialize process owner bitfield.
        }
        // get our port number from the file, if we have the file
        // we don't care about our hostname, we're running on this machine

        else { // not us, learning about a peer
          if (!encounteredSelf)
            earlierPeers++; // counts how many we need to manually connect to, rather than wait for
          peers.add(new peerInfo(parsedLine[0], parsedLine[1], parsedLine[2]));
          // we might not actually need to remember this info for later peers, since
          // they'll come to us.
          // it does make it easy to know how many to expect, so I think it's valid to
          // keep it like this
        }
      }
      readerPeer.close();
    } catch (Exception e) {
      System.err.println("Config file PeerInfo.cfg not found");
      System.exit(-1);
    }

    peerConnections = new HashMap<Integer, peerConnection>();

    for (int i = 0; i < earlierPeers; i++) {
      peerConnection peerConn = new peerConnection(peers.elementAt(i)); // make the thread to connect to the earlier
                                                                        // peers
      peerConnections.put(peers.elementAt(i).id, peerConn);
    }

    try {
      ServerSocket listener = new ServerSocket(port);
      for (int i = 0; i < peers.size() - earlierPeers; i++) { // we're awaiting connections from total peers - earlier
                                                              // peers others
        peerConnection peerConn = new peerConnection(listener.accept(), peers.elementAt(i + earlierPeers).id); // this
                                                                                                               // assumes
                                                                                                               // peers
                                                                                                               // connect
                                                                                                               // in
                                                                                                               // order,
                                                                                                               // they
                                                                                                               // might
                                                                                                               // not.
                                                                                                               // fix?
        peerConnections.put(peers.elementAt(i + earlierPeers).id, peerConn);
      }
      listener.close(); // don't need any more server connections

    } catch (IOException e) {
      System.err.println("Could not start listener");
      System.exit(-1);
    } // it's kind of nice java doesn't let you leave exceptions unhandled but this is
      // getting annoying

  }

  public peerConnection getPeerConnection(int peerId) throws Exception {
    return peerConnections.get(peerId);
    // throw new Exception("Got peer that didn't exist: peer #" + peerId);
  }

  public void initializeBitfield(boolean hasFile) {
    // Fill the bitfield with 1s if the process owner has the file. Otherwise, fill
    // it with 0s.
    Arrays.fill(processOwnerBitfield, hasFile ? (byte) 0xFF : (byte) 0x00);

    // Determine if the process owner has trailing zero bits.
    boolean hasTrailingZeroBits = (pieceCount % 8) != 0;

    // Fill the trailing bits with 0s if the process owner has the file.
    if (hasFile && hasTrailingZeroBits) {
      int iLastByte = processOwnerBitfield.length - 1;
      int numTrailingZeroBits = 8 - (pieceCount % 8);

      for (int i = numTrailingZeroBits; i < 8; i++) {
        processOwnerBitfield[iLastByte] |= (0x01 << i);
      }
    }
  }

  public void sendPieceToPeer(int peer, int piece) {
    try {
      // getPeerConnection(peer).send.sendMessage("test");
      // If we have the file, send some data to the peer we selected
      fileManagerSemaphor.acquire();
      byte[] onePiece = myFileManager.readData(piece, 1); // The one piece is real
      fileManagerSemaphor.release();
      String msgPayload = new String(onePiece);
      String indexBinary = Integer.toString(piece);
      for (int i = indexBinary.length(); i < 4; ++i) {
        indexBinary = "0" + indexBinary;
      }
      msgPayload = indexBinary + msgPayload;
      message pieceMsg = new message(msgPayload.length(), message.MessageType.piece, msgPayload);
      getPeerConnection(peer).send.sendMessage(pieceMsg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void requestPieceFromPeer(peerConnection pC, int piece) {
    try {
      System.out.println("Interested in piece:");
      System.out.println(piece);

      message pieceRequest = new message(32, message.MessageType.request, Integer.toString(piece));
      pC.send.sendMessage(pieceRequest);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("You must specify an id and nothing more");
      return;
    }
    peerProcess Peer = new peerProcess(args[0]); // I think this is how to construct in java it has been a moment
    // TODO: the actual server stuff at the moment (currently only executes once we
    // have 3 peers, is this intended?)
    Random rand = new Random();

    int selectedPeer = -1;
    if (Peer.hasFile) {
      // If we have the file, select neighbors randomly (should be # of connections,
      // only selecting 1 for testing)
      try {
        // Get the data
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

    if (!Peer.hasFile) {
      for (Map.Entry<Integer, peerConnection> entry : Peer.peerConnections.entrySet()) {
        // key is id and value is connection
        System.out.println(entry.getValue().iDesiredPieces);
        if (entry.getValue().iDesiredPieces.size() > 0) {
          Peer.fileManagerSemaphor.acquire();
          int pieceToRequest = entry.getValue().iDesiredPieces.get(rand.nextInt(entry.getValue().iDesiredPieces.size()));
          Peer.fileManagerSemaphor.release();
          Peer.requestPieceFromPeer(entry.getValue(), pieceToRequest);
        }
      }
    }

    int i = 0;
    // Should go right before loop
    long lastRecalc = System.currentTimeMillis();
    while (true) {
      // I have broken this system. It worked before (and still mostly does) but the
      // peer who does nothing right now is blocked by waiting for input
      if (System.currentTimeMillis() - lastRecalc > Peer.unchokingInterval * 1000L) {
        System.out.println("Recalculate top downloaders");
        lastRecalc = System.currentTimeMillis();
      }

      // Peer who has file
      /*
      if (Peer.hasFile && i < Peer.pieceCount) {
        Peer.sendPieceToPeer(selectedPeer, i);
        i = i + 1;
        if (i == Peer.pieceCount) {
          System.out.println("Finished Sending File");
        }
      }
       */
    }
    // will need to use threads (barf)
  }
}
