package ru.practicum.moviehub.model;

public class Movie {
    private final String title;
    private final int year;
    private int id;

    public Movie(String title, int year) {
        this.title = title;
        this.year = year;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Movie{" +
                "title='" + title + '\'' +
                ", year=" + year +
                '}';
    }
}