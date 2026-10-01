package dev.fordes.adfs.config;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fordes.adfs.error.ConfigurationException;

/** 校验并单次替换文件头变量, 不递归解释替换后的内容。 */
public final class FileHeaderTemplate {

    public static final String DEFAULT = """
            Title: adblock list of {{dialect}}
            Total Size: {{total}}
            Last Modified: {{date}}
            Powered by https://github.com/fordes123/ad-filters-subscriber
            """;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([^{}]*)}}");
    private static final Set<String> VARIABLES = Set.of("name", "type", "dialect", "date", "total");

    private FileHeaderTemplate() {
    }

    public static void validate(String template, String field) {
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) {
            if (!VARIABLES.contains(matcher.group(1))) {
                throw new ConfigurationException(field + " 包含未知文件头变量: " + matcher.group());
            }
        }
        String remaining = matcher.replaceAll("");
        if (remaining.contains("{{") || remaining.contains("}}")) {
            throw new ConfigurationException(field + " 包含不完整的文件头变量, 格式必须为 {{变量名}}");
        }
    }

    public static String render(OutputSpec output, OffsetDateTime generatedAt, long total) {
        return VARIABLE.matcher(output.fileHeader()).replaceAll(match -> Matcher.quoteReplacement(
                switch (match.group(1)) {
                    case "name" -> output.path().toString().replace('\\', '/');
                    case "type" -> output.type().value();
                    case "dialect" -> output.dialect() == RuleDialect.NONE ? output.type().value() : output.dialect().value();
                    case "date" -> generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    case "total" -> Long.toString(total);
                    default -> throw new ConfigurationException("未知文件头变量: " + match.group());
                }));
    }
}
