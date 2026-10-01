package dev.fordes.adfs.format.adblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.rule.model.CosmeticOperator;
import dev.fordes.adfs.rule.model.CosmeticRule;
import dev.fordes.adfs.rule.model.DomainConstraint;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.OpaqueRule;

/** 在 Adblock 方言之间执行有官方语义依据的精确投影。 */
public final class AdblockDialectConverter {

    private static final Set<String> PORTABLE_OPTIONS = Set.of(
            "script", "image", "stylesheet", "font", "media", "object", "xmlhttprequest",
            "subdocument", "document", "popup", "websocket", "ping", "other", "domain", "third-party",
            "match-case", "elemhide", "generichide");
    private static final Set<String> ADGUARD_UBO_OPTIONS = Set.of(
            "to", "denyallow", "method", "specifichide", "removeparam");
    private static final Set<String> SHARED_ADGUARD_UBO_CSS_MARKERS = Set.of(
            ":remove(", ":xpath(");
    private static final List<String> NON_PORTABLE_CSS_MARKERS = List.of(
            ":-abp-", ":contains(", ":has-text(", ":matches-attr(", ":matches-css(",
            ":matches-property(", ":matches-prop(", ":matches-path(", ":min-text-length(",
            ":nth-ancestor(", ":others(", ":remove(", ":remove-attr(", ":remove-class(",
            ":style(", ":upward(", ":watch-attr(", ":xpath(");
    private static final Pattern REMOVE_DECLARATION = Pattern.compile(
            "(?s)^(.*\\S)\\s*\\{\\s*remove\\s*:\\s*true\\s*;?\\s*}$");

    private AdblockDialectConverter() {
    }

    public static Optional<Projection> convert(CosmeticRule rule, RuleDialect target) {
        String encoded = encode(rule.domains(), rule.operator().value(), rule.body());
        if (rule.dialect() == target) {
            return Optional.of(new Projection(encoded, "同方言元素规则透传"));
        }
        Optional<Projection> removeAction = convertRemoveAction(rule, target);
        if (removeAction.isPresent()) {
            return removeAction;
        }
        return switch (rule.operator()) {
            case ELEMENT_HIDE, ELEMENT_HIDE_EXCEPTION -> compatibleCss(rule.dialect(), target, rule.body())
                    ? Optional.of(new Projection(encoded, "标准 CSS 元素规则精确透传")) : Optional.empty();
            case EXTENDED_CSS -> target != RuleDialect.CORE && portableExtendedCss(rule, target)
                    ? Optional.of(new Projection(encoded, "兼容的扩展 CSS 规则精确透传")) : Optional.empty();
            case EXTENDED_CSS_EXCEPTION -> convertExtendedException(rule, target);
            case CSS_INJECTION, CSS_INJECTION_EXCEPTION,
                    EXTENDED_CSS_INJECTION, EXTENDED_CSS_INJECTION_EXCEPTION ->
                rule.dialect() == RuleDialect.ADGUARD && target == RuleDialect.UBO
                        && safeAdguardCssInjection(rule.domains(), rule.body())
                        ? Optional.of(new Projection(encoded, "uBO 原生兼容 AdGuard CSS 注入语法"))
                        : Optional.empty();
            case HTML_FILTER, HTML_FILTER_EXCEPTION ->
                rule.dialect() == RuleDialect.ADGUARD && target == RuleDialect.UBO
                        && portableHtml(rule.body())
                        ? Optional.of(new Projection(encode(rule.domains(),
                                rule.operator() == CosmeticOperator.HTML_FILTER ? "##^" : "#@#^", rule.body()),
                                "AdGuard HTML 过滤操作符转换为 uBO 语法"))
                        : Optional.empty();
            case SCRIPTLET, SCRIPTLET_EXCEPTION -> Optional.empty();
        };
    }

