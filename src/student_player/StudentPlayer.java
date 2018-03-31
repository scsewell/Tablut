package student_player;

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
    private static final long  START_TURN_TIMEOUT   = (long)(9.95 * 1000000000);
    
    /**
     * The time allowed to think during turns following the first turn in
     * nanoseconds.
     */
    private static final long  TURN_TIMEOUT         = (long)(1.95 * 1000000000);
    
    private TranspositionTable m_transpositionTable = new TranspositionTable(340);
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
     * Contains a small test.
     */
    public static void main(String[] args)
    {
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
        
        StateExplorer s = new StateExplorer(2, new State(black, white, 40));
        
        StudentPlayer player = new StudentPlayer();
        int move0 = player.getBestMove(s, TURN_TIMEOUT);
        s.makeMove(move0);
        int move1 = player.getBestMove(s, TURN_TIMEOUT);
        s.makeMove(move1);
        int move2 = player.getBestMove(s, TURN_TIMEOUT);
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
                Log.info(String.format("depth: %s  move: %s  evaluation: %s  visits: %s  first cut %s  pv move %s",
                        depth, BoardUtils.getMoveString(bestMove), result >> 16, m_nodeCount,
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
    
    private int m_firstCutCount;
    private int m_notFirstCutCount;
    private int m_pvMoveCount;
    private int m_noPvMoveCount;
    
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
        
        int aOrig = a;
        
        // check if we have visited this state before and know some information about it
        long hash = state.getHash();
        int entry = m_transpositionTable.get(hash, depth, state.getTurnNumber());
        int tableMove = Integer.MIN_VALUE;
        
        // if the entry is valid use the stored information
        if (entry != TranspositionTable.NO_VALUE)
        {
            int score = TranspositionTable.ExtractScore(entry);
            tableMove = TranspositionTable.ExtractMove(entry);
            int entryDepth = TranspositionTable.ExtractDepth(entry);
            
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
        
        // If a leaf state evaluate and return the value
        if (depth <= 0 || state.isTerminal())
        {
            return state.evaluate() << 16;
        }
        
        // get all legal moves for this state
        int[] legalMoves = state.getAllLegalMoves();
        
        // if we don't have a best move stored from a previous iteration, find the
        // expected best move with a limited search from this node with reduced depth.
        // This is quite expensive, but hopefully the move picked will lead to move
        // pruning in the main search.
        if (tableMove == Integer.MIN_VALUE)
        {
            m_noPvMoveCount++;
            int bestValue = -Short.MAX_VALUE;
            int interalDepth = (depth > 4) ? depth / 2 : depth - 2;
            for (int i = 0; i < legalMoves.length; i++)
            {
                if (System.nanoTime() > m_stopTime)
                {
                    break;
                }
                int move = legalMoves[i];
                // apply the move to the board
                state.makeMove(move);
                // evaluate this move
                int result = -negaMax(state, interalDepth, -b, -a);
                // undo the move
                state.unmakeMove();
                // check if the move is the best found so far
                int value = result >> 16;
                if (bestValue < value)
                {
                    bestValue = value;
                    tableMove = move;
                }
            }
        }
        else
        {
            m_pvMoveCount++;
        }
        
        // put the expected best move first in the list of moves to get more prunes
        if (tableMove != Integer.MIN_VALUE && legalMoves.length > 0)
        {
            // find where the expected move is in the move list and move it to the front
            int prev = legalMoves[0];
            legalMoves[0] = tableMove;
            for (int i = 1; i < legalMoves.length; i++)
            {
                int curr = legalMoves[i];
                legalMoves[i] = prev;
                
                if (curr == tableMove)
                {
                    break;
                }
                else
                {
                    prev = curr;
                }
            }
        }
        
        // iterate over all legal moves to find the best value among the child nodes
        int bestMove = 0;
        int bestValue = -Short.MAX_VALUE;
        for (int i = 0; i < legalMoves.length; i++)
        {
            // if time is up we need to stop searching, and we shouldn't use incomplete
            // search results
            if (System.nanoTime() > m_stopTime)
            {
                bestValue = -Short.MAX_VALUE;
                bestMove = 0;
                return (bestValue << 16) | bestMove;
            }
            
            int move = legalMoves[i];
            // apply the move to the board
            state.makeMove(move);
            
            // search to a reduced depth if we don't think the move will be all that good
            int result = -negaMax(state, (i == 0 || depth < 3) ? depth - 1 : depth - 3, -b, -a);
            int score = result >> 16;
            // if the move was searched to a reduced depth but looked promising fully explore it
            if (score > a && i > 1)
            {
                result = -negaMax(state, depth - 1, -b, -a);
                score = result >> 16;
            }
            
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far and update the lower bound
            if (bestValue < score)
            {
                bestValue = score;
                bestMove = move;
                a = score;
            }
            
            // alpha-beta prune
            if (a >= b)
            {
                if (i == 0)
                {
                    m_firstCutCount++;
                }
                else
                {
                    m_notFirstCutCount++;
                }
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
        return (bestValue << 16) | bestMove;
    }
}
