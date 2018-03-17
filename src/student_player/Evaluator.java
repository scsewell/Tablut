package student_player;

/**
 * Computes a heurstic function for the board.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class Evaluator
{
    /**
     * The minimum utility value of a win.
     */
    public static final short WIN_VALUE = 30000;
    
    /**
     * Gets the value of the board for black.
     * 
     * @param state The state to evaluate.
     * @return The value of the board for black.
     */
    public short evaluate(State state)
    {
        // calculate the value of the board for black
        int valueForBlack = 0;
        
        final int PIECE_VALUE = 1000;
        final int KING_CORNER_DISTANCE_VALUE = 100;
        
        // get the piece difference
        valueForBlack += (state.blackCount - state.whiteCount) * PIECE_VALUE;
        
        // black does better the further the distance of the king from a corner
        int kingRow = state.kingSquare / 9;
        int kingCol = state.kingSquare % 9;
        
        int cornerDist0 = Math.abs(0 - kingCol) + Math.abs(0 - kingRow);
        int cornerDist1 = Math.abs(0 - kingCol) + Math.abs(8 - kingRow);
        int cornerDist2 = Math.abs(8 - kingCol) + Math.abs(0 - kingRow);
        int cornerDist3 = Math.abs(8 - kingCol) + Math.abs(8 - kingRow);
        int maxDistance = Math.max(Math.max(cornerDist0, cornerDist1), Math.max(cornerDist2, cornerDist3));
        valueForBlack += maxDistance * KING_CORNER_DISTANCE_VALUE;
        
        return (short)valueForBlack;
    }
}
