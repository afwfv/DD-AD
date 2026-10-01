package org.fordes.adfs.handler.fetch;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpFetcher extends Fetcher {

    private final WebClient webClient;
    private Integer connectTimeout = 10_000;
    private Integer readTimeout = 30_000;
    private Integer writeTimeout = 30_000;
    private Integer bufferSize = 4096;

    public HttpFetcher() {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeout) // 连接超时 10 秒
                .responseTimeout(Duration.ofSeconds(30))              // 响应超时 30 秒
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(this.readTimeout, TimeUnit.MILLISECONDS))   // 读超时
                        .addHandlerLast(new WriteTimeoutHandler(this.writeTimeout, TimeUnit.MILLISECONDS))  // 写超时
                );

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(this.bufferSize)
                )
                .build();

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
//                .defaultHeader(HttpHeaders.ACCEPT_CHARSET, this.charset().displayName())
//                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
                .build();
    }

    public HttpFetcher(Integer connectTimeout, Integer readTimeout, Integer writeTimeout, Integer bufferSize) {
        this();
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.bufferSize = bufferSize;
    }

    @Override
    public Flux<String> fetch(String path) {
        Flux<DataBuffer> data = webClient.get()
                .uri(URI.create(path))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .onErrorResume(e -> {
                    log.error("http rule => {}, fetch failed  => {}", path, e.getMessage(), e);
                    return Flux.empty();
                });

        return this.fetch(data);
    }

    @Override
    protected Charset charset() {
        return StandardCharsets.UTF_8;
    }

}
