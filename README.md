# Archchess Ollama plugin

![Alt text](ollama-logo.png)

An [Archchess](https://github.com/ReivajH06) plugin (type `AI`) that asks a locally installed [Ollama](https://ollama.com) model for its move.

## Install

1. Install Ollama and pull at least one model (`ollama pull qwen3:14b`).
2. Download `ollama.jar` from the [latest release](../../releases/latest) (requires Java 25) and drop it in Archchess' plugins folder.

If Ollama or its models are missing, `start()` throws `IllegalStateException("Local llm not installed")`
and the plugin answers `$archchess:{"error":"Local llm not installed"}`. If Ollama is installed but its
server is not running, the plugin tries to launch `ollama serve` first.

## Choosing the model

First match wins: first program argument, `OLLAMA_MODEL`, `-Dollama.model=...`; otherwise the first installed model.
`OLLAMA_HOST` is honored (`0.0.0.0` / missing port are normalized to `127.0.0.1:11434`).

## How it plays

The model gets the FEN and must answer JSON (`{"from":"e2","to":"e4"}`). Obviously invalid moves
(empty square, opponent's piece, own-piece capture, missing promotion) are rejected and the model is asked
again, up to 4 times. Full legality (checks, pins, blocked paths) is **not** verified here: the backend/frontend must still validate.
Difficulty maps to the sampling temperature (EASY 1.2, MEDIUM 0.7, HARD 0.3, MAX 0).

## Build and release

```bash
mvn package          # target/ollama.jar
git tag v1.0.0 && git push origin v1.0.0   # GitHub Actions publishes the release
```

## Protocol

Same as the Stockfish plugin: `name`, `move <FEN>`, `config <DIFFICULTY>`, `exit`; replies prefixed with `$archchess:`.
