package com.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientAutoConfiguration;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Date;

@Log
@SpringBootApplication
public class FfsClientApplication {

    @Bean
    WebClient webClient() {
        return WebClient.create("http://localhost:8080");
    }

    @Autowired Tracer tracer;

    @Bean
    CommandLineRunner demo(WebClient client) {
        return strings -> {
            Span span = tracer.createSpan("start");
            try {
                client.get().uri("/movies").retrieve().bodyToFlux(Movie.class)
                        .filter(movie -> movie.getTitle().equalsIgnoreCase("aeon flux")).flatMap(movie -> {
                    log.info("Will call movie with id [" + movie.getId() + "]");
                    return client.get().uri("/{id}/events", movie.getId()).retrieve().bodyToFlux(MovieEvent.class);
                }).subscribe(movieEvent -> log.info(movieEvent.toString()));
            } finally {
                log.info("Closing span");
                tracer.close(span);
            }
        };
    }

    public static void main(String[] args) {
        ExceptionUtils.setFail(true);
        SpringApplication.run(FfsClientApplication.class, args);
    }
}


@Data
@AllArgsConstructor
class MovieEvent {
    private Movie movie;
    private Date when;
}

@Data
@AllArgsConstructor
class Movie {
    private String id;
    private String title;
}