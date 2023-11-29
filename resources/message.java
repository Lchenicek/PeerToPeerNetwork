package resources;

public class message {

    public enum MessageType {
        choke(0),
        unchoke(1),
        interested(2),
        notInterested(3),
        have(4),
        bitfield(5),
        request(6),
        piece(7),
        handshake(8);

        public final int value;

        private MessageType(int value) {
            this.value = value;
        }
    }

    int messageLength;
    MessageType messageType;
    String messagePayload; //I'm not sure how the message payload is supposed to be stored/transmitted (I'm assuming for now it's an int)
    String msg;
    byte[] msgBytes; // Stores complete message as byte array.

    public message(int messageLength, MessageType messageType, String messagePayload) {
        this.messageLength = messageLength;
        this.messageType = messageType;
        this.messagePayload = messagePayload;
        if (messageType == MessageType.handshake) {
            //Technically is no option 8, going to use it to create handshakes. messagePayload is ID. length is ignored
            msg = "P2PFILESHARINGPROJ" + "0000000000" + (messagePayload);
        }
        else {
            //Message length is number of bits, should be number of bytes when transmitted, so divide by 8 and take the ceil to get num of bytes
            int lengthInBytes = (int) Math.ceil((float) messageLength / 8.0);
            String length = Integer.toString(lengthInBytes);
            while (length.length() < 4) {
                length = "0" + length;
            }

            msg = length + Integer.toString(messageType.value) + (messagePayload);
        }
    }

    public message(int payloadLength, MessageType messageType, byte[] messagePayload) {
      byte[] messageLengthBytes = new byte[4]; // Stores message length as byte array.
      int messageLength = payloadLength + 1; // +1 for "message type" byte.

      // Convert message length to byte array
      messageLengthBytes = convertIntToByteArray(messageLength);

      msgBytes = new byte[messageLengthBytes.length + messageLength];

      // Consolidate all byte[] into one.
      System.arraycopy(messageLengthBytes, 0, msgBytes, 0, messageLengthBytes.length);
      msgBytes[messageLengthBytes.length] = (byte) messageType.value;
      System.arraycopy(messagePayload, 0, msgBytes, messageLengthBytes.length + 1, messagePayload.length);
    }

    public message(int messageLength, MessageType messageType) {
        this.messageLength = messageLength;
        this.messageType = messageType;
        String length = Integer.toString(messageLength);
        while (length.length() < 4) {
            length = "0" + length;
        }
        msg = length + Integer.toString(messageType.value);
    }

    public static byte[] convertIntToByteArray(int value) {
        return new byte[] {
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    public static boolean isValidHandshake(String handshake, int expectedID) {
        //Verify header
        if (handshake.length() != 32) {
            //This prevents us from getting an error of out of index
            return false;
        }
        String handshakeHeader = handshake.substring(0, 18);
        if (!handshakeHeader.equals("P2PFILESHARINGPROJ")) {
            return false;
        }
        String zeros = handshake.substring(18, 28);
        if (!zeros.equals("0000000000")) {
            return false;
        }
        String peerID = handshake.substring(28, 32);
        if (!peerID.equals(Integer.toString(expectedID))) {
            return false;
        }
        return true;
    }

    public String getMessage() {
        return msg;
    }

    public byte[] getMessageBytes() {
      return msgBytes;
    }
    
    public static MessageType ExtractMessageType(byte[] messageTypeBytes) {
      return MessageType.values()[messageTypeBytes[0]];
    }
    public static void main(String[] args){
        message testMsg = new message(4, MessageType.handshake, "1002");
        System.out.println(testMsg.getMessage());
        System.out.println(isValidHandshake(testMsg.getMessage(), 1002));
        System.out.println(isValidHandshake(testMsg.getMessage(), 1003));
        message bitfieldMessage = new message(10, MessageType.bitfield, "0000000001");
        System.out.println(isValidHandshake(bitfieldMessage.getMessage(), 1002));
    }
}
