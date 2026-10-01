package dev.fordes.adfs.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.format.dns.DnsParser;
import dev.fordes.adfs.format.dnsmasq.DnsmasqParser;
import dev.fordes.adfs.format.hosts.HostsParser;
import dev.fordes.adfs.format.mihomo.MihomoClassicalParser;
import dev.fordes.adfs.format.mihomo.MihomoDomainParser;
import dev.fordes.adfs.format.mihomo.MihomoIpCidrParser;
import dev.fordes.adfs.format.singbox.SingBoxParser;
import dev.fordes.adfs.format.smartdns.SmartDnsParser;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.testing.ParserTestSupport;
import dev.fordes.adfs.testing.ParserTestSupport.ParseOutcome;
import dev.fordes.adfs.testing.TestConfigs;

final class ParserContractTest {

    private static final InputLimits LIMITS = new InputLimits(1_048_576, 262_144);

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesHostsBlockingAndMappingRecords() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("hosts.txt"),
                "0.0.0.0 Ads.Example example.org # comment\n192.0.2.1 mapped.example\n",
                new HostsParser(LIMITS, TestConfigs.rules()), RuleType.HOSTS, RuleDialect.NONE);

        assertEquals(3, entries.size());
        assertInstanceOf(DomainRule.class, entries.get(0));
        assertEquals(RuleAction.BLOCK, ((DomainRule) entries.get(0)).action());
        assertInstanceOf(HostMappingRule.class, entries.get(2));
    }

    @Test
    void parsesDnsDomainAllowAndHostSyntax() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("dns.txt"),
                "||ads.example^\n@@||allow.example^\nplain.example\n192.0.2.1 map.example\n",
                new DnsParser(LIMITS, TestConfigs.rules()), RuleType.DNS, RuleDialect.ADGUARD);

        assertEquals(4, entries.size());
        assertEquals(RuleAction.ALLOW, ((DomainRule) entries.get(1)).action());
        assertInstanceOf(HostMappingRule.class, entries.getLast());
    }

    @Test
    void parsesDnsmasqDomainsAndPreservesRoutingRule() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("dnsmasq.conf"),
                "address=/one.example/two.example/#\nserver=/route.example/1.1.1.1\n",
                new DnsmasqParser(LIMITS, TestConfigs.rules()), RuleType.DNSMASQ, RuleDialect.NONE);

        assertEquals(3, entries.size());
        assertInstanceOf(DnsAddressRule.class, entries.get(0));
        assertInstanceOf(DnsAddressRule.class, entries.get(1));
        assertInstanceOf(OpaqueRule.class, entries.get(2));
    }

    @Test
    void parsesAllMihomoDialects() throws IOException {
        List<RuleEntry> domains = ParserTestSupport.parse(
                temporaryDirectory.resolve("domain.yaml"),
                "payload:\n  - example.com\n  - '.suffix.example'\n  - '*.wild.example'\n",
                new MihomoDomainParser(LIMITS, TestConfigs.rules()), RuleType.MIHOMO, RuleDialect.DOMAIN);
        List<RuleEntry> cidrs = ParserTestSupport.parse(
                temporaryDirectory.resolve("cidr.txt"), "192.0.2.0/24\n2001:db8::/32\n",
                new MihomoIpCidrParser(LIMITS, TestConfigs.rules()), RuleType.MIHOMO, RuleDialect.IPCIDR);
        List<RuleEntry> classical = ParserTestSupport.parse(
                temporaryDirectory.resolve("classical.txt"),
                "DOMAIN,exact.example\nSRC-IP-CIDR,198.51.100.0/24\nDST-PORT,80-443\nNETWORK,tcp\nGEOIP,CN\n",
                new MihomoClassicalParser(LIMITS, TestConfigs.rules()), RuleType.MIHOMO, RuleDialect.CLASSICAL);

        assertEquals(3, domains.size());
        assertEquals(2, cidrs.size());
        assertTrue(cidrs.stream().allMatch(IpCidrRule.class::isInstance));
        assertEquals(5, classical.size());
        assertInstanceOf(RouteRule.class, classical.get(1));
        assertInstanceOf(OpaqueRule.class, classical.getLast());
    }

    @Test
    void parsesSingBoxLogicalRuleAndPreservesUnknownMatcher() throws IOException {
        String json = """
                {"version":3,"rules":[
                  {"type":"logical","mode":"and","rules":[
                    {"domain_suffix":["example.com"]},{"source_port":[53]}],"invert":true},
                  {"geoip":["cn"]}
                ]}
                """;
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("sing-box.json"), json,
                new SingBoxParser(TestConfigs.rules()), RuleType.SING_BOX, RuleDialect.NONE);

        assertEquals(2, entries.size());
        assertInstanceOf(RouteRule.class, entries.getFirst());
        assertInstanceOf(OpaqueRule.class, entries.getLast());
    }

    @Test
    void parsesSmartDnsSemanticAndOpaqueRules() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("smartdns.conf"),
                "address /-.exact.example/192.0.2.1,192.0.2.2\n"
                        + "address /suffix.example/#\naddress /v4.example/#4\n"
                        + "nameserver /route.example/private\n",
                new SmartDnsParser(LIMITS, TestConfigs.rules()), RuleType.SMARTDNS, RuleDialect.NONE);

        assertEquals(4, entries.size());
        assertEquals(2, assertInstanceOf(DnsAddressRule.class, entries.get(0)).addresses().size());
        assertInstanceOf(DnsAddressRule.class, entries.get(1));
        assertInstanceOf(DnsAddressRule.class, entries.get(2));
        assertInstanceOf(OpaqueRule.class, entries.get(3));
    }

    @Test
    void skipsAndCountsInvalidTextRules() throws IOException {
        ParseOutcome outcome = ParserTestSupport.parseOutcome(
                temporaryDirectory.resolve("broken.conf"), "listen-address 0.0.0.0\n",
                new DnsmasqParser(LIMITS, TestConfigs.rules()), RuleType.DNSMASQ, RuleDialect.NONE);

        assertTrue(outcome.entries().isEmpty());
        assertEquals(1, outcome.metrics().invalidRules());
    }

    @Test
    void preservesMihomoFlagsAndRejectsUnexpectedTail() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("flags.txt"),
                "IP-CIDR,192.0.2.0/24,no-resolve\nPROCESS-NAME,\"a\"\"b,c\"\n",
                new MihomoClassicalParser(LIMITS, TestConfigs.rules()), RuleType.MIHOMO, RuleDialect.CLASSICAL);
        assertEquals("IP-CIDR,192.0.2.0/24,no-resolve", assertInstanceOf(OpaqueRule.class, entries.getFirst()).payload());
        ParseOutcome invalid = ParserTestSupport.parseOutcome(
                temporaryDirectory.resolve("tail.txt"), "DOMAIN,ads.example,unexpected\n",
                new MihomoClassicalParser(LIMITS, TestConfigs.rules()), RuleType.MIHOMO, RuleDialect.CLASSICAL);
        assertTrue(invalid.entries().isEmpty());
        assertEquals(1, invalid.metrics().invalidRules());
    }

    @Test
    void acceptsHyphensInSmartDnsHostnames() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("hyphen.conf"),
                "address /ad-server.example/#\n",
                new SmartDnsParser(LIMITS, TestConfigs.rules()), RuleType.SMARTDNS, RuleDialect.NONE);
        assertEquals("ad-server.example", assertInstanceOf(DnsAddressRule.class, entries.getFirst()).pattern().value());
    }
}
