package dev.fordes.adfs.validation.dns;

import dev.fordes.adfs.config.EffectiveConfig.DnsCacheConfig;
import dev.fordes.adfs.config.EffectiveConfig.DnsConfig;
import dev.fordes.adfs.error.DnsException;
import dev.fordes.adfs.rule.model.DomainName;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.*;
import org.xbill.DNS.lookup.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
public final class DnsjavaResolver implements DnsResolver {

    private static final int DEFAULT_DNS_PORT = 53;
    private final Cache cache;
    private final LookupSession session;
    private final Map<String, LongAdder> failureCauses = new ConcurrentHashMap<>();

    public DnsjavaResolver(DnsConfig config) {
        this.cache = createCache(config.cache());
        this.session = createSession(config, cache);
    }

    @Override
    public DnsResult resolve(DomainName domain) {
        Name name;
        try {
            name = Name.fromString(domain.value() + ".");
        } catch (TextParseException exception) {
            throw new DnsException("规范域名无法转换为 DNS Name: " + domain.value(), exception);
        }
        try {
            return queryAddress(name);
        } catch (DnsException | LookupFailedException exception) {
            log.debug("DNS 查询失败:  {} --> A/AAAA | {}", domain.value(), exception.getMessage());
            failureCauses.computeIfAbsent(classify(exception), _ -> new LongAdder()).increment();
            return DnsResult.FAILED;
        }
    }

    @Override
    public Map<String, Long> failureCauses() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        failureCauses.entrySet().stream()
                .sorted(Map.Entry.<String, LongAdder>comparingByValue(
                        Comparator.comparingLong(LongAdder::sum)).reversed())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().sum()));
        return snapshot;
    }

    /**
     * 把异常归到一个人能看懂的类别，用于区分"超时"（多半是并发过高或网络受限）
     * 与"SERVFAIL"、"连接被拒绝"（域名本身或权威服务器的问题）。
     */
    private static String classify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return "超时";
            }
            if (cause instanceof ServerFailedException) {
                return "SERVFAIL";
            }
        }
        String message = exception.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("timed out") || lower.contains("timeout")) {
                return "超时";
            }
            if (lower.contains("servfail")) {
                return "SERVFAIL";
            }
            if (lower.contains("refused")) {
                return "连接被拒绝";
            }
            if (lower.contains("unreachable") || lower.contains("no route")) {
                return "网络不可达";
            }
        }
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private DnsResult queryAddress(Name name) {
        try {
            LookupResult result = await(name, Type.A);
            if (!result.getRecords().isEmpty()) {
                return DnsResult.VALID;
            }
        } catch (NoSuchDomainException _) {
            return DnsResult.INVALID;
        } catch (NoSuchRRSetException _) {
            // A 不存在时继续查询 AAAA。
        }
        try {
            LookupResult result = await(name, Type.AAAA);
            return result.getRecords().isEmpty() ? DnsResult.INVALID : DnsResult.VALID;
        } catch (NoSuchDomainException | NoSuchRRSetException _) {
            return DnsResult.INVALID;
        }
    }

    private LookupResult await(Name name, int type) {
        try {
            return session.lookupAsync(name, type).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LookupFailedException lookupFailure) {
                throw lookupFailure;
            }
            throw new DnsException("DNS 异步查询异常: " + name + " --> " + Type.string(type), cause);
        }
    }

    @Override
    public int cacheSize() {
        return cache.getSize();
    }

    private static Cache createCache(DnsCacheConfig config) {
        Cache cache = new Cache(DClass.IN);
        cache.setMaxEntries(config.maxEntries());
        cache.setMaxCache(toSeconds(config.maxTtl()));
        cache.setMaxNCache(toSeconds(config.maxNegativeTtl()));
        return cache;
    }

    private static LookupSession createSession(DnsConfig config, Cache cache) {
        Resolver resolver = config.servers().isEmpty()
                ? createSystemResolver(config.timeout(), config.retries())
                : createConfiguredResolver(config.servers(), config.timeout(), config.retries());
        return LookupSession.builder()
                .resolver(resolver)
                .cache(cache)
                .clearSearchPath()
                .maxRedirects(config.maxCnameDepth())
                .build();
    }

    private static Resolver createSystemResolver(Duration timeout, int retries) {
        List<Resolver> resolvers = new ArrayList<>();
        for (InetSocketAddress address : ResolverConfig.getCurrentConfig().servers()) {
            SimpleResolver resolver = new SimpleResolver(address);
            resolver.setTimeout(timeout);
            resolvers.add(resolver);
        }
        if (resolvers.isEmpty()) {
            throw new DnsException("系统未提供 DNS 服务器");
        }
        return createExtendedResolver(resolvers, timeout, retries);
    }

    private static Resolver createConfiguredResolver(List<String> servers, Duration timeout, int retries) {
        List<Resolver> resolvers = new ArrayList<>();
        for (String server : servers) {
            InetSocketAddress address = parseServer(server);
            SimpleResolver resolver = new SimpleResolver(address);
            resolver.setTimeout(timeout);
            resolvers.add(resolver);
        }
        return createExtendedResolver(resolvers, timeout, retries);
    }

    private static ExtendedResolver createExtendedResolver(
            List<Resolver> resolvers, Duration timeout, int retries) {
        ExtendedResolver resolver = new ExtendedResolver(resolvers);
        int attempts = Math.incrementExact(retries);
        resolver.setRetries(attempts);
        int timeoutSlots = Math.addExact(Math.multiplyExact(resolvers.size(), attempts), 1);
        resolver.setTimeout(timeout.multipliedBy(timeoutSlots));
        return resolver;
    }

    private static InetSocketAddress parseServer(String server) {
        String address;
        int port = DEFAULT_DNS_PORT;
        if (server.startsWith("[")) {
            int end = server.indexOf(']');
            address = server.substring(1, end);
            if (end + 1 < server.length()) {
                port = Integer.parseInt(server.substring(end + 2));
            }
        } else {
            int separator = server.lastIndexOf(':');
            if (separator >= 0) {
                address = server.substring(0, separator);
                port = Integer.parseInt(server.substring(separator + 1));
            } else {
                address = server;
            }
        }
        try {
            return new InetSocketAddress(InetAddress.getByName(address), port);
        } catch (UnknownHostException exception) {
            throw new DnsException("已校验的 DNS 地址无法解析: " + server, exception);
        }
    }

    private static int toSeconds(Duration duration) {
        return Math.toIntExact(duration.toSeconds());
    }
}
