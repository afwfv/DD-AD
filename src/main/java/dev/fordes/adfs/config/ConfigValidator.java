package dev.fordes.adfs.config;

import dev.fordes.adfs.config.EffectiveConfig.*;
import dev.fordes.adfs.config.InputSpec.HttpSource;
import dev.fordes.adfs.config.InputSpec.LocalSource;
import dev.fordes.adfs.config.InputSpec.SourceLocation;
import dev.fordes.adfs.error.ConfigurationException;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.model.DomainName;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Singleton
@RequiredArgsConstructor
public final class ConfigValidator {

    private static final Duration MIN_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration MIN_READ_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_READ_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration MIN_DNS_TIMEOUT = Duration.ofMillis(500);
    private static final Duration MAX_DNS_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MIN_CACHE_TTL = Duration.ofSeconds(1);
    private static final Duration MAX_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration MAX_NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Pattern TRUE_TOKEN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.*$");
    private static final Pattern IPV4 = Pattern.compile(
            "(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");

    private final AdfsProperties adfs;
    private final InputLimitProperties inputLimits;
    private final HttpProperties http;
    private final RuleProperties rules;
    private final PreprocessorProperties preprocessor;
    private final ConversionProperties conversion;
    private final DnsProperties dns;
    private final DnsCacheProperties dnsCache;
    private final List<InputProperties> inputProperties;
    private final List<OutputProperties> outputProperties;

    public EffectiveConfig validate() {
        Path workingDir = Path.of("").toAbsolutePath().normalize();
        List<InputSpec> inputs = createInputs(workingDir);
        Path outputDir = adfs.getOutputDir().toAbsolutePath().normalize();
        List<OutputSpec> outputs = createOutputs(outputDir);

        validateDurations();
        validateUserAgent();
        validateRuleLimits(inputs);
        Set<String> whitelist = normalizeDomains(rules.getWhitelist(), "rules.whitelist");
        Set<String> trueTokens = validateTrueTokens();
        validateDnsServers();
        validateOutputDir(outputDir, workingDir, inputs, outputs);

        return new EffectiveConfig(
                outputDir,
                new InputLimits(inputLimits.getMaxSize(), inputLimits.getMaxLineLength()),
                new HttpConfig(http.getConnectTimeout(), http.getReadTimeout(), http.getMaxRedirects(),
                        http.getRetries(), http.getUserAgent()),
                new RuleConfig(rules.getMinLength(), rules.getMaxLength(), whitelist,
                        new PreprocessorConfig(preprocessor.getMaxDepth(), preprocessor.getMaxIncludeDepth(), trueTokens)),
                new ConversionConfig(conversion.isAllowExpansion(), conversion.isAllowReduction()),
                new DnsConfig(dns.isEnabled(), dns.isStrictMode(), dns.getServers(), dns.getConcurrency(),
                        dns.getTimeout(), dns.getRetries(), dns.getMaxCnameDepth(),
                        new DnsCacheConfig(dnsCache.getMaxEntries(), dnsCache.getMaxTtl(),
                                dnsCache.getMaxNegativeTtl())),
                inputs,
                outputs);
    }

    private List<InputSpec> createInputs(Path workingDir) {
        if (inputProperties.isEmpty()) {
            throw new ConfigurationException("adfs.input 必须至少包含一个输入");
        }
        Set<String> names = new HashSet<>();
        return inputProperties.stream()
                .sorted(Comparator.comparingInt(InputProperties::getIndex))
                .map(properties -> createInput(properties, workingDir, names))
                .toList();
    }

    private InputSpec createInput(InputProperties properties, Path workingDir, Set<String> names) {
        String name = properties.getName().strip();
        if (!names.add(name)) {
            throw new ConfigurationException("输入名称重复: " + name);
        }
        RuleType type = RuleType.parse(properties.getType());
        RuleDialect dialect = resolveDialect(type, properties.getDialect());
        SourceLocation source = parseSource(properties.getPath(), workingDir, name);
        return new InputSpec(name, source, type, dialect);
    }