    public static Optional<Projection> convert(OpaqueRule rule, RuleDialect target) {
        if (rule.dialect() == target) {
            return Optional.of(new Projection(rule.payload(), "同方言不透明规则透传"));
        }
        AdblockSyntax.CosmeticLocation location = AdblockSyntax.cosmeticOperator(rule.payload());
        if (location != null) {
            return convertOpaqueCosmetic(rule.dialect(), target, rule.payload(), location);
        }
        return convertOpaqueNetwork(rule.dialect(), target, rule.payload());
    }

    private static Optional<Projection> convertExtendedException(CosmeticRule rule, RuleDialect target) {
        if (target == RuleDialect.CORE || !portableExtendedCss(rule, target)) {
            return Optional.empty();
        }
        String operator = target == RuleDialect.ABP ? "#@#" : rule.operator().value();
        return Optional.of(new Projection(encode(rule.domains(), operator, rule.body()),
                target == RuleDialect.ABP
                        ? "Adblock Plus 使用 #@# 例外扩展元素规则"
                        : "兼容的扩展 CSS 例外规则精确透传"));
    }

    private static Optional<Projection> convertOpaqueCosmetic(
            RuleDialect source, RuleDialect target, String text, AdblockSyntax.CosmeticLocation location) {
        String domains = text.substring(0, location.index());
        String body = text.substring(location.index() + location.operator().length());
        if (domains.indexOf('[') >= 0 || domains.indexOf(']') >= 0) {
            return Optional.empty();
        }
        if (domains.indexOf('/') >= 0) {
            return Optional.empty();
        }
        if (source == RuleDialect.UBO && target == RuleDialect.ADGUARD
                && (location.operator().equals("##") || location.operator().equals("#@#"))
                && body.startsWith("^")) {
            if (!portableHtml(body.substring(1))) {
                return Optional.empty();
            }
            String operator = location.operator().equals("##") ? "$$" : "$@$";
            return Optional.of(new Projection(domains + operator + body.substring(1),
                    "uBO HTML 过滤操作符转换为 AdGuard 语法"));
        }
        if (source == RuleDialect.ADGUARD && target == RuleDialect.UBO) {
            if (location.operator().equals("#$#") || location.operator().equals("#@$#")
                    || location.operator().equals("#$?#") || location.operator().equals("#@$?#")) {
                return safeAdguardCssInjection(domains, body)
                        ? Optional.of(new Projection(text, "uBO 兼容该 AdGuard CSS 注入规则"))
                        : Optional.empty();
            }
            if (location.operator().equals("$$") || location.operator().equals("$@$")) {
                if (!portableHtml(body)) {
                    return Optional.empty();
                }
                String operator = location.operator().equals("$$") ? "##^" : "#@#^";
                return Optional.of(new Projection(domains + operator + body,
                        "AdGuard HTML 过滤操作符转换为 uBO 语法"));
            }
        }
        if (location.operator().equals("#@?#") && portableCss(body)) {
            if (adguardUboPair(source, target)) {
                return Optional.of(new Projection(text, "兼容的扩展 CSS 例外规则精确透传"));
            }
            if (target == RuleDialect.ABP) {
                return Optional.of(new Projection(domains + "#@#" + body,
                        "Adblock Plus 使用 #@# 例外扩展元素规则"));
            }
        }
        if (domains.indexOf('*') >= 0 && !adguardUboPair(source, target)) {
            return Optional.empty();
        }
        if ((location.operator().equals("##") || location.operator().equals("#@#"))
                && compatibleCss(source, target, body)) {
            return Optional.of(new Projection(text, "标准 CSS 元素规则精确透传"));
        }
        return Optional.empty();
    }

