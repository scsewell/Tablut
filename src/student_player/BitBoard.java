package student_player;

/**
 * With the provided Board classes, computing values like how many opponent
 * pieces share a row with a given piece or the mirror of the board are quite
 * expensive, requiring looping over many coordinates. I wanted a way to do that
 * more efficiently.
 * 
 * bitboards pack boolean information about a board into numeric primitives.
 * This enables very fast comparisons of boards storing different types of
 * boolean information by using bitwise operations.
 * 
 * For example, suppose we have a bitboard which is true on squares where
 * placing a piece would capture an opponent, and another bitboard that is true
 * wherever a piece can be legally moved. If we bitwise AND these two boards and
 * count the true bits left, we can determine how many squares we can capture
 * opponents from this turn.
 * 
 * Since the board is 9 by 9, it has 81 squares, so we need at least 81 bits.
 * Using three integers gets us 96, with 15 wasted bits. The lower 27 bits in
 * each integer stores three rows of the bitboard. The most significant 5 bits
 * are ignored.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class BitBoard
{
    private static final int ROW_2_SHIFT = 9;
    private static final int ROW_3_SHIFT = 18;
    
    private static final int ROW_MASK_1  = 0x000001FF;
    private static final int ROW_MASK_2  = ROW_MASK_1 << ROW_2_SHIFT;
    private static final int ROW_MASK_3  = ROW_MASK_1 << ROW_3_SHIFT;
    
    private static final int BITS_MASK   = ROW_MASK_1 | ROW_MASK_2 | ROW_MASK_3;
    
    public int               d0;
    public int               d1;
    public int               d2;
    
    /**
     * Creates an empty BitBoard.
     */
    public BitBoard()
    {
    }
    
    /**
     * Constructs a new BitBoard from bitmask integers.
     * 
     * @param d0
     *            The bits for rows 0-2.
     * @param d1
     *            The bits for rows 3-5.
     * @param d1
     *            The bits for rows 6-8.
     */
    public BitBoard(int d0, int d1, int d2)
    {
        this.d0 = BITS_MASK & d0;
        this.d1 = BITS_MASK & d1;
        this.d2 = BITS_MASK & d2;
    }
    
    /**
     * Clones a BitBoard.
     * 
     * @param board
     *            The board to clone.
     */
    public BitBoard(BitBoard board)
    {
        d0 = board.d0;
        d1 = board.d1;
        d2 = board.d2;
    }
    
    /**
     * Copies the values from BitBoard.
     * 
     * @param board
     *            The board to copy the values of.
     */
    public void copy(BitBoard board)
    {
        d0 = board.d0;
        d1 = board.d1;
        d2 = board.d2;
    }
    
    /**
     * Checks if no bits on the board are set.
     * 
     * @return True if no bits are set.
     */
    public boolean isEmpty()
    {
        return (d0 | d1 | d2) == 0;
    }
    
    /**
     * Checks if all bits on the board are set.
     * 
     * @return True if all bits are set.
     */
    public boolean isFull()
    {
        return (d0 & d1 & d2) == BITS_MASK;
    }
    
    /**
     * Computes the cardinality of the board.
     * 
     * @return The number of set bits on the board.
     */
    public int cardinality()
    {
        return Integer.bitCount(d0) + Integer.bitCount(d1) + Integer.bitCount(d2);
    }
    
    /**
     * Get the value of a bit for given square on the board.
     * 
     * @param square
     *            The board square index.
     * @return True if the bit is set.
     */
    public boolean getValue(int square)
    {
        int row = square / 9;
        int col = square % 9;
        return getValue(col, row);
    }
    
    /**
     * Get the value of a bit for given square on the board.
     * 
     * @param row
     *            The square's row.
     * @param col
     *            The square's column.
     * @return True if the bit is set.
     */
    public boolean getValue(int col, int row)
    {
        switch (row)
        {
            case 0:
                return 0 != (d0 & (1 << col));
            case 1:
                return 0 != (d0 & (1 << col + ROW_2_SHIFT));
            case 2:
                return 0 != (d0 & (1 << col + ROW_3_SHIFT));
            case 3:
                return 0 != (d1 & (1 << col));
            case 4:
                return 0 != (d1 & (1 << col + ROW_2_SHIFT));
            case 5:
                return 0 != (d1 & (1 << col + ROW_3_SHIFT));
            case 6:
                return 0 != (d2 & (1 << col));
            case 7:
                return 0 != (d2 & (1 << col + ROW_2_SHIFT));
            case 8:
                return 0 != (d2 & (1 << col + ROW_3_SHIFT));
        }
        return false;
    }
    
    /**
     * Get the values contained in a row of the board.
     * 
     * @param row
     *            The row on the board to get the values of.
     * @return An integer containing the bits for the given row.
     */
    public int getRow(int row)
    {
        switch (row)
        {
            case 0:
                return (d0 & ROW_MASK_1);
            case 1:
                return (d0 & ROW_MASK_2) >> ROW_2_SHIFT;
            case 2:
                return (d0 & ROW_MASK_3) >> ROW_3_SHIFT;
            case 3:
                return (d1 & ROW_MASK_1);
            case 4:
                return (d1 & ROW_MASK_2) >> ROW_2_SHIFT;
            case 5:
                return (d1 & ROW_MASK_3) >> ROW_3_SHIFT;
            case 6:
                return (d2 & ROW_MASK_1);
            case 7:
                return (d2 & ROW_MASK_2) >> ROW_2_SHIFT;
            case 8:
                return (d2 & ROW_MASK_3) >> ROW_3_SHIFT;
        }
        return -1;
    }
    
    /**
     * Clears all bits on the board.
     */
    public void clear()
    {
        d0 = 0;
        d1 = 0;
        d2 = 0;
    }
    
    /**
     * Clears the bit for a given square on the board.
     * 
     * @param square
     *            The board square index.
     */
    public void clear(int square)
    {
        int row = square / 9;
        int col = square % 9;
        clear(col, row);
    }
    
    /**
     * Clears the bit for a given square on the board.
     * 
     * @param row
     *            The square's row.
     * @param col
     *            The square's column.
     */
    public void clear(int col, int row)
    {
        switch (row)
        {
            case 0:
                d0 &= ~(1 << col);
                break;
            case 1:
                d0 &= ~(1 << col + ROW_2_SHIFT);
                break;
            case 2:
                d0 &= ~(1 << col + ROW_3_SHIFT);
                break;
            case 3:
                d1 &= ~(1 << col);
                break;
            case 4:
                d1 &= ~(1 << col + ROW_2_SHIFT);
                break;
            case 5:
                d1 &= ~(1 << col + ROW_3_SHIFT);
                break;
            case 6:
                d2 &= ~(1 << col);
                break;
            case 7:
                d2 &= ~(1 << col + ROW_2_SHIFT);
                break;
            case 8:
                d2 &= ~(1 << col + ROW_3_SHIFT);
                break;
        }
    }
    
    /**
     * Sets the bit for a given square on the board.
     * 
     * @param square
     *            The board square index.
     */
    public void set(int square)
    {
        int row = square / 9;
        int col = square % 9;
        set(col, row);
    }
    
    /**
     * Sets the bit for a given square on the board.
     * 
     * @param col
     *            The square's column.
     * @param row
     *            The square's row.
     */
    public void set(int col, int row)
    {
        switch (row)
        {
            case 0:
                d0 |= (1 << col);
                break;
            case 1:
                d0 |= (1 << col + ROW_2_SHIFT);
                break;
            case 2:
                d0 |= (1 << col + ROW_3_SHIFT);
                break;
            case 3:
                d1 |= (1 << col);
                break;
            case 4:
                d1 |= (1 << col + ROW_2_SHIFT);
                break;
            case 5:
                d1 |= (1 << col + ROW_3_SHIFT);
                break;
            case 6:
                d2 |= (1 << col);
                break;
            case 7:
                d2 |= (1 << col + ROW_2_SHIFT);
                break;
            case 8:
                d2 |= (1 << col + ROW_3_SHIFT);
                break;
        }
    }
    
    /**
     * Sets the bit for a given row on the board.
     * 
     * @param row
     *            The row to set the value of.
     * @param value
     *            The value of the row.
     */
    public void setRow(int row, int value)
    {
        switch (row)
        {
            case 0:
                d0 = BITS_MASK & value;
                break;
            case 1:
                d0 = BITS_MASK & (value << ROW_2_SHIFT);
                break;
            case 2:
                d0 = BITS_MASK & (value << ROW_3_SHIFT);
                break;
            case 3:
                d1 = BITS_MASK & value;
                break;
            case 4:
                d1 = BITS_MASK & (value << ROW_2_SHIFT);
                break;
            case 5:
                d1 = BITS_MASK & (value << ROW_3_SHIFT);
                break;
            case 6:
                d2 = BITS_MASK & value;
                break;
            case 7:
                d2 = BITS_MASK & (value << ROW_2_SHIFT);
                break;
            case 8:
                d2 = BITS_MASK & (value << ROW_3_SHIFT);
                break;
        }
    }
    
    /**
     * Mirrors the board vertically.
     */
    public void mirrorVertical()
    {
        // exchange the top three and bottom three rows while flipping the first and
        // third row in each set of three
        int temp = d0;
        d0 = ((d2 << ROW_3_SHIFT) & ROW_MASK_3) | (d2 & ROW_MASK_2) | (d2 >> ROW_3_SHIFT);
        d1 = ((d1 << ROW_3_SHIFT) & ROW_MASK_3) | (d1 & ROW_MASK_2) | (d1 >> ROW_3_SHIFT);
        d2 = ((temp << ROW_3_SHIFT) & ROW_MASK_3) | (temp & ROW_MASK_2) | (temp >> ROW_3_SHIFT);
    }
    
    /**
     * Mirrors the board horizontally.
     */
    public void mirrorHorzontal()
    {
        final int k1_0 = 0b00000_001001001_001001001_001001001;
        final int k1_1 = k1_0 << 1;
        
        // switch the first and third columns in each of the 3 groups of 3 columns
        d0 = ((d0 >> 2) & k1_0) | (d0 & k1_1) | ((d0 & k1_0) << 2);
        d1 = ((d1 >> 2) & k1_0) | (d1 & k1_1) | ((d1 & k1_0) << 2);
        d2 = ((d2 >> 2) & k1_0) | (d2 & k1_1) | ((d2 & k1_0) << 2);
        
        final int k2_0 = 0b00000_000000111_000000111_000000111;
        final int k2_1 = k2_0 << 3;
        
        // switch the first and third groups of 3 columns
        d0 = ((d0 >> 6) & k2_0) | (d0 & k2_1) | ((d0 & k2_0) << 6);
        d1 = ((d1 >> 6) & k2_0) | (d1 & k2_1) | ((d1 & k2_0) << 6);
        d2 = ((d2 >> 6) & k2_0) | (d2 & k2_1) | ((d2 & k2_0) << 6);
    }
    
    /**
     * Reflects the board around the line row = col.
     */
    public void mirrorDiagonal()
    {
        final int k1_0 = 0b00000_100100100_010010010_001001001; // masks bits on reflection axis
        final int k1_1 = 0b00000_000000000_100100100_010010010;
        final int k1_2 = k1_1 << 8;
        final int k1_3 = 0b00000_001001001_000000000_100100100;
        
        // reflect the values in each of the 9 3x3 regions of the board
        d0 = (d0 & k1_0) | ((d0 & k1_1) << 8) | ((d0 & k1_2) >> 8) | ((d0 & k1_3) >> 16) | ((d0 & k1_3) << 16);
        d1 = (d1 & k1_0) | ((d1 & k1_1) << 8) | ((d1 & k1_2) >> 8) | ((d1 & k1_3) >> 16) | ((d1 & k1_3) << 16);
        d2 = (d2 & k1_0) | ((d2 & k1_1) << 8) | ((d2 & k1_2) >> 8) | ((d2 & k1_3) >> 16) | ((d2 & k1_3) << 16);
        
        final int k2_0 = 0b00000_000000111_000000111_000000111;
        final int k2_1 = k2_0 << 3;
        final int k2_2 = k2_0 << 6;
        
        int t0 = d0;
        int t1 = d1;
        int t2 = d2;
        
        // swap each the 9 3x3 regions of the board across the line of reflection
        d0 = (t0 & k2_0) | ((t1 & k2_0) << 3) | ((t2 & k2_0) << 6);
        d1 = (t1 & k2_1) | ((t0 & k2_1) >> 3) | ((t2 & k2_1) << 3);
        d2 = (t2 & k2_2) | ((t1 & k2_2) >> 3) | ((t0 & k2_2) >> 6);
    }
    
    /**
     * Reflects the board around the line row = -col.
     */
    public void mirrorAntiDiagonal()
    {
        final int k1_0 = 0b00000_001001001_010010010_100100100; // masks bits on reflection axis
        final int k1_1 = 0b00000_000000000_001001001_010010010;
        final int k1_2 = k1_1 << 10;
        final int k1_3 = 0b00000_100100100_000000000_001001001;
        
        // reflect the values in each of the 9 3x3 regions of the board
        d0 = (d0 & k1_0) | ((d0 & k1_1) << 10) | ((d0 & k1_2) >> 10) | ((d0 & k1_3) >> 20) | ((d0 & k1_3) << 20);
        d1 = (d1 & k1_0) | ((d1 & k1_1) << 10) | ((d1 & k1_2) >> 10) | ((d1 & k1_3) >> 20) | ((d1 & k1_3) << 20);
        d2 = (d2 & k1_0) | ((d2 & k1_1) << 10) | ((d2 & k1_2) >> 10) | ((d2 & k1_3) >> 20) | ((d2 & k1_3) << 20);
        
        final int k2_0 = 0b00000_000000111_000000111_000000111;
        final int k2_1 = k2_0 << 3;
        final int k2_2 = k2_0 << 6;
        
        int t0 = d0;
        int t1 = d1;
        int t2 = d2;
        
        // swap each the 9 3x3 regions of the board across the line of reflection
        d0 = (t0 & k2_2) | ((t1 & k2_2) >> 3) | ((t2 & k2_2) >> 6);
        d1 = (t1 & k2_1) | ((t0 & k2_1) << 3) | ((t2 & k2_1) >> 3);
        d2 = (t2 & k2_0) | ((t1 & k2_0) << 3) | ((t0 & k2_0) << 6);
    }
    
    /**
     * Rotates this board -90 degrees
     */
    public void rotateNeg90()
    {
        mirrorDiagonal();
        mirrorVertical();
    }
    
    /**
     * Rotates this board 90 degrees
     */
    public void rotate90()
    {
        mirrorVertical();
        mirrorDiagonal();
    }
    
    /**
     * Rotates this board 180 degrees
     */
    public void rotate180()
    {
        mirrorVertical();
        mirrorHorzontal();
    }
    
    /**
     * Applies a transformation to the board.
     * 
     * @param trasformCode
     *            The code specifying the transformation to apply.
     */
    public void applyTransform(int trasformCode)
    {
        switch (trasformCode)
        {
            case 1:
                mirrorVertical();
                break;
            case 2:
                rotate180();
                break;
            case 3:
                mirrorHorzontal();
                break;
            case 4:
                rotateNeg90();
                break;
            case 5:
                mirrorDiagonal();
                break;
            case 6:
                rotate90();
                break;
            case 7:
                mirrorAntiDiagonal();
                break;
        }
    }
    
    /**
     * Reverts a transformation applied to the board.
     * 
     * @param trasformCode
     *            The code specifying the transformation to undo.
     */
    public void undoTransform(int transformCode)
    {
        switch (transformCode)
        {
            case 1:
                mirrorVertical();
                break;
            case 2:
                rotate180();
                break;
            case 3:
                mirrorHorzontal();
                break;
            case 4:
                rotate90();
                break;
            case 5:
                mirrorDiagonal();
                break;
            case 6:
                rotateNeg90();
                break;
            case 7:
                mirrorAntiDiagonal();
                break;
        }
    }
    
    /**
     * Sets the board to the neighbors of all bits that are currently set.
     */
    public void toNeighbors()
    {
        final int k1 = 0b00000_011111111_011111111_011111111;
        final int k2 = 0b00000_111111110_111111110_111111110;
        
        int t1 = d1;
        d1 = ((d1 & k1) << 1) | ((d1 & k2) >> 1) | ((d1 << 9) & BITS_MASK) | (d1 >> 9) | ((d2 & ROW_MASK_1) << ROW_3_SHIFT) | (d0 >> ROW_3_SHIFT);
        d0 = ((d0 & k1) << 1) | ((d0 & k2) >> 1) | ((d0 << 9) & BITS_MASK) | (d0 >> 9) | ((t1 & ROW_MASK_1) << ROW_3_SHIFT);
        d2 = ((d2 & k1) << 1) | ((d2 & k2) >> 1) | ((d2 << 9) & BITS_MASK) | (d2 >> 9) | (t1 >> ROW_3_SHIFT);
    }
    
    /**
     * Flips the value of squares that neighbor an odd number of squares with set
     * bits.
     */
    public void toggleNeighbors()
    {
        final int k1 = 0b00000_011111111_011111111_011111111;
        final int k2 = 0b00000_111111110_111111110_111111110;
        
        int t1 = d1;
        d1 = ((d1 & k1) << 1) ^ ((d1 & k2) >> 1) ^ ((d1 << 9) & BITS_MASK) ^ (d1 >> 9) ^ ((d2 & ROW_MASK_1) << ROW_3_SHIFT) ^ (d0 >> ROW_3_SHIFT);
        d0 = ((d0 & k1) << 1) ^ ((d0 & k2) >> 1) ^ ((d0 << 9) & BITS_MASK) ^ (d0 >> 9) ^ ((t1 & ROW_MASK_1) << ROW_3_SHIFT);
        d2 = ((d2 & k1) << 1) ^ ((d2 & k2) >> 1) ^ ((d2 << 9) & BITS_MASK) ^ (d2 >> 9) ^ (t1 >> ROW_3_SHIFT);
    }
    
    /**
     * Sets this board to the bitwise NOT of itself.
     */
    public void not()
    {
        d0 = ~d0;
        d1 = ~d1;
        d2 = ~d2;
    }
    
    /**
     * Sets this board to the bitwise AND of itself and another board.
     * 
     * @param other
     *            The board to operate against.
     */
    public void and(BitBoard other)
    {
        d0 &= other.d0;
        d1 &= other.d1;
        d2 &= other.d2;
    }
    
    /**
     * Sets this board to the bitwise AND of itself and the inverse of another
     * board.
     * 
     * @param other
     *            The board to operate against.
     */
    public void andNot(BitBoard other)
    {
        d0 &= ~other.d0;
        d1 &= ~other.d1;
        d2 &= ~other.d2;
    }
    
    /**
     * Sets this board to the bitwise OR of itself and another board.
     * 
     * @param other
     *            The board to operate against.
     */
    public void or(BitBoard other)
    {
        d0 |= other.d0;
        d1 |= other.d1;
        d2 |= other.d2;
    }
    
    /**
     * Sets this board to the bitwise XOR of itself and another board.
     * 
     * @param other
     *            The board to operate against.
     */
    public void xor(BitBoard other)
    {
        d0 ^= other.d0;
        d1 ^= other.d1;
        d2 ^= other.d2;
    }
    
    /**
     * Gets the cardinality of the bitwise operation "a AND b".
     * 
     * @param a
     *            The first board.
     * @param b
     *            The second board.
     * @return The number of set bits after the operation.
     */
    public static int andCount(BitBoard a, BitBoard b)
    {
        int d0 = a.d0 & b.d0;
        int d1 = a.d1 & b.d1;
        int d2 = a.d2 & b.d2;
        return Integer.bitCount(d0) + Integer.bitCount(d1) + Integer.bitCount(d2);
    }
    
    /**
     * Gets the cardinality of the bitwise operation "a AND NOT(b)".
     * 
     * @param a
     *            The first board.
     * @param b
     *            The second board.
     * @return The number of set bits after the operation.
     */
    public static int andNotCount(BitBoard a, BitBoard b)
    {
        int d0 = a.d0 & ~b.d0;
        int d1 = a.d1 & ~b.d1;
        int d2 = a.d2 & ~b.d2;
        return Integer.bitCount(d0) + Integer.bitCount(d1) + Integer.bitCount(d2);
    }
    
    /**
     * Gets the cardinality of the bitwise operation "a OR b".
     * 
     * @param a
     *            The first board.
     * @param b
     *            The second board.
     * @return The number of set bits after the operation.
     */
    public static int orCount(BitBoard a, BitBoard b)
    {
        int d0 = a.d0 | b.d0;
        int d1 = a.d1 | b.d1;
        int d2 = a.d2 | b.d2;
        return Integer.bitCount(d0) + Integer.bitCount(d1) + Integer.bitCount(d2);
    }
    
    /**
     * Gets the cardinality of the bitwise operation "a XOR b".
     * 
     * @param a
     *            The first board.
     * @param b
     *            The second board.
     * @return The number of set bits after the operation.
     */
    public static int xorCount(BitBoard a, BitBoard b)
    {
        int d0 = a.d0 ^ b.d0;
        int d1 = a.d1 ^ b.d1;
        int d2 = a.d2 ^ b.d2;
        return Integer.bitCount(d0) + Integer.bitCount(d1) + Integer.bitCount(d2);
    }
    
    /**
     * Compares two bitboard istances.
     * 
     * @param a
     *            The first board.
     * @param b
     *            The second board.
     * @return 1 if a is greater, 0 if equal, -1 if less.
     */
    public static int comparaeTo(BitBoard a, BitBoard b)
    {
        int comp = Integer.compare(a.d2, b.d2);
        if (comp == 0)
        {
            comp = Integer.compare(a.d1, b.d1);
            if (comp == 0)
            {
                comp = Integer.compare(a.d0, b.d0);
            }
        }
        return comp;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof BitBoard)
        {
            BitBoard other = (BitBoard)obj;
            return d0 == other.d0 && d1 == other.d1 && d2 == other.d2;
        }
        return false;
    }
    
    @Override
    public String toString()
    {
        String str = System.lineSeparator();
        for (int row = 0; row < 9; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                str += getValue((row * 9) + col) ? '1' : '.';
            }
            str += System.lineSeparator();
        }
        return str;
    }
}
