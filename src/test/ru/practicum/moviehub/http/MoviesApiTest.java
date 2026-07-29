package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;


public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final String CT_JSON = "application/json; charset=UTF-8";
    private static final Gson GSON = new Gson();
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static final int EARLIEST_YEAR = 1888;
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final String CURRENT_INTERVAL_OF_YEARS = String.format("год должен быть между %d и %d", EARLIEST_YEAR, (CURRENT_YEAR + 1));

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clean();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovie_whenMovieExist_returnsArray() throws Exception {

        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body().trim();

        List<Movie> moviesFromResponse = GSON.fromJson(body, new ListOfMoviesTypeToken().getType());

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertEquals(1, moviesFromResponse.size(), "Ожидается ровно 1 фильм");
        assertEquals("Белоснежка", moviesFromResponse.getFirst().getTitle(), "Ожидается название единственного добавленного фильма");
        assertEquals(1970, moviesFromResponse.getFirst().getYear(), "Ожидается год выпуска единственного добавленного фильма");
        assertEquals(1, moviesFromResponse.getFirst().getId(), "Ожидается, что первому добавленному фильму присвоится Id = 1");
    }

    @Test
    void getTwoMovies_assignsSequentialIds() throws Exception {
        Movie movie1 = store.add(new Movie("Белоснежка", 1970));
        Movie movie2 = store.add(new Movie("Золушка", 1950));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body().trim();

        List<Movie> moviesFromResponse = GSON.fromJson(body, new ListOfMoviesTypeToken().getType());
        assertEquals(1, movie1.getId(), "У, добавленного первым, фильма Id = 1");
        assertEquals(2, movie2.getId(), "У, добавленного вторым, фильма Id = 2");

        List<Integer> ids = moviesFromResponse.stream()
                .map(Movie::getId)
                .toList();

        assertTrue(ids.contains(1) && ids.contains(2), "В списке Id двух фильмов ожидаются значения Id 1 и 2");
    }

    @Test
    void postValidMovie_returns201AndMovieWithId() throws IOException, InterruptedException {
        String json = GSON.toJson(new Movie("Белоснежка", 1970));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie body = GSON.fromJson(resp.body().trim(), Movie.class);

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        assertEquals("Белоснежка", body.getTitle(), "POST должен вернуть title из тела запроса");
        assertEquals(1970, body.getYear(), "POST должен вернуть year из тела запроса");
        assertEquals(1, body.getId(), "Сервер должен присвоить первый свободный id");
        assertEquals(1, store.getAll().size());
    }

    @Test
    void postMovieWithEmptyTitle_returns422AndErrorDetails() throws Exception {
        String json = "{\"title\": \"\", \"year\": 2000}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of("название не должно быть пустым"), body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithTitleWithOnlySpaces_returns422AndErrorDetails() throws Exception {
        String json = "{\"title\": \"   \", \"year\": 2000}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of("название не должно быть пустым"), body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithoutTitle_returns422AndErrorDetails() throws Exception {
        String json = "{\"year\": 2000}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of("название не должно быть пустым"), body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithYearBelowLowerBound_returns422AndErrorDetails() throws Exception {
        String json = "{\"title\": \"Белоснежка\", \"year\": 1887}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of(CURRENT_INTERVAL_OF_YEARS), body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithYearAtLowerBound_returns201AndMovieWithId() throws Exception {
        String json = "{\"title\": \"Белоснежка\", \"year\": 1888}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie body = GSON.fromJson(resp.body().trim(), Movie.class);

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        assertEquals("Белоснежка", body.getTitle(), "POST должен вернуть title из тела запроса");
        assertEquals(1888, body.getYear(), "POST должен вернуть year из тела запроса");
        assertEquals(1, body.getId(), "Сервер должен присвоить первый свободный id");
        assertEquals(1, store.getAll().size());
    }

    @Test
    void postMovieWithYearAtUpperBound_returns201AndMovieWithId() throws Exception {
        String json = "{\"title\": \"Белоснежка\", \"year\": " + (CURRENT_YEAR + 1) + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie body = GSON.fromJson(resp.body().trim(), Movie.class);

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        assertEquals("Белоснежка", body.getTitle(), "POST должен вернуть title из тела запроса");
        assertEquals((CURRENT_YEAR + 1), body.getYear(), "POST должен вернуть year из тела запроса");
        assertEquals(1, body.getId(), "Сервер должен присвоить первый свободный id");
        assertEquals(1, store.getAll().size());
    }

    @Test
    void postMovieWithYearAboveUpperBound_returns422AndErrorDetails() throws Exception {
        String json = "{\"title\": \"Белоснежка\", \"year\": " + (CURRENT_YEAR + 2) + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of(CURRENT_INTERVAL_OF_YEARS), body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithEmptyTitleAndInvalidYear_returns422AndTwoDetails() throws Exception {
        String json = "{\"title\": \"\", \"year\": " + (CURRENT_YEAR + 2) + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals(2, body.getDetails().size(), "Тело ответа 422 должно содержать 2 описания ошибки");
        assertTrue(body.getDetails().contains(CURRENT_INTERVAL_OF_YEARS), "В теле ответа 422 есть details о недействительном year");
        assertTrue(body.getDetails().contains("название не должно быть пустым"), "В теле ответа 422 есть details о недействительном title");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithTitleOf101Chars_returns422AndErrorDetails() throws Exception {
        String json = GSON.toJson(new Movie("a".repeat(101), 2000));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
        assertEquals("Ошибка валидации", body.getError(), "Тело ответа 422 должно содержать error с описанием ошибки");
        assertEquals(List.of("Максимальная длина title не должна быть больше 100 символов"),
                body.getDetails(), "Тело ответа 422 должно содержать details с причинами отказа");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithTitleOf100Chars_returns201AndMovieWithId() throws Exception {
        String title = "a".repeat(100);
        String json = GSON.toJson(new Movie(title, 2000));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie body = GSON.fromJson(resp.body().trim(), Movie.class);

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        assertEquals(title, body.getTitle(), "POST должен вернуть title из тела запроса");
        assertEquals(2000, body.getYear(), "POST должен вернуть year из тела запроса");
        assertEquals(1, body.getId(), "Сервер должен присвоить первый свободный id");
        assertEquals(1, store.getAll().size());
    }

    @Test
    void postMovieWithWrongContentType_returns415() throws Exception {
        String json = GSON.toJson(new Movie("Белоснежка", 1970));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");
        assertEquals(0, store.getAll().size());
    }

    @Test
    void postMovieWithoutContentType_returns415() throws Exception {
        String json = GSON.toJson(new Movie("Белоснежка", 1970));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");
        assertEquals(0, store.getAll().size());
    }

    @Test
    void postMovieWithMalformedJson_returns400AndErrorResponse() throws Exception {
        String json = "{ \"title\": ";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "POST /movies должен вернуть 400");
        assertEquals("Неправильный формат запроса", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithEmptyBody_returns400AndErrorResponse() throws Exception {
        String json = "";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "POST /movies должен вернуть 400");
        assertEquals("Неправильный формат запроса", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void postMovieWithNullLiteralBody_returns400AndErrorResponse() throws Exception {
        String json = "null";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "POST /movies должен вернуть 400");
        assertEquals("Неправильный формат запроса", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void getMovieById_WhenIdExists_returns200AndMovie() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body().trim();

        Movie movie = GSON.fromJson(body, Movie.class);

        assertEquals(200, resp.statusCode(), "GET /movies/1 должен вернуть 200");
        assertEquals(1, movie.getId(), "Сервер должен вернуть фильм с запрошенным id");
        assertEquals("Белоснежка", movie.getTitle(), "Тело ответа должно содержать title найденного фильма");
        assertEquals(1970, movie.getYear(), "Тело ответа должно содержать year найденного фильма");
    }

    @Test
    void getMovieById_WhenIdNotFound_returns404AndErrorResponse() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/2"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(404, resp.statusCode(), "GET /movies/2 должен вернуть 404");
        assertEquals("Фильм не найден", body.getError(), "Тело ответа 404 должно содержать error с описанием ошибки");
    }

    @Test
    void getMovieById_WhenIdIsNotNumber_returns400AndErrorResponse() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "GET /movies/abc должен вернуть 400");
        assertEquals("Некорректный ID", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
    }

    @Test
    void getMovieById_WhenPathHasTrailingSlash_returns400AndErrorResponse() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "GET /movies/ должен вернуть 400");
        assertEquals("Некорректный ID", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
    }

    @Test
    void getMovieById_WhenPathHasExtraSegment_returns404AndErrorResponse() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1/extra"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(404, resp.statusCode(), "GET /movies/1/extra должен вернуть 404");
        assertEquals("Фильм не найден", body.getError(), "Тело ответа 404 должно содержать error с описанием ошибки");
    }

    @Test
    void deleteMovieById_WhenIdExists_returns200() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode(), "DELETE /movies/1 должен вернуть 204");
        assertTrue(store.getAll().isEmpty(), "Фильм по Id должен быть удален");
    }

    @Test
    void deleteMovieById_WhenIdNotFound_returns404AndErrorResponse() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/2"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(404, resp.statusCode(), "DELETE /movies/2 должен вернуть 404");
        assertEquals("Фильм не найден", body.getError(), "Тело ответа 404 должно содержать error с описанием ошибки");
        assertFalse(store.getAll().isEmpty(), "Фильм не должен быть удален из хранилища");
    }

    @Test
    void deleteMovieById_WhenIdIsNotNumber_returns400AndErrorResponse() throws Exception {
        store.add(new Movie("Белоснежка", 1970));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "DELETE /movies/abc должен вернуть 400");
        assertEquals("Некорректный ID", body.getError(), "Тело ответа 400 должно содержать error с описанием ошибки");
        assertFalse(store.getAll().isEmpty(), "Фильм не должен быть удален из хранилища");
    }

    @Test
    void getMoviesByYear_WhenMoviesExist_returns200() throws Exception {
        Movie movie1 = new Movie("Белоснежка", 1970);
        Movie movie2 = new Movie("Золушка", 1970);
        Movie movie3 = new Movie("Рапунцель", 1980);

        store.add(movie1);
        store.add(movie2);
        store.add(movie3);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1970"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body().trim();

        List<Movie> moviesFromResponse = GSON.fromJson(body, new ListOfMoviesTypeToken().getType());
        List<String> titles = moviesFromResponse.stream()
                .map(Movie::getTitle)
                .toList();
        assertEquals(200, resp.statusCode(), "GET /movies?year=1970 должен вернуть 200");
        assertEquals(2, moviesFromResponse.size(), "В списке тела ответа ожидается 2 объекта");
        assertTrue(titles.contains("Белоснежка"), "В списке тела ответа ожидается фильм с year=1970");
        assertTrue(titles.contains("Золушка"), "В списке тела ответа ожидается фильм с year=1970");
    }

    @Test
    void getMoviesByYear_WhenMoviesNotExist_returns200AndEmptyList() throws Exception {
        Movie movie1 = new Movie("Белоснежка", 1970);
        Movie movie2 = new Movie("Золушка", 1970);
        Movie movie3 = new Movie("Рапунцель", 1980);

        store.add(movie1);
        store.add(movie2);
        store.add(movie3);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body().trim();

        List<Movie> moviesFromResponse = GSON.fromJson(body, new ListOfMoviesTypeToken().getType());

        assertEquals(200, resp.statusCode(), "GET /movies?year=2000 должен вернуть 200");
        assertTrue(moviesFromResponse.isEmpty(), "Список тела ответа должен быть пуст");
    }

    @Test
    void getMoviesByYear_WhenYearNotNumber_returns400AndErrorResponse() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "GET /movies?year=abc должен вернуть 400");
        assertEquals("Некорректный параметр запроса - 'year'", body.getError(),
                "Тело ответа 400 должно содержать error с описанием ошибки");
    }

    @Test
    void getMoviesByYear_WhenYearWithEmptyValue_returns400AndErrorResponse() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year="))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "GET /movies?year= должен вернуть 400");
        assertEquals("Некорректный параметр запроса - 'year'", body.getError(),
                "Тело ответа 400 должно содержать error с описанием ошибки");
    }

    @Test
    void getMoviesByYear_WhenParamIsNotYear_returns400AndErrorResponse() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?title=1980"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ErrorResponse body = GSON.fromJson(resp.body().trim(), ErrorResponse.class);

        assertEquals(400, resp.statusCode(), "GET /movies?title=1980 должен вернуть 400");
        assertEquals("Некорректный параметр запроса - 'year'", body.getError(),
                "Тело ответа 400 должно содержать error с описанием ошибки");
    }
}