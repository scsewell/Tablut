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
    private static final long        START_TURN_TIMEOUT       = (long)(9.95 * 1000000000);
    
    /**
     * The time allowed to think during turns following the first turn in
     * nanoseconds.
     */
    private static final long        TURN_TIMEOUT             = (long)(1.95 * 1000000000);
    
    /**
     * The memory allocated to the transposition table in megabytes. Very important
     * to keep as large as possible.
     */
    private static final int         TRANSPOSITION_TABLE_SIZE = 340;
    
    private final TranspositionTable m_transpositionTable     = new TranspositionTable(TRANSPOSITION_TABLE_SIZE);
    private final KillerTable        m_killers                = new KillerTable(100);
    private int[][]                  m_legalMoves             = new int[101][StateExplorer.MAX_LEGAL_MOVES];
    private int[][]                  m_criticalMoves          = new int[101][StateExplorer.MAX_LEGAL_MOVES];
    private int[][]                  m_regularMoves           = new int[101][StateExplorer.MAX_LEGAL_MOVES];
    private long                     m_stopTime;
    
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
     
//        timeout = 999999999999L;
//        int maxDepth = 7;
        
        // get the time we want to have a result by
        long startTime = System.nanoTime();
        m_stopTime = startTime + timeout;
        
        int nodesVisited = 0;
        m_firstCutCount = 0;
        m_pvMoveCount = 0;
        
        int maxDepth = currentState.getRemainingMoves();
        
        // clear the killer move table
        m_killers.Clear();
        
        // Do an iterative depth search to find a good move.
        // Iterates until all nodes are explored or time is up.
        int bestMove = 0;
        int bestScore = 0;
        
        for (int depth = 1; depth <= maxDepth; depth++)
        {
            m_nodeCount = 0;
            
            int result = pvs(currentState, 0, depth, -Short.MAX_VALUE, Short.MAX_VALUE, true);
            // if (depth > 2)
            // {
            // // set an aspiration window with roughly a third the value of a peice
            // int alpha = bestScore - 5;
            // int beta = bestScore + 5;
            // result = pvs(currentState, depth, alpha, beta);
            //
            // // if the score is outside the window we need to re-search with a full window
            // int score = result >> 16;
            // if (score <= alpha)
            // {
            // Log.info("Fail Low: " + score);
            // result = pvs(currentState, depth, -Short.MAX_VALUE, beta);
            // }
            // else if (beta <= score)
            // {
            // Log.info("Fail High: " + score);
            // result = pvs(currentState, depth, alpha, Short.MAX_VALUE);
            // }
            // }
            // else
            // {
            // // if the score is not informed enough do a full search
            // result = pvs(currentState, depth, -Short.MAX_VALUE, Short.MAX_VALUE);
            // }
            nodesVisited += m_nodeCount;
            
            // unpack the best move and use it if this iteration was completed
            int move = result & 0xFFFF;
            if (move > 0)
            {
                bestMove = move;
                bestScore = result >> 16;
                Log.info(String.format(
                        "depth: %s  move: %s  evaluation: %s  visits: %s  first cut %.5f%%  pv move %.5f%%", depth,
                        Utils.getMoveString(bestMove), bestScore, m_nodeCount,
                        100 * (m_firstCutCount / (float)(nodesVisited)),
                        100 * (m_pvMoveCount / (float)(nodesVisited))));
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
    
    private int m_nodeCount;
    private int m_firstCutCount;
    private int m_pvMoveCount;
    
    /**
     * Does a principle variation search from a given node.
     * 
     * @param state
     *            The current search node.
     * @param ply
     *            The ply of the node.
     * @param depth
     *            The depth left until the cutoff.
     * @param a
     *            The alpha value.
     * @param b
     *            The beta value.
     * @param isPVNode
     *            Indicates if this node is a principle variation node.
     * @return The value of this node in the most significant 16 bits and the best
     *         move in the least signinicant 16 bits.
     */
    private int pvs(StateExplorer state, int ply, int depth, int a, int b, boolean isPVNode)
    {
        // if a leaf state evaluate and return the value
        if (depth <= 0 || state.isTerminal())
        {
            return quiescence(state, ply, 10, a, b);
        }
        
        int aOrig = a;
        
        // check if we have visited this state before and know some information about it
        long hash = state.getHash();
        long entry = m_transpositionTable.get(hash, depth, state.getTurnNumber());
        int tableMove = 0;
        
        // if the entry is valid use the stored information
        if (entry != TranspositionTable.NO_VALUE)
        {
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
                        return packMoveScore(tableMove, score);
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
                return packMoveScore(tableMove, score);
            }
        }
        
        m_nodeCount++;
        int bestMove = 0;
        int bestScore = -Short.MAX_VALUE;
        
        // Search the pv move if we got it from the table.
        // Most of the time this is the best move.
        // We search it before move generation since if we get a cut-off we can save the
        // time
        if (tableMove != 0)
        {
            m_pvMoveCount++;
            
            // apply the move to the board
            state.makeMove(tableMove);
            // do a search to find the score of the node
            int score = -pvs(state, ply + 1, depth - 1, -b, -a, true) >> 16;
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far and update the lower bound
            if (bestScore < score)
            {
                bestScore = score;
                bestMove = tableMove;
                
                if (a < bestScore)
                {
                    a = bestScore;
                    
                    // alpha-beta prune
                    if (a >= b)
                    {
                        m_firstCutCount++;
                        PutTTEntry(state, depth, aOrig, b, bestScore, bestMove);
                        return packMoveScore(bestMove, bestScore);
                    }
                }
            }
        }
        
        // get all legal moves for this state
        int[] moves = m_legalMoves[ply];
        int moveCount = state.getAllLegalMoves(moves);
        
        // if at a pv node, there is no best move in the table, and there are many plys
        // remaining to search, do a reduced search to find a good short first move to
        // check.
        int IIDMove = 0;
        if (isPVNode && tableMove == 0 && depth > 3)
        {
            int maxDepth = depth - 2;
            for (int d = 1; d <= maxDepth; d++)
            {
                int best = -Short.MAX_VALUE;
                for (int i = 0; i < moveCount; i++)
                {
                    int move = moves[i];
                    state.makeMove(move);
                    int score = -pvs(state, ply + 1, d - 1, -b, -a, IIDMove == move) >> 16;
                    state.unmakeMove();
                    
                    if (best < score)
                    {
                        best = score;
                        IIDMove = move;
                    }
                }
            }
        }
        
        // group the moves
        int[] criticalMoves = m_criticalMoves[ply];
        int[] regularMoves = m_regularMoves[ply];
        int criticalMovesCount = 0;
        int regularMovesCount = 0;
        
        for (int i = 0; i < moveCount; i++)
        {
            // get a move
            int move = moves[i];
            
            // skip the principle variation move, it was already searched
            if (move == tableMove)
            {
                continue;
            }
            
            // sets bits in the move indicating the effect of the move
            int classifiedMove = state.classifyMove(move);
            
            // mark the internal iterative deepening move
            if (move == IIDMove)
            {
                classifiedMove |= (1 << 28);
            }
            
            // mark killer moves
            if (m_killers.contains(ply, move))
            {
                classifiedMove |= (1 << 22);
            }
            
            // if the move is important, place it in the list to sort and place in front
            if ((classifiedMove >>> 14) != 0)
            {
                criticalMoves[criticalMovesCount++] = classifiedMove;
            }
            else
            {
                regularMoves[regularMovesCount++] = move;
            }
        }
        
        // sort moves and place them first to get more prunes
        Arrays.sort(criticalMoves, 0, criticalMovesCount);
        boolean prune = false;
        
        // search the best moves
        for (int i = 0; i < criticalMovesCount; i++)
        {
            // if time is up we need to stop searching, and we shouldn't use incomplete
            // search results
            if (System.nanoTime() > m_stopTime)
            {
                return 0;
            }
            
            // get the next move
            int move = criticalMoves[(criticalMovesCount - 1) - i];
            // apply the move to the board
            state.makeMove(move);
            // do a search to find the score of the node
            int score = -pvs(state, ply + 1, depth - 1, -b, -a, false) >> 16;
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far and update the lower bound
            if (bestScore < score)
            {
                bestScore = score;
                bestMove = move;
                
                if (a < bestScore)
                {
                    a = bestScore;
                    
                    // alpha-beta prune
                    if (a >= b)
                    {
                        prune = true;
                        break;
                    }
                }
            }
        }
        
        // search the remaining moves
        if (!prune)
        {
            for (int i = 0; i < regularMovesCount; i++)
            {
                // if time is up we need to stop searching, and we shouldn't use incomplete
                // search results
                if (System.nanoTime() > m_stopTime)
                {
                    return 0;
                }
                
                // get the next move
                int move = regularMoves[i];
                // apply the move to the board
                state.makeMove(move);
                // Search moves not likely to score higher than what is already found with a
                // null window. This means that the search will finish quickly if there is no
                // better score, and return quickly if there is one.
                int searchDepth = depth < 3 ? depth - 1 : depth - 2;
                int score = -pvs(state, ply + 1, searchDepth, -(a + 1), -a, false) >> 16;
                // If there is a score that may be better do a full search with the normal
                // window.
                if (a < score && score < b && depth > 1)
                {
                    score = -pvs(state, ply + 1, depth - 1, -b, -a, false) >> 16;
                }
                // undo the move
                state.unmakeMove();
                
                // check if the move is the best found so far and update the lower bound
                if (bestScore < score)
                {
                    bestScore = score;
                    bestMove = move;
                    
                    if (a < bestScore)
                    {
                        a = bestScore;
                        
                        // alpha-beta prune
                        if (a >= b)
                        {
                            prune = true;
                            break;
                        }
                    }
                }
            }
        }
        
        if (prune && (bestMove >> 14) > 0)
        {
            m_firstCutCount++;
        }
        
        // update transposition table
        PutTTEntry(state, depth, aOrig, b, bestScore, bestMove);
        
        // update killer and history tables with move if a non-capture
        if (prune && ((bestMove >> 25) & 0x3) == 0)
        {
            m_killers.add(ply, bestMove);
        }
        
        // return best score and move
        return packMoveScore(bestMove, bestScore);
    }
    
    /**
     * Does a quescencse search from a given node.
     * 
     * @param state
     *            The current search node.
     * @param ply
     *            The ply of the node.
     * @param depth
     *            The depth left until the cutoff.
     * @param a
     *            The alpha value.
     * @param b
     *            The beta value.
     * @return The value of this node in the most significant 16 bits.
     */
    private int quiescence(StateExplorer state, int ply, int depth, int a, int b)
    {
        // calculate the standing pat score
        int eval = state.evaluate();
        
        // if a leaf state evaluate and return the value
        if (depth <= 0 || state.isTerminal())
        {
            return packMoveScore(0, eval);
        }
        
        if (eval >= b)
        {
            return packMoveScore(0, eval);
        }
        
        // get all legal moves for this state
        int[] moves = m_legalMoves[ply];
        int moveCount = state.getAllLegalMoves(moves);
        
        // get loud moves
        int[] criticalMoves = m_criticalMoves[ply];
        int criticalMovesCount = 0;
        
        for (int i = 0; i < moveCount; i++)
        {
            int move = state.classifyMove(moves[i]);
            
            // if the move is important, place it in the list to sort and place in front
            if ((move >>> 14) != 0)
            {
                criticalMoves[criticalMovesCount++] = move;
            }
        }
        
        // if the state is quiet return the evaluation
        if (criticalMovesCount == 0)
        {
            return packMoveScore(0, eval);
        }
        
        if (a < eval)
        {
            a = eval;
        }
        
        // sort any important moves and place them first to get more prunes
        Arrays.sort(criticalMoves, 0, criticalMovesCount);
        
        // search the best moves
        int bestScore = -Short.MAX_VALUE;
        for (int i = 0; i < criticalMovesCount; i++)
        {
            // if time is up we need to stop searching, and we shouldn't use incomplete
            // search results
            if (System.nanoTime() > m_stopTime)
            {
                return 0;
            }
            
            // get the next move
            int move = criticalMoves[(criticalMovesCount - 1) - i];
            // apply the move to the board
            state.makeMove(move);
            // do a search to find the score of the node
            int score = -quiescence(state, ply + 1, depth - 1, -b, -a) >> 16;
            // undo the move
            state.unmakeMove();
            
            // check if the move is the best found so far and update the lower bound
            if (bestScore < score)
            {
                bestScore = score;
                
                if (a < bestScore)
                {
                    a = bestScore;
                    // alpha-beta prune
                    if (a >= b)
                    {
                        break;
                    }
                }
            }
        }
        return packMoveScore(0, bestScore);
    }
    
    /**
     * Packs the moves and the score into a single integer.
     * 
     * @param move
     *            The move to pack.
     * @param score
     *            The score to pack.
     * @return The packed move and score.
     */
    private static int packMoveScore(int move, int score)
    {
        return (score << 16) | (move & 0x3FFF);
    }
    
    /**
     * Updates the transposition table value for a node.
     */
    private void PutTTEntry(StateExplorer state, int depth, int a, int b, int score, int move)
    {
        int nodeType;
        if (score <= a)
        {
            nodeType = TranspositionTable.ALL_NODE;
        }
        else if (b <= score)
        {
            nodeType = TranspositionTable.CUT_NODE;
        }
        else
        {
            nodeType = TranspositionTable.PV_NODE;
        }
        m_transpositionTable.put(state.getHash(), nodeType, depth, score, move, state.getTurnNumber());
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
        // black.set(0, 3);
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
        black.set(1, 1);
        
        BitBoard white = new BitBoard();
        white.set(2, 4);
        white.set(4, 2);
        white.set(4, 3);
        white.set(4, 5);
        white.set(5, 1);
        white.set(5, 6);
        white.set(6, 2);
        
        // StateExplorer s = new StateExplorer(1, new State(black, white, 41));
        StateExplorer s = new StateExplorer(new TablutBoardState());
        
        StudentPlayerAlt player = new StudentPlayerAlt();
        s.makeMove(player.getBestMove(s, 10000000000L));
    }
    
    private static int Test(StateExplorer state, int depth)
    {
        int count = 1;
        if (depth == 0)
        {
            return count;
        }
        int[] moves = new int[StateExplorer.MAX_LEGAL_MOVES];
        int moveCount = state.getAllLegalMoves(moves);
        for (int i = 0; i < moveCount; i++)
        {
            state.makeMove(moves[i]);
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
