package reivajh06.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Minimal HTTP client for the Ollama REST API (only what the plugin needs). */
class OllamaClient {

	private static final Gson GSON = new Gson();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final String baseUrl;

	OllamaClient(String host) {
		this.baseUrl = normalize(host);
	}

	/** Accepts null, "host", "host:port", "0.0.0.0:11434" or a full URL and returns "http://host:port". */
	static String normalize(String host) {
		if(host == null || host.isBlank()) return "http://127.0.0.1:11434";

		String value = host.trim();
		String scheme = "http";
		if(value.contains("://")) {
			scheme = value.substring(0, value.indexOf("://"));
			value = value.substring(value.indexOf("://") + 3);
		}
		while(value.endsWith("/")) value = value.substring(0, value.length() - 1);

		String name = value.contains(":") ? value.substring(0, value.lastIndexOf(':')) : value;
		String port = value.contains(":") ? value.substring(value.lastIndexOf(':') + 1) : "11434";

		if(name.isBlank() || name.equals("0.0.0.0")) name = "127.0.0.1";
		if(port.isBlank()) port = "11434";
		return scheme + "://" + name + ":" + port;
	}

	boolean isUp() {
		try {
			return send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags")).timeout(Duration.ofSeconds(3)).GET().build()).statusCode() == 200;
		} catch(IOException e) {
			return false;
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	List<String> models() throws IOException {
		JsonObject body = GSON.fromJson(get("/api/tags"), JsonObject.class);
		List<String> names = new ArrayList<>();
		JsonArray models = body.getAsJsonArray("models");
		if(models != null) for(JsonElement m : models) names.add(m.getAsJsonObject().get("name").getAsString());
		return names;
	}

	/** Sends one chat turn asking for JSON output and returns the text of the answer. */
	String chat(String model, String system, String user, double temperature) throws IOException {
		JsonObject request = new JsonObject();
		request.addProperty("model", model);
		request.addProperty("stream", false);
		request.addProperty("format", "json");
		request.add("messages", GSON.toJsonTree(List.of(
				Map.of("role", "system", "content", system),
				Map.of("role", "user", "content", user))));
		JsonObject options = new JsonObject();
		options.addProperty("temperature", temperature);
		request.add("options", options);

		HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
				.timeout(Duration.ofMinutes(5))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)))
				.build();

		try {
			HttpResponse<String> response = send(httpRequest);
			if(response.statusCode() != 200) throw new IOException("Ollama answered " + response.statusCode() + ": " + response.body());
			return GSON.fromJson(response.body(), JsonObject.class).getAsJsonObject("message").get("content").getAsString();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for Ollama", e);
		}
	}

	private String get(String path) throws IOException {
		try {
			HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10)).GET().build());
			if(response.statusCode() != 200) throw new IOException("Ollama answered " + response.statusCode());
			return response.body();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for Ollama", e);
		}
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
