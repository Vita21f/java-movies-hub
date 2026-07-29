package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.*;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private int counter = 0;

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie add(Movie movie) {
        counter += 1;
        movies.put(counter, movie);
        movie.setId(counter);
        return movie;
    }

    public void clean() {
        movies.clear();
        counter = 0;
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public void removeMovie(Movie movie) {
        movies.remove(movie.getId());
    }
}