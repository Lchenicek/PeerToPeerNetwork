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

    public message(int messageLength, MessageType messageType, String messagePayload) {
        this.messageLength = messageLength;
        this.messageType = messageType;
        this.messagePayload = messagePayload;
        if (messageType == MessageType.handshake) {
            //Technically is no option 8, going to use it to create handshakes. messagePayload is ID. length is ignored
            msg = "P2PFILESHARINGPROJ" + "0000000000" + (messagePayload);
        }
        else {
            String length = Integer.toString(messageLength);
            while (length.length() < 4) {
                length = "0" + length;
            }
            msg = length + Integer.toString(messageType.value) + (messagePayload);
        }
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

    public static void main(String[] args){
        message testMsg = new message(4, MessageType.handshake, "1002");
        System.out.println(testMsg.getMessage());
        System.out.println(isValidHandshake(testMsg.getMessage(), 1002));
        System.out.println(isValidHandshake(testMsg.getMessage(), 1003));
        message bitfieldMessage = new message(10, MessageType.bitfield, "0000000001");
        System.out.println(isValidHandshake(bitfieldMessage.getMessage(), 1002));
    }
}
