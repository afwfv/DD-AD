package dev.fordes.adfs.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ConversionPropertiesTest {

    @Test
    void documentedDefaultsMatchExampleConfiguration() {
        ConversionProperties properties = new ConversionProperties();

        assertFalse(properties.isAllowExpansion());
        assertTrue(properties.isAllowReduction(),
                "config/application-example.yaml 声明 allow-reduction 默认为 true");
    }
}
