package student_player;

import java.util.Arrays;
import java.util.Random;

import boardgame.Board;
import coordinates.Coordinates;
import tablut.TablutBoardState;

/**
 * Implements the game state. This replaces and extends the functionality of
 * TablutBoardState, but does things much more optimally. In general this is
 * achieved by doing the following:
 * 
 * 1) Using bitboards to compute board heuristics and such in constant time. 2)
 * Not checking for things like out of bound indices. The code should never
 * supply invalid values anyways. 3) Using primitive types where possible. This
 * keeps memory accesses to a minimum. 4) Never instantiating objects while
 * processing the state, except where absolutely neccessary.
 * 
 * Instead of cloning states, a single state should be used to explore the
 * search tree by making and unmaking moves as states are explored. This is
 * keeps pressure off of the garbage collector and should improve the cache hit
 * rate.
 * 
 * As well, states can determine the transformation required to get to a
 * canonical state that is shared by any symmetric boards using the symmetries
 * of the square.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class State
{
    private static final int      BLACK        = 0;
    private static final int      WHITE        = 1;
    private static final int      MAX_TURNS    = 100;
    private static final int      NOT_ON_BOARD = -1;
    private static final long[][] HASH_KEYS    = new long[3][81];
    
    // private static BitBoard[] m_legalMovesForPiece = new BitBoard[16];
    // private static BitBoard m_legalMovesForKing = new BitBoard();
    // private static BitBoard m_allLegalMoves = new BitBoard();
    
    /**
     * The static constructor. Generates a table containing unique hashes for black,
     * white, and king piece when on each board square. This is used when computing
     * board hashes.
     */
    static
    {
        Random rand = new Random(100);
        for (int i = 0; i < 81; i++)
        {
            HASH_KEYS[0][i] = rand.nextLong();
            HASH_KEYS[1][i] = rand.nextLong();
            HASH_KEYS[2][i] = rand.nextLong();
        }
        
        // for (int i = 0; i < m_legalMovesForPiece.length; i++)
        // {
        // m_legalMovesForPiece[i] = new BitBoard();
        // }
    }
    
    private int      m_turnNumber;
    private int      m_turnPlayer;
    private int      m_winner;
    
    private BitBoard m_pieces          = new BitBoard();
    private BitBoard m_piecesReflected = new BitBoard();
    private BitBoard m_black           = new BitBoard();
    private BitBoard m_whiteWithKing   = new BitBoard();
    private BitBoard m_whiteNoKing     = new BitBoard();
    
    private int[]    m_blackPieces     = new int[16];
    private int      m_blackPieceCount = 0;
    private int[]    m_whitePieces     = new int[8];
    private int      m_whitePieceCount = 0;
    private int      m_kingSquare      = NOT_ON_BOARD;
    
    /**
     * Creates a board with a given state.
     * 
     * @param turn The current turn.
     * @param player The current player.
     * @param black The positions of all black pieces on the board.
     * @param white The positions of all white's normal pieces on the board.
     * @param king The position of the king.
     */
    public State(int turn, int player, BitBoard black, BitBoard white, int king)
    {
        m_turnNumber = turn;
        m_turnPlayer = player;
        m_winner = Board.NOBODY;
        
        m_black.copy(black);
        m_whiteNoKing.copy(white);
        m_whiteWithKing.copy(white);
        m_whiteWithKing.set(king);
        m_kingSquare = king;
        
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        generatePiecesLists();
    }
    
    /**
     * Initializes the state from a TablutBoardState.
     * 
     * @param state
     *            The state to initialize from.
     */
    public State(TablutBoardState state)
    {
        // increment turn with every move rather then every other move
        m_turnNumber = ((2 * state.getTurnNumber()) - 1) + state.getTurnPlayer();
        m_turnPlayer = state.getTurnPlayer();
        m_winner = state.getWinner();
        
        // copy all board squares
        for (int i = 0; i < 81; i++)
        {
            int row = i / 9;
            int col = i % 9;
            
            switch (state.getPieceAt(Coordinates.get(col, row)))
            {
                case BLACK:
                    m_black.set(i);
                    
                    m_blackPieces[m_blackPieceCount++] = i;
                    
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);
                    break;
                case WHITE:
                    
                    m_whiteNoKing.set(i);
                    m_whiteWithKing.set(i);
                    
                    m_whitePieces[m_whitePieceCount++] = i;
                    
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);
                    break;
                case KING:
                    m_whiteWithKing.set(i);
                    
                    m_kingSquare = i;
                    
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);
                    break;
                default:
                    break;
            }
        }
    }
    
    /**
     * Checks if this state is the end of the game.
     */
    public boolean isTerminal()
    {
        return m_winner != Board.NOBODY || m_turnNumber > MAX_TURNS;
    }
    
    /**
     * Gets the number of moves left until the game ends.
     */
    public int getRemainingMoves()
    {
        return isTerminal() ? 0 : (MAX_TURNS - m_turnNumber + 1);
    }
    
    private BitBoard m_assistingPieces = new BitBoard();
    private BitBoard m_capturedPieces  = new BitBoard();
    private BitBoard m_capturedKing    = new BitBoard();
    private BitBoard m_escapedKing     = new BitBoard();
    
    /**
     * Applies a move to the state.
     * 
     * @param move
     *            The move to make. The index of the source square is packed into
     *            bits 0-6. The index of the destination square is packed in bits
     *            7-13.
     * @return The move with the captured pieces encoded into the higher order bits.
     */
    public long makeMove(int move)
    {
        long fullMove = move;
        
        // extract the board squares moved from and to from the move integer.
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        // move the piece on all relevant boards
        BitBoard playerPieces;
        BitBoard opponentPieces;
        
        if (m_turnPlayer == BLACK)
        {
            playerPieces = m_black;
            opponentPieces = m_whiteWithKing;
        }
        else
        {
            playerPieces = m_whiteWithKing;
            opponentPieces = m_black;
            
            if (m_kingSquare == from)
            {
                m_kingSquare = to;
            }
            else
            {
                m_whiteNoKing.clear(fromCol, fromRow);
                m_whiteNoKing.set(toCol, toRow);
            }
        }
        
        playerPieces.clear(fromCol, fromRow);
        playerPieces.set(toCol, toRow);
        
        // find pieces that can help the moved piece make a capture
        m_assistingPieces.copy(playerPieces);
        m_assistingPieces.or(BitBoardConsts.corners);
        m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
        
        // find captured pieces
        m_capturedPieces.copy(m_assistingPieces);
        m_capturedPieces.toNeighbors();
        m_capturedPieces.and(opponentPieces);
        m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
        
        int kingSquare = m_kingSquare;
        
        // enforce the special rules for capturing the king
        if (m_turnPlayer == BLACK)
        {
            m_capturedKing.clear();
            m_capturedKing.set(m_kingSquare);
            m_capturedKing.and(m_capturedPieces);
            m_capturedKing.and(BitBoardConsts.king4Surround);
            
            // is the king threatened while on the safer center squares?
            if (!m_capturedKing.isEmpty())
            {
                if (!m_capturedKing.equals(BitBoardConsts.center))
                {
                    // the king is not on the center tile of the safer squares, and thus can't be
                    // captured, so remove it from the captured pieces set
                    m_capturedPieces.clear(m_kingSquare);
                }
                else
                {
                    // the king is on the center tile, so if it is surrounded by four enemies let it
                    // be captured, otherwise remove it from the captured pieces set
                    m_capturedKing.or(m_black);
                    m_capturedKing.and(BitBoardConsts.king4Surround);
                    
                    if (!m_capturedKing.equals(BitBoardConsts.king4Surround))
                    {
                        // king should not be captured
                        m_capturedPieces.clear(m_kingSquare);
                    }
                }
            }
            
            // if the king was captured, black wins
            if (m_capturedPieces.getValue(m_kingSquare))
            {
                m_winner = BLACK;
                m_kingSquare = NOT_ON_BOARD;
                
                fullMove |= (((long)kingSquare) << 39) | (1L << 38);
            }
            
            // remove captured pieces extra white boards
            m_whiteNoKing.andNot(m_capturedPieces);
        }
        else
        {
            // check if king is in the corner
            m_escapedKing.clear();
            m_escapedKing.set(m_kingSquare);
            m_escapedKing.and(BitBoardConsts.corners);
            
            if (!m_escapedKing.isEmpty())
            {
                // king got away, white wins
                m_winner = WHITE;
            }
        }
        
        // add any captured pieces to the move information
        if (!m_capturedPieces.isEmpty())
        {
            int shift = 14;
            int num = m_capturedPieces.d0;
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
                    if (index != kingSquare)
                    {
                        fullMove |= (((long)index) << (shift + 1)) | (1L << shift);
                        shift += 8;
                    }
                }
                else
                {
                    switch (stage)
                    {
                        case 0:
                            num = m_capturedPieces.d1;
                            index = 26;
                            break;
                        case 1:
                            num = m_capturedPieces.d2;
                            index = 53;
                            break;
                    }
                    stage++;
                }
            }
        }
        
        // remove captured pieces from the board
        opponentPieces.andNot(m_capturedPieces);
        
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        generatePiecesLists();
        
        // increment the turn
        m_turnNumber++;
        m_turnPlayer = (m_turnPlayer + 1) % 2;
        
        // return the move packed with the captured pieces
        return fullMove;
    }
    
    /**
     * Undoes a move applied to this state.
     * 
     * @param move
     *            The move to unmake. The bits for the move contain the following information.
     * @formatter:off
     *      bits 0-6     the board square index of square moved from
     *      bits 7-13    the board square index of square moved to
     *      bits 14      set if a piece was captured
     *      bits 15-21   the board square index of the piece if captured
     *      bits 22      set if a second piece was captured
     *      bits 23-29   the board square index of the piece if captured
     *      bits 30      set if a third piece was captured
     *      bits 31-37   the board square index of the piece if captured
     *      bits 38      set if the king was captured
     *      bits 39-45   the board square index of the king if captured
     * @formatter:on
     */
    public void unmakeMove(long move)
    {
        // decrement the turn
        m_turnNumber--;
        m_turnPlayer = (m_turnPlayer + 1) % 2;
        m_winner = Board.NOBODY;
        
        // extract the board squares moved from and to from the move integer.
        int from = (int)(move & 0x7F);
        int to = (int)((move >> 7) & 0x7F);
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        if (m_turnPlayer == BLACK)
        {
            // unmove the piece
            m_black.clear(toCol, toRow);
            m_black.set(fromCol, fromRow);
            
            // add back any captured pieces
            int capture0 = (int)((move >> 14) & 0xFF);
            if (capture0 > 0)
            {
                m_whiteWithKing.set(capture0 >> 1);
                
                int capture1 = (int)((move >> 22) & 0xFF);
                if (capture1 > 0)
                {
                    m_whiteWithKing.set(capture1 >> 1);
                    
                    int capture2 = (int)((move >> 30) & 0xFF);
                    if (capture2 > 0)
                    {
                        m_whiteWithKing.set(capture2 >> 1);
                    }
                }
            }
            
            // add back the king if it was captured
            int kingCapture = (int)((move >> 38) & 0xFF);
            if (kingCapture > 0)
            {
                kingCapture >>= 1;
                m_whiteWithKing.set(kingCapture);
                m_kingSquare = kingCapture;
            }
        }
        else
        {
            // unmove the piece
            m_whiteWithKing.clear(toCol, toRow);
            m_whiteWithKing.set(fromCol, fromRow);
            
            if (m_kingSquare == to)
            {
                m_kingSquare = from;
            }
            
            // add back any captured pieces
            int capture0 = (int)((move >> 14) & 0xFF);
            if (capture0 > 0)
            {
                m_black.set(capture0 >> 1);
                
                int capture1 = (int)((move >> 22) & 0xFF);
                if (capture1 > 0)
                {
                    m_black.set(capture1 >> 1);
                    
                    int capture2 = (int)((move >> 30) & 0xFF);
                    if (capture2 > 0)
                    {
                        m_black.set(capture2 >> 1);
                    }
                }
            }
        }
        
        // update remaining bitboards
        m_whiteNoKing.copy(m_whiteWithKing);
        m_whiteNoKing.clear(m_kingSquare);
        
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        generatePiecesLists();
    }
    
    /**
     * Updates the list of squares black and white pieces are on.
     */
    private void generatePiecesLists()
    {
        // get the board squares of all black pieces
        m_blackPieceCount = 0;
        int num = m_black.d0;
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
                m_blackPieces[m_blackPieceCount++] = index;
            }
            else
            {
                switch (stage)
                {
                    case 0:
                        num = m_black.d1;
                        index = 26;
                        break;
                    case 1:
                        num = m_black.d2;
                        index = 53;
                        break;
                }
                stage++;
            }
        }
        
        // get the board squares of all non-king white pieces
        m_whitePieceCount = 0;
        num = m_whiteNoKing.d0;
        stage = 0;
        index = -1;
        while (stage < 3)
        {
            int shiftIndex = Integer.numberOfTrailingZeros(num);
            if (shiftIndex != 32)
            {
                shiftIndex++;
                num = num >> shiftIndex;
                index += shiftIndex;
                m_whitePieces[m_whitePieceCount++] = index;
            }
            else
            {
                switch (stage)
                {
                    case 0:
                        num = m_whiteNoKing.d1;
                        index = 26;
                        break;
                    case 1:
                        num = m_whiteNoKing.d2;
                        index = 53;
                        break;
                }
                stage++;
            }
        }
    }
    
    /**
     * Gets the value of this board for the player whose turn it is.
     */
    public int evaluate()
    {
        if (isTerminal())
        {
            if (m_winner != Board.NOBODY)
            {
                // a win is infinite utility for player, loss is negative infinity
                return (m_turnPlayer == m_winner ? 1 : -1) * (1 << 30);
            }
            // draw is 0 utility for both players
            return 0;
        }
        else
        {
            // calculate the value of the board for black
            int valueForBlack = 0;
            
            // the heuristic value of a piece
            final int PIECE_VALUE = 1000;
            
            valueForBlack += (m_blackPieceCount - (m_whitePieceCount + 1)) * PIECE_VALUE;
            
            for (int i = 0; i < m_blackPieceCount; i++)
            {
                int index = m_blackPieces[i];
                int row = index / 9;
                int col = index % 9;
                
                valueForBlack -= (Math.abs(4 - col) + Math.abs(4 - row));
            }
            
            // flip the board value if the turn player is white
            return m_turnPlayer == BLACK ? valueForBlack : -valueForBlack;
        }
    }
    
    /**
     * Computes a hash for the current board. Any boards symmetrically will have the
     * same hash.
     */
    public long calculateHash()
    {
        long hash = 0;
        
        calcCanonicalTransform();
        
        for (int i = 0; i < m_blackPieceCount; i++)
        {
            hash ^= HASH_KEYS[0][BoardUtils.TRANSFORMED_INDICIES[m_canonicalTransform][m_blackPieces[i]]];
        }
        for (int i = 0; i < m_whitePieceCount; i++)
        {
            hash ^= HASH_KEYS[1][BoardUtils.TRANSFORMED_INDICIES[m_canonicalTransform][m_whitePieces[i]]];
        }
        if (m_kingSquare != NOT_ON_BOARD)
        {
            hash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[m_canonicalTransform][m_kingSquare]];
        }
        return hash;
    }
    
    private int[] m_legalMoves = new int[183];
    private int   m_legalMoveCount;
    
    /**
     * Finds all moves that the player can currently make.
     */
    public int[] getAllLegalMoves()
    {
        m_legalMoveCount = 0;
        
        if (m_turnPlayer == BLACK)
        {
            for (int i = 0; i < m_blackPieceCount; i++)
            {
                getMoves(m_blackPieces[i], false);
            }
        }
        else
        {
            for (int i = 0; i < m_whitePieceCount; i++)
            {
                getMoves(m_whitePieces[i], false);
            }
            getMoves(m_kingSquare, true);
        }
        
        return Arrays.copyOf(m_legalMoves, m_legalMoveCount);
    }
    
    /**
     * Gets the legal moves that may be made for a piece.
     * 
     * @param square
     *            The board square index of the piece.
     * @param isKing
     *            Indicates if this piece is the king.
     */
    private void getMoves(int square, boolean isKing)
    {
        int row = square / 9;
        int col = square % 9;
        
        int rowMoves = BitBoardConsts.getLegalMovesHorizontal(col, row, isKing, m_pieces);
        int colMoves = BitBoardConsts.getLegalMovesVertical(col, row, isKing, m_piecesReflected);
        
        /*
         * Extract the minimum and maximum bounds of the legal moves from the move
         * integers packed into bits 9-12 and 13-16 respectively.
         */
        int baseIndex = row * 9;
        for (int i = ((rowMoves >> 9) & 0xf); i <= ((rowMoves >> 13) & 0xf); i++)
        {
            if ((rowMoves & (1 << i)) != 0)
            {
                m_legalMoves[m_legalMoveCount++] = square | ((baseIndex + i) << 7);
            }
        }
        
        for (int i = ((colMoves >> 9) & 0xf); i <= ((colMoves >> 13) & 0xf); i++)
        {
            if ((colMoves & (1 << i)) != 0)
            {
                m_legalMoves[m_legalMoveCount++] = square | (((i * 9) + col) << 7);
            }
        }
    }
    
    private int      m_canonicalTransform;
    private BitBoard m_canonical = new BitBoard();
    private BitBoard m_current   = new BitBoard();
    private boolean  m_duplicate;
    
    /**
     * Computes the transformation for this board to a canonical board such that any
     * symmetrical boards are guarenteed to have the same canonical board.
     */
    private void calcCanonicalTransform()
    {
        if (calcCanonicalBoardTransform(m_black))
        {
            if (calcCanonicalBoardTransform(m_whiteNoKing))
            {
                calcCanonicalKingTransform();
            }
        }
    }
    
    /**
     * Gets the king tile with the greatest value out of the symmetrical king tiles,
     * as this may be used to represent the canonical form.
     * 
     * @return True if this board is not symmetrically unique.
     */
    private boolean calcCanonicalKingTransform()
    {
        boolean duplicate = false;
        m_canonicalTransform = 0;
        
        // only evaluate if king is on the board
        if (m_kingSquare != NOT_ON_BOARD)
        {
            int canonicalKing = m_kingSquare;
            
            for (int i = 1; i < 8; i++)
            {
                int transformedKing = BoardUtils.TRANSFORMED_INDICIES[i][m_kingSquare];
                int compare = transformedKing - canonicalKing;
                
                if (compare == 0)
                {
                    duplicate = true;
                }
                else if (compare > 0)
                {
                    canonicalKing = transformedKing;
                    duplicate = false;
                }
            }
        }
        return duplicate;
    }
    
    /**
     * Gets the board with the greatest value out of the symmetrical boards, as this
     * may be used to represent the canonical form.
     * 
     * @param board
     *            The board to get the canonical variant of.
     * @return True if this board is not symmetrically unique.
     */
    private boolean calcCanonicalBoardTransform(BitBoard board)
    {
        m_canonical.copy(board);
        m_current.copy(board);
        m_canonicalTransform = 0;
        m_duplicate = false;
        
        m_current.mirrorVertical();
        compareTransformed(1);
        
        m_current.mirrorHorzontal();
        compareTransformed(2);
        
        m_current.mirrorVertical();
        compareTransformed(3);
        
        m_current.mirrorDiagonal();
        compareTransformed(4);
        
        m_current.mirrorVertical();
        compareTransformed(5);
        
        m_current.mirrorHorzontal();
        compareTransformed(6);
        
        m_current.mirrorVertical();
        compareTransformed(7);
        
        return m_duplicate;
    }
    
    /**
     * Tests if the current bitboard in the canonical board finding process has a
     * greater value the maximum (canonical) bitboard found so far. If so, the
     * canonical bitboard and transformation are updated.
     * 
     * @param transformCode
     *            The transform code that will arrive at the current BitBoard state
     *            from the original.
     */
    private void compareTransformed(int transformCode)
    {
        switch (BitBoard.comparaeTo(m_current, m_canonical))
        {
            case 0:
                m_duplicate = true;
                break;
            case 1:
                m_canonical.copy(m_current);
                m_canonicalTransform = transformCode;
                m_duplicate = false;
                break;
        }
    }
    
    @Override
    public String toString()
    {
        String str = System.lineSeparator();
        str += "Turn " + m_turnNumber + ", ";
        
        if (isTerminal())
        {
            str += "Game Over: ";
            if (m_winner != Board.NOBODY)
            {
                str += (m_winner == BLACK ? "Black Won" : "White Won");
            }
            else
            {
                str += "Draw";
            }
        }
        else
        {
            str += "Next Move: " + (m_turnPlayer == BLACK ? "Black" : "White");
        }
        
        str += System.lineSeparator();
        str += "Black Pieces: " + m_black.cardinality() + " ";
        str += "White Pieces: " + m_whiteWithKing.cardinality() + " ";
        
        str += System.lineSeparator();
        for (int row = 0; row < 9; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                int square = (row * 9) + col;
                
                if (m_black.getValue(square))
                {
                    str += 'b';
                }
                else if (m_whiteNoKing.getValue(square))
                {
                    str += 'w';
                }
                else if (m_kingSquare == square)
                {
                    str += 'K';
                }
                else
                {
                    str += '.';
                }
            }
            str += System.lineSeparator();
        }
        return str;
    }
}
