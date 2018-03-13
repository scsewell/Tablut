package student_player;

/**
 * A class that helps manage board squares.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class BoardUtils
{
    /**
     * The board square transformation mapping.
     */
    public static int[][] TRANSFORMED_INDICIES = new int[8][81];

    /*
     * Creates all of the tile instances.
     */
    static
    {
        for (int transformation = 0; transformation < 8; transformation++)
        {
            for (int square = 0; square < 81; square++)
            {
                TRANSFORMED_INDICIES[transformation][square] = getTransformed(transformation, square);
            }
        }
    }

    /**
     * Gets a string represeting a move.
     * 
     * @param move
     *            The move integer.
     * @return A formatted string displaying the contents of the move.
     */
    public static String getMoveString(int move)
    {
        int from = move & 0x7F;
        int to = (move >> 7) & 0x7F;
        return String.format("(%s %s) -> (%s %s)", from % 9, from / 9, to % 9, to / 9);
    }

    /**
     * Gets the board square resulting from a transformation.
     * 
     * @param trasformCode
     *            The code giving the type of transformation.
     * @param index
     *            The index of the board square to transform.
     * @return The transformed board square index.
     */
    private static int getTransformed(int trasformCode, int index)
    {
        int row = index / 9;
        int col = index % 9;

        switch (trasformCode)
        {
            case 0:
                return index;
            case 1:
                return ((8 - row) * 9) + col;
            case 2:
                return ((8 - row) * 9) + (8 - col);
            case 3:
                return (row * 9) + (8 - col);
            case 4:
                return ((8 - col) * 9) + row;
            case 5:
                return (col * 9) + row;
            case 6:
                return (col * 9) + (8 - row);
            case 7:
                return ((8 - col) * 9) + (8 - row);
        }
        return -1;
    }

    /**
     * Gets the board square resulting from an inverse transformation.
     * 
     * @param trasformCode
     *            The code giving the type of transformation.
     * @param index
     *            The index of the board square to transform.
     * @return The transformed board square index.
     */
    private static int getUntransformed(int trasformCode, int index)
    {
        int row = index / 9;
        int col = index % 9;

        switch (trasformCode)
        {
            case 0:
                return index;
            case 1:
                return ((8 - row) * 9) + col;
            case 2:
                return ((8 - row) * 9) + (8 - col);
            case 3:
                return (row * 9) + (8 - col);
            case 4:
                return (col * 9) + (8 - row);
            case 5:
                return (col * 9) + row;
            case 6:
                return ((8 - col) * 9) + row;
            case 7:
                return ((8 - col) * 9) + (8 - row);
        }
        return -1;
    }
}