    private static SourceLocation parseSource(String value, Path workingDir, String name) {
        String source = value.strip();
        String lowerSource = source.toLowerCase(Locale.ROOT);
        if (lowerSource.startsWith("http://") || lowerSource.startsWith("https://")) {
            try {
                URI uri = new URI(source);
                if (!uri.isAbsolute() || uri.getHost() == null || uri.getFragment() != null) {
                    throw new ConfigurationException("远程输入 URI 非法: " + name);
                }
                return new HttpSource(uri);
            } catch (URISyntaxException exception) {
                throw new ConfigurationException("远程输入 URI 非法: " + name, exception);
            }
        }
        if (URI_SCHEME.matcher(source).matches()) {
            throw new ConfigurationException("远程输入仅支持 HTTP/HTTPS: " + name);
        }
        try {
            return new LocalSource(workingDir.resolve(source).normalize().toAbsolutePath());
        } catch (InvalidPathException exception) {
            throw new ConfigurationException("本地输入路径非法: " + name, exception);
        }
    }

    private List<OutputSpec> createOutputs(Path outputDir) {
        if (outputProperties.isEmpty()) {
            throw new ConfigurationException("adfs.output 必须至少包含一个输出");
        }
        Set<Path> paths = new HashSet<>();
        String fileHeader = adfs.getFileHeader() == null ? FileHeaderTemplate.DEFAULT : adfs.getFileHeader();
        FileHeaderTemplate.validate(fileHeader, "adfs.config.file-header");
        return outputProperties.stream()
                .sorted(Comparator.comparingInt(OutputProperties::getIndex))
                .map(properties -> createOutput(properties, outputDir, paths, fileHeader))
                .toList();
    }

    private static OutputSpec createOutput(
            OutputProperties properties, Path outputDir, Set<Path> paths, String parentHeader) {
        Path relative;
        try {
            relative = Path.of(properties.getName().strip()).normalize();
        } catch (InvalidPathException exception) {
            throw new ConfigurationException("输出名称不是合法路径: " + properties.getIndex(), exception);
        }
        if (relative.toString().isEmpty() || relative.isAbsolute() || relative.startsWith("..")) {
            throw new ConfigurationException("输出路径必须是输出目录内的文件: " + properties.getName());
        }
        Path path = outputDir.resolve(relative).normalize();
        if (!path.startsWith(outputDir)
                || paths.stream().anyMatch(existing -> path.startsWith(existing) || existing.startsWith(path))) {
            throw new ConfigurationException("输出路径越界、重复或存在文件与目录冲突: " + properties.getName());
        }
        paths.add(path);
        RuleType type = RuleType.parse(properties.getType());
        RuleDialect dialect = resolveDialect(type, properties.getDialect());
        ContainerFormat container = resolveContainer(type, properties.getContainer());
        String field = "adfs.output[" + properties.getIndex() + "].file-header";
        String configured = properties.getFileHeader();
        String fileHeader = configured == null ? parentHeader : configured;
        if (container == ContainerFormat.JSON) {
            if (configured != null && !configured.isBlank()) {
                throw new ConfigurationException(field + " 不适用于 JSON 输出: " + relative);
            }
            fileHeader = "";
        }
        FileHeaderTemplate.validate(fileHeader, field);
        return new OutputSpec(relative, type, dialect, container, fileHeader.isBlank() ? "" : fileHeader);
    }