    private static Optional<Projection> convertOpaqueNetwork(
            RuleDialect source, RuleDialect target, String text) {
        int separator = AdblockSyntax.optionSeparator(text);
        if (separator < 0) {
            return Optional.empty();
        }
        List<String> options = AdblockSyntax.options(text.substring(separator + 1));
        boolean exception = text.startsWith("@@");
        List<String> convertedOptions = new ArrayList<>(options.size());
        for (String option : options) {
            String name = optionName(option);
            Optional<String> converted = convertOption(source, target, option, name);
            if (converted.isPresent()) {
                convertedOptions.add(converted.orElseThrow());
                continue;
            }
            if (!portableOption(source, target, name, exception)) {
                return Optional.empty();
            }
            if (name.equals("domain") && option.indexOf('*') >= 0 && !adguardUboPair(source, target)) {
                return Optional.empty();
            }
            if (name.equals("domain") && option.indexOf('/') >= 0 && !adguardUboPair(source, target)) {
                return Optional.empty();
            }
            convertedOptions.add(option);
        }
        String pattern = text.substring(0, separator);
        String normalizedPattern = normalizeHostnamePattern(source, target, pattern);
        return Optional.of(new Projection(normalizedPattern + "$" + String.join(",", convertedOptions),
                "目标方言支持规则中的全部网络选项"));
    }

    private static Optional<String> convertOption(
            RuleDialect source, RuleDialect target, String option, String name) {
        if ((source == RuleDialect.ADGUARD && target == RuleDialect.ABP
                || source == RuleDialect.ABP && target == RuleDialect.ADGUARD)
                && name.equals("rewrite")
                && option.regionMatches(true, "rewrite=".length(), "abp-resource:", 0,
                        "abp-resource:".length())) {
            return Optional.of(option);
        }
        if (!adguardUboPair(source, target)) {
            return Optional.empty();
        }
        if (source == RuleDialect.ADGUARD && target == RuleDialect.UBO && name.equals("urltransform")) {
            return Optional.of(renameOption(option, "uritransform"));
        }
        if (source == RuleDialect.UBO && target == RuleDialect.ADGUARD && name.equals("uritransform")) {
            return Optional.of(renameOption(option, "urltransform"));
        }
        if (name.equals("queryprune")) {
            return Optional.of(renameOption(option, "removeparam"));
        }
        return Optional.empty();
    }

    private static String renameOption(String option, String targetName) {
        int equals = option.indexOf('=');
        return targetName + (equals < 0 ? "" : option.substring(equals));
    }

    private static boolean portableOption(
            RuleDialect source, RuleDialect target, String name, boolean exception) {
        if (PORTABLE_OPTIONS.contains(name)) {
            if ((name.equals("elemhide") || name.equals("generichide")) && !exception) {
                return false;
            }
            if (name.equals("popup") && source != target) {
                return false;
            }
            return !name.equals("document") || !exception
                    || (source == RuleDialect.UBO) == (target == RuleDialect.UBO);
        }
        if (ADGUARD_UBO_OPTIONS.contains(name) && adguardUboPair(source, target)) {
            return !name.equals("specifichide") || exception;
        }
        return false;
    }

