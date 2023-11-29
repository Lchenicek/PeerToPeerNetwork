package resources;

import java.util.ArrayList;

public class bitfield {
    private final ArrayList<Boolean> pieces = new ArrayList<Boolean>();
    private int ownedPieces;
    //track number of pieces we have, so we can check if the bitfield is full without
    //actually looping through it

    public bitfield(int filesize, int pieceSize, boolean hasFile) {
        // TODO(bndalichako): Note that project PDF talks about implementing trailing 0 bits.
        //If the file size is 100Bytes and a piece is 10bytes, we can just record the 10 pieces we may or may not have
        int bitfieldSize = (int) Math.ceil((double) filesize/(double) pieceSize);
        if (hasFile) {
            //If we have the file, set all bits to 1
            for (int i = 0; i < bitfieldSize; ++i) {
                pieces.add(true);
            }
            ownedPieces = bitfieldSize;
        }
        else {
            for (int i = 0; i < bitfieldSize; ++i) {
                pieces.add(false);
            }
            ownedPieces = 0;
        }
    }

    public bitfield(String bitfieldMsg){
        //constructor from message
        int msgSize = Integer.parseInt(bitfieldMsg.substring(0, 4)) * 8;
        System.out.println(msgSize);
        ownedPieces = 0;
        for(int i = 5; i < msgSize; i++){
            try{
                if(bitfieldMsg.charAt(i) == '1'){ 
                    pieces.add(true);
                    ownedPieces++;
                }
            else pieces.add(false);
            } catch (IndexOutOfBoundsException e){ break; }
        }
    }

    public ArrayList<Boolean> getBitfield() {
        return pieces;
    }

    public void addPiece(int index) {
        if(!pieces.get(index)){
            pieces.set(index, true);
            ownedPieces++;
        }
        //if the piece isn't in the bitfield, add it
        //little overhead to avoid issues with dupes
    }

    public int getPieceCount(){
        return ownedPieces;
    }

    //Takes in another resources.bitfield, returns a list of pieces we want by index?
    public ArrayList<Integer> getMissingBits(ArrayList<Boolean> otherBitfield) {
        ArrayList<Integer> intrestingPieces = new ArrayList<Integer>();
        for (int i = 0; i < otherBitfield.size(); ++i) {
            //If the otherbitfield has piece i but we don't than we are interested in that
            if (otherBitfield.get(i) && !pieces.get(i)) {
                intrestingPieces.add(i);
            }
        }
        return intrestingPieces;
    }

    public String getMessagePayload() {
        //Returns pieces as string (0 for missing, 1 for has)
        String payload = "";
        for (int i = 0; i < pieces.size(); ++i) {
            if (pieces.get(i)) {
                payload += "1";
            }
            else {
                payload += "0";
            }
        }
        return payload;
    }

    public ArrayList<Integer> processBitfieldMessage(String bitfieldMsg) {
        int msgSize = (Integer.parseInt(bitfieldMsg.substring(0, 4)) * 8);  //convert size to bits
        ArrayList<Integer> intrestingBits = new ArrayList<>();
        //We start at 5 because 0-3 is the size, 4 is the type and 5-msgSize is payload
        for (int i = 5; i < msgSize; ++i) {
            //This only works because we know the size of the payload and Array list are the same
            try{
                if (bitfieldMsg.charAt(i) == '1' && !pieces.get(i - 5)) {
                    intrestingBits.add(i - 5);
                }
            } catch (IndexOutOfBoundsException e){ break; } 
            //for bitfield messages that aren't cleanly their number of bits the bytes size implies
            //i.e. a 15 bit message that would expect 16
        }
        return intrestingBits;
    }

    public boolean hasFile(){
        return ownedPieces == pieces.size();
    }
    //if the number of owned pieces is equal to the number of pieces, we have the whole file

    public static void main(String[] args){
        bitfield testBitfield = new bitfield(100, 10, false);
        bitfield semiFullBitfield = new bitfield(100, 10, false);
        semiFullBitfield.addPiece(3);
        semiFullBitfield.addPiece(7);
        bitfield fullBitfield = new bitfield(100, 10, true);
        ArrayList<Integer> test1 = testBitfield.getMissingBits(semiFullBitfield.getBitfield());
        String semiFullPayload = semiFullBitfield.getMessagePayload();
        System.out.println(semiFullPayload);
        message semiFullMsg = new message(semiFullPayload.length(), message.MessageType.bitfield, semiFullPayload);
        bitfield secondSemiFullBitfield = new bitfield(semiFullMsg.getMessage());
        System.out.println(semiFullMsg.getMessage());
        System.out.println("Test1:");
        for (Integer integer : test1) {
            System.out.println(integer);
            System.out.println(testBitfield.hasFile());
        }
        System.out.println("\nTest2:");
        ArrayList<Integer> test2 = testBitfield.getMissingBits(fullBitfield.getBitfield());
        for (Integer integer : test2) {
            System.out.println(integer);
            System.out.println(testBitfield.hasFile());
        }
        System.out.println("\nTest3:");
        String test3 = semiFullBitfield.getMessagePayload();
        System.out.println(test3);

        System.out.println("\nTest4");
        System.out.println(fullBitfield.hasFile());
        System.out.println(secondSemiFullBitfield.hasFile());
        
    }
}
