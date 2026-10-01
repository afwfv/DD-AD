package dev.fordes.adfs.rule.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.fordes.adfs.error.RuleProcessingException;

final class RuleValueTest {

    @Test
    void normalizesInternationalDomainNamesAndRootDot() {
        assertEquals("xn--bcher-kva.example", new DomainName(" BÜCHER.Example. ").value());
        assertEquals("service_name.example", new DomainName("SERVICE_NAME.EXAMPLE").value());
    }

    @Test
    void rejectsInvalidDomainBoundariesAndLabels() {
        assertThrows(RuleProcessingException.class, () -> new DomainName(null));
        assertThrows(RuleProcessingException.class, () -> new DomainName("example..com"));
        assertThrows(RuleProcessingException.class, () -> new DomainName("bad label.example"));
        assertThrows(RuleProcessingException.class, () -> new DomainName("a".repeat(64) + ".example"));
        assertTrue(DomainName.tryParse("valid.example").isPresent());
        assertTrue(DomainName.tryParse("invalid..example").isEmpty());
    }

    @Test
    void parsesMasksAndRendersIpAddresses() {
        IpAddress ipv4 = IpAddress.parse("192.0.2.129");
        IpAddress ipv6 = IpAddress.parse("2001:db8::1");

        assertEquals(IpFamily.IPV4, ipv4.family());
        assertEquals("192.0.2.0", ipv4.mask(24).text());
        assertEquals("2001:db8:0:0:0:0:0:0", ipv6.mask(64).text());
        assertTrue(IpAddress.parse("0.0.0.0").isBlockingTarget());
        assertTrue(IpAddress.parse("127.0.0.1").isBlockingTarget());
        assertTrue(IpAddress.parse("::1").isBlockingTarget());
        assertFalse(ipv4.isBlockingTarget());
    }

    @Test
    void validatesIpAndCidrSyntax() {
        assertEquals("192.0.2.0/24", IpCidr.parse("192.0.2.0/24").text());
        assertThrows(RuleProcessingException.class, () -> IpAddress.parse("192.0.2.256"));
        assertThrows(RuleProcessingException.class, () -> IpAddress.parse("2001:db8::1%1"));
        assertThrows(RuleProcessingException.class, () -> IpCidr.parse("192.0.2.1/24"));
        assertThrows(IllegalArgumentException.class,
                () -> new IpCidrRule(IpAddress.parse("192.0.2.1"), 33, RuleAction.BLOCK));
    }
}
