package reivajh06.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import reivajh06.plugins.Plugin;
import reivajh06.plugins.PluginConfiguration;
import reivajh06.plugins.PluginResponse;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI-type Archchess plugin that asks a local Ollama model for its move.
 * <p>
 * Archchess drives AI plugins in two steps: {@link #requestMove(String)} must answer which piece to move
 * ({@code selected_tile}) and {@link #requestMove(String, String)} then receives that piece's legal moves and answers
 * {@code from}, {@code to} and, if needed, {@code promote_to}. Only obviously invalid answers are filtered here;
 * the backend remains the authority on legality.
 */
public class OllamaPlugin implements Plugin {

	static final int MAX_ATTEMPTS = 4;
	private static final Gson GSON = new Gson();

	private static final String SYSTEM = "You are a chess player. Answer ONLY with a JSON object, no explanations.";

	private static volatile String modelArgument;

	private volatile OllamaClient client;
	private volatile String model;
	private volatile double temperature = 0.7;
	private volatile boolean ready;

	/** Cheap on purpose: ServiceLoader instantiates it; heavy work belongs in {@link #start()}. */
	public OllamaPlugin() {
	}

	/** Used by {@link Main} to pass the model given as the first program argument. */
	static void modelArgument(String value) {
		modelArgument = value;
	}

	@Override
	public String name() {
		return "Ollama";
	}

	@Override
	public PluginType type() {
		return PluginType.AI;
	}

	@Override
	public synchronized void start() {
		if(ready) return;

		OllamaClient candidate = new OllamaClient(System.getenv("OLLAMA_HOST"));
		if(!candidate.isUp()) launchServer(candidate);

		List<String> installed;
		try {
			installed = candidate.models();
		} catch(IOException e) {
			throw new IllegalStateException("Local llm not installed", e);
		}
		if(installed.isEmpty()) throw new IllegalStateException("Local llm not installed");

		String requested = firstNonBlank(modelArgument, System.getenv("OLLAMA_MODEL"), System.getProperty("ollama.model"));
		if(requested != null && !installed.contains(requested)) {
			String withTag = requested + ":latest";
			if(installed.contains(withTag)) requested = withTag;
			else throw new IllegalStateException("Model '" + requested + "' is not installed. Installed: " + installed);
		}

		this.client = candidate;
		this.model = requested != null ? requested : installed.getFirst();
		this.ready = true;
	}

	@Override
	public boolean isReady() {
		return ready;
	}

	@Override
	public boolean sendParameters(PluginConfiguration configuration) {
		temperature = switch(configuration.difficulty()) {
			case EASY -> 1.2;
			case MEDIUM -> 0.7;
			case HARD -> 0.3;
			case MAX -> 0.0;
		};
		return true;
	}

	/** Step 1: which piece to move. */
	@Override
	public PluginResponse requestMove(String fen) {
		PluginResponse notReady = checkReady();
		if(notReady != null) return notReady;

		Board board;
		try {
			board = new Board(fen);
		} catch(RuntimeException e) {
			return error(e.getMessage());
		}

		String side = board.whiteToMove ? "White (uppercase letters)" : "Black (lowercase letters)";
		String prompt = "Chess position (FEN): " + fen + "\nYou play " + side + ". Choose ONE of your pieces to move now."
				+ " Answer as JSON: {\"selected_tile\":\"e2\"}";

		String lastProblem = "no answer";
		for(int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				JsonObject answer = ask(prompt);
				String tile = text(answer, "selected_tile", "from", "tile");
				if(tile == null || !Board.isSquare(tile)) { lastProblem = "not a square: " + tile; continue; }
				if(!board.isOwn(board.pieceAt(tile))) { lastProblem = "no piece of yours on " + tile; continue; }

				return new PluginResponse(Map.of("selected_tile", tile), Map.of("model", model, "attempts", String.valueOf(attempt)));
			} catch(IOException e) {
				return error("Ollama request failed: " + e.getMessage());
			} catch(JsonParseException e) {
				lastProblem = "answer was not valid JSON";
			}
		}
		return error("The model gave no valid piece after " + MAX_ATTEMPTS + " attempts (" + lastProblem + ")");
	}

	/** Step 2: pick among the legal moves of the selected piece ("e2e3 e2e4 ..."). */
	@Override
	public PluginResponse requestMove(String fen, String legalMoves) {
		PluginResponse notReady = checkReady();
		if(notReady != null) return notReady;

		List<String> legal = legalMoves == null ? List.of()
				: Arrays.stream(legalMoves.trim().toLowerCase(Locale.ROOT).split("\\s+")).filter(s -> !s.isEmpty()).toList();
		if(legal.isEmpty()) return error("The selected piece has no legal moves");

		Board board;
		try {
			board = new Board(fen);
		} catch(RuntimeException e) {
			return error(e.getMessage());
		}

		String prompt = "Chess position (FEN): " + fen + "\nThese are the ONLY moves allowed for the piece you chose: "
				+ String.join(" ", legal) + "\nPick the best one. Answer as JSON: {\"from\":\"e2\",\"to\":\"e4\"}"
				+ " (add \"promote_to\":\"queen|rook|bishop|knight\" if a pawn reaches the last rank).";

		String lastProblem = "no answer";
		for(int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				JsonObject answer = ask(prompt);
				String from = text(answer, "from");
				String to = text(answer, "to");
				if(from == null || to == null) { lastProblem = "missing from/to"; continue; }
				if(!legal.contains(from + to)) { lastProblem = from + to + " is not in the allowed list"; continue; }

				String promotion = text(answer, "promote_to", "promotion", "promoteTo");
				Map<String, String> move = new HashMap<>(Map.of("from", from, "to", to));
				if(board.isPromotion(from, to)) move.put("promote_to", promotionName(promotion));

				return new PluginResponse(move, Map.of("model", model, "attempts", String.valueOf(attempt)));
			} catch(IOException e) {
				return error("Ollama request failed: " + e.getMessage());
			} catch(JsonParseException e) {
				lastProblem = "answer was not valid JSON";
			}
		}
		return error("The model gave no valid move after " + MAX_ATTEMPTS + " attempts (" + lastProblem + ")");
	}

	// --- helpers -----------------------------------------------------------------------------------------------

	private JsonObject ask(String prompt) throws IOException {
		String content = client.chat(model, SYSTEM, prompt, temperature);
		JsonObject object = GSON.fromJson(content, JsonObject.class);
		if(object == null) throw new JsonParseException("empty answer");
		return object;
	}

	private PluginResponse checkReady() {
		return ready ? null : error("Local llm not installed");
	}

	private static PluginResponse error(String message) {
		return new PluginResponse(Map.of("error", message), Map.of());
	}

	private static String text(JsonObject object, String... keys) {
		for(String key : keys) {
			if(object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString().trim().toLowerCase(Locale.ROOT);
		}
		return null;
	}

	/** Maps whatever the model wrote ("q", "Queen", "knight"...) to the names the backend expects; queen by default. */
	static String promotionName(String value) {
		if(value == null || value.isBlank()) return "queen";
		return switch(value.trim().toLowerCase(Locale.ROOT).charAt(0)) {
			case 'r' -> "rook";
			case 'b' -> "bishop";
			case 'n', 'k' -> "knight";
			default -> "queen";
		};
	}

	private static String firstNonBlank(String... values) {
		for(String v : values) if(v != null && !v.isBlank()) return v;
		return null;
	}

	/** Tries to start "ollama serve" if the executable exists, then waits a few seconds for it. */
	private static void launchServer(OllamaClient candidate) {
		String executable = findExecutable();
		if(executable == null) return;

		try {
			new ProcessBuilder(executable, "serve").redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			for(int i = 0; i < 20 && !candidate.isUp(); i++) Thread.sleep(500);
		} catch(IOException e) {
			// handled by the caller: models() will fail and start() reports "Local llm not installed"
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String findExecutable() {
		String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "ollama.exe" : "ollama";
		String path = System.getenv("PATH");
		if(path != null) {
			for(String dir : path.split(File.pathSeparator)) {
				File candidate = new File(dir, exe);
				if(candidate.canExecute()) return candidate.getAbsolutePath();
			}
		}
		String localAppData = System.getenv("LOCALAPPDATA");
		if(localAppData != null) {
			File candidate = new File(localAppData, "Programs/Ollama/" + exe);
			if(candidate.canExecute()) return candidate.getAbsolutePath();
		}
		return null;
	}
}
