package org.fordes.adfs.handler.fetch;

import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.util.Util;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.nio.charset.Charset;
import java.nio.file.Path;

@Slf4j
public class LocalFetcher extends Fetcher {

    private int bufferSize = 4096;

    public LocalFetcher() {
        super();
    }

    public LocalFetcher(int bufferSize) {
        super();
        this.bufferSize = bufferSize;
    }

    @Override
    public Flux<String> fetch(String path) {

        Flux<DataBuffer> data = Flux.just(path)
                .map(Util::normalizePath)
                .map(Path::of)
                .flatMap(p -> DataBufferUtils.read(p, new DefaultDataBufferFactory(), this.bufferSize))
                .onErrorResume(e -> {
                    log.error("local rule => {}, read failed  => {}", path, e.getMessage(), e);
                    return Flux.empty();
                });

        return this.fetch(data);
    }

    @Override
    protected Charset charset() {
        return super.charset();
    }

}