    private static RuleDialect resolveDialect(RuleType type, String configured) {
        RuleDialect dialect = configured == null ? defaultDialect(type) : RuleDialect.parse(configured);
        boolean supported = switch (type) {
            case ADBLOCK -> dialect == RuleDialect.CORE || dialect == RuleDialect.ADGUARD
                    || dialect == RuleDialect.ABP || dialect == RuleDialect.UBO;
            case DNS -> dialect == RuleDialect.ADGUARD;
            case MIHOMO -> dialect == RuleDialect.CLASSICAL || dialect == RuleDialect.DOMAIN
                    || dialect == RuleDialect.IPCIDR;
            case HOSTS, DNSMASQ, SING_BOX, SMARTDNS -> dialect == RuleDialect.NONE;
        };
        if (!supported) {
            throw new ConfigurationException("规则类型与方言不兼容: " + type.value() + " --> " + dialect.value());
        }
        return dialect;
    }

    private static RuleDialect defaultDialect(RuleType type) {
        return switch (type) {
            case ADBLOCK -> RuleDialect.CORE;
            case DNS -> RuleDialect.ADGUARD;
            case MIHOMO -> RuleDialect.CLASSICAL;
            case HOSTS, DNSMASQ, SING_BOX, SMARTDNS -> RuleDialect.NONE;
        };
    }

    private static ContainerFormat resolveContainer(RuleType type, String configured) {
        ContainerFormat container = configured == null ? defaultContainer(type) : ContainerFormat.parse(configured);
        boolean supported = switch (type) {
            case MIHOMO -> container == ContainerFormat.TEXT || container == ContainerFormat.YAML;
            case SING_BOX -> container == ContainerFormat.JSON;
            case ADBLOCK, DNS, HOSTS, DNSMASQ, SMARTDNS -> container == ContainerFormat.TEXT;
        };
        if (!supported) {
            throw new ConfigurationException(
                    "规则类型与容器不兼容: " + type.value() + " --> " + container.value());
        }
        return container;
    }

    private static ContainerFormat defaultContainer(RuleType type) {
        return switch (type) {
            case MIHOMO -> ContainerFormat.YAML;
            case SING_BOX -> ContainerFormat.JSON;
            case ADBLOCK, DNS, HOSTS, DNSMASQ, SMARTDNS -> ContainerFormat.TEXT;
        };
    }

    private void validateDurations() {
        requireRange(http.getConnectTimeout(), MIN_CONNECT_TIMEOUT, MAX_CONNECT_TIMEOUT, "http.connect-timeout");
        requireRange(http.getReadTimeout(), MIN_READ_TIMEOUT, MAX_READ_TIMEOUT, "http.read-timeout");
        requireRange(dns.getTimeout(), MIN_DNS_TIMEOUT, MAX_DNS_TIMEOUT, "dns.timeout");
        requireRange(dnsCache.getMaxTtl(), MIN_CACHE_TTL, MAX_CACHE_TTL, "dns.cache.max-ttl");
        requireRange(dnsCache.getMaxNegativeTtl(), MIN_CACHE_TTL, MAX_NEGATIVE_CACHE_TTL,
                "dns.cache.max-negative-ttl");
        if (dnsCache.getMaxNegativeTtl().compareTo(dnsCache.getMaxTtl()) > 0) {
            throw new ConfigurationException("dns.cache.max-negative-ttl 不得大于 dns.cache.max-ttl");
        }
    }

