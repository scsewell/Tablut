package student_player;

/**
 * Stores the score of board states that have been visited, so if they are
 * revisited along a different branch of the search tree there is no need to
 * re-explore the same child nodes.
 * 
 * The table is split into small chunks to avoid needing to allocate a massive
 * single table. That causes the JVM to be rather unhappy.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class TranspositionTable
{
    private static final int  NODE_TYPE_LEN   = 2;
    private static final long NODE_TYPE_MASK  = 0b0011L;
    
    private static final int  MOVE_LEN        = 14;
    private static final int  MOVE_SHIFT      = NODE_TYPE_LEN;
    private static final long MOVE_MASK       = 0b0011_1111_1111_1111L << MOVE_SHIFT;
    
    private static final int  SCORE_LEN       = 16;
    private static final int  SCORE_SHIFT     = MOVE_LEN + MOVE_SHIFT;
    private static final long SCORE_MASK      = 0b1111_1111_1111_1111L << SCORE_SHIFT;
    
    private static final int  DEPTH_LEN       = 5;
    private static final int  DEPTH_SHIFT     = SCORE_LEN + SCORE_SHIFT;
    private static final long DEPTH_MASK      = 0b0001_1111L << DEPTH_SHIFT;
    
    private static final int  AGE_LEN         = 7;
    private static final int  AGE_SHIFT       = DEPTH_LEN + DEPTH_SHIFT;
    private static final long AGE_MASK        = 0b0111_1111L << AGE_SHIFT;
    
    /**
     * The number of chunks the split the table into.
     */
    public static final int   TABLE_CHUNKS    = 64;
    
    /**
     * The cost per element stored in bytes.
     */
    private static final int  ELEMENT_SIZE    = 16;
    
    /**
     * How many turns ahead a new entry must be to replace older entries.
     */
    private static final int  REPLACEMENT_AGE = 12;
    
    /**
     * Node was not found in the table.
     */
    public static final int   NO_VALUE        = 0;
    
    /**
     * Node is a PV node, score is exact.
     */
    public static final int   PV_NODE         = 1;
    
    /**
     * Node is an all node, score is an upper bound.
     */
    public static final int   ALL_NODE        = 2;
    
    /**
     * Node is a cut node, score is a lower bound.
     */
    public static final int   CUT_NODE        = 3;
    
    private final long[][]    m_hashTable;
    private final long[][]    m_dataTable;
    private final int         m_capacity;
    private final int         m_chunkCapacity;
    
    /**
     * Constructs a transposition table.
     * 
     * @param size
     *            The size in megabytes to reserve for the transposition table.
     */
    public TranspositionTable(int size)
    {
        m_capacity = (size * 1024 * 1024) / ELEMENT_SIZE;
        m_chunkCapacity = m_capacity / TABLE_CHUNKS;
        
        m_hashTable = new long[TABLE_CHUNKS][m_chunkCapacity];
        m_dataTable = new long[TABLE_CHUNKS][m_chunkCapacity];
    }
    
    /**
     * Updates the transpositiontable with a value.
     * 
     * @param hash
     *            The hash of the state.
     * @param nodeType
     *            The type of node this state represents.
     * @param depth
     *            The depth of the state in the search.
     * @param score
     *            The score of the state.
     * @param move
     *            The best move at the state.
     * @param turnNumber
     *            The turn number at this state.
     */
    public void put(long hash, int nodeType, int depth, int score, int move, int turnNumber)
    {
        // get the index to check in the table
        int index = Math.abs((int)(hash % m_capacity));
        
        int chunkIndex = index % TABLE_CHUNKS;
        int arrayIndex = index % m_chunkCapacity;
        
        // check if there is an element already stored in the table
        boolean canReplace = m_hashTable[chunkIndex][arrayIndex] == 0;
        
        // if there is something in the table already only replace it if it is no longer
        // useful
        if (!canReplace)
        {
            long data = m_dataTable[chunkIndex][arrayIndex];
            
            int entryDepth = (int)(data & DEPTH_MASK) >>> DEPTH_SHIFT;
            int entryAge = (int)((data & AGE_MASK) >>> AGE_SHIFT);
            
            canReplace = entryDepth < depth || (turnNumber - entryAge) >= REPLACEMENT_AGE;
        }
        
        // if updating the entry store the hash and values
        if (canReplace)
        {
            long value = ((long)turnNumber << AGE_SHIFT) | ((long)depth << DEPTH_SHIFT) | (((long)score << SCORE_SHIFT) & SCORE_MASK) | (((long)move << MOVE_SHIFT) & MOVE_MASK) | nodeType;
            
            m_hashTable[chunkIndex][arrayIndex] = hash;
            m_dataTable[chunkIndex][arrayIndex] = value;
        }
    }
    
    /**
     * Gets a node type, score, and best move from the transposition table.
     * 
     * @param hash
     *            The hash of the state whose information to retrieve.
     * @param depth
     *            The depth of the state in the search.
     * @param turnNumber
     *            The turn number at this state.
     * @return The node type in bits 0-1, the score in bits 2-17, the best move in
     *         bits 18-31, and the depth in bits 32-35.
     */
    public long get(long hash, int depth, int turnNumber)
    {
        // get the index to check in the table
        int index = Math.abs((int)(hash % m_capacity));
        
        int chunkIndex = index % TABLE_CHUNKS;
        int arrayIndex = index % m_chunkCapacity;
        
        // check if this hash represents the same value
        long storedHash = m_hashTable[chunkIndex][arrayIndex];
        if (storedHash == hash)
        {
            return m_dataTable[chunkIndex][arrayIndex];
        }
        return NO_VALUE;
    }
    
    /**
     * Gets the node type from a table value.
     * 
     * @param value
     *            The value returned from the transposition table.
     */
    public static int ExtractNodeType(long value)
    {
        return (int)(value & NODE_TYPE_MASK);
    }
    
    /**
     * Gets the node score from a table value.
     * 
     * @param value
     *            The value returned from the transposition table.
     */
    public static int ExtractScore(long value)
    {
        return (int)((value & SCORE_MASK) >> SCORE_SHIFT);
    }
    
    /**
     * Gets the best move from a table value.
     * 
     * @param value
     *            The value returned from the transposition table.
     */
    public static int ExtractMove(long value)
    {
        return (int)((value & MOVE_MASK) >>> MOVE_SHIFT);
    }
    
    /**
     * Gets the depth from a table value.
     * 
     * @param value
     *            The value returned from the transposition table.
     */
    public static int ExtractDepth(long value)
    {
        return (int)((value & DEPTH_MASK) >>> DEPTH_SHIFT);
    }
}
