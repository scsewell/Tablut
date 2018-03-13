package student_player;

/**
 * Stores information about a visited state in a compact form.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class TranspositionData
{
    /**
     * Node is a PV node, score is exact.
     */
    public static final int PV_NODE  = 0;
    
    /**
     * Node is an all node, score is an upper bound.
     */
    public static final int ALL_NODE = 1;
    
    /**
     * Node is a cut node, score is a lower bound.
     */
    public static final int CUT_NODE = 2;
    
    /**
     * The type of node this store represents.
     */
    public int              nodeType;
    
    /**
     * The depth at which this node was found at.
     */
    public int              depth;
    
    /**
     * The score of this node.
     */
    public int              score;
    
    /**
     * The best move currently known at this node. Used to pick a good first move
     * when exploring using this state.
     */
    public int              bestMove;
    
    /**
     * Gets the best move and score packed into a long.
     * 
     * @return the move in the upper 32 bits, the score in the lower 32 bits.
     */
    public long getValue()
    {
        return (((long)bestMove << 32)) | (((long)score) & 0xFFFFFFFFL);
    }
}
