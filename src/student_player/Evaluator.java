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
    public static final short    WIN_VALUE                          = 30000;
    
    /**
     * The mapping from piece type and location to net value.
     */
    public static final int[][]  SQUARE_VALUES;
    
    /**
     * The diamond shaped region at the center of the board where white starts.
     */
    public static final BitBoard REGION_CENTER;
    public static final int      REGION_CENTER_VALUE_BLACK          = -3;
    public static final int      REGION_CENTER_VALUE_WHITE          = -2;
    public static final int      REGION_CENTER_VALUE_KING           = 0;
    
    /**
     * The regions of the board at the middle of the edges of the board.
     */
    public static final BitBoard REGION_EDGE;
    public static final int      REGION_EDGE_VALUE_BLACK            = -3;
    public static final int      REGION_EDGE_VALUE_WHITE            = -2;
    public static final int      REGION_EDGE_VALUE_KING             = 5;
    
    /**
     * The region between the edges of the board and the center region.
     */
    public static final BitBoard REGION_CORE;
    public static final int      REGION_CORE_VALUE_BLACK            = 3;
    public static final int      REGION_CORE_VALUE_WHITE            = 3;
    public static final int      REGION_CORE_VALUE_KING             = 3;
    
    /**
     * The board squares vertically or horizontally adjacent to the corner squares.
     */
    public static final BitBoard REGION_CORNER_ADJACENT;
    public static final int      REGION_CORNER_ADJACENT_VALUE_BLACK = -6;
    public static final int      REGION_CORNER_ADJACENT_VALUE_WHITE = -15;
    public static final int      REGION_CORNER_ADJACENT_VALUE_KING  = -10;
    
    /**
     * The squares vertically or horizontally spaced one square away from the corner
     * squares.
     */
    public static final BitBoard REGION_CORNER_BLOCK;
    public static final int      REGION_CORNER_BLOCK_VALUE_BLACK    = 10;
    public static final int      REGION_CORNER_BLOCK_VALUE_WHITE    = 0;
    public static final int      REGION_CORNER_BLOCK_VALUE_KING     = 6;
    
    /**
     * The board squares diagonally adjacent to the corner squares.
     */
    public static final BitBoard REGION_CORNER_DIAGONAL;
    public static final int      REGION_CORNER_DIAGONAL_VALUE_BLACK = 10;
    public static final int      REGION_CORNER_DIAGONAL_VALUE_WHITE = 2;
    public static final int      REGION_CORNER_DIAGONAL_VALUE_KING  = 5;
    
    /**
     * The weighting of the square values in the evaluation.
     */
    public static final int      SQUARE_VALUE_MULTIPIER             = 5;
    
    /**
     * The value of the black pieces values.
     */
    public static final int      BLACK_PIECE_VALUE                  = 1200;
    
    /**
     * The value of white pieces.
     */
    public static final int      WHITE_PIECE_VALUE                  = 1000;
    
    /**
     * The value of moves that could capture a piece.
     */
    public static final int      THREAT_VALUE                       = 330;
    
    /**
     * The value added to a peice per move able to be made.
     */
    public static final int      MOVE_VALUE                         = 5;
    
    /*
     * The value of the each square closer a black piece is to the king.
     */
    public static final int      KING_DISTANCE_VALUE                = 18;
    
    /**
     * The value added to the king per move able to be made.
     */
    public static final int      KING_MOVE_VALUE                    = 150;
    
    /**
     * The value of an unblocked view of a corner for the king.
     */
    public static final int      KING_CORNER_MOVE_VALUE             = 700;
    
    /**
     * Static constructor.
     */
    static
    {
        //@formatter:off
        REGION_CENTER = new BitBoard(
                0b000010000_000000000_000000000,
                0b000101000_001010100_000101000,
                0b000000000_000000000_000010000
        );
        REGION_EDGE = new BitBoard(
                0b000000000_000000000_000111000,
                0b100000001_100000001_100000001,
                0b000111000_000000000_000000000
        );
        REGION_CORE = new BitBoard(
                0b011101110_001111100_000000000,
                0b011000110_010000010_011000110,
                0b000000000_001111100_011101110
        );
        REGION_CORNER_ADJACENT = new BitBoard(
                0b000000000_100000001_010000010,
                0b000010000_000101000_000010000,
                0b010000010_100000001_000000000
        );
        REGION_CORNER_BLOCK = new BitBoard(
                0b100000001_000000000_001000100,
                0b000000000_000000000_000000000,
                0b001000100_000000000_100000001
        );
        REGION_CORNER_DIAGONAL = new BitBoard(
                0b000000000_010000010_000000000,
                0b000000000_000000000_000000000,
                0b000000000_010000010_000000000
        );
        //@formatter:on
        
        SQUARE_VALUES = new int[3][81];
        for (int i = 0; i < 81; i++)
        {
            if (REGION_CENTER.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_CENTER_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_CENTER_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_CENTER_VALUE_KING;
            }
            if (REGION_EDGE.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_EDGE_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_EDGE_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_EDGE_VALUE_KING;
            }
            if (REGION_CORE.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_CORE_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_CORE_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_CORE_VALUE_KING;
            }
            if (REGION_CORNER_ADJACENT.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_CORNER_ADJACENT_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_CORNER_ADJACENT_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_CORNER_ADJACENT_VALUE_KING;
            }
            if (REGION_CORNER_BLOCK.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_CORNER_BLOCK_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_CORNER_BLOCK_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_CORNER_BLOCK_VALUE_KING;
            }
            if (REGION_CORNER_DIAGONAL.getValue(i))
            {
                SQUARE_VALUES[0][i] += REGION_CORNER_DIAGONAL_VALUE_BLACK;
                SQUARE_VALUES[1][i] += REGION_CORNER_DIAGONAL_VALUE_WHITE;
                SQUARE_VALUES[2][i] += REGION_CORNER_DIAGONAL_VALUE_KING;
            }
        }
    }
    
    private BitBoard[] m_blackLegalMoves    = new BitBoard[16];
    private BitBoard   m_allBlackLegalMoves = new BitBoard();
    private BitBoard[] m_whiteLegalMoves    = new BitBoard[8];
    private BitBoard   m_kingLegalMoves     = new BitBoard();
    private BitBoard   m_allWhiteLegalMoves = new BitBoard();
    private BitBoard   m_pieces             = new BitBoard();
    private BitBoard   m_piecesReflected    = new BitBoard();
    private BitBoard   m_kingExitCorner     = new BitBoard();
    
    /**
     * Constructor.
     */
    public Evaluator()
    {
        for (int i = 0; i < m_blackLegalMoves.length; i++)
        {
            m_blackLegalMoves[i] = new BitBoard();
        }
        for (int i = 0; i < m_whiteLegalMoves.length; i++)
        {
            m_whiteLegalMoves[i] = new BitBoard();
        }
    }
    
    /**
     * Gets the value of the board for black.
     * 
     * @param state
     *            The state to evaluate.
     * @param turnPlayer
     *            The turn player.
     * @return The value of the board for black.
     */
    public short evaluate(State state, int turnPlayer)
    {
        state.updatePieceLists();
        
        m_pieces.copy(state.black);
        m_pieces.or(state.white);
        m_pieces.set(state.kingSquare);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        int kingCol = state.kingSquare % 9;
        int kingRow = state.kingSquare / 9;
        
        // get the legal moves for each black piece
        int blackSquareValues = 0;
        int blackMovableSquares = 0;
        int blackKingDistance = 0;
        for (int i = 0; i < state.blackCount; i++)
        {
            int square = state.blackPieces[i];
            blackSquareValues += SQUARE_VALUES[0][square];
            BitBoard legalMoves = m_blackLegalMoves[i];
            BitBoardConsts.getLegalMoves(square, false, m_pieces, m_piecesReflected, legalMoves);
            m_allBlackLegalMoves.or(legalMoves);
            blackMovableSquares += legalMoves.cardinality();
            blackKingDistance += Math.abs(kingCol - (square % 9)) + Math.abs(kingRow - (square / 9));
        }
        if (state.blackCount > 0)
        {
            blackKingDistance /= state.blackCount;
        }
        
        // get the legal moves for each white piece
        int whiteSquareValues = 0;
        int whiteMovableSquares = 0;
        for (int i = 0; i < state.whiteCount; i++)
        {
            int square = state.whitePieces[i];
            whiteSquareValues += SQUARE_VALUES[1][square];
            BitBoard legalMoves = m_whiteLegalMoves[i];
            BitBoardConsts.getLegalMoves(square, false, m_pieces, m_piecesReflected, legalMoves);
            m_allWhiteLegalMoves.or(legalMoves);
            whiteMovableSquares += legalMoves.cardinality();
        }
        
        // get the legal moves for the king
        whiteSquareValues += SQUARE_VALUES[2][state.kingSquare];
        BitBoardConsts.getLegalMoves(state.kingSquare, false, m_pieces, m_piecesReflected, m_kingLegalMoves);
        m_allWhiteLegalMoves.or(m_kingLegalMoves);
        int kingMovableSquares = m_kingLegalMoves.cardinality();
        
        // calculate the value of the board for black
        int valueForBlack = -6000;
        
        // get the number of threating moves each player can make
        // int blackThreats = countThreats(state.white, state.black,
        // m_allBlackLegalMoves);
        // int whiteThreats = countThreats(state.black, state.white,
        // m_allWhiteLegalMoves);
        // valueForBlack += (blackThreats - whiteThreats) * THREAT_VALUE;
        
        // black does better when it's pieces are near to the king
        valueForBlack -= blackKingDistance * KING_DISTANCE_VALUE;
        
        // the ability to make more moves is valuable, especially for the king
        valueForBlack += (blackMovableSquares * MOVE_VALUE);
        valueForBlack -= (whiteMovableSquares * MOVE_VALUE) + (kingMovableSquares * KING_MOVE_VALUE);
        
        // get the value for the pieces based on their current squares
        valueForBlack += (blackSquareValues - whiteSquareValues) * SQUARE_VALUE_MULTIPIER;
        
        // get the piece difference
        valueForBlack += state.blackCount * BLACK_PIECE_VALUE;
        valueForBlack -= state.whiteCount * WHITE_PIECE_VALUE;
        
        // black does poorly if the king can reach a corner
        m_kingExitCorner.copy(m_kingLegalMoves);
        m_kingExitCorner.and(BitBoardConsts.corners);
        switch (m_kingExitCorner.cardinality())
        {
            case 1: // very risky for black, forced to make a move
                if (turnPlayer == StateExplorer.BLACK)
                {
                    valueForBlack -= KING_CORNER_MOVE_VALUE;
                }
                else
                {
                    valueForBlack = -WIN_VALUE;
                }
                break;
            case 2: // game over for black
                valueForBlack = -WIN_VALUE;
                break;
        }
        
        // check that the value is in the required range
        if (valueForBlack < -Short.MAX_VALUE || Short.MAX_VALUE < valueForBlack)
        {
            Log.info("Board evaluation is not in range: " + valueForBlack);
        }
        return (short)valueForBlack;
    }
    
    private BitBoard m_threats = new BitBoard();
    
    /**
     * Counts the number of moves that the opponent can make to remove a piece.
     */
    private int countThreats(BitBoard pieces, BitBoard opponentPieces, BitBoard opponentMoves)
    {
        int threatCount = 0;
        
        m_threats.copy(pieces);
        m_threats.shiftLeftOne();
        m_threats.and(opponentPieces);
        m_threats.shiftRightTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);
        
        m_threats.copy(pieces);
        m_threats.shiftRightOne();
        m_threats.and(opponentPieces);
        m_threats.shiftLeftTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);
        
        m_threats.copy(pieces);
        m_threats.shiftUpOne();
        m_threats.and(opponentPieces);
        m_threats.shiftDownTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);
        
        m_threats.copy(pieces);
        m_threats.shiftDownOne();
        m_threats.and(opponentPieces);
        m_threats.shiftUpTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);
        
        return threatCount;
    }
}
