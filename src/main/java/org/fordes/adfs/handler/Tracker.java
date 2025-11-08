package org.fordes.adfs.handler;

import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.config.AdFSProperties;
import org.fordes.adfs.constant.Constants;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

/**
 * @author Chengfs on 2025/10/31
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.config.tracking", name = "enable", havingValue = "true")
public class Tracker implements DisposableBean {

    private final Path file;
    private final Sinks.Many<String> sink;
    private final Disposable subscription;

    public Tracker(AdFSProperties properties) throws IOException {
        var config = properties.getConfig().tracking();

        this.file = Path.of(config.path());
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, Constants.Symbol.EMPTY, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        this.sink = Sinks.many().unicast().onBackpressureBuffer();
        this.subscription = sink.asFlux()
                .bufferTimeout(5000, Duration.ofSeconds(30))
                .filter(batch -> !batch.isEmpty())
                .concatMap(this::writeBatch)
                .retry()
                .subscribe();

        log.info("lost collector is enabled, path: {}", file);
    }

    public Mono<Void> write(String source, String ruleName, String rule) {
        String line = String.format("[%s] [%s] %s\n", source, ruleName, rule);
        return Mono.fromRunnable(() -> sink.tryEmitNext(line));
    }

    public void writeSync(String source, String ruleName, String rule) {
        write(source,ruleName, rule).subscribe();
    }

    private Mono<Void> writeBatch(List<String> lines) {
        return Mono.fromCallable(() -> {
                    StringBuilder sb = new StringBuilder(lines.size() * 100);
                    lines.forEach(sb::append);
                    Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public void destroy() throws Exception {
        sink.tryEmitComplete();
        if (subscription != null) {
            Thread.sleep(2000);
            subscription.dispose();
        }
    }
}