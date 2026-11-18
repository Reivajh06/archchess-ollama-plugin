package reivajh06.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Just enough FEN parsing to describe the board to the model and to reject impossible tile choices. */
final class Fen {

	private static final String ORDER = "KQRBNP";

	private final char[][] board = new char[8][8]; // [rank 0 = rank 8][file 0 = a]
	private final boolean whiteToMove;

	Fen(String fen) {
		String[] parts = fen.strip().split("\\s+");
		String[] ranks = parts[0].split("/");
		for (int r = 0; r < 8; r++) {
			int f = 0;
			for (char c : ranks[r].toCharArray()) {
				if (Character.isDigit(c)) f += c - '0';
				else board[r][f++] = c;
			}
		}
		whiteToMove = parts.length < 2 || parts[1].equals("w");
	}

	boolean whiteToMove() {
		return whiteToMove;
	}

	static boolean isSquare(String s) {
		return s != null && s.length() == 2 && s.charAt(0) >= 'a' && s.charAt(0) <= 'h' && s.charAt(1) >= '1' && s.charAt(1) <= '8';
	}

	/** @return the piece letter on that square (uppercase = white), or 0 when it is empty. */
	char pieceAt(String square) {
		return board[8 - (square.charAt(1) - '0')][square.charAt(0) - 'a'];
	}

	/** @return null when the tile holds a piece of the side to move that may still be chosen, otherwise why not. */
	String selectionProblem(String tile, Set<String> excluded) {
		if (!isSquare(tile)) return "A tile looks like e2.";
		char piece = pieceAt(tile);
		if (piece == 0) return "There is no piece on " + tile + ".";
		if (Character.isUpperCase(piece) != whiteToMove) return "The piece on " + tile + " is not yours.";
		if (excluded.contains(tile)) return "The piece on " + tile + " cannot move.";
		return null;
	}

	/** Tiles holding a piece of the side to move. */
	List<String> ownPieceTiles() {
		List<String> tiles = new ArrayList<>();
		for (int r = 0; r < 8; r++)
			for (int f = 0; f < 8; f++) {
				char c = board[r][f];
				if (c != 0 && Character.isUpperCase(c) == whiteToMove) tiles.add("" + (char) ('a' + f) + (8 - r));
			}
		return tiles;
	}

	/** True when a pawn of the side to move that goes to {@code to} promotes. */
	boolean promotes(String from, String to) {
		return Character.toLowerCase(pieceAt(from)) == 'p' && to.charAt(1) == (whiteToMove ? '8' : '1');
	}

	/** Piece list per colour, e.g. "White: Ke1, Qd1, Pe2 ...": language models read this far better than a FEN. */
	String describe() {
		return "White: " + pieces(true) + "\nBlack: " + pieces(false);
	}

	private String pieces(boolean white) {
		List<String> list = new ArrayList<>();
		for (char type : ORDER.toCharArray())
			for (int r = 7; r >= 0; r--)
				for (int f = 0; f < 8; f++) {
					char c = board[r][f];
					if (c != 0 && Character.isUpperCase(c) == white && Character.toUpperCase(c) == type)
						list.add("" + type + (char) ('a' + f) + (8 - r));
				}
		return String.join(", ", list);
	}
}
