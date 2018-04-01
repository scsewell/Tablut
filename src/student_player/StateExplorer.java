package student_player;

import java.util.Arrays;
import java.util.Random;

import boardgame.Board;
import coordinates.Coordinates;
import tablut.TablutBoardState;

/**
 * Implements the ability to explore game states. This replaces and extends the
 * functionality of TablutBoardState, but does things much more optimally. In
 * general this is achieved by doing the following:
 * 
 * 1) Using bitboards to compute board heuristics and such in constant time. 2)
 * Not checking for things like out of bound indices. The code should never
 * supply invalid values anyways. 3) Using primitive types where possible. This
 * keeps memory accesses to a minimum. 4) Never instantiating objects while
 * processing the state, except where absolutely neccessary.
 * 
 * Instead of cloning states, a small number of states are be used to explore
 * the search tree by making and unmaking moves as states are explored. This is
 * keeps pressure off of the garbage collector and should improve the cache hit
 * rate.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class StateExplorer
{
    public static final int      BLACK     = 0;
    public static final int      WHITE     = 1;
    public static final int      MAX_MOVES = 100;
    
    /**
     * The Zorbist hash values, a table containing unique hashes for a black, white,
     * or king piece for each board square.
     */
    public static final long[][] HASH_KEYS;
    public static final long     PLAYER_HASH;
    
    /*
     * Creates all of the tile instances.
     */
    static
    {
        Random rand = new Random(100);
        
        HASH_KEYS = new long[3][81];
        for (int i = 0; i < 81; i++)
        {
            HASH_KEYS[0][i] = rand.nextLong();
            HASH_KEYS[1][i] = rand.nextLong();
            HASH_KEYS[2][i] = rand.nextLong();
        }
        PLAYER_HASH = rand.nextLong();
    }
    
    private final Evaluator m_evaluator = new Evaluator();
    private final State[]   m_stack;
    private final int       m_startTurn;
    
    private State           m_currentState;
    private int             m_turnNumber;
    private int             m_turnPlayer;
    private int             m_winner;
    
    /**
     * Initializes the state explorer with a given root state.
     * 
     * @param turn
     *            The current turn number.
     * @param state
     *            The board state.
     */
    public StateExplorer(int turn, State state)
    {
        // make sure the turn number is valid and use it to get the current turn's
        // player
        m_turnNumber = Math.min(Math.max(turn, 1), MAX_MOVES) - 1;
        m_startTurn = m_turnNumber;
        
        // initialize the state stack with enough states to play out any remaining moves
        m_stack = new State[(1 + MAX_MOVES) - m_startTurn];
        
        initialize(state);
    }
    
    /**
     * Initializes the state explorer with a given root state.
     * 
     * @param state
     *            The state to initialize from.
     */
    public StateExplorer(TablutBoardState state)
    {
        // increment turn with every move rather then every other move
        m_turnNumber = (2 * state.getTurnNumber()) + state.getTurnPlayer();
        m_startTurn = m_turnNumber;
        
        // initialize the state stack with enough states to play out any remaining moves
        m_stack = new State[(1 + MAX_MOVES) - m_startTurn];
        
        initialize(new State(state));
    }
    
    /**
     * Sets up the state exploration.
     * 
     * @param state
     *            The initial state.
     */
    public void initialize(State state)
    {
        m_turnPlayer = m_turnNumber % 2;
        m_winner = Board.NOBODY;
        
        for (int i = 0; i < m_stack.length; i++)
        {
            m_stack[i] = new State();
        }
        m_currentState = m_stack[0];
        m_currentState.copy(state);
        m_currentState.updatePieceLists();
        m_currentState.calculateHash();
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
    
    /**
     * Gets the hash for the current board state.
     */
    public long getHash()
    {
        return m_currentState.hash;
    }
    
    /**
     * Gets the value of this board for the player whose turn it is.
     */
    public short evaluate()
    {
        if (m_winner != Board.NOBODY)
        {
            // wins closer to the first turn are considered more valuable
            return (short)((m_turnPlayer == m_winner ? 1 : -1) * (Evaluator.WIN_VALUE + (MAX_MOVES - m_turnNumber)));
        }
        else if (m_turnNumber == MAX_MOVES)
        {
            // draw is 0 utility for both players
            return 0;
        }
        else
        {
            short value = m_evaluator.evaluate(m_currentState);
            return (m_turnPlayer == BLACK) ? value : (short)-value;
        }
    }
    
    private int[]    m_legalMoves           = new int[183];
    private int      m_legalMoveCount;
    private BitBoard m_pieces               = new BitBoard();
    private BitBoard m_piecesReflected      = new BitBoard();
    private BitBoard m_kingReachableCorners = new BitBoard();
    private BitBoard m_opponentPieces       = new BitBoard();
    private BitBoard m_assistingPieces      = new BitBoard();
    private BitBoard m_capturedPieces       = new BitBoard();
    private BitBoard m_kingNeighbors        = new BitBoard();
    private BitBoard m_escapedKing          = new BitBoard();
    
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
        State nextState = m_stack[(m_turnNumber - m_startTurn) + 1];
        nextState.copy(m_currentState);
        
        nextState.move = move & 0x3FFF;
        nextState.hash ^= PLAYER_HASH;
        
        // extract the board squares moved from and to from the move integer.
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        int opponent;
        
        if (m_turnPlayer == BLACK)
        {
            opponent = WHITE;
            
            // move the piece
            nextState.black.clear(fromCol, fromRow);
            nextState.black.set(toCol, toRow);
            
            // incrementally update the board hash
            nextState.hash ^= HASH_KEYS[0][from];
            nextState.hash ^= HASH_KEYS[0][to];
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(nextState.black);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            int kingRow = nextState.kingSquare / 9;
            int kingCol = nextState.kingSquare % 9;
            
            m_opponentPieces.copy(nextState.white);
            m_opponentPieces.set(kingCol, kingRow);
            
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(m_opponentPieces);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // enforce the special rules for capturing the king
            if (m_capturedPieces.getValue(kingCol, kingRow))
            {
                m_kingNeighbors.clear();
                m_kingNeighbors.set(kingCol, kingRow);
                m_kingNeighbors.toNeighbors();
                
                int blackSurround = BitBoard.andCount(m_kingNeighbors, nextState.black);
                int centerSurround = BitBoard.andCount(m_kingNeighbors, BitBoardConsts.center);
                boolean atCenter = BitBoardConsts.king4Surround.getValue(kingCol, kingRow);
                
                // the king is safe on the center cross squares unless surrounded
                if (atCenter && blackSurround + centerSurround < 4)
                {
                    m_capturedPieces.clear(kingCol, kingRow);
                }
            }
            
            // remove captured pieces
            nextState.white.andNot(m_capturedPieces);
            
            // if the king was captured, black wins
            if (m_capturedPieces.getValue(kingCol, kingRow))
            {
                m_capturedPieces.clear(kingCol, kingRow);
                nextState.hash ^= HASH_KEYS[2][nextState.kingSquare];
                nextState.kingSquare = State.NOT_ON_BOARD;
                m_winner = BLACK;
            }
        }
        else
        {
            opponent = BLACK;
            
            // move the piece
            if (nextState.kingSquare == from)
            {
                nextState.kingSquare = to;
                
                // incrementally update the board hash
                nextState.hash ^= HASH_KEYS[2][from];
                nextState.hash ^= HASH_KEYS[2][to];
            }
            else
            {
                nextState.white.clear(fromCol, fromRow);
                nextState.white.set(toCol, toRow);
                
                // incrementally update the board hash
                nextState.hash ^= HASH_KEYS[1][from];
                nextState.hash ^= HASH_KEYS[1][to];
            }
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(nextState.white);
            m_assistingPieces.set(nextState.kingSquare);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(nextState.black);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // remove captured pieces
            nextState.black.andNot(m_capturedPieces);
            
            // check if king is in the corner
            m_escapedKing.clear();
            m_escapedKing.set(nextState.kingSquare);
            m_escapedKing.and(BitBoardConsts.corners);
            
            // if king gets away, white wins
            if (!m_escapedKing.isEmpty())
            {
                m_winner = WHITE;
            }
        }
        
        // add update the board hash from captured pieces
        if (!m_capturedPieces.isEmpty())
        {
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
                    nextState.hash ^= HASH_KEYS[opponent][index];
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
        
        // update where each player's pieces are on the board for this turn
        nextState.updatePieceLists();
        
        // increment the turn
        m_turnNumber++;
        m_turnPlayer = m_turnNumber % 2;
        m_currentState = nextState;
    }
    
    /**
     * Undoes the move last applied to this state.
     */
    public void unmakeMove()
    {
        m_turnNumber--;
        m_turnPlayer = m_turnNumber % 2;
        m_winner = Board.NOBODY;
        m_currentState = m_stack[(m_turnNumber - m_startTurn)];
    }
    
    /**
     * Gets the move packed with the type of move.
     * 
     * @param move
     *            The move to make. The index of the source square is packed into
     *            bits 0-6. The index of the destination square is packed in bits
     *            7-13.
     * 
     * @return The move with the number of captures in bits 26-25, if white's turn
     *         and the move puts the king in sight of a corner in bit 24, if black's
     *         turn and the move blocks the king in bit 23, and the original move in
     *         bits 0-13.
     */
    public int classifyMove(int move)
    {
        State nextState = m_stack[(m_turnNumber - m_startTurn) + 1];
        nextState.copy(m_currentState);
        
        // extract the board squares moved from and to from the move integer.
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        if (m_turnPlayer == BLACK)
        {
            // check if the king can no longer move to an exit
            m_pieces.copy(nextState.black);
            m_pieces.or(nextState.white);
            m_pieces.set(nextState.kingSquare);
            m_piecesReflected.copy(m_pieces);
            m_piecesReflected.mirrorDiagonal();
            
            BitBoardConsts.getLegalMoves(nextState.kingSquare, true, m_pieces, m_piecesReflected, m_kingReachableCorners);
            m_kingReachableCorners.and(BitBoardConsts.corners);
            boolean kingCanLeave = !m_kingReachableCorners.isEmpty();
            
            // move the piece
            nextState.black.clear(fromCol, fromRow);
            nextState.black.set(toCol, toRow);
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(nextState.black);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            int kingRow = nextState.kingSquare / 9;
            int kingCol = nextState.kingSquare % 9;
            
            m_opponentPieces.copy(nextState.white);
            m_opponentPieces.set(kingCol, kingRow);
            
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(m_opponentPieces);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // enforce the special rules for capturing the king
            if (m_capturedPieces.getValue(kingCol, kingRow))
            {
                m_kingNeighbors.clear();
                m_kingNeighbors.set(kingCol, kingRow);
                m_kingNeighbors.toNeighbors();
                
                int blackSurround = BitBoard.andCount(m_kingNeighbors, nextState.black);
                int centerSurround = BitBoard.andCount(m_kingNeighbors, BitBoardConsts.center);
                boolean atCenter = BitBoardConsts.king4Surround.getValue(kingCol, kingRow);
                
                // the king is safe on the center cross squares unless surrounded
                if (atCenter && blackSurround + centerSurround < 4)
                {
                    m_capturedPieces.clear(kingCol, kingRow);
                }
            }
            
            // remove captured pieces
            nextState.white.andNot(m_capturedPieces);
            
            // check if the king can no longer move to an exit
            m_pieces.copy(nextState.black);
            m_pieces.or(nextState.white);
            m_pieces.set(nextState.kingSquare);
            m_piecesReflected.copy(m_pieces);
            m_piecesReflected.mirrorDiagonal();
            
            BitBoardConsts.getLegalMoves(nextState.kingSquare, true, m_pieces, m_piecesReflected, m_kingReachableCorners);
            m_kingReachableCorners.and(BitBoardConsts.corners);
            
            if (kingCanLeave && m_kingReachableCorners.isEmpty())
            {
                move |= (1 << 23);
            }
        }
        else
        {
            // move the piece
            if (nextState.kingSquare == from)
            {
                nextState.kingSquare = to;
            }
            else
            {
                nextState.white.clear(fromCol, fromRow);
                nextState.white.set(toCol, toRow);
            }
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(nextState.white);
            m_assistingPieces.set(nextState.kingSquare);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(nextState.black);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // remove captured pieces
            nextState.black.andNot(m_capturedPieces);
            
            // check if the king can now move to an exit
            m_pieces.copy(nextState.black);
            m_pieces.or(nextState.white);
            m_pieces.set(nextState.kingSquare);
            m_piecesReflected.copy(m_pieces);
            m_piecesReflected.mirrorDiagonal();
            
            BitBoardConsts.getLegalMoves(nextState.kingSquare, true, m_pieces, m_piecesReflected, m_kingReachableCorners);
            m_kingReachableCorners.and(BitBoardConsts.corners);
            
            if (!m_kingReachableCorners.isEmpty())
            {
                move |= (1 << 24);
            }
        }
        
        return move | (m_capturedPieces.cardinality() << 25);
    }
    
    /**
     * Finds all moves that the player can currently make.
     */
    public int[] getAllLegalMoves()
    {
        m_legalMoveCount = 0;
        
        m_pieces.copy(m_currentState.black);
        m_pieces.or(m_currentState.white);
        m_pieces.set(m_currentState.kingSquare);
        m_piecesReflected.copy(m_pieces);
        m_piecesReflected.mirrorDiagonal();
        
        if (m_turnPlayer == BLACK)
        {
            for (int i = 0; i < m_currentState.blackCount; i++)
            {
                getMoves(m_currentState.blackPieces[i], false);
            }
        }
        else
        {
            getMoves(m_currentState.kingSquare, true);
            
            for (int i = 0; i < m_currentState.whiteCount; i++)
            {
                getMoves(m_currentState.whitePieces[i], false);
            }
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
        return str + m_currentState.toString();
    }
}
