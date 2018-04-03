package student_player;

/**
 * Implements the killer heuristic by keeping track of moves that caused a beta
 * cut-offs at each ply. These moves are good to try ealier in the move list in
 * other braches at the same ply.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class KillerTable
{
    /**
     * The killer moves table size starting at ply zero.
     */
    private static final int    KILLER_MOVES_BASE_COUNT = 6;
    
    /**
     * Factor by which to exponentially increase the killer move table size as ply
     * increases.
     */
    private static final double KILLER_MOVES_GROWTH_FAC = 0.265;
    
    /**
     * The max size of the killer moves table per ply.
     */
    private static final int    KILLER_MOVES_MAX        = 24;
    
    private final int           m_maxPlys;
    private final int[][]       m_killerMoves;
    private final int[]         m_killerCount;
    private final int[]         m_killerOldest;
    
    /**
     * Creates a new killer table.
     * 
     * @param maxPlyCount
     *            The maxiumum number of plys to hold killer moves for.
     */
    public KillerTable(int maxPlyCount)
    {
        m_maxPlys = maxPlyCount;
        m_killerCount = new int[maxPlyCount];
        m_killerOldest = new int[maxPlyCount];
        m_killerMoves = new int[maxPlyCount][];
        
        for (int ply = 0; ply < maxPlyCount; ply++)
        {
            int increment = Math.min((int)(Math.exp(KILLER_MOVES_GROWTH_FAC * ply) - 1), KILLER_MOVES_MAX);
            int size = Math.min(KILLER_MOVES_MAX, KILLER_MOVES_BASE_COUNT + increment);
            m_killerMoves[ply] = new int[size];
        }
    }
    
    /**
     * Clears the killer move table.
     */
    public void Clear()
    {
        for (int i = 0; i < m_maxPlys; i++)
        {
            m_killerCount[i] = 0;
            m_killerOldest[i] = 0;
        }
    }
    
    /**
     * Adds a killer move.
     * 
     * @param ply
     *            The ply of the search.
     * @param move
     *            The move played.
     */
    public void add(int ply, int move)
    {
        move &= 0x3FFF;
        
        int killerCount = m_killerCount[ply];
        int[] killerMoves = m_killerMoves[ply];
        
        // check if the killer move is already in the table
        for (int i = 0; i < killerCount; i++)
        {
            if (move == killerMoves[i])
            {
                return;
            }
        }
        
        // add the move to the table in an empty spot or replace the oldest entry.
        if (killerCount < killerMoves.length)
        {
            killerMoves[killerCount] = move;
            killerCount++;
            m_killerCount[ply] = killerCount;
        }
        else
        {
            int replace = m_killerOldest[ply];
            killerMoves[replace++] = move;
            if (replace == killerCount)
            {
                replace = 0;
            }
            m_killerOldest[ply] = replace;
        }
    }
    
    /**
     * Checks if a moves is a killer move.
     * 
     * @param ply
     *            The ply of the search.
     * @param move
     *            The move to check.
     * @return True if the move is a killer move.
     */
    public boolean contains(int ply, int move)
    {
        int killerCount = m_killerCount[ply];
        int[] killerMoves = m_killerMoves[ply];
        
        for (int i = 0; i < killerCount; i++)
        {
            if (move == killerMoves[i])
            {
                return true;
            }
        }
        return false;
    }
}
