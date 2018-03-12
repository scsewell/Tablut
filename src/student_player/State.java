package student_player;

import java.util.Random;

import boardgame.Board;
import coordinates.Coordinates;
import tablut.TablutBoardState;

/**
 * Implements the game state. This replaces and extends the functionality of
 * TablutBoardState, but does things much more optimally. For example checks for
 * things like out of bounds are ignored to get the most performance.
 * 
 * All states have a canonical version that ignores any symmetry of the sqaure,
 * allowing for identifying any boards symmetrically equivalent to one already
 * explored.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class State
{
    private static final int      BLACK     = 0;
    private static final int      WHITE     = 1;
    private static final int      MAX_TURNS = 100;
    private static final long[][] HASH_KEYS = new long[3][81];

    // private static BitBoard[] m_legalMovesForPiece = new BitBoard[16];
    // private static BitBoard m_legalMovesForKing = new BitBoard();
    // private static BitBoard m_allLegalMoves = new BitBoard();

    /**
     * The static constructor. Generates a hash for a black, white, or king piece on
     * any square, used by the hashing function.
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

    private int[]    m_blackPieces;
    private int      m_blackPieceCount;
    private int[]    m_whitePieces;
    private int      m_whitePieceCount;
    private int      m_kingSquare;

    private BitBoard m_pieces          = new BitBoard();
    private BitBoard m_piecesReflected = new BitBoard();
    private BitBoard m_black           = new BitBoard();
    private BitBoard m_whiteWithKing   = new BitBoard();
    private BitBoard m_whiteNoKing     = new BitBoard();

    public int[]     legalMoves        = new int[183];
    public int       legalMoveCount    = 0;

    /**
     * Clone contructor.
     * 
     * @param state
     *            The state to copy.
     */
    public State(State state)
    {
        m_turnNumber = state.m_turnNumber;
        m_turnPlayer = state.m_turnPlayer;
        m_winner = state.m_winner;

        m_blackPieces = state.m_blackPieces.clone();
        m_blackPieceCount = state.m_blackPieceCount;
        m_whitePieces = state.m_whitePieces.clone();
        m_whitePieceCount = state.m_whitePieceCount;
        m_kingSquare = state.m_kingSquare;

        m_pieces.copy(state.m_pieces);
        m_piecesReflected.copy(state.m_piecesReflected);
        m_black.copy(state.m_black);
        m_whiteWithKing.copy(state.m_whiteWithKing);
        m_whiteNoKing.copy(state.m_whiteNoKing);
    }

    /**
     * Initializes the state from a TablutBoardState.
     * 
     * @param state
     *            The state to setup using.
     */
    public State(TablutBoardState state)
    {
        m_blackPieces = new int[16];
        m_blackPieceCount = 0;
        m_whitePieces = new int[8];
        m_whitePieceCount = 0;

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
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);

                    m_black.set(i);

                    m_blackPieces[m_blackPieceCount++] = i;
                    break;
                case WHITE:
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);

                    m_whiteNoKing.set(i);
                    m_whiteWithKing.set(i);

                    m_whitePieces[m_whitePieceCount++] = i;
                    break;
                case KING:
                    m_pieces.set(i);
                    m_piecesReflected.set(row, col);

                    m_whiteWithKing.set(i);

                    m_kingSquare = i;
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
     * Gets the number of turns until the game ends.
     */
    public int remainingTurns()
    {
        return isTerminal() ? 0 : (MAX_TURNS - m_turnNumber + 1);
    }

    private static final BitBoard m_assistingPieces = new BitBoard();
    private static final BitBoard m_capturedPieces  = new BitBoard();
    private static final BitBoard m_capturedKing    = new BitBoard();
    private static final BitBoard m_escapedKing     = new BitBoard();

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
        BitBoard playerPieces;
        BitBoard opponentPieces;

        // extract the board squares moved from and to from the move integer.
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;

        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = from / 9;
        int toCol = from % 9;

        if (m_turnPlayer == BLACK)
        {
            playerPieces = m_black;
            opponentPieces = m_whiteWithKing;
        }
        else
        {
            playerPieces = m_whiteWithKing;
            opponentPieces = m_black;

            // move the piece on special white boards
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

        // move the piece
        playerPieces.clear(fromCol, fromRow);
        playerPieces.set(toCol, toRow);

        BitBoard oneCross = BitBoardConsts.oneCrosses[to];
        BitBoard twoCross = BitBoardConsts.twoCrosses[to];

        // find pieces that can help the moved piece make a capture
        m_assistingPieces.copy(playerPieces);
        m_assistingPieces.or(BitBoardConsts.corners);
        m_assistingPieces.and(twoCross);

        // find captured pieces
        m_capturedPieces.copy(m_assistingPieces);
        m_capturedPieces.toNeighbors();
        m_capturedPieces.and(opponentPieces);
        m_capturedPieces.and(oneCross);

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
                m_kingSquare = -1;
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

        // remove captured pieces from the board
        opponentPieces.andNot(m_capturedPieces);

        // rebuild board that contains all pieces
        m_pieces.copy(m_black);
        m_pieces.or(m_whiteWithKing);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();

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
        
        // increment the turn
        m_turnNumber++;
        m_turnPlayer = (m_turnPlayer + 1) % 2;
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
                return m_turnPlayer == m_winner ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            }
            return 0;
        }
        return 0;
    }

    /**
     * Computes a hash for the current board. Any boards symmetrically identical
     * share a hash.
     */
    public long calculateHash()
    {
        long hash = 0;

        int transform = calcCanonicalTransform();

        for (int i = 0; i < m_blackPieceCount; i++)
        {
            hash ^= HASH_KEYS[0][BoardUtils.TRANSFORMED_INDICIES[transform][m_blackPieces[i]]];
        }
        for (int i = 0; i < m_whitePieceCount; i++)
        {
            hash ^= HASH_KEYS[1][BoardUtils.TRANSFORMED_INDICIES[transform][m_whitePieces[i]]];
        }
        hash ^= HASH_KEYS[2][BoardUtils.TRANSFORMED_INDICIES[transform][m_kingSquare]];

        return hash;
    }

    /**
     * Finds all moves that the player can currently make.
     */
    public void computeAllLegalMoves()
    {
        legalMoveCount = 0;

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
                legalMoves[legalMoveCount++] = square | ((baseIndex + i) << 7);
            }
        }

        for (int i = ((colMoves >> 9) & 0xf); i <= ((colMoves >> 13) & 0xf); i++)
        {
            if ((colMoves & (1 << i)) != 0)
            {
                legalMoves[legalMoveCount++] = square | (((i * 9) + col) << 7);
            }
        }
    }

    private static int      m_canonicalTransform;
    private static BitBoard m_canonical = new BitBoard();
    private static BitBoard m_current   = new BitBoard();
    private static boolean  m_duplicate;

    /**
     * Computes the transformation for this board to a canonical board such that any
     * symmetrical boards are guarenteed to have the same canonical board.
     * 
     * @return The canonical transform code.
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
        int canonicalKing = m_kingSquare;
        m_canonicalTransform = 0;
        boolean duplicate = false;

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
