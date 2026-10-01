package dev.fordes.adfs.rule.conversion;

import dev.fordes.adfs.config.EffectiveConfig.ConversionConfig;

public record ConversionPolicy(boolean allowExpansion, boolean allowReduction) {

    public static ConversionPolicy from(ConversionConfig config) {
        return new ConversionPolicy(config.allowExpansion(), config.allowReduction());
    }

    public boolean allows(ConversionScope scope) {
        return switch (scope) {
            case EXACT -> true;
            case EXPANDED -> allowExpansion;
            case REDUCED -> allowReduction;
            case MIXED -> allowExpansion && allowReduction;
            case UNSUPPORTED -> false;
        };
    }
}
