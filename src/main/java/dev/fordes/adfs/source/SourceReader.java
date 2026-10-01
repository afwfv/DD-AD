package dev.fordes.adfs.source;

import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.InputSpec;

/** 打开一种输入位置对应的有界来源会话。 */
public interface SourceReader {

    boolean supports(InputSpec input);

    SourceSession open(InputSpec input, EffectiveConfig config);
}
