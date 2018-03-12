package student_player;

import java.util.HashSet;
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
        
        // StudentPlayer player = new StudentPlayer();
        // player.chooseMove(initialState);
        
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
        Log.Info(time0 / 1000000000.0f);
        Log.Info(time1 / 1000000000.0f);
        
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
        
        Log.printMemoryUsage();
    }
    
    /**
     * Decides on a move to submit.
     */
    public Move chooseMove(TablutBoardState boardState)
    {
        // get the timeout for this turn
        int turn = boardState.getTurnNumber();
        long timeout = (turn == 1 ? START_TURN_TIMEOUT : TURN_TIMEOUT);
        
        // get the time we want to have a result by
        m_stopTime = System.nanoTime() + timeout;
        
        State state = new State(boardState);
        
        int depth = 0;
        Log.Info("Turns left: " + state.getRemainingTurns());
        while (System.nanoTime() < m_stopTime && depth <= state.getRemainingTurns())
        {
            m_nodeCount = 0;
            NegamaxSearch(state, depth);
            Log.Info(String.format("depth completed: %s  nodes expanded: %s", depth, m_nodeCount));
            depth++;
        }
        
        Move myMove = boardState.getRandomMove();
        Log.printMemoryUsage();
        return myMove;
    }
    
    private void NegamaxSearch(State state, int maxDepth)
    {
        Negamax(state, maxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    }
    
    private int Negamax(State state, int depth, int a, int b, int sign)
    {
        m_nodeCount++;
        
        if (depth == 0 || state.isTerminal())
        {
            return sign * state.evaluate();
        }
        
        int bestValue = Integer.MIN_VALUE;
        
        int[] legalMoves = state.getAllLegalMoves();
        for (int i = 0; i < legalMoves.length; i++)
        {
            if (System.nanoTime() > m_stopTime)
            {
                return bestValue;
            }
            
            long move = state.makeMove(legalMoves[i]);
            int value = -Negamax(state, depth - 1, -b, -a, -sign);
            state.unmakeMove(move);
            
            bestValue = Math.max(bestValue, value);
            a = Math.max(a, value);
            if (a >= b)
            {
                // break;
            }
        }
        return bestValue;
    }
}
