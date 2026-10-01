package dev.fordes.adfs.rule.model;

public record NetworkMatch(Network value) implements MatchExpression {

    public enum Network {
        TCP("tcp"),
        UDP("udp");

        private final String value;

        Network(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
