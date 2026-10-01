package dev.fordes.adfs.format.adblock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dev.fordes.adfs.config.RuleDialect;

public record AdblockDialectDefinition(
        RuleDialect dialect,
        Set<String> builtInTokens,
        Map<String, AdblockCapability> cosmeticOperators,
        Map<String, AdblockCapability> options) {

    private static final Set<String> COMMON_OPTIONS = Set.of(
            "script", "image", "stylesheet", "font", "media", "object", "xmlhttprequest", "subdocument",
            "document", "popup", "websocket", "ping", "other", "domain", "third-party", "match-case",
            "elemhide", "generichide");
    private static final Set<String> VALUE_MODIFIERS = Set.of(
            "redirect", "redirect-rule", "removeparam", "csp", "permissions", "replace", "urltransform");

    public AdblockDialectDefinition {
        builtInTokens = Set.copyOf(builtInTokens);
        cosmeticOperators = Map.copyOf(cosmeticOperators);
        options = Map.copyOf(options);
    }

    public static AdblockDialectDefinition forDialect(RuleDialect dialect) {
        Map<String, AdblockCapability> operators = switch (dialect) {
            case ABP -> Map.of("#@#", AdblockCapability.SEMANTIC, "##", AdblockCapability.SEMANTIC,
                    "#?#", AdblockCapability.SEMANTIC, "#$#", AdblockCapability.SEMANTIC);
            case CORE -> Map.of("#@#", AdblockCapability.SEMANTIC, "##", AdblockCapability.SEMANTIC);
            case ADGUARD -> Map.ofEntries(
                    Map.entry("#@$?#", AdblockCapability.SEMANTIC),
                    Map.entry("#$?#", AdblockCapability.SEMANTIC),
                    Map.entry("#@?#", AdblockCapability.SEMANTIC), Map.entry("#?#", AdblockCapability.SEMANTIC),
                    Map.entry("#@%#", AdblockCapability.SEMANTIC), Map.entry("#%#", AdblockCapability.SEMANTIC),
                    Map.entry("#@$#", AdblockCapability.SEMANTIC), Map.entry("#$#", AdblockCapability.SEMANTIC),
                    Map.entry("$@$", AdblockCapability.SEMANTIC), Map.entry("$$", AdblockCapability.SEMANTIC),
                    Map.entry("#@#", AdblockCapability.SEMANTIC), Map.entry("##", AdblockCapability.SEMANTIC));
            case UBO -> Map.of(
                    "#@?#", AdblockCapability.SEMANTIC, "#?#", AdblockCapability.SEMANTIC,
                    "#@$?#", AdblockCapability.PASSTHROUGH, "#$?#", AdblockCapability.PASSTHROUGH,
                    "#@$#", AdblockCapability.PASSTHROUGH, "#$#", AdblockCapability.PASSTHROUGH,
                    "#@#", AdblockCapability.SEMANTIC, "##", AdblockCapability.SEMANTIC);
            default -> throw new IllegalArgumentException("不是 Adblock 方言: " + dialect.value());
        };
        Map<String, AdblockCapability> options = new HashMap<>();
        COMMON_OPTIONS.forEach(option -> options.put(option, AdblockCapability.SEMANTIC));
        if (dialect == RuleDialect.ADGUARD || dialect == RuleDialect.UBO) {
            options.put("important", AdblockCapability.SEMANTIC);
            options.put("badfilter", AdblockCapability.SEMANTIC);
            options.put("first-party", AdblockCapability.SEMANTIC);
            options.put("all", AdblockCapability.SEMANTIC);
            options.put("inline-script", AdblockCapability.SEMANTIC);
            options.put("inline-font", AdblockCapability.SEMANTIC);
            options.put("specifichide", AdblockCapability.SEMANTIC);
            options.put("to", AdblockCapability.PASSTHROUGH);
            options.put("denyallow", AdblockCapability.PASSTHROUGH);
            options.put("method", AdblockCapability.PASSTHROUGH);
            options.put("queryprune", AdblockCapability.PASSTHROUGH);
        }
        if (dialect == RuleDialect.ADGUARD || dialect == RuleDialect.ABP) {
            options.put("genericblock", AdblockCapability.SEMANTIC);
        }
        if (dialect == RuleDialect.UBO) {
            options.put("genericblock", AdblockCapability.INVALID);
            options.put("webrtc", AdblockCapability.INVALID);
            options.put("uritransform", AdblockCapability.PASSTHROUGH);
        }
        if (dialect == RuleDialect.ADGUARD) {
            options.put("xhr", AdblockCapability.SEMANTIC);
            options.put("webrtc", AdblockCapability.INVALID);
            options.put("uritransform", AdblockCapability.INVALID);
            options.put("urlskip", AdblockCapability.INVALID);
        }
        if (dialect == RuleDialect.ABP) {
            options.put("xhr", AdblockCapability.INVALID);
            options.put("webrtc", AdblockCapability.SEMANTIC);
        }
        VALUE_MODIFIERS.forEach(option -> options.put(option,
                dialect == RuleDialect.CORE ? AdblockCapability.PASSTHROUGH : AdblockCapability.SEMANTIC));
        if (dialect == RuleDialect.ABP) {
            options.put("important", AdblockCapability.INVALID);
            options.put("badfilter", AdblockCapability.INVALID);
            options.put("first-party", AdblockCapability.INVALID);
            options.put("to", AdblockCapability.INVALID);
            options.put("denyallow", AdblockCapability.INVALID);
            options.put("method", AdblockCapability.INVALID);
            options.put("queryprune", AdblockCapability.INVALID);
            options.put("redirect", AdblockCapability.INVALID);
            options.put("redirect-rule", AdblockCapability.INVALID);
            options.put("removeparam", AdblockCapability.INVALID);
            options.put("permissions", AdblockCapability.INVALID);
            options.put("replace", AdblockCapability.INVALID);
            options.put("urltransform", AdblockCapability.INVALID);
            options.put("uritransform", AdblockCapability.INVALID);
            options.put("urlskip", AdblockCapability.INVALID);
        }
        if (dialect == RuleDialect.CORE) {
            options.put("xhr", AdblockCapability.INVALID);
        }
        Set<String> tokens = switch (dialect) {
            case CORE -> Set.of("core");
            case ADGUARD -> Set.of("adguard");
            case ABP -> Set.of("abp");
            case UBO -> Set.of("ubo", "ext_ublock");
            default -> throw new IllegalArgumentException("不是 Adblock 方言: " + dialect.value());
        };
        return new AdblockDialectDefinition(dialect, tokens, operators, options);
    }

    public AdblockCapability option(String name) {
        return options.getOrDefault(name, AdblockCapability.PASSTHROUGH);
    }

    public AdblockCapability cosmetic(String operator) {
        return cosmeticOperators.getOrDefault(operator, AdblockCapability.INVALID);
    }
}
