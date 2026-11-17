package reivajh06.ollama;

/** Just enough FEN reading to reject obviously impossible answers (not a rules engine). */
class Board {

	private final char[][] squares = new char[8][8]; // [rank 0..7 = rank 1..8][file 0..7]
	final boolean whiteToMove;

	Board(String fen) {
		String[] fields = fen.trim().split("\\s+");
		String[] ranks = fields[0].split("/");
		if(ranks.length != 8) throw new IllegalArgumentException("Invalid FEN: " + fen);

		for(int r = 0; r < 8; r++) {
			int file = 0;
			for(char c : ranks[r].toCharArray()) {
				if(Character.isDigit(c)) file += c - '0';
				else squares[7 - r][file++] = c;
			}
		}
		whiteToMove = fields.length < 2 || fields[1].equals("w");
	}

	/** Piece letter ('P', 'n'...) at an algebraic square like "e4", or 0 when empty or invalid. */
	char pieceAt(String square) {
		if(!isSquare(square)) return 0;
		return squares[square.charAt(1) - '1'][square.charAt(0) - 'a'];
	}

	boolean isOwn(char piece) {
		return piece != 0 && Character.isUpperCase(piece) == whiteToMove;
	}

	boolean isPromotion(String from, String to) {
		char piece = pieceAt(from);
		return Character.toLowerCase(piece) == 'p' && (to.charAt(1) == '8' || to.charAt(1) == '1');
	}


	static boolean isSquare(String s) {
		return s != null && s.length() == 2 && s.charAt(0) >= 'a' && s.charAt(0) <= 'h' && s.charAt(1) >= '1' && s.charAt(1) <= '8';
	}
}
