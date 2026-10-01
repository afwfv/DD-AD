package dev.fordes.adfs.format.singbox;

import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import dev.fordes.adfs.source.SourceSession;
import dev.fordes.adfs.source.Utf8Reader;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class SingBoxParser implements RuleParser {

    private static final int MAX_NESTING_DEPTH = 32;
    private static final int MAX_NUMBER_LENGTH = 64;
    private final RuleConfig rules;
    private final JsonFactory factory;
    private boolean opaqueSeen;

    public SingBoxParser(RuleConfig rules) {
        this.rules = rules;
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxStringLength(rules.maxLength())
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .build();
        this.factory = JsonFactory.builder().streamReadConstraints(constraints)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        opaqueSeen = false;
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), new Utf8Reader(session.root()))) {
            if (log.isDebugEnabled()) {
                MDC.remove(RuleSpool.INPUT_RULE);
            }
            require(parser.nextToken(), JsonToken.START_OBJECT, parser, "顶层必须是对象");
            int version = 0;
            boolean ruleArray = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken(), JsonToken.PROPERTY_NAME, parser, "顶层字段名非法");
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (field.equals("version")) {
                    if (value != JsonToken.VALUE_NUMBER_INT) {
                        throw syntax(parser, "version 必须是整数");
                    }
                    int formatVersion = parser.getIntValue();
                    if (formatVersion < 1 || formatVersion > 3) {
                        throw syntax(parser, "不支持的 Sing-box version: " + formatVersion);
                    }
                    version = formatVersion;
                } else if (field.equals("rules")) {
                    require(value, JsonToken.START_ARRAY, parser, "rules 必须是数组");
                    ruleArray = true;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.currentToken() != JsonToken.START_OBJECT) {
                            session.invalidRule();
                            log.warn("Sing-box 规则语法非法, 已跳过:  {} --> Sing-box 解析 | {} --> {}",
                                    MDC.get(RuleSpool.INPUT), parser.currentToken(), "rules 元素必须是对象");
                            parser.skipChildren();
                            continue;
                        }
                        byte[] object;
                        try {
                            object = captureObject(parser);
                        } catch (RuleProcessingException | IllegalArgumentException exception) {
                            session.invalidRule();
                            log.warn("Sing-box 规则语法非法, 已跳过:  {} --> Sing-box 解析 | {} --> {}",
                                    MDC.get(RuleSpool.INPUT), "<已读取的规则对象>", exception.getMessage());
                            continue;
                        }
                        if (log.isDebugEnabled()) {
                            MDC.put(RuleSpool.INPUT_RULE, new String(object, StandardCharsets.UTF_8));
                        }
                        try {
                            consumer.accept(parseRule(object, 0));
                        } catch (RuleProcessingException | IllegalArgumentException exception) {
                            session.invalidRule();
                            log.warn("Sing-box 规则语法非法, 已跳过:  {} --> Sing-box 解析 | {} --> {}",
                                    MDC.get(RuleSpool.INPUT), new String(object, StandardCharsets.UTF_8),
                                    exception.getMessage());
                        }
                        if (log.isDebugEnabled()) {
                            MDC.remove(RuleSpool.INPUT_RULE);
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
            if (version == 0 || !ruleArray) {
                throw syntax(parser, "顶层缺少 version 或 rules");
            }
            if (opaqueSeen && version != 3) {
                throw syntax(parser, "旧版 Sing-box 未建模字段不能安全升级至输出版本 3: " + version);
            }
            if (parser.nextToken() != null) {
                throw syntax(parser, "顶层对象后存在额外 JSON 内容");
            }
            return ParseResult.COMPLETE;
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            session.invalidRule();
            log.error("Sing-box 来源结构非法, 已丢弃该来源:  {} --> Sing-box 解析 | 来源结构 --> {}",
                    MDC.get(RuleSpool.INPUT), exception.getMessage());
            return ParseResult.INVALID_SOURCE;
        } catch (RuleProcessingException exception) {
            session.invalidRule();
            log.error("Sing-box 来源语义非法, 已丢弃该来源:  {} --> Sing-box 解析 | 来源语义 --> {}",
                    MDC.get(RuleSpool.INPUT), exception.getMessage());
            return ParseResult.INVALID_SOURCE;
        }
    }

    private byte[] captureObject(JsonParser parser) throws IOException {
        long start = parser.currentTokenLocation().getCharOffset();
        byte[] object = copyObject(parser);
        long length = parser.currentLocation().getCharOffset() - start;
        if (length < rules.minLength() || length > rules.maxLength()) {
            throw syntax(parser, "单条 Sing-box 规则长度越界: " + length);
        }
        return object;
    }

    private byte[] copyObject(JsonParser parser) throws IOException {
        try (RuleBuffer bytes = new RuleBuffer(Math.addExact(64, Math.multiplyExact(rules.maxLength(), 6)));
                JsonGenerator generator = factory.createGenerator(ObjectWriteContext.empty(), bytes)) {
            generator.copyCurrentStructure(parser);
            generator.flush();
            return bytes.toByteArray();
        }
    }

    private RuleEntry parseRule(byte[] object, int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) {
            throw new RuleProcessingException("Sing-box 逻辑规则嵌套超过上限: " + MAX_NESTING_DEPTH);
        }
        List<MatchExpression> expressions = new ArrayList<>();
        List<MatchExpression> destinations = new ArrayList<>();
        List<MatchExpression> sourcePorts = new ArrayList<>();
        List<MatchExpression> destinationPorts = new ArrayList<>();
        List<MatchExpression> nested = null;
        int nestedCount = 0;
        boolean invert = false;
        boolean unsupported = false;
        String type = "default";
        String mode = null;
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), new String(object, StandardCharsets.UTF_8))) {
            require(parser.nextToken(), JsonToken.START_OBJECT, parser, "规则必须是对象");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "domain" -> addDomains(parser, destinations, DomainKind.EXACT);
                    case "domain_suffix" -> addDomains(parser, destinations, DomainKind.SUFFIX);
                    case "domain_keyword" -> addDomains(parser, destinations, DomainKind.KEYWORD);
                    case "domain_regex" -> addDomains(parser, destinations, DomainKind.REGEX);
                    case "source_ip_cidr" -> addCidrs(parser, expressions, MatchSide.SOURCE);
                    case "ip_cidr" -> addCidrs(parser, destinations, MatchSide.DESTINATION);
                    case "source_port" -> addPorts(parser, sourcePorts, MatchSide.SOURCE);
                    case "port" -> addPorts(parser, destinationPorts, MatchSide.DESTINATION);
                    case "source_port_range" -> addPortRanges(parser, sourcePorts, MatchSide.SOURCE);
                    case "port_range" -> addPortRanges(parser, destinationPorts, MatchSide.DESTINATION);
                    case "network" -> addNetworks(parser, expressions);
                    case "process_name" -> addProcesses(parser, expressions, ProcessMatch.ProcessField.NAME);
                    case "process_path" -> addProcesses(parser, expressions, ProcessMatch.ProcessField.PATH);
                    case "package_name" -> addProcesses(parser, expressions, ProcessMatch.ProcessField.PACKAGE);
                    case "invert" -> invert = parser.getBooleanValue();
                    case "type" -> type = requireString(parser, "type 必须是字符串");
                    case "mode" -> mode = requireString(parser, "mode 必须是字符串");
                    case "rules" -> {
                        require(value, JsonToken.START_ARRAY, parser, "logical rules 必须是数组");
                        nested = new ArrayList<>();
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            require(parser.currentToken(), JsonToken.START_OBJECT, parser,
                                    "logical rules 元素必须是对象");
                            RuleEntry child = parseRule(copyObject(parser), depth + 1);
                            nestedCount++;
                            if (child instanceof RouteRule route) {
                                nested.add(route.expression());
                            } else {
                                unsupported = true;
                            }
                        }
                    }
                    default -> {
                        unsupported = true;
                        parser.skipChildren();
                    }
                }
            }
        }
        for (List<MatchExpression> group : List.of(destinations, sourcePorts, destinationPorts)) {
            if (!group.isEmpty()) {
                expressions.add(group.size() == 1 ? group.getFirst() : new AnyOf(group));
            }
        }
        if (type.equals("logical")) {
            if (!expressions.isEmpty() || nested == null || nestedCount == 0 || mode == null) {
                throw new RuleProcessingException("Sing-box logical 规则字段组合非法");
            }
            if (!mode.equals("and") && !mode.equals("or")) {
                throw new RuleProcessingException("Sing-box logical mode 非法: " + mode);
            }
        } else if (type.equals("default")) {
            if (mode != null || nested != null) {
                throw new RuleProcessingException("Sing-box default 规则不能包含 mode 或 rules");
            }
            if (expressions.isEmpty() && !unsupported) {
                throw new RuleProcessingException("Sing-box 规则没有匹配字段");
            }
        } else {
            throw new RuleProcessingException("Sing-box 规则 type 非法: " + type);
        }
        if (unsupported) {
            opaqueSeen = true;
            return new OpaqueRule(RuleType.SING_BOX, RuleDialect.NONE, DomainEnvelope.UNKNOWN,
                    new String(object, StandardCharsets.UTF_8));
        }
        MatchExpression expression;
        if (type.equals("logical")) {
            expression = mode.equals("and") ? new AllOf(nested) : new AnyOf(nested);
        } else {
            expression = expressions.size() == 1 ? expressions.getFirst() : new AllOf(expressions);
        }
        return new RouteRule(invert ? new Not(expression) : expression);
    }

    private static String requireString(JsonParser parser, String message) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw syntax(parser, message);
        }
        return parser.getString();
    }

    private static void addDomains(JsonParser parser, List<MatchExpression> target, DomainKind kind) throws IOException {
        List<String> values = strings(parser);
        List<MatchExpression> matches = values.stream().map(value -> (MatchExpression) new DomainMatch(switch (kind) {
            case EXACT -> new ExactDomain(new DomainName(value));
            case SUFFIX -> value.startsWith(".") ? new Subdomain(new DomainName(value.substring(1)))
                    : new SuffixDomain(new DomainName(value));
            case KEYWORD -> new KeywordDomain(value);
            case REGEX -> new RegexDomain(value);
        })).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static void addCidrs(JsonParser parser, List<MatchExpression> target, MatchSide side) throws IOException {
        List<MatchExpression> matches = strings(parser).stream().map(value -> {
            IpAddress address = value.indexOf('/') < 0 ? IpAddress.parse(value) : null;
            IpCidr cidr = address == null ? IpCidr.parse(value)
                    : new IpCidr(address, address.family() == IpFamily.IPV4 ? 32 : 128);
            return (MatchExpression) new IpCidrMatch(side, cidr.network(), cidr.prefixLength());
        }).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static void addPorts(JsonParser parser, List<MatchExpression> target, MatchSide side) throws IOException {
        List<MatchExpression> matches = integers(parser).stream()
                .map(port -> (MatchExpression) new PortMatch(side, port, port)).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static void addPortRanges(JsonParser parser, List<MatchExpression> target, MatchSide side) throws IOException {
        List<MatchExpression> matches = strings(parser).stream().map(value -> {
            int separator = value.indexOf(':');
            if (separator < 0 || value.indexOf(':', separator + 1) >= 0) {
                throw new RuleProcessingException("Sing-box 端口范围非法: " + value);
            }
            return (MatchExpression) new PortMatch(side, separator == 0 ? 0 : Integer.parseInt(value.substring(0, separator)),
                    separator == value.length() - 1 ? 65_535 : Integer.parseInt(value.substring(separator + 1)));
        }).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static void addNetworks(JsonParser parser, List<MatchExpression> target) throws IOException {
        List<MatchExpression> matches = strings(parser).stream().map(value -> (MatchExpression) new NetworkMatch(
                switch (value) {
                    case "tcp" -> NetworkMatch.Network.TCP;
                    case "udp" -> NetworkMatch.Network.UDP;
                    default -> throw new RuleProcessingException("Sing-box network 非法: " + value);
                })).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static void addProcesses(
            JsonParser parser,
            List<MatchExpression> target,
            ProcessMatch.ProcessField field) throws IOException {
        List<MatchExpression> matches = strings(parser).stream()
                .map(value -> (MatchExpression) new ProcessMatch(field, value)).toList();
        target.add(matches.size() == 1 ? matches.getFirst() : new AnyOf(matches));
    }

    private static List<String> strings(JsonParser parser) throws IOException {
        List<String> values = new ArrayList<>();
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            values.add(parser.getString());
            return List.copyOf(values);
        }
        require(parser.currentToken(), JsonToken.START_ARRAY, parser, "字段必须是字符串或字符串数组");
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(parser.currentToken(), JsonToken.VALUE_STRING, parser, "数组元素必须是字符串");
            values.add(parser.getString());
        }
        if (values.isEmpty()) {
            throw syntax(parser, "数组不得为空");
        }
        return List.copyOf(values);
    }

    private static List<Integer> integers(JsonParser parser) throws IOException {
        List<Integer> values = new ArrayList<>();
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            values.add(parser.getIntValue());
            return List.copyOf(values);
        }
        require(parser.currentToken(), JsonToken.START_ARRAY, parser, "字段必须是数字或数字数组");
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw syntax(parser, "数组元素必须是整数");
            }
            values.add(parser.getIntValue());
        }
        if (values.isEmpty()) {
            throw syntax(parser, "数组不得为空");
        }
        return List.copyOf(values);
    }

    private static void require(JsonToken actual, JsonToken expected, JsonParser parser, String message) {
        if (actual != expected) {
            throw syntax(parser, message);
        }
    }

    private static RuleProcessingException syntax(JsonParser parser, String message) {
        return new RuleProcessingException(message);
    }

    private enum DomainKind {
        EXACT,
        SUFFIX,
        KEYWORD,
        REGEX
    }
}
