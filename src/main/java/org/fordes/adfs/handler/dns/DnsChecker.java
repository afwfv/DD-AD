package org.fordes.adfs.handler.dns;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.*;
import io.netty.util.concurrent.Future;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.constant.Constants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.IntStream;

@Data
@Slf4j
@Component
@EnableConfigurationProperties(DnsChecker.Config.class)
public class DnsChecker {

    private final Config config;
    private final NioEventLoopGroup eventLoopGroup;
    private final DnsServerAddressStreamProvider provider;
    private final ArrayBlockingQueue<DnsNameResolver> resolvers;

    public DnsChecker(Config config) {
        this.config = config;
        if (config.enable) {
            this.eventLoopGroup = new NioEventLoopGroup(config.concurrency);
            this.resolvers = new ArrayBlockingQueue<>(config.concurrency);
            if (config.provider.isEmpty()) {
                this.provider = DnsServerAddressStreamProviders.platformDefault();
            } else {
                InetSocketAddress[] array = config.provider.stream().map(e -> {
                    String[] split = e.split(Constants.COLON);
                    return new InetSocketAddress(split[0], split.length > 1 ? Integer.parseInt(split[1]) : 53);
                }).toArray(InetSocketAddress[]::new);

                this.provider = new SequentialDnsServerAddressStreamProvider(array);
            }

            final DnsCache cache = new DefaultDnsCache(5, 60, 60);
            final DnsCnameCache cnameCache = new DefaultDnsCnameCache(5, 60);
            IntStream.range(0, config.concurrency)
                    .forEach(i -> {
                        EventLoop eventLoop = this.eventLoopGroup.next();
                        DnsNameResolver resolver = new DnsNameResolverBuilder(eventLoop)
                                .datagramChannelFactory(NioDatagramChannel::new)
                                .socketChannelFactory(NioSocketChannel::new)
                                .nameServerProvider(this.provider)
                                .queryTimeoutMillis(config.timeout)
                                .maxQueriesPerResolve(1)
                                .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                                .resolveCache(cache)
                                .cnameCache(cnameCache)
                                .build();
                        resolvers.add(resolver);
                    });

        } else {
            log.warn("dns check is disabled");
            this.resolvers = null;
            this.eventLoopGroup = null;
            this.provider = null;
        }
    }

    @ConfigurationProperties(prefix = "application.config.domain-detect")
    public record Config(Boolean enable, Integer timeout, Integer concurrency, List<String> provider) {

        public Config(Boolean enable, Integer timeout, Integer concurrency, List<String> provider) {
            this.enable = Optional.ofNullable(enable).orElse(Boolean.TRUE);
            this.timeout = Optional.ofNullable(timeout).orElse(1000);
            this.concurrency = Optional.ofNullable(concurrency).orElse(4);
            this.provider = Optional.ofNullable(provider).filter(e -> !e.isEmpty()).orElse(List.of());
        }
    }

    public Mono<Boolean> lookup(String domain) {
        return Mono.create(sink -> {
            if (resolvers == null) {
                sink.success(true);
                return;
            }

            DnsNameResolver resolver;
            try {
                resolver = resolvers.take();
            } catch (InterruptedException e) {
                sink.success(true);
                log.error("dns resolve interrupted", e);
                return;
            }

            Future<List<InetAddress>> future = resolver.resolveAll(domain);
            future.addListener(result -> {

                boolean res = true;
                if (!result.isSuccess()) {
                    Throwable cause = result.cause();

                    if (cause instanceof UnknownHostException) {
                        res = false;
                    } else {
                        log.warn("dns check failed: {} => {}", domain, cause.getMessage());
                    }
                }
                sink.success(res);
                resolvers.offer(resolver);
                log.debug("dns check done, available: {}", resolvers.size());
            });
        });
    }
}