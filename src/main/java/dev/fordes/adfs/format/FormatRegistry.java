package dev.fordes.adfs.format;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.InputSpec;
import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.ConfigurationException;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.format.adblock.AdblockParser;
import dev.fordes.adfs.format.adblock.AdblockWriter;
import dev.fordes.adfs.format.dns.DnsParser;
import dev.fordes.adfs.format.dnsmasq.DnsmasqParser;
import dev.fordes.adfs.format.hosts.HostsParser;
import dev.fordes.adfs.format.mihomo.MihomoClassicalParser;
import dev.fordes.adfs.format.mihomo.MihomoDomainParser;
import dev.fordes.adfs.format.mihomo.MihomoIpCidrParser;
import dev.fordes.adfs.format.singbox.SingBoxParser;
import dev.fordes.adfs.format.singbox.SingBoxWriter;
import dev.fordes.adfs.format.smartdns.SmartDnsParser;
import dev.fordes.adfs.format.smartdns.SmartDnsWriter;
import dev.fordes.adfs.rule.conversion.ConversionPolicy;
import dev.fordes.adfs.rule.dedup.CanonicalStore;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;

@Singleton
public final class FormatRegistry {

    private static final Map<RuleType, Set<RuleDialect>> SUPPORTED_DIALECTS = Map.of(
            RuleType.HOSTS, Set.of(RuleDialect.NONE),
            RuleType.DNS, Set.of(RuleDialect.ADGUARD),
            RuleType.DNSMASQ, Set.of(RuleDialect.NONE),
            RuleType.MIHOMO, Set.of(RuleDialect.DOMAIN, RuleDialect.IPCIDR, RuleDialect.CLASSICAL),
            RuleType.SING_BOX, Set.of(RuleDialect.NONE),
            RuleType.SMARTDNS, Set.of(RuleDialect.NONE),
            RuleType.ADBLOCK, Set.of(RuleDialect.CORE, RuleDialect.ADGUARD, RuleDialect.ABP, RuleDialect.UBO));

    public void validateSupported(EffectiveConfig config) {
        for (InputSpec input : config.inputs()) {
            if (!isSupported(input.type(), input.dialect())) {
                throw new ConfigurationException("输入格式尚未实现: " + input.name() + " --> "
                        + input.type().value() + "/" + input.dialect().value());
            }
        }
        for (OutputSpec output : config.outputs()) {
            if (!isSupported(output.type(), output.dialect())) {
                throw new ConfigurationException("输出格式尚未实现: " + output.path() + " --> "
                        + output.type().value() + "/" + output.dialect().value());
            }
        }
    }

    public RuleParser createParser(InputSpec input, EffectiveConfig config) {
        return switch (input.type()) {
            case HOSTS -> new HostsParser(config.inputLimits(), config.rules());
            case DNS -> new DnsParser(config.inputLimits(), config.rules());
            case DNSMASQ -> new DnsmasqParser(config.inputLimits(), config.rules());
            case MIHOMO -> switch (input.dialect()) {
                case DOMAIN -> new MihomoDomainParser(config.inputLimits(), config.rules());
                case IPCIDR -> new MihomoIpCidrParser(config.inputLimits(), config.rules());
                case CLASSICAL -> new MihomoClassicalParser(config.inputLimits(), config.rules());
                default -> throw new ConfigurationException("Mihomo 输入方言尚未实现: " + input.dialect().value());
            };
            case SING_BOX -> new SingBoxParser(config.rules());
            case SMARTDNS -> new SmartDnsParser(config.inputLimits(), config.rules());
            case ADBLOCK -> new AdblockParser(config.inputLimits(), config.rules(), input.dialect());
        };
    }

    public OutputTarget createWriter(OutputSpec target, EffectiveConfig config, Path nextDir, Path runDir, int index) {
        Path output = nextDir.resolve(target.path()).normalize();
        Path storePath = runDir.resolve("target-" + index + ".bin");
        try {
            Path parent = output.getParent();
            Files.createDirectories(parent);
            CanonicalStore store = new CanonicalStore(storePath);
            try {
                OutputStream stream = new BufferedOutputStream(Files.newOutputStream(output));
                try {
                    OutputDeduplicator deduplicator = new OutputDeduplicator(store);
                    ConversionPolicy policy = ConversionPolicy.from(config.conversion());
                    RuleWriter writer = switch (target.type()) {
                        case SING_BOX -> new SingBoxWriter(target, policy,
                                config.rules().whitelist(), deduplicator, stream);
                        case SMARTDNS -> new SmartDnsWriter(target, policy,
                                config.rules().whitelist(), deduplicator, stream);
                        case ADBLOCK -> new AdblockWriter(target, policy,
                                config.rules().whitelist(), deduplicator, stream);
                        default -> new BasicRuleWriter(target, policy,
                                config.rules().whitelist(), deduplicator, stream);
                    };
                    return new OutputTarget(target, writer, store, deduplicator);
                } catch (RuntimeException exception) {
                    closeAfterFailure(stream, store, exception);
                    throw exception;
                }
            } catch (IOException exception) {
                try {
                    store.close();
                } catch (RuntimeException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
                throw exception;
            }
        } catch (IOException exception) {
            throw new OutputException("创建目标输出失败: " + output, exception);
        }
    }

    private static void closeAfterFailure(OutputStream stream, CanonicalStore store, RuntimeException failure) {
        try {
            stream.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        try {
            store.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static boolean isSupported(RuleType type, RuleDialect dialect) {
        return SUPPORTED_DIALECTS.getOrDefault(type, Set.of()).contains(dialect);
    }
}
