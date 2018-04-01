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
public class StudentPlayerAlt extends TablutPlayer
{
    /**
     * The time allowed to think during the first turn in nanoseconds.
     */
    private static final long  START_TURN_TIMEOUT   = (long)(9.95 * 1000000000);
    
    /**
     * The time allowed to think during turns following the first turn in
     * nanoseconds.
     */
    private static final long  TURN_TIMEOUT         = (long)(1.95 * 1000000000);
    
    private TranspositionTable m_transpositionTable = new TranspositionTable(340);
    private long               m_stopTime;
    
    /**
     * Associate this player implementation with my student ID.
     */
    public StudentPlayerAlt()
    {
        super("AltPlayer");
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
        long timeout = (turn == 0 ? START_TURN_TIMEOUT : TURN_TIMEOUT);
        
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
            m_firstCutCount = 0;
            m_notFirstCutCount = 0;
            m_pvMoveCount = 0;
            m_noPvMoveCount = 0;
            
            // search for the best move
            int result = negaMax(currentState, depth, -Short.MAX_VALUE, Short.MAX_VALUE);
            nodesVisited += m_nodeCount;
            
            // unpack the best move and use it if this iteration was completed
            if (System.nanoTime() < m_stopTime)
            {
                int move = result & 0xFFFF;
                // only use valid moves
                if (move > 0)
                {
                    bestMove = move;
                }
                Log.info(String.format("depth: %s  move: %s  evaluation: %s  visits: %s  first cut %s  pv move %s",
                        depth, Utils.getMoveString(bestMove), result >> 16, m_nodeCount,
                        (m_firstCutCount / (float)(m_firstCutCount + m_notFirstCutCount)),
                        (m_pvMoveCount / (float)(m_pvMoveCount + m_noPvMoveCount))));
            }
            else
            {
                break;
            }
        }
        Log.info(String.format("Total nodes visited: %s", nodesVisited));
        Log.info(String.format("Time used: %s", (System.nanoTime() - startTime) / 1000000000.0));
        Log.printMemoryUsage();
        return bestMove;
    }
    
    private int   m_nodeCount;
    private int   m_firstCutCount;
    private int   m_notFirstCutCount;
    private int   m_pvMoveCount;
    private int   m_noPvMoveCount;
    
    private int[] m_criticalMoves = new int[183];
    private int   m_criticalMovesCount;
    private int[] m_regularMoves  = new int[183];
    private int   m_regularMovesCount;
    
    /**
     * Does a negamax search from a given node (negamax being a simplification of
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
     * @return The value of this node in the most significant 16 bits and the best
     *         move in the least signinicant 16 bits.
     */
    private int negaMax(StateExplorer state, int depth, int a, int b)
    {
        m_nodeCount++;
        
        // if a leaf state evaluate and return the value
        if (depth <= 0 || state.isTerminal())
        {
            return state.evaluate() << 16;
        }
        
        int aOrig = a;
        
        // check if we have visited this state before and know some information about it
        long hash = state.getHash();
        long entry = m_transpositionTable.get(hash, depth, state.getTurnNumber());
        int tableMove = 0;
        
        // if the entry is valid use the stored information
        if (entry != TranspositionTable.NO_VALUE)
        {
            m_pvMoveCount++;
            int score = TranspositionTable.ExtractScore(entry);
            int entryDepth = TranspositionTable.ExtractDepth(entry);
            tableMove = TranspositionTable.ExtractMove(entry);
            
            // this entry stores more complete search information to a greater or equal
            // depth, so we can just use the stored values
            if (entryDepth >= depth)
            {
                // the score represents a different value based on the node type
                switch (TranspositionTable.ExtractNodeType(entry))
                {
                    case TranspositionTable.PV_NODE:
                        return (score << 16) | tableMove;
                    case TranspositionTable.CUT_NODE:
                        a = Math.max(a, score);
                        break;
                    case TranspositionTable.ALL_NODE:
                        b = Math.min(b, score);
                        break;
                }
            }
            // alpha-beta prune
            if (a >= b)
            {
                return (score << 16) | tableMove;
            }
        }
        else
        {
            m_noPvMoveCount++;
        }
        
        // get all legal moves for this state
        int[] legalMoves = state.getAllLegalMoves();
        m_criticalMovesCount = 0;
        m_regularMovesCount = 0;
        
        // get the move types and mark the principle variation move
        for (int i = 0; i < legalMoves.length; i++)
        {
            int move = state.classifyMove(legalMoves[i]);
            if ((move & 0x3FFF) == tableMove)
            {
                move |= (1 << 30);
            }
            
            // if the move is important, place it in the list to sort and place in front
            if ((move >>> 14) > 0)
            {
                m_criticalMoves[m_criticalMovesCount++] = move;
            }
            else
            {
                m_regularMoves[m_regularMovesCount++] = move;
            }
        }
        
        // sort any important moves and place them first to get more prunes
        if (m_criticalMovesCount > 0)
        {
            Arrays.sort(m_criticalMoves, 0, m_criticalMovesCount);
            
            // put all the moves back into the move list in the new ordering
            int lastCritIndex = m_criticalMovesCount - 1;
            
            for (int i = 0; i < m_criticalMovesCount; i++)
            {
                legalMoves[i] = m_criticalMoves[lastCritIndex - i];
            }
            // put back the remaining moves in no particular order
            for (int i = 0; i < m_regularMovesCount; i++)
            {
                legalMoves[m_criticalMovesCount + i] = m_regularMoves[i];
            }
        }
        
        // iterate over all legal moves to find the best value among the child nodes
        int bestMove = 0;
        int bestScore = -Short.MAX_VALUE;
        for (int i = 0; i < legalMoves.length; i++)
        {
            // if time is up we need to stop searching, and we shouldn't use incomplete
            // search results
            if (System.nanoTime() > m_stopTime)
            {
                bestScore = -Short.MAX_VALUE;
                bestMove = 0;
                return (bestScore << 16) | bestMove;
            }
            
            int move = legalMoves[i];
            // apply the move to the board
            state.makeMove(move);
            
            int result = -negaMax(state, depth - 1, -b, -a);
            int score = result >> 16;
            
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far and update the lower bound
            if (bestScore < score)
            {
                bestScore = score;
                bestMove = move;
            }
            
            if (a < bestScore)
            {
                a = bestScore;
            }
            
            // alpha-beta prune
            if (a >= b)
            {
                break;
            }
        }
        
        // update the transposition table with the node value
        int nodeType;
        if (bestScore <= aOrig)
        {
            nodeType = TranspositionTable.ALL_NODE;
        }
        else if (bestScore >= b)
        {
            nodeType = TranspositionTable.CUT_NODE;
        }
        else
        {
            nodeType = TranspositionTable.PV_NODE;
        }
        
        m_transpositionTable.put(hash, nodeType, depth, bestScore, bestMove, state.getTurnNumber());
        
        // return the best move and best value
        return (bestScore << 16) | bestMove;
    }
    
    /**
     * Contains a small test.
     */
    public static void main(String[] args)
    {
        // benchmark for state exploration
        long startTime = System.nanoTime();
        Log.info(Test(new TablutBoardState(), 3));
        Log.info(String.format("TablutBoardState, time used: %s", (System.nanoTime() - startTime) / 1000000000.0));
        
        startTime = System.nanoTime();
        Log.info(Test(new StateExplorer(new TablutBoardState()), 3));
        Log.info(String.format("StateExplorer, time used: %s", (System.nanoTime() - startTime) / 1000000000.0));
        
        // test case
        BitBoard black = new BitBoard();
        black.set(0, 3);
        black.set(0, 4);
        black.set(0, 5);
        black.set(2, 2);
        black.set(3, 0);
        black.set(3, 2);
        black.set(4, 0);
        black.set(4, 7);
        black.set(4, 8);
        black.set(5, 0);
        black.set(5, 8);
        black.set(7, 4);
        black.set(8, 2);
        black.set(8, 4);
        black.set(8, 5);
        
        BitBoard white = new BitBoard();
        white.set(2, 4);
        white.set(4, 2);
        white.set(4, 3);
        white.set(4, 5);
        white.set(5, 1);
        white.set(5, 6);
        white.set(6, 2);
        
        //StateExplorer s = new StateExplorer(2, new State(black, white, 40));
        StateExplorer s = new StateExplorer(new TablutBoardState());
        
        StudentPlayerAlt player = new StudentPlayerAlt();
        s.makeMove(player.getBestMove(s, TURN_TIMEOUT));
    }
    
    private static int Test(StateExplorer state, int depth)
    {
        int count = 1;
        if (depth == 0)
        {
            return count;
        }
        for (int move : state.getAllLegalMoves())
        {
            state.makeMove(move);
            count += Test(state, depth - 1);
            state.unmakeMove();
        }
        return count;
    }
    
    private static int Test(TablutBoardState state, int depth)
    {
        int count = 1;
        if (depth == 0)
        {
            return count;
        }
        for (TablutMove move : state.getAllLegalMoves())
        {
            TablutBoardState child = (TablutBoardState)state.clone();
            child.processMove(move);
            count += Test(child, depth - 1);
        }
        return count;
    }
}
