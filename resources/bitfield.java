package resources;

import java.util.ArrayList;

public class bitfield {
    private final ArrayList<Boolean> pieces = new ArrayList<Boolean>();

    public bitfield(int filesize, int pieceSize, boolean hasFile) {
        //If the file size is 100Bytes and a piece is 10bytes, we can just record the 10 pieces we may or may not have
        int bitfieldSize = (int) Math.ceil((double) filesize/(double) pieceSize);
        if (hasFile) {
            //If we have the file, set all bits to 1
            for (int i = 0; i < bitfieldSize; ++i) {
                pieces.add(true);
            }
        }
        else {
            for (int i = 0; i < bitfieldSize; ++i) {
                pieces.add(false);
            }
        }
    }

    public ArrayList<Boolean> getBitfield() {
        return pieces;
    }

    public void addPiece(int index) {
        pieces.set(index, true);
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
        int msgSize = Integer.parseInt(bitfieldMsg.substring(0, 4));
        ArrayList<Integer> intrestingBits = new ArrayList<>();
        //We start at 5 because 0-3 is the size, 4 is the type and 5-msgSize is payload
        for (int i = 5; i < msgSize; ++i) {
            //This only works because we know the size of the payload and Array list are the same
            if (bitfieldMsg.charAt(i) == '1' && !pieces.get(i - 5)) {
                intrestingBits.add(i);
            }
        }
        return intrestingBits;
    }

    public static void main(String[] args){
        bitfield testBitfield = new bitfield(100, 10, false);
        bitfield semiFullBitfield = new bitfield(100, 10, false);
        semiFullBitfield.addPiece(3);
        semiFullBitfield.addPiece(7);
        bitfield fullBitfield = new bitfield(100, 10, true);
        ArrayList<Integer> test1 = testBitfield.getMissingBits(semiFullBitfield.getBitfield());
        System.out.println("Test1:");
        for (Integer integer : test1) {
            System.out.println(integer);
        }
        System.out.println("\nTest2:");
        ArrayList<Integer> test2 = testBitfield.getMissingBits(fullBitfield.getBitfield());
        for (Integer integer : test2) {
            System.out.println(integer);
        }
        System.out.println("\nTest3:");
        String test3 = semiFullBitfield.getMessagePayload();
        System.out.println(test3);
    }
}
