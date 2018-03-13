package student_player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Stack;

import boardgame.Move;
import tablut.TablutBoardState;
import tablut.TablutMove;
import tablut.TablutPlayer;

/**
 * The core file in Tablut AI implementation.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class StudentPlayer extends TablutPlayer
{
    /**
     * The time allowed to think during the first turn in nanoseconds.
     */
    private static final long START_TURN_TIMEOUT = (long)(1.98 * 1000000000);
    
    /**
     * The time allowed to think during turns following the first turn in
     * nanoseconds.
     */
    private static final long TURN_TIMEOUT       = (long)(1.98 * 1000000000);
    
    private long              m_stopTime;
    private long              m_nodeCount;
    private long              m_hitCount;
    
    /**
     * Associate this player implementation with my student ID.
     */
    public StudentPlayer()
    {
        super("260617022");
    }
    
    public static void main(String[] args)
    {
        Random rand = new Random(100);
        TablutBoardState initialState = new TablutBoardState();
        
        BitBoard black = new BitBoard();
        black.set(0, 1);
        black.set(0, 3);
        black.set(0, 4);
        black.set(0, 5);
        black.set(1, 4);
        black.set(2, 3);
        black.set(3, 8);
        black.set(4, 7);
        black.set(4, 8);
        black.set(5, 0);
        black.set(5, 8);
        black.set(7, 4);
        black.set(8, 3);
        black.set(8, 4);
        black.set(8, 5);
        
        BitBoard white = new BitBoard();
        white.set(3, 4);
        white.set(4, 5);
        white.set(4, 6);
        white.set(5, 4);
        white.set(6, 4);
        
        State s = new State(1, 0, black, white, 4);
        Log.info(s);
        
        StudentPlayer player = new StudentPlayer();
        int move0 = player.getBestMove(s, 5 * 1000000000L);
        Log.info(BoardUtils.getMoveString(move0));
        s.makeMove(move0);
        Log.info(s);
        int move1 = player.getBestMove(s, 5 * 1000000000L);
        Log.info(BoardUtils.getMoveString(move1));
        s.makeMove(move1);
        Log.info(s);
        
        for (int game = 0; game < 1; game++)
        {
            State state = new State(initialState);
            Stack<Long> moves = new Stack<Long>();
            while (!state.isTerminal())
            {
                int[] legalMoves = state.getAllLegalMoves();
                int move = legalMoves[rand.nextInt(legalMoves.length)];
                long moveResult = state.makeMove(move);
                moves.push(moveResult);
            }
            while (!moves.isEmpty())
            {
                long move = moves.pop();
                state.unmakeMove(move);
            }
        }
        
        long time0 = 0;
        long time1 = 0;
        for (int game = 0; game < 1000; game++)
        {
            State state = new State(initialState);
            Stack<Long> moves = new Stack<Long>();
            while (!state.isTerminal())
            {
                int[] legalMoves = state.getAllLegalMoves();
                int move = legalMoves[rand.nextInt(legalMoves.length)];
                long t = System.nanoTime();
                long moveResult = state.makeMove(move);
                time0 += System.nanoTime() - t;
                moves.push(moveResult);
            }
            while (!moves.isEmpty())
            {
                long move = moves.pop();
                long t = System.nanoTime();
                state.unmakeMove(move);
                time1 += System.nanoTime() - t;
            }
        }
//        Log.info(time0 / 1000000000.0f);
//        Log.info(time1 / 1000000000.0f);
        
        // long time1 = 0;
        // for (int game = 0; game < 10000; game++)
        // {
        // TablutBoardState state = new TablutBoardState();
        // while (!state.gameOver())
        // {
        // long t = System.nanoTime();
        // state.processMove((TablutMove)state.getRandomMove());
        // time1 += System.nanoTime() - t;
        // }
        // }
        // Log.Info(time1 / 1000000000.0f);
    }
    
    private TranspositionTable m_transpositionTable = new TranspositionTable();
    
    /**
     * Decides on a move to play.
     * 
     * @param state
     *            The current state of the baord.
     * @return The chosen move.
     */
    public Move chooseMove(TablutBoardState boardState)
    {
        // get the timeout for this turn so we know how long to plan moves
        int turn = boardState.getTurnNumber();
        long timeout = (turn == 1 ? START_TURN_TIMEOUT : TURN_TIMEOUT);
        
        int move = getBestMove(new State(boardState), timeout);
        
        // extract the coordinates of the move from the packed move integer
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        
        int fromRow = from / 9;
        int fromCol = from % 9;
        int toRow = to / 9;
        int toCol = to % 9;
        
        // return the chosen move
        return new TablutMove(fromCol, fromRow, toCol, toRow, boardState.getTurnPlayer());
    }
    
    /**
     * Gets the best move available.
     * 
     * @param currentState
     *            The current state of the baord.
     * @param timeout
     *            How much time to take searching in nanoseconds.
     * @return The chosen move.
     */
    private int getBestMove(State currentState, long timeout)
    {
        // get the time we want to have a result by
        m_stopTime = System.nanoTime() + timeout;
        
        int nodesVisited = 0;
        
        // Do an iterative depth search to find a good move.
        // Iterates until all nodes are explored or time is up.
        int bestMove = -1;
        for (int depth = 1; depth <= currentState.getRemainingMoves(); depth++)
        {
            m_nodeCount = 0;
            m_hitCount = 0;
            // search for the best move
            long result = Negamax(currentState, depth, -Integer.MAX_VALUE, Integer.MAX_VALUE);
            
            // unpack the best move and use it if this iteration was completed
            if (System.nanoTime() < m_stopTime)
            {
                bestMove = (int)(result >> 32);
            }
            else
            {
                break;
            }
            Log.info(String.format("depth completed: %s  nodes visited this iteration: %s  table hits: %s", depth, m_nodeCount, m_hitCount));
            Log.info(m_transpositionTable.size());
            nodesVisited += m_nodeCount;
        }
        Log.info(String.format("Total nodes visited: %s", m_nodeCount));
        return bestMove;
    }
    
    /**
     * Does a negaman search from a given node (negamax being a simplification of
     * min-max that applies zero-sum games). Does alpha-beta pruning.
     * 
     * @param state
     *            The current search node.
     * @param depth
     *            The depth left until the cutoff.
     * @param a
     *            The alpha value.
     * @param b
     *            The beta value.
     * @return The value of this node in the lower 32 bits and the best move in the
     *         upper 32 bits.
     */
    private long Negamax(State state, int depth, int a, int b)
    {
        m_nodeCount++;
        
        int aOrig = a;
        
        // check if we have visited this state before and know some information about it
        long hash = state.calculateHash();
        TranspositionData entry = m_transpositionTable.get(hash);
        
        // if the entry is valid use the stored data
        if (entry != null && entry.depth >= depth)
        {
            m_hitCount++;
            // the score represents a different value based on the node type
            switch (entry.nodeType)
            {
                case TranspositionData.PV_NODE:
                    // the exact value and best move are known
                    return entry.getValue();
                case TranspositionData.CUT_NODE:
                    // this node contains a lower bound
                    a = Math.max(a, entry.score);
                    break;
                case TranspositionData.ALL_NODE:
                    // this node contains an upper bound
                    b = Math.min(b, entry.score);
                    break;
            }
            // alpha-beta prune
            if (a >= b)
            {
                return entry.getValue();
            }
        }
        
        // If a leaf state evaluate and return the value
        if (depth == 0 || state.isTerminal())
        {
            return state.evaluate();
        }
        
        // generate all legal moves for this state
        int[] legalMoves = state.getAllLegalMoves();
        
        // iterate over all legal moves to find the best heuristic value among the child
        // nodes
        long bestValue = -Integer.MAX_VALUE;
        long bestMove = 0;
        for (int i = 0; i < legalMoves.length; i++)
        {
            // if time is up we need to stop searching
            if (System.nanoTime() > m_stopTime)
            {
                bestValue = -Integer.MAX_VALUE;
                bestMove = 0;
                break;
            }
            
            // apply the move to the board
            long move = state.makeMove(legalMoves[i]);
            // evaluate this move
            long result = -Negamax(state, depth - 1, -b, -a);
            // undo the move
            state.unmakeMove(move);
            
            // check if the move is the best found so far
            int value = (int)result;
            if (bestValue < value)
            {
                bestValue = value;
                bestMove = move & 0x3FFFL;
            }
            
            // update the lower bound
            a = Math.max(a, value);
            // alpha-beta prune
            if (a >= b)
            {
                break;
            }
        }
        
        // update the transposition table with the new node value
        if (entry == null)
        {
            entry = new TranspositionData();
            // add the new entry to the table
            m_transpositionTable.put(hash, entry);
        }
        
        // add the node data
        entry.depth = depth;
        entry.score = (int)(bestValue & 0xFFFFFFFFL);
        entry.bestMove = (int)bestMove;
        
        // set the node type
        if (bestValue <= aOrig)
        {
            entry.nodeType = TranspositionData.ALL_NODE;
        }
        else if (bestValue >= b)
        {
            entry.nodeType = TranspositionData.CUT_NODE;
        }
        else
        {
            entry.nodeType = TranspositionData.PV_NODE;
        }
        
        // return the bset move and best value
        return (bestMove << 32) | (bestValue & 0xFFFFFFFFL);
    }
}
