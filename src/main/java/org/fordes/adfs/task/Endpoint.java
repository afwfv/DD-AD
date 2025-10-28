package org.fordes.adfs.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.config.InputProperties;
import org.fordes.adfs.config.OutputProperties;
import org.fordes.adfs.handler.Parser;
import org.fordes.adfs.handler.rule.Handler;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

/**
 * @author fordes on 2024/4/10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Endpoint {

    private final ApplicationContext context;
    private final InputProperties input;
    private final OutputProperties output;
    private final Parser parser;

    @Bean
    ApplicationRunner start() {
        return args -> {
            long start = System.currentTimeMillis();
            this.initialize()
                    .thenMany(Flux.fromStream(input.stream()))
                    .flatMap(e -> parser.handle(e.getValue(), e.getKey()), 1)
                    .flatMap(rule -> Flux.fromIterable(output.getFiles())
                            .filter(file -> file.filter().contains(rule.getType()))
                            .flatMap(config -> {
                                Handler handler = Handler.getHandler(config.type());
                                String content = handler.format(rule);

                                return content == null
                                        ? Mono.empty()
                                        : Mono.just(Tuples.of(config, content));
                            }))
                    .groupBy(Tuple2::getT1, Tuple2::getT2)
                    .flatMap(group -> {
                        Path path = Path.of(output.getPath(), group.key().name());
                        return group
                                .bufferTimeout(5000, Duration.ofSeconds(1))
                                .concatMap(batch -> asyncBatchWrite(path, batch))
                                .subscribeOn(Schedulers.single());
                    })
                    .then()
                    .doFinally(signal -> {
                        log.info("all done, cost {}ms", System.currentTimeMillis() - start);
                        this.exit();
                    })
                    .block();
        };
    }

    private Mono<Void> initialize() {
        long start = System.currentTimeMillis();
        return Mono.just(output.getPath())
                .flatMapMany(p -> {
                    Path path = Path.of(p);
                    return Mono.fromCallable(() -> {
                        Files.createDirectories(path);
                        return p;
                    }).subscribeOn(Schedulers.boundedElastic()).flux();
                })
                .onErrorResume(ex -> {
                    log.error("create output dir failed", ex);
                    return Mono.empty();
                })
                .flatMap(dir ->
                        Flux.fromIterable(output.getFiles()).map(config -> {
                            String header = config.displayHeader(Handler.getHandler(config.type()), output.getFileHeader());
                            Path path = Path.of(dir, config.name());
                            return Tuples.of(header, path);
                        })
                )
                .flatMap(t -> {
                    String fileHeader = t.getT1();
                    Path path = t.getT2();
                    return Mono.fromCallable(() -> {

                        Files.writeString(
                                path,
                                fileHeader,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE
                        );

                        return true;
                    }).subscribeOn(Schedulers.boundedElastic()).then();
                }).doOnError(ex -> {
                    log.error("initialize failed", ex);
                    this.exit();
                })
                .doFinally(signal -> {
                    log.info("initialize done in {}ms", System.currentTimeMillis() - start);
                })
                .then();
    }

    private Mono<Void> asyncBatchWrite(Path path, List<String> batch) {
        return Mono.fromCallable(() -> {
                    Files.write(path, batch, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    return (Void) null;
                })
                .onErrorResume(e -> Mono.fromRunnable(() -> log.error("Write failed", e)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void exit() {
        int exit = SpringApplication.exit(this.context, () -> 0);
        System.exit(exit);
    }

}