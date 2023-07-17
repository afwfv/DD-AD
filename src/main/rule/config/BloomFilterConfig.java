package org.fordes.adg.rule.config;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.bloomfilter.BloomFilterUtil;
import lombok.AllArgsConstructor;
import org.fordes.adg.rule.util.RuleUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Chengfs on 2023/4/7
 */
@Configuration
@AllArgsConstructor
public class BloomFilterConfig {

    private final FilterConfig config;

    @Bean("bloomFilter")
    public BitSetBloomFilter getBloomFilter() {
        long numOfBits = RuleUtil.optimalNumOfBits(config.getExpectedQuantity(), config.getFaultTolerance());
        int numOfHashFunctions = RuleUtil.optimalNumOfHashFunctions(config.getExpectedQuantity(), numOfBits);
        return BloomFilterUtil.createBitSet((int) numOfBits, (int) numOfBits, numOfHashFunctions);
    }
}

    
    