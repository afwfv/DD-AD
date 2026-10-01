package dev.fordes.adfs.format.dnsmasq;

import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.IpAddress;

final class DnsmasqSyntax {

    private DnsmasqSyntax() {
    }

    static void selector(String value) {
        if (value.isEmpty() || value.equals("#") || value.equals("*")) {
            return;
        }
        String domain = value.startsWith("*") ? value.substring(1) : value;
        if (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        new DomainName(domain);
    }

    static void server(String body) {
        String upstream = body;
        boolean scoped = body.startsWith("/");
        if (scoped) {
            int last = body.lastIndexOf('/');
            if (last == 0) {
                throw new RuleProcessingException("dnsmasq server 域名段未闭合");
            }
            for (String domain : body.substring(1, last).split("/", -1)) {
                selector(domain);
            }
            upstream = body.substring(last + 1);
        }
        if (upstream.isEmpty() && scoped || upstream.equals("#")) {
            return;
        }
        String[] parts = upstream.split("@", -1);
        if (parts.length > 3 || parts[0].isEmpty()) {
            throw new RuleProcessingException("dnsmasq server 上游或来源参数非法");
        }
        endpoint(parts[0]);
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.chars().anyMatch(Character::isWhitespace)) {
                throw new RuleProcessingException("dnsmasq server 来源参数为空或包含空白");
            }
            if (part.indexOf(':') >= 0 || part.indexOf('#') >= 0 || part.matches("[0-9.]+")) {
                endpoint(part);
            } else if (!part.matches("[a-zA-Z0-9_.:-]+")) {
                throw new RuleProcessingException("dnsmasq server 接口名称非法");
            }
        }
    }

    private static void endpoint(String value) {
        int hash = value.indexOf('#');
        String host = hash < 0 ? value : value.substring(0, hash);
        if (hash >= 0) {
            String port = value.substring(hash + 1);
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65_535) {
                throw new RuleProcessingException("dnsmasq server 端口非法: " + port);
            }
        }
        int zone = host.indexOf('%');
        if (zone >= 0) {
            if (!host.substring(zone + 1).matches("[a-zA-Z0-9_.:-]+")) {
                throw new RuleProcessingException("dnsmasq server IPv6 scope-id 非法");
            }
            host = host.substring(0, zone);
        }
        if (host.indexOf(':') >= 0 || host.matches("[0-9.]+")) {
            IpAddress.parse(host);
        } else {
            new DomainName(host);
        }
    }
}
