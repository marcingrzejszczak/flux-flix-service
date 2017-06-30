package com.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class FfsServiceApplication {

    public static void main(String[] args) {
        ExceptionUtils.setFail(true);
        SpringApplication.run(FfsServiceApplication.class, args);
    }
}

@Component
class MovieHandler {

    private final FluxFlixService ffs;

    MovieHandler(FluxFlixService ffs) {
        this.ffs = ffs;
    }

    Mono<ServerResponse> all(ServerRequest request) {
        return ok().body(ffs.all(), Movie.class);
    }

    Mono<ServerResponse> byId(ServerRequest request) {
        return ok().body(ffs.byId(request.pathVariable("id")), Movie.class);
    }

    Mono<ServerResponse> events(ServerRequest request) {
        return ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(ffs.byId(request.pathVariable("id"))
                        .flatMapMany(ffs::streamStreams), MovieEvent.class);
    }
}

@RestController
@RequestMapping("/movies")
class FluxFlixRestController {

    private final FluxFlixService fluxFlixService;

    FluxFlixRestController(FluxFlixService fluxFlixService) {
        this.fluxFlixService = fluxFlixService;
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<MovieEvent> crossTheStreams(@PathVariable String id) {
        Mono<Movie> movieMono = fluxFlixService.byId(id);
        return fluxFlixService.streamStreams(movieMono.block());
    }

    @GetMapping("/{id}")
    Mono<Movie> byId(@PathVariable String id) {
        return fluxFlixService.byId(id);
    }

    @GetMapping
    Flux<Movie> all() {
        return fluxFlixService.all();
    }

}

@Log
@Service
class FluxFlixService {

    private final MovieRepository movieRepository;

    FluxFlixService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public Flux<MovieEvent> streamStreams(Movie movie) {
        return Flux.<MovieEvent>generate(sink -> sink.next(new MovieEvent(movie, new Date())))
                   .delayElements(Duration.ofSeconds(1))
        //alternatively:
//        return Flux.interval(Duration.ofSeconds(1))
//                .map(ignore -> new MovieEvent(movie, new Date()));
                .doFinally(s -> log.info("Streaming info on '" + movie.getTitle() +
                        "' ended: " + s));
    }

    public Flux<Movie> all() {
        return movieRepository.findAll();
    }

    public Mono<Movie> byId(String id) {
        return movieRepository.findById(id);
    }
}

@Log
@Component
class MovieDataCLR implements CommandLineRunner {

    private final MovieRepository movieRepository;

    MovieDataCLR(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public void run(String... strings) throws Exception {
        this.movieRepository
                .deleteAll()
                .thenMany(Flux.just("Flux Gordon", "Enter the Mono<Void>", "Back to the Future", "AEon Flux"))
                .map(title -> new Movie(UUID.randomUUID().toString(), title))
                .flatMap(movie -> movieRepository.save(movie))
                .doOnNext(m -> log.info("Saved movie \u001B[32m" + m.getTitle() + "\u001B[0m (id=" + m.getId() + ")"))
                .blockLast();
    }
}

interface MovieRepository extends ReactiveMongoRepository<Movie, String> {
}

@AllArgsConstructor
@Data
@Document
class Movie {
    @Id
    private String id;
    private String title;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class MovieEvent {
    private Movie movie;
    private Date when;
}
