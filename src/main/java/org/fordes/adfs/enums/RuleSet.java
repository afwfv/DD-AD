package org.fordes.adfs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum RuleSet {

    EASYLIST,
    DNSMASQ,
    CLASH,
    SMARTDNS,
    HOSTS,
    ;

    public static RuleSet of(String name) {
        return Stream.of(values())
                .filter(v -> v.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown format: " + name));
    }

}
