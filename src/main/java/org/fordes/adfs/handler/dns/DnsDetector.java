package org.fordes.adfs.handler.dns;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.*;
import io.netty.util.concurrent.Future;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.config.AdFSProperties;
import org.fordes.adfs.constant.Constants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

@Data
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.config.domain-detect", name = "enable", havingValue = "true")
public class DnsDetector {

    private final NioEventLoopGroup group;
    private final List<DnsNameResolver> resolvers;
    private final AtomicLong nextIndex;
    private final LongAdder cacheHits;

    public DnsDetector(AdFSProperties properties) {
        var config = properties.getConfig().domainDetect();
        int concurrency = config.concurrency();

        this.group = new NioEventLoopGroup(concurrency);
        this.resolvers = new ArrayList<>(concurrency);
        this.nextIndex = new AtomicLong(0);
        this.cacheHits = new LongAdder();


        //初始化
        DnsServerAddressStreamProvider provider = buildProvider(config.provider());
        DnsCache sharedCache = this.buildDnsCache(config.cacheTtlMin(), config.cacheTtlMax(), config.cacheNegativeTtl());
        DnsCnameCache sharedCnameCache = new DefaultDnsCnameCache(config.cacheTtlMin(), config.cacheTtlMax());
        IntStream.range(0, concurrency).forEach(i -> {
            EventLoop loop = group.next();
            DnsNameResolver resolver = new DnsNameResolverBuilder(loop)
                    .datagramChannelFactory(NioDatagramChannel::new)
                    .nameServerProvider(provider)
                    .queryTimeoutMillis(config.timeout())
                    .maxQueriesPerResolve(2)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .resolveCache(sharedCache)
                    .cnameCache(sharedCnameCache)
                    .optResourceEnabled(false)
                    .build();
            resolvers.add(resolver);
        });

        log.info("dns detector init success, concurrency:{}", concurrency);
    }

    private DnsServerAddressStreamProvider buildProvider(List<String> provider) {
        if (provider.isEmpty()) {
            return DnsServerAddressStreamProviders.platformDefault();
        }

        InetSocketAddress[] addresses = provider.stream()
                .map(e -> {
                    String[] parts = e.split(Constants.Symbol.COLON);
                    return new InetSocketAddress(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 53);
                })
                .toArray(InetSocketAddress[]::new);

        return new SequentialDnsServerAddressStreamProvider(addresses);
    }

    private DnsCache buildDnsCache(int minTtl, int maxTtl, int negativeTtl) {
        return new DefaultDnsCache(minTtl, maxTtl, negativeTtl) {
            @Override
            public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
                List<? extends DnsCacheEntry> result = super.get(hostname, additionals);
                if (result != null && !result.isEmpty()) {
                    cacheHits.increment();
                }
                return result;
            }
        };
    }

    public Mono<Boolean> lookup(String domain) {
        if (domain == null || domain.isEmpty()) {
            return Mono.just(true);
        }

        String normalizedDomain = domain.toLowerCase().trim();
        DnsNameResolver resolver = resolvers.get((int) (nextIndex.getAndIncrement() % resolvers.size()));
        return lookup(resolver, normalizedDomain);
    }

    private Mono<Boolean> lookup(DnsNameResolver resolver, String domain) {
        return Mono.create(sink -> {

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
                log.debug("dns check done, available: {}", resolvers.size());
            });
        });
    }


    @PreDestroy
    public void destroy() {
        try {
            log.info("dns detector shutdown, total queries:{}, cache hits:{}", nextIndex.get(), cacheHits.sum());

            resolvers.forEach(DnsNameResolver::close);
            group.shutdownGracefully().await(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error during shutdown", e);
        }
    }
}