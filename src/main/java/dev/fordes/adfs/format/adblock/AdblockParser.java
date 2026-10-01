package dev.fordes.adfs.format.adblock;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.*;

@Slf4j
public final class AdblockParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;
    private final AdblockDialectDefinition dialect;


    public AdblockParser(InputLimits limits, RuleConfig rules, RuleDialect dialect) {
        this.limits = limits;
        this.rules = rules;
        this.dialect = AdblockDialectDefinition.forDialect(dialect);
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        AdblockPreprocessor preprocessor = new AdblockPreprocessor(
                limits, rules.preprocessor(), dialect.builtInTokens());
        preprocessor.process(session, (line, affinity) -> {
            try {
                if (log.isDebugEnabled()) {
                    MDC.put(RuleSpool.INPUT_RULE, line.text());
                }
                parseLine(line, entry -> consumer.accept(affinity.isEmpty() ? entry : new SafariRule(entry, affinity)));
            } catch (RuleProcessingException | IllegalArgumentException exception) {
                session.invalidRule();
                log.warn("Adblock 规则语法非法, 已跳过:  {} --> Adblock 解析 | {} --> {}",
                        MDC.get(RuleSpool.INPUT), line.text(), exception.getMessage());
            }
        });
        return ParseResult.COMPLETE;
    }

    static boolean isComment(String text) {
        String stripped = text.strip();
        AdblockSyntax.CosmeticLocation operator = AdblockSyntax.cosmeticOperator(stripped);
        return stripped.startsWith("!") || stripped.startsWith("[Adblock")
                || stripped.startsWith("#") && (operator == null || operator.index() != 0);
    }

    private void parseLine(SourceLine line, RuleConsumer consumer) {
        String stripped = line.text().strip();
        if (stripped.isEmpty() || stripped.startsWith("!") || stripped.startsWith("[Adblock")) {
            return;
        }
        AdblockSyntax.CosmeticLocation cosmetic = AdblockSyntax.cosmeticOperator(stripped);
        if (stripped.startsWith("#") && (cosmetic == null || cosmetic.index() != 0)) {
            return;
        }
        String text = TextSource.ruleText(line, rules.minLength(), rules.maxLength());
        if (cosmetic != null) {
            parseCosmetic(text, cosmetic, line, consumer);
        } else {
            parseNetwork(text, line, consumer);
        }
    }

    private void parseCosmetic(
            String text, AdblockSyntax.CosmeticLocation location, SourceLine line, RuleConsumer consumer) {
        AdblockCapability capability = dialect.cosmetic(location.operator());
        if (capability == AdblockCapability.INVALID) {
            throw failure(line, "当前 Adblock 方言不支持元素操作符: " + location.operator());
        }
        if (capability == AdblockCapability.PASSTHROUGH) {
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        String domainText = text.substring(0, location.index());
        if (domainText.indexOf('[') >= 0 || domainText.indexOf(']') >= 0) {
            if (dialect.dialect() != RuleDialect.ADGUARD) {
                throw failure(line, "当前 Adblock 方言不支持元素规则域名修饰符");
            }
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        if (domainText.indexOf('*') >= 0) {
            if (dialect.dialect() == RuleDialect.CORE) {
                throw failure(line, "当前 Adblock 方言不支持元素规则域名通配符");
            }
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        if (domainText.indexOf('/') >= 0 && dialect.dialect() == RuleDialect.UBO) {
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        String body = text.substring(location.index() + location.operator().length()).strip();
        if (body.isEmpty()) {
            throw failure(line, "Adblock 元素规则主体为空");
        }
        if (body.startsWith("+js(") || body.startsWith("^") || location.operator().length() > 3) {
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        List<DomainConstraint> domains = parseCosmeticDomains(domainText, line);
        CosmeticOperator operator = cosmeticOperator(location.operator());
        boolean exception = operator == CosmeticOperator.ELEMENT_HIDE_EXCEPTION
                || operator == CosmeticOperator.EXTENDED_CSS_EXCEPTION
                || operator == CosmeticOperator.SCRIPTLET_EXCEPTION
                || operator == CosmeticOperator.CSS_INJECTION_EXCEPTION
                || operator == CosmeticOperator.EXTENDED_CSS_INJECTION_EXCEPTION
                || operator == CosmeticOperator.HTML_FILTER_EXCEPTION;
        consumer.accept(new CosmeticRule(domains, exception, operator, body, dialect.dialect()));
    }

    private void parseNetwork(String text, SourceLine line, RuleConsumer consumer) {
        boolean allow = text.startsWith("@@");
        String rule = allow ? text.substring(2) : text;
        int optionSeparator = AdblockSyntax.optionSeparator(rule);
        String body = optionSeparator < 0 ? rule : rule.substring(0, optionSeparator);
        String optionText = optionSeparator < 0 ? "" : rule.substring(optionSeparator + 1);
        if (optionSeparator >= 0 && optionText.isEmpty()) {
            throw failure(line, "Adblock 选项分隔符后没有选项");
        }
        ParsedOptions options = parseOptions(optionText, line, allow ? RuleAction.ALLOW : RuleAction.BLOCK);
        if (body.isEmpty()) {
            if (optionText.isEmpty()) {
                throw failure(line, "Adblock 网络规则主体和选项均为空");
            }
            consumer.accept(opaque(text, DomainEnvelope.UNKNOWN));
            return;
        }
        if (options.passthrough()) {
            consumer.accept(opaque(text, envelope(body)));
            return;
        }
        consumer.accept(new AdblockNetworkRule(
                parsePattern(body, line), allow ? RuleAction.ALLOW : RuleAction.BLOCK,
                options.included(), options.excluded(), options.domains(), options.party(),
                options.matchCase(), options.important(), options.modifiers(), dialect.dialect()));
    }

    private ParsedOptions parseOptions(String text, SourceLine line, RuleAction action) {
        Set<AdblockResourceType> included = EnumSet.noneOf(AdblockResourceType.class);
        Set<AdblockResourceType> excluded = EnumSet.noneOf(AdblockResourceType.class);
        List<DomainConstraint> domains = new ArrayList<>();
        List<AdblockModifier> modifiers = new ArrayList<>();
        PartyConstraint party = PartyConstraint.ANY;
        boolean matchCase = false;
        boolean important = false;
        boolean passthrough = false;
        if (text.isEmpty()) {
            return new ParsedOptions(included, excluded, domains, party, false, false, modifiers, false);
        }
        for (String raw : AdblockSyntax.options(text)) {
            String option = raw.strip();
            boolean negated = option.startsWith("~");
            String positive = negated ? option.substring(1) : option;
            int equals = positive.indexOf('=');
            String name = (equals < 0 ? positive : positive.substring(0, equals)).toLowerCase(Locale.ROOT);
            String value = equals < 0 ? "" : positive.substring(equals + 1);
            name = canonicalOptionName(name);
            AdblockCapability capability = dialect.option(name);
            if (capability == AdblockCapability.INVALID) {
                throw failure(line, "当前 Adblock 方言不支持选项: " + name);
            }
            if (capability == AdblockCapability.PASSTHROUGH) {
                if (name.isEmpty()) {
                    throw failure(line, "Adblock 选项名称为空");
                }
                passthrough = true;
                continue;
            }
            AdblockResourceType resource = resourceType(name);
            if (resource != null) {
                requireNoValue(value, line, name);
                if ((resource == AdblockResourceType.ELEMHIDE
                        || resource == AdblockResourceType.GENERICHIDE
                        || resource == AdblockResourceType.GENERICBLOCK
                        || resource == AdblockResourceType.SPECIFICHIDE)
                        && action != RuleAction.ALLOW) {
                    throw failure(line, "Adblock 页面级隐藏例外选项只能用于例外规则: " + name);
                }
                (negated ? excluded : included).add(resource);
            } else if (name.equals("domain")) {
                if (negated || value.isEmpty()) {
                    throw failure(line, "Adblock domain 选项需要非空参数且不能整体否定");
                }
                if (value.indexOf('*') >= 0) {
                    if (dialect.dialect() != RuleDialect.ADGUARD && dialect.dialect() != RuleDialect.UBO) {
                        throw failure(line, "当前 Adblock 方言不支持 domain 选项中的扩展域名模式");
                    }
                    passthrough = true;
                } else if (value.indexOf('/') >= 0
                        && (dialect.dialect() == RuleDialect.ADGUARD || dialect.dialect() == RuleDialect.UBO)) {
                    passthrough = true;
                } else {
                    domains.addAll(parseDomainConstraints(value, '|', line));
                }
            } else if (name.equals("third-party")) {
                requireNoValue(value, line, name);
                party = negated ? PartyConstraint.FIRST_PARTY : PartyConstraint.THIRD_PARTY;
            } else if (name.equals("first-party")) {
                requireNoValue(value, line, name);
                party = negated ? PartyConstraint.THIRD_PARTY : PartyConstraint.FIRST_PARTY;
            } else if (name.equals("match-case")) {
                requireFlag(negated, value, line, name);
                matchCase = true;
            } else if (name.equals("important")) {
                requireFlag(negated, value, line, name);
                important = true;
            } else {
                if (negated) {
                    throw failure(line, "Adblock 修饰符不允许否定: " + name);
                }
                AdblockModifier.Type type = modifierType(name, line);
                if (action == RuleAction.ALLOW && type.requiresValue() && value.isEmpty()) {
                    passthrough = true;
                    continue;
                }
                modifiers.add(new AdblockModifier(type, value));
                if (!name.equals("badfilter")) {
                    passthrough = true;
                }
            }
        }
        if (!Collections.disjoint(included, excluded)) {
            throw failure(line, "Adblock 资源类型同时被包含和排除");
        }
        return new ParsedOptions(included, excluded, domains, party, matchCase, important, modifiers, passthrough);
    }

    private AdblockPattern parsePattern(String body, SourceLine line) {
        if (body.startsWith("/") && body.endsWith("/") && body.length() > 2) {
            return new AdblockPattern(AdblockPattern.Kind.REGEX, body.substring(1, body.length() - 1));
        }
        if (body.startsWith("||") && body.endsWith("^")) {
            String domain = body.substring(2, body.length() - 1);
            if (domain.indexOf('*') >= 0 || domain.indexOf('/') >= 0 || domain.indexOf('^') >= 0) {
                return new AdblockPattern(AdblockPattern.Kind.URL, body);
            }
            return DomainName.tryParse(domain)
                    .map(value -> new AdblockPattern(AdblockPattern.Kind.DOMAIN_ANCHOR, value.value()))
                    .orElseGet(() -> new AdblockPattern(AdblockPattern.Kind.URL, body));
        }
        if (dialect.dialect() == RuleDialect.UBO) {
            Optional<DomainName> hostname = DomainName.tryParse(body);
            if (hostname.isPresent()) {
                return new AdblockPattern(AdblockPattern.Kind.DOMAIN_ANCHOR, hostname.orElseThrow().value());
            }
        }
        return new AdblockPattern(AdblockPattern.Kind.URL, body);
    }

    private static List<DomainConstraint> parseCosmeticDomains(String text, SourceLine line) {
        if (text.isBlank()) {
            return List.of();
        }
        return parseDomainConstraints(text, ',', line);
    }

    private static List<DomainConstraint> parseDomainConstraints(String text, char separator, SourceLine line) {
        List<DomainConstraint> domains = new ArrayList<>();
        for (String raw : splitStructured(text, separator, line)) {
            String value = raw.strip();
            boolean excluded = value.startsWith("~");
            String domain = excluded ? value.substring(1) : value;
            if (domain.isEmpty()) {
                throw failure(line, "Adblock 域名约束为空");
            }
            domains.add(new DomainConstraint(new DomainName(domain), excluded));
        }
        return List.copyOf(domains);
    }

    private static List<String> splitStructured(String text, char separator, SourceLine line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        char quote = 0;
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\') {
                current.append(character);
                escaped = true;
            } else if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
            } else if (character == '\'' || character == '"') {
                current.append(character);
                quote = character;
            } else if (character == '(') {
                current.append(character);
                depth++;
            } else if (character == ')') {
                if (depth == 0) {
                    throw failure(line, "Adblock 选项包含多余右括号");
                }
                current.append(character);
                depth--;
            } else if (character == separator && depth == 0) {
                addPart(values, current, line);
            } else {
                current.append(character);
            }
        }
        if (escaped || quote != 0 || depth != 0) {
            throw failure(line, "Adblock 选项包含未闭合的转义、引号或括号");
        }
        addPart(values, current, line);
        return List.copyOf(values);
    }

    private static void addPart(List<String> values, StringBuilder current, SourceLine line) {
        String value = current.toString().strip();
        if (value.isEmpty()) {
            throw failure(line, "Adblock 列表包含空项");
        }
        values.add(value);
        current.setLength(0);
    }

    private OpaqueRule opaque(String text, DomainEnvelope envelope) {
        return new OpaqueRule(RuleType.ADBLOCK, dialect.dialect(), envelope, text);
    }

    private static DomainEnvelope envelope(String body) {
        if (body.startsWith("||") && body.endsWith("^") && body.indexOf('*') < 0) {
            return DomainEnvelope.SUFFIX;
        }
        return DomainEnvelope.UNKNOWN;
    }

    private static AdblockResourceType resourceType(String value) {
        for (AdblockResourceType type : AdblockResourceType.values()) {
            if (type.value().equals(value)) {
                return type;
            }
        }
        return null;
    }

    private String canonicalOptionName(String name) {
        if (name.equals("xhr")
                && (dialect.dialect() == RuleDialect.ADGUARD || dialect.dialect() == RuleDialect.UBO)) {
            return "xmlhttprequest";
        }
        if (dialect.dialect() != RuleDialect.UBO) {
            return name;
        }
        return switch (name) {
            case "1p" -> "first-party";
            case "3p" -> "third-party";
            case "css" -> "stylesheet";
            case "doc" -> "document";
            case "ehide" -> "elemhide";
            case "frame" -> "subdocument";
            case "from" -> "domain";
            case "ghide" -> "generichide";
            case "shide" -> "specifichide";
            default -> name;
        };
    }

    private static AdblockModifier.Type modifierType(String value, SourceLine line) {
        for (AdblockModifier.Type type : AdblockModifier.Type.values()) {
            if (type.value().equals(value)) {
                return type;
            }
        }
        throw failure(line, "Adblock 修饰符未在能力表声明: " + value);
    }

    private static CosmeticOperator cosmeticOperator(String value) {
        for (CosmeticOperator operator : CosmeticOperator.values()) {
            if (operator.value().equals(value)) {
                return operator;
            }
        }
        throw new IllegalStateException("元素操作符能力表与模型不一致: " + value);
    }

    private static void requireFlag(boolean negated, String value, SourceLine line, String name) {
        if (negated || !value.isEmpty()) {
            throw failure(line, "Adblock 标志选项不能否定或携带参数: " + name);
        }
    }

    private static void requireNoValue(String value, SourceLine line, String name) {
        if (!value.isEmpty()) {
            throw failure(line, "Adblock 选项不能携带参数: " + name);
        }
    }

    private static RuleProcessingException failure(SourceLine line, String message) {
        return new RuleProcessingException(message);
    }

    private record ParsedOptions(
            Set<AdblockResourceType> included,
            Set<AdblockResourceType> excluded,
            List<DomainConstraint> domains,
            PartyConstraint party,
            boolean matchCase,
            boolean important,
            List<AdblockModifier> modifiers,
            boolean passthrough) {

        private ParsedOptions {
            included = Set.copyOf(included);
            excluded = Set.copyOf(excluded);
            domains = List.copyOf(domains);
            modifiers = List.copyOf(modifiers);
        }
    }
}
