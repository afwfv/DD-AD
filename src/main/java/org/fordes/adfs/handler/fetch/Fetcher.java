package org.fordes.adfs.handler.fetch;

import jakarta.annotation.Nonnull;
import org.fordes.adfs.enums.HandleType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.Charset;

public abstract class Fetcher {

    protected @Nonnull Charset charset() {
        return Charset.defaultCharset();
    }

    public abstract Flux<String> fetch(@Nonnull String path);

    protected Flux<String> fetch(Flux<DataBuffer> buffers) {
        final StringBuilder lineBuffer = new StringBuilder();
        return buffers.flatMap(data -> {
                    String chunk = data.toString(this.charset());
                    DataBufferUtils.release(data);

                    lineBuffer.append(chunk);
                    String full = lineBuffer.toString();

                    String[] lines = full.split("\\r?\\n", -1);
                    int len = lines.length;

                    lineBuffer.setLength(0);

                    if (!full.endsWith("\n") && !full.endsWith("\r")) {
                        lineBuffer.append(lines[len - 1]);
                        len--;
                    }

                    return Flux.fromArray(lines).take(len);
                })
                .concatWith(Flux.defer(() -> {
                    if (lineBuffer.length() > 0) {
                        return Flux.just(lineBuffer.toString());
                    } else {
                        return Flux.empty();
                    }
                }));
    }

    public final static Fetcher getFetcher(HandleType type) {
        switch (type) {
            case LOCAL:
                return new LocalFetcher();
            case REMOTE:
                return new HttpFetcher();
        }
        throw new IllegalArgumentException("unsupported handle type: " + type);
    }
}
