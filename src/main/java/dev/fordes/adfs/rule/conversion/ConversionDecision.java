package dev.fordes.adfs.rule.conversion;

import java.util.Set;
import java.util.EnumSet;

public record ConversionDecision(ConversionScope scope, Set<ConversionLoss> losses, String reason) {

    public ConversionDecision {
        losses = Set.copyOf(losses);
    }

    public ConversionDecision(ConversionScope scope, String reason) {
        this(scope, Set.of(), reason);
    }

    public ConversionDecision with(ConversionScope additionalScope, ConversionLoss loss, String additionalReason) {
        EnumSet<ConversionLoss> combinedLosses = losses.isEmpty()
                ? EnumSet.noneOf(ConversionLoss.class) : EnumSet.copyOf(losses);
        combinedLosses.add(loss);
        return new ConversionDecision(scope.combine(additionalScope), combinedLosses,
                reason + "; " + additionalReason);
    }
}
