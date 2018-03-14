package student_player;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Stack;

/**
 * Stores board positions that have been visited and important information about
 * them. Pools transposition data objects to remove pressure from the garbage
 * collector.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class TranspositionTable extends LinkedHashMap<Long, TranspositionData>
{
    /**
     * The maximum number of elements in the transposition table.
     */
    private static final int         MAX_SIZE = 1000000;
    
    private Stack<TranspositionData> m_pool;
    
    /**
     * Constructs a transposition table.
     */
    public TranspositionTable()
    {
        super(MAX_SIZE + 1, 0.75f, true);
        m_pool = new Stack<TranspositionData>();
    }
    
    /**
     * Gets a transposition data object.
     * 
     * @param hash
     *            The hash for the data.
     * @return A transposition data object.
     */
    public TranspositionData getNewData(long hash)
    {
        TranspositionData entry;
        
        // use a pooled object if any are available
        if (m_pool.size() > 0)
        {
            entry = m_pool.pop();
        }
        else
        {
            entry = new TranspositionData();
        }
        
        // add the entry to the table using the key
        put(hash, entry);
        // return the new entry
        return entry;
    }
    
    @Override
    protected boolean removeEldestEntry(Entry<Long, TranspositionData> eldest)
    {
        // pool the data object
        boolean remove = size() > MAX_SIZE;
        if (remove)
        {
            m_pool.push(eldest.getValue());
        }
        return remove;
    }
}
