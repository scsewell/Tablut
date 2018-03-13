package student_player;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * Stores board positions that have been visited and important information about
 * them.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class TranspositionTable extends LinkedHashMap<Long, TranspositionData>
{
    /**
     * The maximum number of elements in the transposition table.
     */
    private static final int MAX_SIZE = 100000000;
    
    /**
     * Constructs a transposition table.
     */
    public TranspositionTable()
    {
        super(MAX_SIZE + 1, 0.75f);
    }
    
    @Override
    protected boolean removeEldestEntry(Entry<Long, TranspositionData> eldest)
    {
        return size() > MAX_SIZE;
    }
}
