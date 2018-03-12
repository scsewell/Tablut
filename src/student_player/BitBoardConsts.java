package student_player;

/**
 * Stores pre-generated BitBoards to save on computation time.
 * 
 * @author Scott Sewell, ID: 260617022
 */
public class BitBoardConsts
{
    /**
     * The corner squares.
     */
    public static final BitBoard      corners;

    /**
     * The center square.
     */
    public static final BitBoard      center;

    /**
     * Squares that only the king is allowed to move onto.
     */
    public static final BitBoard      onlyKingAllowed;

    /**
     * Squares that any piece is allowed to move onto.
     */
    public static final BitBoard      anyPieceAllowed;

    /**
     * Squares for which the king must be surrounded on all 4 sides to be captured.
     */
    public static final BitBoard      king4Surround;

    /**
     * Maps the square to a bitboard containing all the squares horizontally or
     * vertically adjacent to the square.
     */
    public static final BitBoard[]    oneCrosses         = new BitBoard[81];

    /**
     * Maps the square to a bitboard containing all the squares horizontally or
     * vertically separated by one square from the given square.
     */
    public static final BitBoard[]    twoCrosses         = new BitBoard[81];

    /**
     * Maps the row or column of a piece and the arrangement of all pieces on that
     * row/column to an integer whose set bits match the indices the piece may
     * legally move to on that row or column. Does not accound for movement rules
     * regarding the corners or center tile.
     */
    public static final int[][]      legalMoves       = new int[9][512];

    /**
     * Maps the square of a piece and the arrangement of all pieces on its row to a
     * bitboard indicating squares the piece can legally move horizontally onto.
     * Does not accound for movement rules regarding the corners or center tile.
     */
    private static final BitBoard[][] m_hLegalMoveBoards = new BitBoard[81][512];

    /**
     * Maps the square of a piece and the arrangement of all pieces on its column to
     * a bitboard indicating squares the piece can legally move vertically onto.
     * Does not accound for movement rules regarding the corners or center tile.
     */
    private static final BitBoard[][] m_vLegalMoveBoards = new BitBoard[81][512];

    /**
     * Static constructor. Initializes all the constant values.
     */
    static
    {
        corners = new BitBoard();
        corners.set(0, 0);
        corners.set(0, 8);
        corners.set(8, 0);
        corners.set(8, 8);

        center = new BitBoard();
        center.set(4, 4);

        onlyKingAllowed = new BitBoard(center);
        onlyKingAllowed.or(corners);

        anyPieceAllowed = new BitBoard(onlyKingAllowed);
        anyPieceAllowed.not();

        king4Surround = new BitBoard(center);
        king4Surround.set(3, 4);
        king4Surround.set(4, 3);
        king4Surround.set(4, 5);
        king4Surround.set(5, 4);

        for (int i = 0; i < 81; i++)
        {
            BitBoard oneCross = new BitBoard();
            oneCross.set(i);
            oneCross.toNeighbors();
            oneCrosses[i] = oneCross;

            BitBoard twoCross = new BitBoard(oneCross);
            twoCross.toggleNeighbors();
            twoCross.clear(i);
            twoCrosses[i] = twoCross;

            int row = i / 9;
            int col = i % 9;

            /*
             * Compute a bitboard giving the squares a piece can legally move to
             * horizontally given any combination of pieces sharing its row for every spot
             * the piece could be on the board. That's right, (9^2) * (2^9) = 41472
             * bitboards are required to store that! That takes a couple megabytes, but the
             * performance gain is worth it. Vertical moves are similar, but we need to
             * store the vertical move as though it were horizontal since we can't easily
             * access columns while rows are very fast to extract.
             */
            int colNum = (1 << col);
            int colNumInv = ~colNum;
            int leftMask = colNum - 1;
            int rightMask = ~leftMask;

            for (int j = 0; j < 256; j++)
            {
                int rowPieces = ((j & rightMask) << 1) | (j & leftMask);

                int precceding = rowPieces << (31 - col);
                int leftBlock = (precceding == 0) ? -1 : col - Integer.numberOfLeadingZeros(precceding);

                int following = rowPieces >> col;
                int rightBlock = (following == 0) ? 9 : col + Integer.numberOfTrailingZeros(following);

                int moves = ((1 << rightBlock) - 1) & ~((1 << (leftBlock + 1)) - 1) & colNumInv;

                rowPieces |= colNum;

                BitBoard hMoves = new BitBoard();
                hMoves.setRow(row, moves);
                m_hLegalMoveBoards[i][rowPieces] = hMoves;

                BitBoard vMoves = new BitBoard(hMoves);
                vMoves.mirrorDiagonal();
                m_vLegalMoveBoards[i][rowPieces] = vMoves;

                // Pack the index of the start and end of the move in the integer in the unused
                // higher bits.
                int start = (moves == 0) ? 1 : Integer.numberOfTrailingZeros(moves);
                int end = (moves == 0) ? 0 : 31 - Integer.numberOfLeadingZeros(moves);

                // Pack the index of the piece blocking movement in each direction. Use a
                // special code to indicate nothing is blocking movement.
                int startBlock = (start == 0) ? 15 : start - 1;
                int endBlock = (end == 8) ? 15 : end + 1;

                legalMoves[col][rowPieces] = moves | (start << 9) | (end << 13) | (startBlock << 17) | (endBlock << 21);
            }
        }
    }

