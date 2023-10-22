package resources;

import java.util.ArrayList;

public class message {
    int messageLength;
    int messageType;
    int messagePayload; //I'm not sure how the message payload is supposed to be stored/transmitted (I'm assuming for now it's an int)
    String msg;

    public message(int messageLength, int messageType, int messagePayload) {
        this.messageLength = messageLength;
        this.messageType = messageType;
        this.messagePayload = messagePayload;
        if (messageType == 8) {
            //Technically is no option 8, going to use it to create handshakes. messagePayload is ID. length is ignored
            msg = "P2PFILESHARINGPROJ" + "0000000000" + Integer.toString(messagePayload);
        }
        else {
            String length = Integer.toString(messageLength);
            while (length.length() < 4) {
                length = "0" + length;
            }
            msg = length + Integer.toString(messageType) + Integer.toString(messagePayload);
        }
    }

    public String getMessage() {
        return msg;
    }

    public static void main(String[] args){
        message testMsg = new message(4, 8, 1002);
        System.out.println(testMsg.getMessage());
    }
}
