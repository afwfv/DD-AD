package dev.fordes.adfs.rule.spool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.model.AdblockModifier;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.AdblockResourceType;
import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.AnyOf;
import dev.fordes.adfs.rule.model.CosmeticOperator;
import dev.fordes.adfs.rule.model.CosmeticRule;
import dev.fordes.adfs.rule.model.DomainConstraint;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainPattern;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.IpFamily;
import dev.fordes.adfs.rule.model.KeywordDomain;
import dev.fordes.adfs.rule.model.MatchExpression;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.NetworkMatch;
import dev.fordes.adfs.rule.model.Not;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PartyConstraint;
import dev.fordes.adfs.rule.model.PortMatch;
import dev.fordes.adfs.rule.model.ProcessMatch;
import dev.fordes.adfs.rule.model.RegexDomain;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.DnsResponse;
import dev.fordes.adfs.rule.model.SafariRule;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;
import dev.fordes.adfs.rule.model.WildcardDomain;

public final class RuleCodec {

    private static final int VERSION = 5;
    private static final int SAFARI = 12;
    private static final int DOMAIN_SUBDOMAIN = 13;
    private static final int DNS_ADDRESS = 14;
    private static final int DOMAIN_EXACT = 1;
    private static final int DOMAIN_SUFFIX = 2;
    private static final int HOST_MAPPING = 3;
    private static final int IP_CIDR = 4;
    private static final int OPAQUE = 5;
    private static final int DOMAIN_KEYWORD = 6;
    private static final int DOMAIN_WILDCARD = 7;
    private static final int DOMAIN_REGEX = 8;
    private static final int ROUTE = 9;
    private static final int ADBLOCK_NETWORK = 10;
    private static final int COSMETIC = 11;
    private static final int MATCH_DOMAIN = 1;
    private static final int MATCH_IP_CIDR = 2;
    private static final int MATCH_PORT = 3;
    private static final int MATCH_NETWORK = 4;
    private static final int MATCH_PROCESS = 5;
    private static final int MATCH_ALL = 6;
    private static final int MATCH_ANY = 7;
    private static final int MATCH_NOT = 8;

