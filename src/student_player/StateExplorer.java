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
        State initialState = new State();
        
        // copy all board squares
        for (int i = 0; i < 81; i++)
        {
            int row = i / 9;
            int col = i % 9;
            switch (state.getPieceAt(Coordinates.get(col, row)))
            {
                case BLACK:
                    initialState.black.set(i);
                    break;
                case WHITE:
                    initialState.white.set(i);
                    break;
                case KING:
                    initialState.kingSquare = i;
                    break;
                default:
                    break;
            }
        }
        
        // increment turn with every move rather then every other move
        m_turnNumber = (2 * state.getTurnNumber()) + state.getTurnPlayer();
        m_startTurn = m_turnNumber;
        
        // initialize the state stack with enough states to play out any remaining moves
        m_stack = new State[(1 + MAX_MOVES) - m_startTurn];
        
        initialize(initialState);
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
    
    private BitBoard m_opponentPieces  = new BitBoard();
    private BitBoard m_assistingPieces = new BitBoard();
    private BitBoard m_capturedPieces  = new BitBoard();
    private BitBoard m_kingNeighbors   = new BitBoard();
    private BitBoard m_escapedKing     = new BitBoard();
    
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
        m_currentState = nextState;
        m_currentState.move = move;
        
        m_currentState.hash ^= PLAYER_HASH;
        
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
            m_currentState.black.clear(fromCol, fromRow);
            m_currentState.black.set(toCol, toRow);
            
            // incrementally update the board hash
            m_currentState.hash ^= HASH_KEYS[0][from];
            m_currentState.hash ^= HASH_KEYS[0][to];
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(m_currentState.black);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            m_opponentPieces.copy(m_currentState.white);
            m_opponentPieces.set(m_currentState.kingSquare);
            
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(m_opponentPieces);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // enforce the special rules for capturing the king
            if (m_capturedPieces.getValue(m_currentState.kingSquare))
            {
                m_kingNeighbors.clear();
                m_kingNeighbors.set(m_currentState.kingSquare);
                m_kingNeighbors.toNeighbors();
                
                int blackSurround = BitBoard.andCount(m_kingNeighbors, m_currentState.black);
                int centerSurround = BitBoard.andCount(m_kingNeighbors, BitBoardConsts.center);
                boolean atCenter = BitBoardConsts.king4Surround.getValue(m_currentState.kingSquare);
                
                // the king is safe on the center 5 squared unless surrounded
                if (atCenter && blackSurround + centerSurround < 4)
                {
                    m_capturedPieces.clear(m_currentState.kingSquare);
                }
            }
            
            // remove captured pieces
            m_currentState.white.andNot(m_capturedPieces);
            
            // if the king was captured, black wins
            if (m_capturedPieces.getValue(m_currentState.kingSquare))
            {
                m_capturedPieces.clear(m_currentState.kingSquare);
                m_currentState.hash ^= HASH_KEYS[2][m_currentState.kingSquare];
                
                m_currentState.kingSquare = State.NOT_ON_BOARD;
                m_winner = BLACK;
            }
        }
        else
        {
            opponent = BLACK;
            
            if (m_currentState.kingSquare == from)
            {
                m_currentState.kingSquare = to;
                
                // incrementally update the board hash
                m_currentState.hash ^= HASH_KEYS[2][from];
                m_currentState.hash ^= HASH_KEYS[2][to];
            }
            else
            {
                m_currentState.white.clear(fromCol, fromRow);
                m_currentState.white.set(toCol, toRow);
                
                // incrementally update the board hash
                m_currentState.hash ^= HASH_KEYS[1][from];
                m_currentState.hash ^= HASH_KEYS[1][to];
            }
            
            // find pieces that can help the moved piece make a capture
            m_assistingPieces.copy(m_currentState.white);
            m_assistingPieces.set(m_currentState.kingSquare);
            m_assistingPieces.or(BitBoardConsts.onlyKingAllowed);
            m_assistingPieces.and(BitBoardConsts.twoCrosses[to]);
            
            // find captured pieces
            m_capturedPieces.copy(m_assistingPieces);
            m_capturedPieces.toNeighbors();
            m_capturedPieces.and(m_currentState.black);
            m_capturedPieces.and(BitBoardConsts.oneCrosses[to]);
            
            // remove captured pieces
            m_currentState.black.andNot(m_capturedPieces);
            
            // check if king is in the corner
            m_escapedKing.clear();
            m_escapedKing.set(m_currentState.kingSquare);
            m_escapedKing.and(BitBoardConsts.corners);
            
            // if king gets away, white wins
            if (!m_escapedKing.isEmpty())
            {
                m_winner = WHITE;
            }
        }
        
        // add any captured pieces to the move information and update the baord hash
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
                    m_currentState.hash ^= HASH_KEYS[opponent][index];
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
        m_currentState.updatePieceLists();
        
        // increment the turn
        m_turnNumber++;
        m_turnPlayer = m_turnNumber % 2;
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
    
    private int[]    m_legalMoves      = new int[183];
    private int      m_legalMoveCount;
    private BitBoard m_pieces          = new BitBoard();
    private BitBoard m_piecesReflected = new BitBoard();
    
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
        
        // print the number of pieces for each team
        str += System.lineSeparator();
        str += "Black Pieces: " + m_currentState.black.cardinality() + " ";
        str += "White Pieces: " + (m_currentState.white
                .cardinality() + (m_currentState.kingSquare == State.NOT_ON_BOARD ? 0 : 1)) + " ";
        
        // print the board nicely formatted
        long move = (m_turnNumber > 0) ? m_currentState.move : -1;
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
                
                if (m_currentState.black.getValue(square))
                {
                    str = FormatPiece(str, "b", isTo);
                }
                else if (m_currentState.white.getValue(square))
                {
                    str = FormatPiece(str, "w", isTo);
                }
                else if (m_currentState.kingSquare == square)
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
