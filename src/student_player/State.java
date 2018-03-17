package student_player;

/**
 * Stores board state information.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class State
{
    /*
     * Invalid board square used for pieces that have been removed from the board.
     */
    public static final int NOT_ON_BOARD = -1;
    
    /*
     * The zorbist hash of the board state.
     */
    public long             hash;
    
    /*
     * The move that arrived at this state. 0 if a root state.
     */
    public int              move;
    
    /**
     * The positions of black pieces on the board as a bitboard.
     */
    public BitBoard         black        = new BitBoard();
    
    /**
     * The board squares of black pieces on the board as a list of board indices.
     */
    public int[]            blackPieces  = new int[16];
    
    /**
     * The number of black pieces on the board.
     */
    public int              blackCount   = 0;
    
    /**
     * The positions of non-king white pieces on the board as a bitboard.
     */
    public BitBoard         white        = new BitBoard();
    
    /**
     * The board squares of non-king white pieces on the board as a list of board
     * indices.
     */
    public int[]            whitePieces  = new int[8];
    
    /**
     * The number of non-king white pieces on the board.
     */
    public int              whiteCount   = 0;
    
    /**
     * The board square of the king as a board index.
     */
    public int              kingSquare   = NOT_ON_BOARD;
    
    /**
     * Default constructor.
     */
    public State()
    {
    }
    
    /**
     * Constructs a new board state from the given pieces.
     * 
     * @param black
     *            A bitboard indicating the positions of black pieces.
     * @param white
     *            A bitboard indicating the positions of non-king white pieces.
     * @param kingSquare
     *            The board sqaure index of the king.
     */
    public State(BitBoard black, BitBoard white, int kingSquare)
    {
        this.black.copy(black);
        this.white.copy(white);
        this.kingSquare = kingSquare;
    }
    
    /**
     * Copies the fields from a given state.
     * 
     * @param state
     *            The state to copy.
     */
    public void copy(State state)
    {
        hash = state.hash;
        black.copy(state.black);
        white.copy(state.white);
        kingSquare = state.kingSquare;
    }
    
    /**
     * Computes the hash for the current board using the zorbist hashing method
     * using the current piece lists.
     */
    public void calculateHash()
    {
        hash = 0;
        for (int i = 0; i < blackCount; i++)
        {
            hash ^= StateExplorer.HASH_KEYS[0][blackPieces[i]];
        }
        for (int i = 0; i < whiteCount; i++)
        {
            hash ^= StateExplorer.HASH_KEYS[1][whitePieces[i]];
        }
        if (kingSquare != NOT_ON_BOARD)
        {
            hash ^= StateExplorer.HASH_KEYS[2][kingSquare];
        }
    }
    
    /**
     * Updates the pieces lists from the black and white piece bitboards.
     */
    public void updatePieceLists()
    {
        blackCount = updatePieceList(black, blackPieces);
        whiteCount = updatePieceList(white, whitePieces);
    }
    
    /**
     * Updates the list of squares a player's non-king pieces are on by iterating
     * the bitboard of piece locations.
     * 
     * @param pieces
     *            The bitboard containing the locations of the pieces.
     * @param piecesList
     *            The list to store the indices.
     * @return The number of elements in the list.
     */
    private int updatePieceList(BitBoard pieces, int[] piecesList)
    {
        int pieceCount = 0;
        // Iterate all set bits in the bitboard to find the indices where pieces are.
        // Not a pretty method, but such is life with an 81 square board packed into
        // three ints.
        int num = pieces.d0;
        int stage = 0;
        int index = -1;
        while (stage < 3)
        {
            int shiftIndex = Integer.numberOfTrailingZeros(num);
            if (shiftIndex != 32)
            {
                shiftIndex++;
                num = num >> shiftIndex;
                index += shiftIndex;
                piecesList[pieceCount++] = index;
            }
            else
            {
                switch (stage)
                {
                    case 0:
                        num = pieces.d1;
                        index = 26;
                        break;
                    case 1:
                        num = pieces.d2;
                        index = 53;
                        break;
                }
                stage++;
            }
        }
        return pieceCount;
    }
}