    public byte[] encode(RuleEntry entry) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(VERSION);
            switch (entry) {
                case SafariRule safari -> {
                    output.writeByte(SAFARI);
                    writeString(output, safari.affinity());
                    output.write(encode(safari.rule()));
                }
                case DomainRule(var pattern, RuleAction action) -> {
                    int tag = switch (pattern) {
                        case ExactDomain _ -> DOMAIN_EXACT;
                        case SuffixDomain _ -> DOMAIN_SUFFIX;
                        case Subdomain _ -> DOMAIN_SUBDOMAIN;
                        case KeywordDomain _ -> DOMAIN_KEYWORD;
                        case WildcardDomain _ -> DOMAIN_WILDCARD;
                        case RegexDomain _ -> DOMAIN_REGEX;
                    };
                    output.writeByte(tag);
                    writeString(output, pattern.value());
                    if (pattern instanceof WildcardDomain wildcard) {
                        writeString(output, wildcard.syntax().name());
                    }
                    writeString(output, action.value());
                }
                case DnsAddressRule rule -> {
                    output.writeByte(DNS_ADDRESS);
                    writeString(output, rule.format().value());
                    writeExpression(output, new DomainMatch(rule.pattern()));
                    writeString(output, rule.response().name());
                    output.writeInt(rule.families().size());
                    for (IpFamily family : rule.families().stream().sorted().toList()) {
                        writeString(output, family.name());
                    }
                    output.writeInt(rule.addresses().size());
                    for (IpAddress address : rule.addresses()) {
                        writeAddress(output, address);
                    }
                }
                case HostMappingRule(IpAddress address, DomainName hostname) -> {
                    output.writeByte(HOST_MAPPING);
                    writeAddress(output, address);
                    writeString(output, hostname.value());
                }
                case IpCidrRule(IpAddress network, int prefixLength, RuleAction action) -> {
                    output.writeByte(IP_CIDR);
                    writeAddress(output, network);
                    output.writeInt(prefixLength);
                    writeString(output, action.value());
                }
                case RouteRule(MatchExpression expression) -> {
                    output.writeByte(ROUTE);
                    writeExpression(output, expression);
                }
                case AdblockNetworkRule rule -> {
                    output.writeByte(ADBLOCK_NETWORK);
                    writeAdblockNetwork(output, rule);
                }
                case CosmeticRule rule -> {
                    output.writeByte(COSMETIC);
                    writeCosmetic(output, rule);
                }
                case OpaqueRule(RuleType type, RuleDialect dialect, DomainEnvelope envelope, String payload) -> {
                    output.writeByte(OPAQUE);
                    writeString(output, type.value());
                    writeString(output, dialect.value());
                    writeString(output, envelope.value());
                    writeString(output, payload);
                }
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("内存规则编码失败", exception);
        }
    }

    public RuleEntry decode(byte[] record) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record))) {
            int version = input.readUnsignedByte();
            if (version != VERSION) {
                throw new RuleProcessingException("RuleSpool 版本未知: " + version);
            }
            int tag = input.readUnsignedByte();
            RuleEntry entry = switch (tag) {
                case SAFARI -> readSafari(input);
                case DOMAIN_EXACT -> new DomainRule(new ExactDomain(new DomainName(readString(input))), readAction(input));
                case DOMAIN_SUBDOMAIN -> new DomainRule(new Subdomain(new DomainName(readString(input))), readAction(input));
                case DOMAIN_SUFFIX -> new DomainRule(new SuffixDomain(new DomainName(readString(input))), readAction(input));
                case DOMAIN_KEYWORD -> new DomainRule(new KeywordDomain(readString(input)), readAction(input));
                case DOMAIN_WILDCARD -> new DomainRule(new WildcardDomain(readString(input), WildcardSyntax.valueOf(readString(input))), readAction(input));
                case DOMAIN_REGEX -> new DomainRule(new RegexDomain(readString(input)), readAction(input));
                case DNS_ADDRESS -> readDnsAddress(input);
                case HOST_MAPPING -> new HostMappingRule(readAddress(input), new DomainName(readString(input)));
                case IP_CIDR -> new IpCidrRule(readAddress(input), input.readInt(), readAction(input));
                case ROUTE -> new RouteRule(readExpression(input));
                case ADBLOCK_NETWORK -> readAdblockNetwork(input);
                case COSMETIC -> readCosmetic(input);
                case OPAQUE -> new OpaqueRule(RuleType.parse(readString(input)), RuleDialect.parse(readString(input)),
                        readEnvelope(input), readString(input));
                default -> throw new RuleProcessingException("RuleSpool 条目类型未知: " + tag);
            };
            if (input.read() >= 0) {
                throw new RuleProcessingException("RuleSpool 条目包含未消费数据: " + tag);
            }
            return entry;
        } catch (EOFException exception) {
            throw new RuleProcessingException("RuleSpool 条目被截断", exception);
        } catch (IOException exception) {
            throw new RuleProcessingException("读取 RuleSpool 条目失败", exception);
        }
    }

    private static DnsAddressRule readDnsAddress(DataInputStream input) throws IOException {
        RuleType format = RuleType.parse(readString(input));
        if (!(readExpression(input) instanceof DomainMatch(var pattern))) {
            throw new RuleProcessingException("DNS 赋值域名模式非法");
        }
        DnsResponse response = DnsResponse.valueOf(readString(input));
        int count = readCollectionSize(input, "DNS 地址族");
        Set<IpFamily> families = EnumSet.noneOf(IpFamily.class);
        for (int index = 0; index < count; index++) {
            families.add(IpFamily.valueOf(readString(input)));
        }
        int size = readCollectionSize(input, "DNS 地址");
        List<IpAddress> addresses = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            addresses.add(readAddress(input));
        }
        return new DnsAddressRule(format, pattern, response, families, addresses);
    }

    private SafariRule readSafari(DataInputStream input) throws IOException {
        try {
            Set<String> blockers = Set.of(readString(input).split(",", -1));
            byte[] nested = input.readAllBytes();
            if (nested.length < 2 || nested[1] == SAFARI) {
                throw new RuleProcessingException("Safari 分组条目为空或包含嵌套分组");
            }
            return new SafariRule(decode(nested), blockers);
        } catch (IllegalArgumentException exception) {
            throw new RuleProcessingException("Safari 分组条目非法: " + exception.getMessage(), exception);
        }
    }

    private static void writeAdblockNetwork(DataOutputStream output, AdblockNetworkRule rule) throws IOException {
        writeString(output, rule.dialect().value());
        writeString(output, rule.pattern().kind().value());
        writeString(output, rule.pattern().value());
        writeString(output, rule.action().value());
        writeResources(output, rule.includedResourceTypes());
        writeResources(output, rule.excludedResourceTypes());
        writeDomainConstraints(output, rule.domainConstraints());
        writeString(output, rule.partyConstraint().value());
        output.writeBoolean(rule.matchCase());
        output.writeBoolean(rule.important());
        output.writeInt(rule.modifiers().size());
        for (AdblockModifier modifier : rule.modifiers()) {
            writeString(output, modifier.type().value());
            writeString(output, modifier.value());
        }
    }

    private static AdblockNetworkRule readAdblockNetwork(DataInputStream input) throws IOException {
        RuleDialect dialect = RuleDialect.parse(readString(input));
        AdblockPattern pattern = new AdblockPattern(readPatternKind(input), readString(input));
        RuleAction action = readAction(input);
        Set<AdblockResourceType> included = readResources(input);
        Set<AdblockResourceType> excluded = readResources(input);
        List<DomainConstraint> domains = readDomainConstraints(input);
        PartyConstraint party = readParty(input);
        boolean matchCase = input.readBoolean();
        boolean important = input.readBoolean();
        int size = readCollectionSize(input, "Adblock 修饰符");
        List<AdblockModifier> modifiers = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            modifiers.add(new AdblockModifier(readModifierType(input), readString(input)));
        }
        return new AdblockNetworkRule(pattern, action, included, excluded, domains, party, matchCase, important,
                modifiers, dialect);
    }

    private static void writeCosmetic(DataOutputStream output, CosmeticRule rule) throws IOException {
        writeDomainConstraints(output, rule.domains());
        output.writeBoolean(rule.exception());
        writeString(output, rule.operator().value());
        writeString(output, rule.body());
        writeString(output, rule.dialect().value());
    }

    private static CosmeticRule readCosmetic(DataInputStream input) throws IOException {
        List<DomainConstraint> domains = readDomainConstraints(input);
        boolean exception = input.readBoolean();
        CosmeticOperator operator = switch (readString(input)) {
            case "##" -> CosmeticOperator.ELEMENT_HIDE;
            case "#@#" -> CosmeticOperator.ELEMENT_HIDE_EXCEPTION;
            case "#?#" -> CosmeticOperator.EXTENDED_CSS;
            case "#@?#" -> CosmeticOperator.EXTENDED_CSS_EXCEPTION;
            case "#%#" -> CosmeticOperator.SCRIPTLET;
            case "#@%#" -> CosmeticOperator.SCRIPTLET_EXCEPTION;
            case "#$#" -> CosmeticOperator.CSS_INJECTION;
            case "#@$#" -> CosmeticOperator.CSS_INJECTION_EXCEPTION;
            case "#$?#" -> CosmeticOperator.EXTENDED_CSS_INJECTION;
            case "#@$?#" -> CosmeticOperator.EXTENDED_CSS_INJECTION_EXCEPTION;
            case "$$" -> CosmeticOperator.HTML_FILTER;
            case "$@$" -> CosmeticOperator.HTML_FILTER_EXCEPTION;
            case String value -> throw new RuleProcessingException("Cosmetic operator 未知: " + value);
        };
        return new CosmeticRule(domains, exception, operator, readString(input), RuleDialect.parse(readString(input)));
    }

    private static void writeResources(DataOutputStream output, Set<AdblockResourceType> resources) throws IOException {
        output.writeInt(resources.size());
        for (AdblockResourceType resource : resources.stream().sorted().toList()) {
            writeString(output, resource.value());
        }
    }

    private static Set<AdblockResourceType> readResources(DataInputStream input) throws IOException {
        int size = readCollectionSize(input, "Adblock 资源类型");
        Set<AdblockResourceType> resources = EnumSet.noneOf(AdblockResourceType.class);
        for (int index = 0; index < size; index++) {
            resources.add(readResource(input));
        }
        return Set.copyOf(resources);
    }

    private static void writeDomainConstraints(DataOutputStream output, List<DomainConstraint> domains)
            throws IOException {
        output.writeInt(domains.size());
        for (DomainConstraint domain : domains) {
            writeString(output, domain.domain().value());
            output.writeBoolean(domain.excluded());
        }
    }

    private static List<DomainConstraint> readDomainConstraints(DataInputStream input) throws IOException {
        int size = readCollectionSize(input, "Adblock 域名约束");
        List<DomainConstraint> domains = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            domains.add(new DomainConstraint(new DomainName(readString(input)), input.readBoolean()));
        }
        return List.copyOf(domains);
    }

    private static int readCollectionSize(DataInputStream input, String field) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > input.available()) {
            throw new RuleProcessingException(field + "数量非法: " + size);
        }
        return size;
    }

    private static AdblockPattern.Kind readPatternKind(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "domain-anchor" -> AdblockPattern.Kind.DOMAIN_ANCHOR;
            case "url" -> AdblockPattern.Kind.URL;
            case "regex" -> AdblockPattern.Kind.REGEX;
            case String value -> throw new RuleProcessingException("Adblock pattern 类型未知: " + value);
        };
    }

    private static AdblockResourceType readResource(DataInputStream input) throws IOException {
        String value = readString(input);
        for (AdblockResourceType type : AdblockResourceType.values()) {
            if (type.value().equals(value)) {
                return type;
            }
        }
        throw new RuleProcessingException("Adblock 资源类型未知: " + value);
    }

    private static PartyConstraint readParty(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "any" -> PartyConstraint.ANY;
            case "first-party" -> PartyConstraint.FIRST_PARTY;
            case "third-party" -> PartyConstraint.THIRD_PARTY;
            case String value -> throw new RuleProcessingException("Adblock party 约束未知: " + value);
        };
    }

    private static AdblockModifier.Type readModifierType(DataInputStream input) throws IOException {
        String value = readString(input);
        for (AdblockModifier.Type type : AdblockModifier.Type.values()) {
            if (type.value().equals(value)) {
                return type;
            }
        }
        throw new RuleProcessingException("Adblock 修饰符未知: " + value);
    }

    private static void writeAddress(DataOutputStream output, IpAddress address) throws IOException {
        writeString(output, address.family().value());
        output.writeLong(address.high());
        output.writeLong(address.low());
    }

    private static void writeExpression(DataOutputStream output, MatchExpression expression) throws IOException {
        switch (expression) {
            case DomainMatch(DomainPattern pattern) -> {
                output.writeByte(MATCH_DOMAIN);
                writeString(output, patternType(pattern));
                writeString(output, pattern.value());
                if (pattern instanceof WildcardDomain wildcard) {
                    writeString(output, wildcard.syntax().name());
                }
            }
            case IpCidrMatch(MatchSide side, IpAddress network, int prefixLength) -> {
                output.writeByte(MATCH_IP_CIDR);
                writeString(output, side.value());
                writeAddress(output, network);
                output.writeInt(prefixLength);
            }
            case PortMatch(MatchSide side, int first, int last) -> {
                output.writeByte(MATCH_PORT);
                writeString(output, side.value());
                output.writeInt(first);
                output.writeInt(last);
            }
            case NetworkMatch(var value) -> {
                output.writeByte(MATCH_NETWORK);
                writeString(output, value.value());
            }
            case ProcessMatch(var field, String value) -> {
                output.writeByte(MATCH_PROCESS);
                writeString(output, field.value());
                writeString(output, value);
            }
            case AllOf(var expressions) -> writeExpressions(output, MATCH_ALL, expressions);
            case AnyOf(var expressions) -> writeExpressions(output, MATCH_ANY, expressions);
            case Not(MatchExpression child) -> {
                output.writeByte(MATCH_NOT);
                writeExpression(output, child);
            }
        }
    }

    private static void writeExpressions(DataOutputStream output, int tag, List<MatchExpression> expressions)
            throws IOException {
        output.writeByte(tag);
        output.writeInt(expressions.size());
        for (MatchExpression expression : expressions) {
            writeExpression(output, expression);
        }
    }

    private static MatchExpression readExpression(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case MATCH_DOMAIN -> new DomainMatch(readPattern(input));
            case MATCH_IP_CIDR -> new IpCidrMatch(readMatchSide(input), readAddress(input), input.readInt());
            case MATCH_PORT -> new PortMatch(readMatchSide(input), input.readInt(), input.readInt());
            case MATCH_NETWORK -> new NetworkMatch(switch (readString(input)) {
                case "tcp" -> NetworkMatch.Network.TCP;
                case "udp" -> NetworkMatch.Network.UDP;
                case String value -> throw new RuleProcessingException("RuleSpool network 未知: " + value);
            });
            case MATCH_PROCESS -> new ProcessMatch(readProcessField(input), readString(input));
            case MATCH_ALL -> new AllOf(readExpressions(input));
            case MATCH_ANY -> new AnyOf(readExpressions(input));
            case MATCH_NOT -> new Not(readExpression(input));
            default -> throw new RuleProcessingException("RuleSpool match expression 类型未知");
        };
    }

    private static MatchSide readMatchSide(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "source" -> MatchSide.SOURCE;
            case "destination" -> MatchSide.DESTINATION;
            case String value -> throw new RuleProcessingException("RuleSpool match side 未知: " + value);
        };
    }

    private static List<MatchExpression> readExpressions(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > input.available()) {
            throw new RuleProcessingException("RuleSpool 表达式数量非法: " + size);
        }
        List<MatchExpression> expressions = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            expressions.add(readExpression(input));
        }
        return List.copyOf(expressions);
    }

    private static ProcessMatch.ProcessField readProcessField(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "name" -> ProcessMatch.ProcessField.NAME;
            case "path" -> ProcessMatch.ProcessField.PATH;
            case "package" -> ProcessMatch.ProcessField.PACKAGE;
            case String value -> throw new RuleProcessingException("RuleSpool process field 未知: " + value);
        };
    }

    private static DomainPattern readPattern(DataInputStream input) throws IOException {
        String type = readString(input);
        String value = readString(input);
        return switch (type) {
            case "exact" -> new ExactDomain(new DomainName(value));
            case "subdomain" -> new Subdomain(new DomainName(value));
            case "suffix" -> new SuffixDomain(new DomainName(value));
            case "keyword" -> new KeywordDomain(value);
            case "wildcard" -> new WildcardDomain(value, WildcardSyntax.valueOf(readString(input)));
            case "regex" -> new RegexDomain(value);
            default -> throw new RuleProcessingException("RuleSpool domain pattern 未知: " + type);
        };
    }

    private static String patternType(DomainPattern pattern) {
        return switch (pattern) {
            case ExactDomain _ -> "exact";
            case SuffixDomain _ -> "suffix";
            case Subdomain _ -> "subdomain";
            case KeywordDomain _ -> "keyword";
            case WildcardDomain _ -> "wildcard";
            case RegexDomain _ -> "regex";
        };
    }

    private static IpAddress readAddress(DataInputStream input) throws IOException {
        IpFamily family = switch (readString(input)) {
            case "ipv4" -> IpFamily.IPV4;
            case "ipv6" -> IpFamily.IPV6;
            case String value -> throw new RuleProcessingException("RuleSpool IP family 未知: " + value);
        };
        return new IpAddress(family, input.readLong(), input.readLong());
    }

    private static RuleAction readAction(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "block" -> RuleAction.BLOCK;
            case "allow" -> RuleAction.ALLOW;
            case String value -> throw new RuleProcessingException("RuleSpool action 未知: " + value);
        };
    }

    private static DomainEnvelope readEnvelope(DataInputStream input) throws IOException {
        return switch (readString(input)) {
            case "unknown" -> DomainEnvelope.UNKNOWN;
            case "exact" -> DomainEnvelope.EXACT;
            case "suffix" -> DomainEnvelope.SUFFIX;
            case String value -> throw new RuleProcessingException("RuleSpool domain envelope 未知: " + value);
        };
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new RuleProcessingException("RuleSpool 字符串长度非法: " + length);
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }
}
