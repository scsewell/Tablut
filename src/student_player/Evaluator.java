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
    public static final short     WIN_VALUE                          = 30000;
    
    /**
     * The mapping from piece type and location to net value.
     */
    public static final int[][]   SQUARE_VALUES;
    
    /**
     * The diamond shaped region at the center of the board where white starts.
     */
    private static final BitBoard REGION_CENTER;
    private static final int      REGION_CENTER_VALUE_BLACK          = -3;
    private static final int      REGION_CENTER_VALUE_WHITE          = -2;
    private static final int      REGION_CENTER_VALUE_KING           = 0;
    
    /**
     * The regions of the board at the middle of the edges of the board.
     */
    private static final BitBoard REGION_EDGE;
    private static final int      REGION_EDGE_VALUE_BLACK            = -3;
    private static final int      REGION_EDGE_VALUE_WHITE            = -2;
    private static final int      REGION_EDGE_VALUE_KING             = 5;
    
    /**
     * The region between the edges of the board and the center region.
     */
    private static final BitBoard REGION_CORE;
    private static final int      REGION_CORE_VALUE_BLACK            = 3;
    private static final int      REGION_CORE_VALUE_WHITE            = 3;
    private static final int      REGION_CORE_VALUE_KING             = 3;
    
    /**
     * The board squares vertically or horizontally adjacent to the corner squares.
     */
    private static final BitBoard REGION_CORNER_ADJACENT;
    private static final int      REGION_CORNER_ADJACENT_VALUE_BLACK = -6;
    private static final int      REGION_CORNER_ADJACENT_VALUE_WHITE = -15;
    private static final int      REGION_CORNER_ADJACENT_VALUE_KING  = -10;
    
    /**
     * The squares vertically or horizontally spaced one square away from the corner
     * squares.
     */
    private static final BitBoard REGION_CORNER_BLOCK;
    private static final int      REGION_CORNER_BLOCK_VALUE_BLACK    = 10;
    private static final int      REGION_CORNER_BLOCK_VALUE_WHITE    = 0;
    private static final int      REGION_CORNER_BLOCK_VALUE_KING     = 6;
    
    /**
     * The board squares diagonally adjacent to the corner squares.
     */
    private static final BitBoard REGION_CORNER_DIAGONAL;
    private static final int      REGION_CORNER_DIAGONAL_VALUE_BLACK = 10;
    private static final int      REGION_CORNER_DIAGONAL_VALUE_WHITE = 2;
    private static final int      REGION_CORNER_DIAGONAL_VALUE_KING  = 5;
    
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
    
    private final int        squareValueMultiplier;
    private final int        blackPieceValue;
    private final int        whitePieceValue;
    private final int        threatValue;
    private final int        moveValue;
    private final int        kingMoveValue;
    private final int        kingDistanceValue;
    private final int        kingCornerMoveValue;
    
    private final BitBoard[] m_blackLegalMoves    = new BitBoard[16];
    private final BitBoard   m_allBlackLegalMoves = new BitBoard();
    private final BitBoard[] m_whiteLegalMoves    = new BitBoard[8];
    private final BitBoard   m_kingLegalMoves     = new BitBoard();
    private final BitBoard   m_allWhiteLegalMoves = new BitBoard();
    private final BitBoard   m_pieces             = new BitBoard();
    private final BitBoard   m_piecesReflected    = new BitBoard();
    private final BitBoard   m_kingExitCorner     = new BitBoard();
    private final BitBoard   m_threats            = new BitBoard();
    
    /**
     * Contructs a new Evaluator instance with the provided weightings
     * 
     * @param squareValue
     *            The weighting of the square positional value.
     * @param blackPieceValue
     *            The value of each black piece.
     * @param whitePieceValue
     *            The value of each white piece.
     * @param threatValue
     *            The value of moves that could capture a piece.
     * @param moveValue
     *            The value added to a peice per move able to be made.
     * @param kingMoveValue
     *            The value added to the king per move able to be made.
     * @param kingDistanceValue
     *            The value of the each square closer a black piece is to the king.
     * @param kingCornerMoveValue
     *            The value of an unblocked view of a corner for the king.
     */
    public Evaluator(
        int squareValue,
        int blackPieceValue,
        int whitePieceValue,
        int threatValue,
        int moveValue,
        int kingMoveValue,
        int kingDistanceValue,
        int kingCornerMoveValue)
    {
        this.squareValueMultiplier = squareValue;
        this.blackPieceValue = blackPieceValue;
        this.whitePieceValue = whitePieceValue;
        this.threatValue = threatValue;
        this.moveValue = moveValue;
        this.kingMoveValue = kingMoveValue;
        this.kingDistanceValue = kingDistanceValue;
        this.kingCornerMoveValue = kingCornerMoveValue;
        
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
        int valueForBlack = -10000;
        
        // get the number of threating moves each player can make
        valueForBlack += countThreats(state.white, state.black, m_allBlackLegalMoves) * threatValue;
        valueForBlack -= countThreats(state.black, state.white, m_allWhiteLegalMoves) * threatValue;
        
        // black does better when it's pieces are near to the king
        valueForBlack -= blackKingDistance * kingDistanceValue;
        
        // the ability to make more moves is valuable, especially for the king
        valueForBlack += (blackMovableSquares * moveValue);
        valueForBlack -= (whiteMovableSquares * moveValue) + (kingMovableSquares * kingMoveValue);
        
        // get the value for the pieces based on their current squares
        valueForBlack += (blackSquareValues - whiteSquareValues) * squareValueMultiplier;
        
        // get the piece difference
        valueForBlack += state.blackCount * blackPieceValue;
        valueForBlack -= state.whiteCount * whitePieceValue;
        
        // black does poorly if the king can reach a corner
        m_kingExitCorner.copy(m_kingLegalMoves);
        m_kingExitCorner.and(BitBoardConsts.corners);
        switch (m_kingExitCorner.cardinality())
        {
            case 1:
                if (turnPlayer == StateExplorer.BLACK)
                {
                    // very risky for black, forced to make a move
                    valueForBlack -= kingCornerMoveValue;
                }
                else
                {
                    // black loses next turn
                    valueForBlack = -WIN_VALUE;
                }
                break;
            case 2:
                // black loses next turn
                valueForBlack = -WIN_VALUE;
                break;
        }
        
        return (turnPlayer == StateExplorer.BLACK) ? (short)valueForBlack : (short)-valueForBlack;
    }
    
    /**
     * Counts the number of moves that the opponent can make to remove a piece.
     */
    private int countThreats(BitBoard pieces, BitBoard opponentPieces, BitBoard opponentMoves)
    {
        int threatCount = 0;
        
        // potential captures from squares right
        m_threats.copy(pieces);
        m_threats.shiftLeftOne();
        m_threats.and(opponentPieces);
        m_threats.shiftRightTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);

        // potential captures from squares left
        m_threats.copy(pieces);
        m_threats.shiftRightOne();
        m_threats.and(opponentPieces);
        m_threats.shiftLeftTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);

        // potential captures from squares above
        m_threats.copy(pieces);
        m_threats.shiftUpOne();
        m_threats.and(opponentPieces);
        m_threats.shiftDownTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);

        // potential captures from squares below
        m_threats.copy(pieces);
        m_threats.shiftDownOne();
        m_threats.and(opponentPieces);
        m_threats.shiftUpTwo();
        threatCount += BitBoard.andCount(m_threats, opponentMoves);
        
        return threatCount;
    }
}
