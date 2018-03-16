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
 * The state stores all moves that have been made up to the current state,
 * allowing many moves to be made and unmade easily.
 * 
 * As well, states can determine the transformation required to get to a
 * canonical state that is shared by any symmetric boards. This is used to make
 * symmetric boards share a hash so they are considered equivalent.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class State
{
    private static final int      BLACK        = 0;
    private static final int      WHITE        = 1;
    private static final int      MAX_MOVES    = 100;
    private static final int      NOT_ON_BOARD = -1;
    
    private static final long[][] HASH_KEYS    = new long[3][81];
    
    /**
     * The static constructor. Prepares the Zorbist hash values, a table containing
     * unique hashes for a black, white, or king piece for each board square. This
     * is used when computing board hashes.
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
    }
    
    // stacks storing infromation about ancestor moves
    private long[]    m_moves               = new long[MAX_MOVES + 2];
    private long[]    m_hashes              = new long[MAX_MOVES + 2];
    private int[]     m_canonicalTransforms = new int[MAX_MOVES + 2];
    private int[][][] m_pieceSquares        = new int[MAX_MOVES + 2][2][16];
    private int[][]   m_pieceCounts         = new int[MAX_MOVES + 2][2];
    
    // the current board state
    private int       m_turnNumber;
    private int       m_turnPlayer;
    private int       m_winner;
    
    private BitBoard  m_pieces              = new BitBoard();
    private BitBoard  m_piecesReflected     = new BitBoard();
    private BitBoard  m_black               = new BitBoard();
    private BitBoard  m_whiteWithKing       = new BitBoard();
    private BitBoard  m_whiteNoKing         = new BitBoard();
    private int       m_kingSquare          = NOT_ON_BOARD;
    
    /**
     * Creates a board with a given state.
     * 
     * @param turn
     *            The current turn.
     * @param player
     *            The current player.
     * @param black
     *            The positions of all black pieces on the board.
     * @param white
     *            The positions of all white's normal pieces on the board.
     * @param king
     *            The position of the king.
     */
    public State(int turn, int player, BitBoard black, BitBoard white, int king)
    {
        m_turnNumber = turn - 1;
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
        
        computePiecesLists();
        
        m_hashes[0] = calculateHash();
        m_canonicalTransforms[0] = calcCanonicalTransform();
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
        m_turnNumber = (2 * (state.getTurnNumber() - 1)) + state.getTurnPlayer();
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
                    break;
                case WHITE:
                    m_whiteNoKing.set(i);
                    m_whiteWithKing.set(i);
                    break;
                case KING:
                    m_kingSquare = i;
                    m_whiteWithKing.set(i);
                    break;
                default:
                    break;
            }
        }
        
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        computePiecesLists();
        
        m_hashes[0] = calculateHash();
        m_canonicalTransforms[0] = calcCanonicalTransform();
    }
    
    /**
     * Checks if this state is the end of the game.
     */
    public boolean isTerminal()
    {
        return m_winner != Board.NOBODY || m_turnNumber == MAX_MOVES;
    }
    
    /**
     * Gets the current turn number.
     */
    public int getTurnNumber()
    {
        return m_turnNumber;
    }
    
    /**
     * Gets the number of moves left until the game ends.
     */
    public int getRemainingMoves()
    {
        return isTerminal() ? 0 : MAX_MOVES - m_turnNumber;
    }
    
    private BitBoard m_assistingPieces   = new BitBoard();
    private BitBoard m_capturedPieces    = new BitBoard();
    private BitBoard m_kingCenterCapture = new BitBoard();
    private BitBoard m_escapedKing       = new BitBoard();
    
    /**
     * Applies a move to the state.
     * 
     * @param move
     *            The move to make. The index of the source square is packed into
     *            bits 0-6. The index of the destination square is packed in bits
     *            7-13.
     */
    public void makeMove(int move)
    {
        long fullMove = move;
        long previousHash = m_hashes[m_turnNumber];
        int previousTransform = m_canonicalTransforms[m_turnNumber];
        
        // extract the board squares moved from and to from the move integer.
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        // move the piece on all relevant boards
        int opponent;
        BitBoard playerPieces;
        BitBoard opponentPieces;
        
        if (m_turnPlayer == BLACK)
        {
            opponent = WHITE;
            playerPieces = m_black;
            opponentPieces = m_whiteWithKing;
            
            // incrementally update the board hash
            previousHash ^= HASH_KEYS[0][BoardUtils.TRANSFORMED_INDICIES[previousTransform][from]];
            previousHash ^= HASH_KEYS[0][BoardUtils.TRANSFORMED_INDICIES[previousTransform][to]];
        }
        else
        {
            opponent = BLACK;
            playerPieces = m_whiteWithKing;
            opponentPieces = m_black;
            
            if (m_kingSquare == from)
            {
                m_kingSquare = to;
                
                // incrementally update the board hash
                previousHash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[previousTransform][from]];
                previousHash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[previousTransform][to]];
            }
            else
            {
                m_whiteNoKing.clear(fromCol, fromRow);
                m_whiteNoKing.set(toCol, toRow);
                
                // incrementally update the board hash
                previousHash ^= HASH_KEYS[1][BoardUtils.TRANSFORMED_INDICIES[previousTransform][from]];
                previousHash ^= HASH_KEYS[1][BoardUtils.TRANSFORMED_INDICIES[previousTransform][to]];
            }
        }
        
        playerPieces.clear(fromCol, fromRow);
        playerPieces.set(toCol, toRow);
        
        // find pieces that can help the moved piece make a capture
        m_assistingPieces.copy(playerPieces);
        m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
        m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
        
        // find captured pieces
        m_capturedPieces.copy(m_assistingPieces);
        m_capturedPieces.toNeighbors();
        m_capturedPieces.and(opponentPieces);
        m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
        
        // enforce the special rules for capturing the king
        if (m_turnPlayer == BLACK)
        {
            m_kingCenterCapture.clear();
            m_kingCenterCapture.set(m_kingSquare);
            m_kingCenterCapture.and(m_capturedPieces);
            m_kingCenterCapture.and(BitBoardConsts.king4Surround);
            
            // is the king threatened while on the safer center squares?
            if (!m_kingCenterCapture.isEmpty())
            {
                if (!m_kingCenterCapture.equals(BitBoardConsts.center))
                {
                    // the king is not on the center tile of the safer squares, and thus can't be
                    // captured, so remove it from the captured pieces set
                    m_capturedPieces.clear(m_kingSquare);
                }
                else
                {
                    // the king is on the center tile, so if it is surrounded by four enemies let it
                    // be captured, otherwise remove it from the captured pieces set
                    m_kingCenterCapture.or(m_black);
                    m_kingCenterCapture.and(BitBoardConsts.king4Surround);
                    
                    if (!m_kingCenterCapture.equals(BitBoardConsts.king4Surround))
                    {
                        // king should not be captured
                        m_capturedPieces.clear(m_kingSquare);
                    }
                }
            }
            
            // remove captured pieces from the boards
            m_whiteNoKing.andNot(m_capturedPieces);
            m_whiteWithKing.andNot(m_capturedPieces);
            
            if (m_capturedPieces.getValue(m_kingSquare))
            {
                // if the king was captured, black wins
                m_capturedPieces.clear(m_kingSquare);
                previousHash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[previousTransform][m_kingSquare]];
                fullMove |= (((long)m_kingSquare) << 39) | (1L << 38);
                
                m_kingSquare = NOT_ON_BOARD;
                m_winner = BLACK;
            }
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
            
            m_black.andNot(m_capturedPieces);
        }
        
        // add any captured pieces to the move information and update the baord hash
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
                    previousHash ^= HASH_KEYS[opponent][BoardUtils.TRANSFORMED_INDICIES[previousTransform][index]];
                    fullMove |= (((long)index) << (shift + 1)) | (1L << shift);
                    shift += 8;
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
        
        // update other bitboard with the changed values
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();

        // store the move
        m_moves[m_turnNumber] = fullMove;
        
        // increment the turn
        m_turnNumber++;
        m_turnPlayer = (m_turnPlayer + 1) % 2;

        int canonicalTransform = previousTransform;
        // int canonicalTransform = calcCanonicalTransform();
        // if (canonicalTransform != previousTransform)
        // {
        // previousHash = calculateHash();
        // }
        // m_canonicalTransforms[m_turnNumber] = canonicalTransform;
        
        // store the hash and transform of the board
        m_hashes[m_turnNumber] = previousHash;
        m_canonicalTransforms[m_turnNumber] = canonicalTransform;
        
        // find where each player's pieces are on the board for this turn
        computePiecesLists();
    }
    
    /**
     * Undoes the move last applied to this state.
     */
    public void unmakeMove()
    {
        // decrement the turn
        m_turnNumber--;
        m_turnPlayer = (m_turnPlayer + 1) % 2;
        m_winner = Board.NOBODY;
        
        // get the most recent move
        long move = m_moves[m_turnNumber];
        
        /*
         * The format of the move value.
         * 
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
    }
    
    /**
     * Updates the list of squares black and white pieces are on for the current
     * turn.
     */
    private void computePiecesLists()
    {
        computePieceList(BLACK, m_black);
        computePieceList(WHITE, m_whiteNoKing);
    }
    
    /**
     * Updates the list of squares a player's non-king pieces are on by iterating
     * the bitboard of piece locations for that player for the current turn.
     * 
     * @param player
     *            The player to update the pieces for.
     * @param playerPieces
     *            The bitboard containing the locations for the player's pieces.
     */
    private void computePieceList(int player, BitBoard playerPieces)
    {
        // update the board squares of all black pieces
        int pieceCount = 0;
        int num = playerPieces.d0;
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
                m_pieceSquares[m_turnNumber][player][pieceCount++] = index;
            }
            else
            {
                switch (stage)
                {
                    case 0:
                        num = playerPieces.d1;
                        index = 26;
                        break;
                    case 1:
                        num = playerPieces.d2;
                        index = 53;
                        break;
                }
                stage++;
            }
        }
        m_pieceCounts[m_turnNumber][player] = pieceCount;
    }
    
    /**
     * The minimum utility value of a win.
     */
    public static final short WIN_VALUE = 30000;
    
    /**
     * Gets the value of this board for the player whose turn it is.
     */
    public short evaluate()
    {
        if (isTerminal())
        {
            if (m_winner != Board.NOBODY)
            {
                // wins closer to turn 1 are considered better
                return (short)((m_turnPlayer == m_winner ? 1 : -1) * (WIN_VALUE + (MAX_MOVES - m_turnNumber)));
            }
            // draw is 0 utility for both players
            return 0;
        }
        else
        {
            // calculate the value of the board for black
            int valueForBlack = 0;
            
            final int PIECE_VALUE = 1000;
            final int KING_CORNER_DISTANCE_VALUE = 100;
            
            int blackCount = m_pieceCounts[m_turnNumber][BLACK];
            int whiteCount = m_pieceCounts[m_turnNumber][WHITE];
            // get the piece difference
            valueForBlack += (blackCount - (whiteCount + 1)) * PIECE_VALUE;
            
            // black does better the further the distance of the king from a corner
            int kingRow = m_kingSquare / 9;
            int kingCol = m_kingSquare % 9;
            
            int cornerDist0 = Math.abs(0 - kingCol) + Math.abs(0 - kingRow);
            int cornerDist1 = Math.abs(0 - kingCol) + Math.abs(8 - kingRow);
            int cornerDist2 = Math.abs(8 - kingCol) + Math.abs(0 - kingRow);
            int cornerDist3 = Math.abs(8 - kingCol) + Math.abs(8 - kingRow);
            int maxDistance = Math.max(Math.max(cornerDist0, cornerDist1), Math.max(cornerDist2, cornerDist3));
            valueForBlack += maxDistance * KING_CORNER_DISTANCE_VALUE;
            
            // flip the board value if the turn player is white
            return (short)(m_turnPlayer == BLACK ? valueForBlack : -valueForBlack);
        }
    }
    
    /**
     * Computes the hash for the current board using the zorbist hashing method. Any
     * boards symmetrically identical will have the same hash.
     */
    public long calculateHash()
    {
        long hash = 0;
        
        int canonicalTransform = calcCanonicalTransform();
        
        int[] blackPieces = m_pieceSquares[m_turnNumber][BLACK];
        for (int i = 0; i < m_pieceCounts[m_turnNumber][BLACK]; i++)
        {
            hash ^= HASH_KEYS[0][BoardUtils.TRANSFORMED_INDICIES[canonicalTransform][blackPieces[i]]];
        }
        
        int[] whitePieces = m_pieceSquares[m_turnNumber][WHITE];
        for (int i = 0; i < m_pieceCounts[m_turnNumber][WHITE]; i++)
        {
            hash ^= HASH_KEYS[1][BoardUtils.TRANSFORMED_INDICIES[canonicalTransform][whitePieces[i]]];
        }
        
        if (m_kingSquare != NOT_ON_BOARD)
        {
            hash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[canonicalTransform][m_kingSquare]];
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
        
        int[] pieces = m_pieceSquares[m_turnNumber][m_turnPlayer];
        int pieceCount = m_pieceCounts[m_turnNumber][m_turnPlayer];
        
        for (int i = 0; i < pieceCount; i++)
        {
            getMoves(pieces[i], false);
        }
        
        if (m_turnPlayer == WHITE)
        {
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
    private int calcCanonicalTransform()
    {
        if (calcCanonicalBoardTransform(m_black))
        {
            if (calcCanonicalBoardTransform(m_whiteNoKing))
            {
                calcCanonicalKingTransform();
            }
        }
        return m_canonicalTransform;
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
    
    /*
     * Prints the board in a nice format. Indicates the last moved piece by placing
     * brackets around it and marks where it moved from with an 'x'.
     */
    @Override
    public String toString()
    {
        // print turn information
        String str = System.lineSeparator();
        str += "Turn " + (m_turnNumber + 1) + ", ";
        
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
        
        // print the number of pieces for each team
        str += System.lineSeparator();
        str += "Black Pieces: " + m_black.cardinality() + " ";
        str += "White Pieces: " + m_whiteWithKing.cardinality() + " ";
        
        // print the board nicely formatted
        long move = (m_turnNumber > 0) ? m_moves[m_turnNumber - 1] : -1;
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
                
                if (m_black.getValue(square))
                {
                    str = FormatPiece(str, "b", isTo);
                }
                else if (m_whiteNoKing.getValue(square))
                {
                    str = FormatPiece(str, "w", isTo);
                }
                else if (m_kingSquare == square)
                {
                    str = FormatPiece(str, "K", isTo);
                }
                else
                {
                    if (BitBoardConsts.onlyKingAllowed.getValue(col, row))
                    {
                        str += ". ";
                    }
                    else
                    {
                        str += isFrom ? "x " : ". ";
                    }
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
        if (wasMoved)
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
