package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.store.MoviesStore;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final Gson GSON = new Gson();
    protected final MoviesStore store;

    public BaseHttpHandler(MoviesStore store) {
        this.store = store;
    }

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(204, -1);
    }

    protected void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Allow", "GET, POST, DELETE");
        ex.sendResponseHeaders(405, -1);
    }

    protected void sendUnsupportedMediaType(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(415, -1);
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        ErrorResponse error = new ErrorResponse(message);
        sendJson(ex, status, GSON.toJson(error));
    }
}