    /**
     * Fills a bitboard with all the squares a piece may legally move. This does not
     * include the square the piece currently occupies.
     * 
     * @param square
     *            The index of the piece's board square.
     * @param isKing
     *            Indicates if this piece is the king.
     * @param occupied
     *            A bitboard that marks the location of every piece in play.
     * @param occupiedRefl
     *            A bitboard that is the diagonal mirror of the bitboard marking the
     *            location of every piece in play.
     * @param result
     *            The bitboard to put the legal moves into.
     */
    public static void getLegalMovesHorizontal(int square, boolean isKing, BitBoard occupied, BitBoard occupiedRefl,
            BitBoard result)
    {
        int row = square / 9;
        int col = square % 9;
        int squareRefl = (col * 9) + row;

        result.copy(m_hLegalMoveBoards[square][occupied.getRow(row)]);
        result.or(m_vLegalMoveBoards[squareRefl][occupiedRefl.getRow(col)]);

        if (!isKing)
        {
            result.and(anyPieceAllowed);
        }
    }

    /**
     * Gets all squares a piece may legally move horizontally to. This does not
     * include the square the piece currently occupies.
     * 
     * @param col
     *            The column of the board square.
     * @param row
     *            The row of the board square.
     * @param isKing
     *            Indicates if this piece is the king.
     * @param occupied
     *            A bitboard that marks the location of every piece in play.
     * @return An integer containing the legal moves for the row.
     */
    public static int getLegalMovesHorizontal(int col, int row, boolean isKing, BitBoard occupied)
    {
        int moves = legalMoves[col][occupied.getRow(row)];
        if (!isKing)
        {
            switch (row)
            {
                case 0:
                case 8:
                    moves &= 0b0_1111_1111_1111_1111_011111110;
                    break;
                case 4:
                    moves &= 0b0_1111_1111_1111_1111_111101111;
                    break;
            }
        }
        return moves;
    }

    /**
     * Gets all squares a piece may legally move vertically to. This does not
     * include the square the piece currently occupies.
     * 
     * @param col
     *            The column of the board square.
     * @param row
     *            The row of the board square.
     * @param isKing
     *            Indicates if this piece is the king.
     * @param occupiedRefl
     *            A bitboard that is the diagonal mirror of the bitboard marking the
     *            location of every piece in play.
     * @return An integer containing the legal moves for the column.
     */
    public static int getLegalMovesVertical(int col, int row, boolean isKing, BitBoard occupiedRefl)
    {
        int moves = legalMoves[row][occupiedRefl.getRow(col)];
        if (!isKing)
        {
            switch (col)
            {
                case 0:
                case 8:
                    moves &= 0b0_1111_1111_1111_1111_011111110;
                    break;
                case 4:
                    moves &= 0b0_1111_1111_1111_1111_111101111;
                    break;
            }
        }
        return moves;
    }
}
