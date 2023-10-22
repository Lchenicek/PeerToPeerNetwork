import java.util.ArrayList;
import java.util.RandomAccess;

public class bitfield {
    private final ArrayList<Boolean> pieces = new ArrayList<Boolean>();

    public bitfield(int filesize, int pieceSize, int hasFile) {
        //If the file size is 100Bytes and a piece is 10bytes, we can just record the 10 pieces we may or may not have
        int bitfieldSize = filesize / pieceSize;
        if (hasFile == 1) {
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

    //Takes in another bitfield, returns a list of pieces we want by index?
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

    public static void main(String[] args){
        bitfield testBitfield = new bitfield(100, 10, 0);
        bitfield semiFullBitfield = new bitfield(100, 10, 0);
        semiFullBitfield.addPiece(3);
        semiFullBitfield.addPiece(7);
        bitfield fullBitfield = new bitfield(100, 10, 1);
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
    }
}
