package reivajh06.ollama;

import com.google.gson.Gson;
import reivajh06.plugins.PluginConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Stand-alone line protocol, useful to try the plugin without the backend:
 * {@code name}, {@code move <FEN>}, {@code config <LEVEL> [ms]}, {@code exit}. Replies start with {@code $archchess:}.
 * The first program argument, if present, is the model to use.
 */
public class Main {

	private static final String PREFIX = "$archchess:";
	private static final Gson GSON = new Gson();

	public static void main(String[] args) throws IOException {
		if(args.length > 0) OllamaPlugin.modelArgument(args[0]);

		OllamaPlugin plugin = new OllamaPlugin();
		String startError = null;
		try {
			plugin.start();
		} catch(RuntimeException e) {
			startError = e.getMessage();
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		for(String line; (line = in.readLine()) != null; ) {
			line = line.trim();
			if(line.isEmpty()) continue;
			if(line.equals("exit")) return;

			if(startError != null) { reply(Map.of("error", startError)); continue; }

			String[] parts = line.split("\\s+", 2);
			switch(parts[0]) {
				case "name" -> reply(Map.of("name", plugin.name()));
				case "config" -> reply(config(plugin, parts.length > 1 ? parts[1] : ""));
				case "move" -> {
					if(parts.length < 2) reply(Map.of("error", "move needs a FEN"));
					else reply(plugin.requestMove(parts[1]).message());
				}
				default -> reply(Map.of("error", "Unknown command: " + parts[0]));
			}
		}
	}

	private static Map<String, String> config(OllamaPlugin plugin, String arguments) {
		String[] parts = arguments.trim().split("\\s+");
		try {
			long ms = parts.length > 1 ? Long.parseLong(parts[1]) : 1000;
			plugin.sendParameters(new PluginConfiguration(PluginConfiguration.Difficulty.valueOf(parts[0].toUpperCase()), ms));
			return Map.of("config", "ok");
		} catch(IllegalArgumentException e) {
			return Map.of("error", "Invalid config: " + arguments);
		}
	}

	private static void reply(Map<String, String> body) {
		System.out.println(PREFIX + GSON.toJson(body));
	}
}