    private static void requireRange(Duration value, Duration minimum, Duration maximum, String field) {
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new ConfigurationException(field + " 超出允许范围");
        }
    }

    private void validateUserAgent() {
        boolean printableAscii = http.getUserAgent().chars().allMatch(character -> character >= 0x20 && character <= 0x7e);
        if (!printableAscii) {
            throw new ConfigurationException("http.user-agent 只能包含可打印 ASCII 字符");
        }
    }

    private void validateRuleLimits(List<InputSpec> inputs) {
        if (rules.getMinLength() > rules.getMaxLength()) {
            throw new ConfigurationException("rules.min-length 不得大于 rules.max-length");
        }
        boolean hasTextInput = inputs.stream().anyMatch(input -> input.type() != RuleType.SING_BOX);
        if (hasTextInput && rules.getMaxLength() > inputLimits.getMaxLineLength()) {
            throw new ConfigurationException("存在文本输入时 rules.max-length 不得大于 input.max-line-length");
        }
    }

    private static Set<String> normalizeDomains(List<String> domains, String field) {
        Set<String> normalized = new HashSet<>();
        for (String domain : domains) {
            try {
                normalized.add(new DomainName(domain).value());
            } catch (RuleProcessingException exception) {
                throw new ConfigurationException(field + " 包含非法域名: " + domain, exception);
            }
        }
        return Set.copyOf(normalized);
    }

    private Set<String> validateTrueTokens() {
        Set<String> tokens = new HashSet<>();
        for (String token : preprocessor.getTrueTokens()) {
            if (token == null || !TRUE_TOKEN.matcher(token).matches() || token.equals("false")) {
                throw new ConfigurationException("rules.preprocessor.true-tokens 包含非法 token: " + token);
            }
            tokens.add(token);
        }
        return Set.copyOf(tokens);
    }

    private void validateDnsServers() {
        for (String server : dns.getServers()) {
            if (server == null || server.isBlank()) {
                throw new ConfigurationException("dns.servers 不得包含空值");
            }
            String address = extractDnsAddress(server);
            if (!IPV4.matcher(address).matches() && !isIpv6Literal(address)) {
                throw new ConfigurationException("dns.servers 只接受 IP 字面量: " + server);
            }
        }
    }

    private static String extractDnsAddress(String server) {
        if (server.startsWith("[")) {
            int end = server.indexOf(']');
            if (end < 0 || end + 1 < server.length() && server.charAt(end + 1) != ':') {
                throw new ConfigurationException("IPv6 DNS 服务器格式非法: " + server);
            }
            validatePort(server.substring(end + 1), server);
            return server.substring(1, end);
        }
        int separator = server.lastIndexOf(':');
        if (separator >= 0) {
            validatePort(server.substring(separator), server);
            return server.substring(0, separator);
        }
        return server;
    }

    private static void validatePort(String suffix, String server) {
        if (suffix.isEmpty()) {
            return;
        }
        try {
            int port = Integer.parseInt(suffix.substring(1));
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException("端口越界");
            }
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("DNS 服务器端口非法: " + server, exception);
        }
    }

    private static boolean isIpv6Literal(String address) {
        if (address.indexOf(':') < 0 || address.indexOf('%') >= 0) {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed instanceof Inet6Address;
        } catch (UnknownHostException _) {
            return false;
        }
    }

    private static void validateOutputDir(
            Path outputDir, Path workingDir, List<InputSpec> inputs, List<OutputSpec> outputs) {
        Path parent = outputDir.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) {
            throw new ConfigurationException("output-dir 的父目录必须存在且可写: " + outputDir);
        }
        if (Files.isSymbolicLink(outputDir) || Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
            throw new ConfigurationException("output-dir 不得是符号链接或非目录对象: " + outputDir);
        }
        Set<Path> protectedDirectories = new HashSet<>();
        protectedDirectories.add(outputDir.getRoot());
        protectedDirectories.add(workingDir);
        protectedDirectories.add(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize());
        protectedDirectories.add(ProgramLocation.directory());
        List<Path> localInputs = inputs.stream()
                .map(InputSpec::source)
                .filter(LocalSource.class::isInstance)
                .map(LocalSource.class::cast)
                .map(LocalSource::path)
                .toList();
        for (OutputSpec output : outputs) {
            Path target = outputDir.resolve(output.path()).normalize();
            for (Path protectedDirectory : protectedDirectories) {
                if (protectedDirectory.startsWith(target)) {
                    throw new ConfigurationException("输出文件不得等于或包含受保护目录: " + target);
                }
            }
            for (Path input : localInputs) {
                if (target.startsWith(input) || input.startsWith(target)) {
                    throw new ConfigurationException(
                            "输出文件不得与本地输入路径冲突: " + target + " --> " + input);
                }
            }
        }
    }

}
