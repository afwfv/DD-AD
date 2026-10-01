package dev.fordes.adfs.rule.model;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import dev.fordes.adfs.error.RuleProcessingException;

public record IpAddress(IpFamily family, long high, long low) {

    public static IpAddress parse(String value) {
        if (value.indexOf(':') >= 0) {
            return parseIpv6(value);
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new RuleProcessingException("IP 地址非法: " + value);
        }
        long address = 0;
        for (String part : parts) {
            try {
                if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                    throw new NumberFormatException("IPv4 octet 不是十进制数字");
                }
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    throw new NumberFormatException("IPv4 octet 越界");
                }
                address = address << 8 | octet;
            } catch (NumberFormatException exception) {
                throw new RuleProcessingException("IPv4 地址非法: " + value, exception);
            }
        }
        return new IpAddress(IpFamily.IPV4, 0, address);
    }

    private static IpAddress parseIpv6(String value) {
        if (value.indexOf('%') >= 0) {
            throw new RuleProcessingException("IPv6 地址不得包含 zone id: " + value);
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) {
                throw new RuleProcessingException("IPv6 地址非法: " + value);
            }
            ByteBuffer bytes = ByteBuffer.wrap(address.getAddress());
            return new IpAddress(IpFamily.IPV6, bytes.getLong(), bytes.getLong());
        } catch (UnknownHostException exception) {
            throw new RuleProcessingException("IPv6 地址非法: " + value, exception);
        }
    }

    public boolean isBlockingTarget() {
        return switch (family) {
            case IPV4 -> high == 0 && (low == 0 || low >>> 24 == 127);
            case IPV6 -> high == 0 && (low == 0 || low == 1);
        };
    }

    public IpAddress mask(int prefixLength) {
        if (family == IpFamily.IPV4) {
            long mask = prefixLength == 0 ? 0 : -1L << (32 - prefixLength) & 0xffff_ffffL;
            return new IpAddress(family, 0, low & mask);
        }
        if (prefixLength == 0) {
            return new IpAddress(family, 0, 0);
        }
        if (prefixLength <= 64) {
            return new IpAddress(family, high & -1L << (64 - prefixLength), 0);
        }
        return new IpAddress(family, high, low & -1L << (128 - prefixLength));
    }

    public String text() {
        if (family == IpFamily.IPV4) {
            return (low >>> 24 & 255) + "." + (low >>> 16 & 255) + "." + (low >>> 8 & 255) + "." + (low & 255);
        }
        byte[] bytes = ByteBuffer.allocate(16).putLong(high).putLong(low).array();
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("固定长度 IPv6 地址无法编码", exception);
        }
    }
}
