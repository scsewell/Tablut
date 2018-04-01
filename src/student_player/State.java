package student_player;

import boardgame.Board;
import coordinates.Coordinates;
import tablut.TablutBoardState;

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
     * Constructs a new board state from the board state.
     */
    public State(TablutBoardState state)
    {
        for (int i = 0; i < 81; i++)
        {
            int row = i / 9;
            int col = i % 9;
            switch (state.getPieceAt(Coordinates.get(col, row)))
            {
                case BLACK:
                    black.set(col, row);
                    break;
                case WHITE:
                    white.set(col, row);
                    break;
                case KING:
                    kingSquare = i;
                    break;
                default:
                    break;
            }
        }
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
    
    /*
     * Prints the State in a nice format. Indicates the last moved piece by placing
     * brackets around it and marks where it moved from with an 'x'.
     */
    @Override
    public String toString()
    {
        // print the number of pieces for each team
        String str = System.lineSeparator();
        str += "Black Pieces: " + black.cardinality() + " ";
        str += "White Pieces: " + (white.cardinality() + (kingSquare == State.NOT_ON_BOARD ? 0 : 1)) + " ";
        
        // print the board nicely formatted
        int from = (int)(move & 0x7F);
        int to = (int)((move >> 7) & 0x7F);
        
        str += System.lineSeparator();
        str += "    0 1 2 3 4 5 6 7 8    " + System.lineSeparator();
        str += "  +-------------------+  " + System.lineSeparator();
        for (int row = 0; row < 9; row++)
        {
            str += row + " | ";
            for (int col = 0; col < 9; col++)
            {
                int square = (row * 9) + col;
                boolean isFrom = (from == square);
                boolean isTo = (to == square);
                
                if (black.getValue(col, row))
                {
                    str = FormatPiece(str, "b", isTo);
                }
                else if (white.getValue(col, row))
                {
                    str = FormatPiece(str, "w", isTo);
                }
                else if (kingSquare == square)
                {
                    str = FormatPiece(str, "K", isTo);
                }
                else
                {
                    str += (isFrom && move > 0) ? "x " : ". ";
                }
            }
            str += "| " + row + System.lineSeparator();
        }
        str += "  +-------------------+  " + System.lineSeparator();
        str += "    0 1 2 3 4 5 6 7 8    " + System.lineSeparator();
        return str;
    }
    
    /**
     * Adds a piece to the board string.
     * 
     * @param current
     *            The current board string.
     * @param piece
     *            The character to use for the piece on the board.
     * @param wasMoved
     *            Indicates if the piece moved last turn.
     * @return The new board string.
     */
    private String FormatPiece(String current, String piece, boolean wasMoved)
    {
        if (wasMoved && move > 0)
        {
            current = current.substring(0, current.length() - 1);
            current += "(" + piece + ")";
        }
        else
        {
            current += piece + " ";
        }
        return current;
    }
}
