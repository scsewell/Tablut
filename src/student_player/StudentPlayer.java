package student_player;

import java.util.HashSet;
import java.util.Random;

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
        BitBoard b = new BitBoard();
        b.set(4, 8);
        b.set(3, 7);
        b.set(5, 7);
        b.set(2, 6);
        b.set(6, 6);
        b.set(1, 5);
        b.set(7, 5);
        b.set(0, 4);
        b.set(8, 4);
        b.set(0, 3);
        b.set(8, 3);
        b.set(1, 2);
        b.set(7, 2);
        b.set(2, 1);
        b.set(6, 1);
        b.set(3, 1);
        b.set(5, 1);
        b.set(4, 2);

        b.set(6, 4);

        // long time0 = 0;
        // for (int game = 0; game < 3000; game++)
        // {
        // TablutBoardState board = new TablutBoardState();
        //
        // for (int i = 0; i < 50; i++)
        // {
        // State state = new State(board);
        //
        // if (!state.isTerminal())
        // {
        // long t = System.nanoTime();
        // time0 += System.nanoTime() - t;
        // }
        //
        // if (!board.gameOver())
        // {
        // board.processMove((TablutMove)board.getRandomMove());
        // }
        // }
        // }
        // Log.Info(time0 / 1000000000.0f);
        TablutBoardState initialState = new TablutBoardState();

        StudentPlayer player = new StudentPlayer();
        player.chooseMove(initialState);

        Random rand = new Random();

        long time0 = 0;
        for (int game = 0; game < 1000; game++)
        {
            State state = new State(initialState);
            while (!state.isTerminal())
            {
                //Log.Info(state);
                long t = System.nanoTime();
                state.computeAllLegalMoves();
                time0 += System.nanoTime() - t;
                state.makeMove(state.legalMoves[rand.nextInt(state.legalMoveCount)]);
            }
            //Log.Info(state);
        }
        Log.Info(time0 / 1000000000.0f);

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
        // state.calculateHash();
        // state.calculateHash();
        // state.calculateHash();
        //
        // long time1 = 0;
        // long t = System.nanoTime();
        // for (int i = 0; i < 10000000; i++)
        // {
        // state.calculateHash();
        // }
        // time1 += System.nanoTime() - t;
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

        m_nodeCount = 0;
        int depth = 0;
        Log.Info("Turns left: " + state.remainingTurns());
        while (System.nanoTime() < m_stopTime && depth <= state.remainingTurns())
        {
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

        state.computeAllLegalMoves();
        for (int i = 0; i < state.legalMoveCount; i++)
        {
            if (System.nanoTime() > m_stopTime)
            {
                return bestValue;
            }

            State child = new State(state);
            child.makeMove(state.legalMoves[i]);

            int value = -Negamax(child, depth - 1, -b, -a, -sign);
            bestValue = Math.max(bestValue, value);
            a = Math.max(a, value);
            if (a >= b)
            {
                break;
            }
        }
        return bestValue;
    }

    private void NegamaxSearchOld(TablutBoardState state, int maxDepth)
    {
        NegamaxOld(state, maxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    }

    private int NegamaxOld(TablutBoardState state, int depth, int a, int b, int sign)
    {
        m_nodeCount++;

        if (depth == 0 || state.gameOver())
        {
            return 0;// sign * state.evaluate();
        }

        int bestValue = Integer.MIN_VALUE;
        for (TablutMove move : state.getAllLegalMoves())
        {
            if (System.nanoTime() > m_stopTime)
            {
                return bestValue;
            }

            TablutBoardState child = (TablutBoardState)state.clone();
            child.processMove(move);

            int value = -NegamaxOld(child, depth - 1, -b, -a, -sign);
            bestValue = Math.max(bestValue, value);
            a = Math.max(a, value);
            if (a >= b)
            {
                break;
            }
        }
        return bestValue;
    }
}
