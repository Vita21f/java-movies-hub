package ru.practicum.moviehub.http;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
    private static final int EARLIEST_YEAR = 1888;
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final String CURRENT_INTERVAL_OF_YEARS = String.format("год должен быть между %d и %d", EARLIEST_YEAR, (CURRENT_YEAR + 1));
    private static final int MAX_TITLE_LENGTH = 100;

    public MoviesHandler(MoviesStore store) {
        super(store);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        switch (method) {
            case "GET":
                processGet(ex);
                break;

            case "POST":
                processPost(ex);
                break;

            case "DELETE":
                processDelete(ex);
                break;

            default:
                sendMethodNotAllowed(ex);
                break;
        }
    }

    private void processGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.equalsIgnoreCase("/movies")) {
            String query = ex.getRequestURI().getQuery();

            if (query == null) {
                sendJson(ex, 200, GSON.toJson(store.getAll()));
                return;
            }

            Optional<List<Movie>> filteredOpt = findMoviesByQuery(ex, query);
            if (filteredOpt.isEmpty()) {
                return;
            }
            sendJson(ex, 200, GSON.toJson(filteredOpt.get()));
            return;
        }

        Optional<Movie> movieOpt = findMovieByPath(ex);
        if (movieOpt.isEmpty()) {
            return;
        }
        sendJson(ex, 200, GSON.toJson(movieOpt.get()));
    }

    private void processPost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendUnsupportedMediaType(ex);
            return;
        }

        InputStream is = ex.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        Movie movie;
        try {
            movie = GSON.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Неправильный формат запроса");
            return;
        }

        if (movie == null) {
            sendError(ex, 400, "Неправильный формат запроса");
            return;
        }

        List<String> details = validate(movie);

        if (details.isEmpty()) {
            store.add(movie);
            sendJson(ex, 201, GSON.toJson(movie));
        } else {
            ErrorResponse error = new ErrorResponse("Ошибка валидации", details);
            sendJson(ex, 422, GSON.toJson(error));
        }
    }

    private void processDelete(HttpExchange ex) throws IOException {
        Optional<Movie> movieOptional = findMovieByPath(ex);
        if (movieOptional.isEmpty()) {
            return;
        }
        store.removeMovie(movieOptional.get());
        sendNoContent(ex);
    }

    private List<String> validate(Movie movie) {
        List<String> details = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            details.add("название не должно быть пустым");
        }

        int year = movie.getYear();
        if (year < EARLIEST_YEAR || year > (CURRENT_YEAR + 1)) {
            details.add(CURRENT_INTERVAL_OF_YEARS);
        }

        if (movie.getTitle() != null && movie.getTitle().length() > MAX_TITLE_LENGTH) {
            details.add("Максимальная длина title не должна быть больше " + MAX_TITLE_LENGTH + " символов");
        }

        return details;
    }

    private Optional<Movie> findMovieByPath(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String[] elements = path.split("/");

        if (elements.length > 3) {
            sendError(ex, 404, "Фильм не найден");
            return Optional.empty();
        }

        if (elements.length < 3) {
            sendError(ex, 400, "Некорректный ID");
            return Optional.empty();
        }

        int id;
        try {
            id = Integer.parseInt(elements[2]);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return Optional.empty();
        }

        Optional<Movie> movieOpt = store.getMovieById(id);

        if (movieOpt.isEmpty()) {
            sendError(ex, 404, "Фильм не найден");
            return Optional.empty();
        }
        return movieOpt;
    }

    private Optional<List<Movie>> findMoviesByQuery(HttpExchange ex, String query) throws IOException {
        String[] elements = query.split("=");

        if (elements.length != 2) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
            return Optional.empty();
        }

        if (!elements[0].equals("year")) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
            return Optional.empty();
        }

        int year;
        try {
            year = Integer.parseInt(elements[1]);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
            return Optional.empty();
        }

        return Optional.of(store.getAll().stream()
                .filter(movie -> movie.getYear() == year)
                .toList());
    }
}