    private static String optionName(String option) {
        String positive = option.startsWith("~") ? option.substring(1) : option;
        int equals = positive.indexOf('=');
        return (equals < 0 ? positive : positive.substring(0, equals)).strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHostnamePattern(RuleDialect source, RuleDialect target, String pattern) {
        int offset = pattern.startsWith("@@") ? 2 : 0;
        String body = pattern.substring(offset);
        Optional<DomainName> hostname = DomainName.tryParse(body);
        if (hostname.isEmpty()) {
            return pattern;
        }
        if (source == RuleDialect.UBO) {
            return pattern.substring(0, offset) + "||" + hostname.orElseThrow().value() + "^";
        }
        if (target == RuleDialect.UBO) {
            return pattern + "*";
        }
        return pattern;
    }

    private static boolean portableExtendedCss(CosmeticRule rule, RuleDialect target) {
        if (rule.dialect() == RuleDialect.ABP || target == RuleDialect.ABP) {
            return portableCss(rule.body());
        }
        return compatibleCss(rule.dialect(), target, rule.body());
    }

    private static boolean compatibleCss(RuleDialect source, RuleDialect target, String body) {
        if (portableCss(body)) {
            return true;
        }
        if (target == RuleDialect.CORE || !adguardUboPair(source, target) || body.contains(":-abp-")) {
            return false;
        }
        return NON_PORTABLE_CSS_MARKERS.stream()
                .filter(body::contains)
                .allMatch(SHARED_ADGUARD_UBO_CSS_MARKERS::contains);
    }

    private static boolean portableCss(String body) {
        if (body.startsWith("+js(") || body.startsWith("^") || body.indexOf('{') >= 0) {
            return false;
        }
        return NON_PORTABLE_CSS_MARKERS.stream().noneMatch(body::contains);
    }

    private static boolean portableHtml(String body) {
        return portableCss(body) && !body.contains("tag-content") && !body.contains("wildcard")
                && !body.contains("max-length") && !body.contains("min-length")
                && !body.startsWith("responseheader(");
    }

    private static boolean safeAdguardCssInjection(String domains, String body) {
        return hasPositiveDomain(domains) && safeCssDeclarations(body);
    }

    private static boolean safeAdguardCssInjection(List<DomainConstraint> domains, String body) {
        return domains.stream().anyMatch(domain -> !domain.excluded())
                && safeCssDeclarations(body);
    }

    private static boolean hasPositiveDomain(String domains) {
        return !domains.isBlank() && List.of(domains.split(",", -1)).stream()
                .map(String::strip)
                .anyMatch(domain -> !domain.isEmpty() && !domain.startsWith("~"));
    }

    private static boolean safeCssDeclarations(String body) {
        int declarationStart = body.lastIndexOf('{');
        if (declarationStart <= 0 || !body.endsWith("}")) {
            return false;
        }
        String selector = body.substring(0, declarationStart).strip();
        if (!compatibleCss(RuleDialect.ADGUARD, RuleDialect.UBO, selector)) {
            return false;
        }
        String declarations = body.substring(declarationStart + 1, body.length() - 1).toLowerCase(Locale.ROOT);
        return !declarations.contains("url(") && !declarations.contains("image-set(")
                && !declarations.contains("/*") && !declarations.contains("*/")
                && !declarations.contains("\\") && !declarations.contains("//");
    }

    private static boolean adguardUboPair(RuleDialect source, RuleDialect target) {
        return source == RuleDialect.ADGUARD && target == RuleDialect.UBO
                || source == RuleDialect.UBO && target == RuleDialect.ADGUARD;
    }

    private static Optional<Projection> convertRemoveAction(CosmeticRule rule, RuleDialect target) {
        if (rule.operator() != CosmeticOperator.ELEMENT_HIDE) {
            return Optional.empty();
        }
        Matcher declaration = REMOVE_DECLARATION.matcher(rule.body());
        if ((rule.dialect() == RuleDialect.ADGUARD || rule.dialect() == RuleDialect.ABP)
                && target == RuleDialect.UBO && declaration.matches()
                && rule.domains().stream().anyMatch(domain -> !domain.excluded())
                && compatibleCss(rule.dialect(), target, declaration.group(1))) {
            return Optional.of(new Projection(encode(rule.domains(), "##", declaration.group(1) + ":remove()"),
                    "remove 声明转换为 uBO :remove() 操作符"));
        }
        if (rule.dialect() == RuleDialect.UBO
                && (target == RuleDialect.ADGUARD || target == RuleDialect.ABP)
                && rule.body().endsWith(":remove()")) {
            String selector = rule.body().substring(0, rule.body().length() - ":remove()".length());
            if (!compatibleCss(rule.dialect(), target, selector)) {
                return Optional.empty();
            }
            return Optional.of(new Projection(encode(rule.domains(), "##", selector + " { remove: true; }"),
                    "uBO :remove() 操作符转换为 remove 声明"));
        }
        return Optional.empty();
    }

    private static String encode(List<DomainConstraint> domains, String operator, String body) {
        return String.join(",", domains.stream()
                .map(domain -> (domain.excluded() ? "~" : "") + domain.domain().value()).toList())
                + operator + body;
    }

    public record Projection(String text, String reason) {
    }
}
