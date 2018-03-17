package student_player;

import java.util.Arrays;

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
    private static final long  START_TURN_TIMEOUT   = (long)(1.98 * 1000000000);
    
    /**
     * The time allowed to think during turns following the first turn in
     * nanoseconds.
     */
    private static final long  TURN_TIMEOUT         = (long)(1.98 * 1000000000);
    
    private TranspositionTable m_transpositionTable = new TranspositionTable(400);
    private long               m_stopTime;
    private int                m_nodeCount;
    
    /**
     * Associate this player implementation with my student ID.
     */
    public StudentPlayer()
    {
        super("260617022");
    }
    
    /**
     * Contains a few tests.
     */
    public static void main(String[] args)
    {
        BitBoard black = new BitBoard();
        BitBoard white = new BitBoard();
        StateExplorer s;
        
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
        
        white.set(3, 4);
        white.set(4, 5);
        white.set(4, 6);
        white.set(5, 4);
        white.set(6, 4);
        
        s = new StateExplorer(1, new State(black, white, 4));
        
        // BitBoard black = new BitBoard();
        // black.set(0, 3);
        // black.set(0, 4);
        // black.set(0, 5);
        // black.set(2, 2);
        // black.set(3, 0);
        // black.set(3, 2);
        // black.set(4, 1);
        // black.set(4, 7);
        // black.set(4, 8);
        // black.set(5, 0);
        // black.set(6, 6);
        // black.set(5, 8);
        // black.set(8, 4);
        //
        // BitBoard white = new BitBoard();
        // white.set(2, 4);
        // white.set(4, 2);
        // white.set(4, 5);
        // white.set(5, 6);
        // white.set(6, 2);
        // white.set(8, 1);
        //
        //s = new StateExplorer(1, new State(black, white, 35));
        
        s = new StateExplorer(new TablutBoardState());
        
        StudentPlayer player = new StudentPlayer();
        int move0 = player.getBestMove(s, 5 * 1000000000L);
        s.makeMove(move0);
        int move1 = player.getBestMove(s, 5 * 1000000000L);
        s.makeMove(move1);
        int move2 = player.getBestMove(s, 5 * 1000000000L);
        s.makeMove(move2);
        Log.info(s);
    }
    
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
        
        int move = getBestMove(new StateExplorer(boardState), timeout);
        
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
    private int getBestMove(StateExplorer currentState, long timeout)
    {
        Log.info(currentState);
        
        // get the time we want to have a result by
        long startTime = System.nanoTime();
        m_stopTime = startTime + timeout;
        
        int nodesVisited = 0;
        
        // Do an iterative depth search to find a good move.
        // Iterates until all nodes are explored or time is up.
        int bestMove = 0;
        int maxDepth = currentState.getRemainingMoves();
        
        for (int depth = 1; depth <= maxDepth; depth++)
        {
            m_nodeCount = 0;
            
            // search for the best move
            int result = Negamax(currentState, depth, -Short.MAX_VALUE, Short.MAX_VALUE);
            nodesVisited = m_nodeCount;
            
            // unpack the best move and use it if this iteration was completed
            if (System.nanoTime() < m_stopTime)
            {
                int move = result & 0xFFFF;
                // only use valid moves
                if (move > 0)
                {
                    bestMove = move;
                }
                Log.info(String.format("depth finished: %s  move: %s  nodes visited: %s", depth,
                        BoardUtils.getMoveString(bestMove), m_nodeCount));
            }
            else
            {
                break;
            }
        }
        m_transpositionTable.printStatistics();
        Log.info(String.format("Total nodes visited: %s", nodesVisited));
        Log.info(String.format("Time used: %s", (System.nanoTime() - startTime) / 1000000000.0));
        Log.printMemoryUsage();
        return bestMove;
    }
    
    /**
     * Does a negaman search from a given node (negamax being a simplification of
     * min-max that applies zero-sum games). Implements alpha-beta pruning.
     * 
     * @param state
     *            The current search node.
     * @param depth
     *            The depth left until the cutoff.
     * @param a
     *            The alpha value.
     * @param b
     *            The beta value.
     * @return The value of this node in the upper 16 bits and the best move in the
     *         lower 16 bits.
     */
    private int Negamax(StateExplorer state, int depth, int a, int b)
    {
        m_nodeCount++;
        
        int aOrig = a;
        
        // check if we have visited this state before and know some information about it
        long hash = state.getHash();
        int entry = m_transpositionTable.get(hash, depth, state.getTurnNumber());
        
        // if the entry is valid use the stored information
        if (entry != TranspositionTable.NO_VALUE)
        {
            int score = TranspositionTable.ExtractScore(entry);
            int move = 0;
            
            // the score represents a different value based on the node type
            switch (TranspositionTable.ExtractNodeType(entry))
            {
                case TranspositionTable.PV_NODE:
                    move = TranspositionTable.ExtractMove(entry);
                    return (score << 16) | (move & 0xFFFF);
                case TranspositionTable.CUT_NODE:
                    a = Math.max(a, score);
                    break;
                case TranspositionTable.ALL_NODE:
                    b = Math.min(b, score);
                    break;
            }
            
            // alpha-beta prune
            if (a >= b)
            {
                return (score << 16) | (move & 0xFFFF);
            }
        }
        
        // If a leaf state evaluate and return the value
        if (depth == 0 || state.isTerminal())
        {
            return state.evaluate() << 16;
        }
        
        // generate all legal moves for this state
        int[] legalMoves = state.getAllLegalMoves();
        
        // try to place the best moves first, as it greatly improves the pruning
        // performance
        // int[] sortedMoves = new int[legalMoves.length];
        // for (int i = 0; i < legalMoves.length; i++)
        // {
        // int move = legalMoves[i];
        // state.makeMove(move);
        // int result = -Negamax(state, Math.min(depth - 1, 1), -b, -a);
        // state.unmakeMove();
        //
        // sortedMoves[i] = (result & 0xFFFF0000) | (move & 0xFFFF);
        // }
        // Arrays.sort(sortedMoves);
        
        // iterate over all legal moves to find the best heuristic value among the child
        // nodes
        int bestMove = 0;
        int bestValue = -Short.MAX_VALUE;
        // for (int i = sortedMoves.length - 1; i >= 0; i--)
        for (int i = 0; i < legalMoves.length; i++)
        {
            // if time is up we need to stop searching, and we shouldn't use incomplete
            // search results
            if (System.nanoTime() > m_stopTime)
            {
                bestValue = -Short.MAX_VALUE;
                bestMove = 0;
                return (bestValue << 16) | (bestMove & 0xFFFF);
            }
            
            // int move = sortedMoves[i] & 0xFFFF;
            int move = legalMoves[i];
            // apply the move to the board
            state.makeMove(move);
            // evaluate this move
            int result = -Negamax(state, depth - 1, -b, -a);
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far
            int value = result >> 16;
            if (bestValue < value)
            {
                bestValue = value;
                bestMove = move & 0xFFFF;
            }
            
            // update the lower bound
            a = Math.max(a, value);
            // alpha-beta prune
            if (a >= b)
            {
                break;
            }
        }
        
        // update the transposition table with the node value
        int nodeType;
        if (bestValue <= aOrig)
        {
            nodeType = TranspositionTable.ALL_NODE;
        }
        else if (bestValue >= b)
        {
            nodeType = TranspositionTable.CUT_NODE;
        }
        else
        {
            nodeType = TranspositionTable.PV_NODE;
        }
        m_transpositionTable.put(hash, nodeType, depth, bestValue, bestMove, state.getTurnNumber());
        
        // return the best move and best value
        return (bestValue << 16) | (bestMove & 0xFFFF);
    }
}
