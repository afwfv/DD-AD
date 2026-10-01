package dev.fordes.adfs;

import dev.fordes.adfs.application.AdfsRunner;
import dev.fordes.adfs.config.CliConfiguration;
import dev.fordes.adfs.error.AdfsException;
import dev.fordes.adfs.error.ConfigurationException;
import dev.fordes.adfs.error.ExitCode;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.InitializationException;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "adfs",
        description = "读取、转换并发布广告过滤规则",
        version = "2.0.0",
        mixinStandardHelpOptions = true)
public final class AdfsCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, paramLabel = "<文件>", description = "指定 YAML 配置文件")
    @Nullable
    private Path configFile;

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String[] args) {
        return new CommandLine(new AdfsCommand()).execute(args);
    }

    @Override
    public Integer call() {
        try (ApplicationContext context = ApplicationContext.builder(AdfsCommand.class, Environment.CLI)
                .enableDefaultPropertySources(false)
                .propertySources(CliConfiguration.load(configFile))
                .start()) {
            printBanner(context);
            return context.getBean(AdfsRunner.class).run().value();
        } catch (BeanContextException | InitializationException exception) {
            log.error("配置加载失败, 处理已终止: {}", exception.getMessage(), exception);
            return ExitCode.CONFIGURATION.value();
        } catch (AdfsException exception) {
            log.error("处理已终止: {} --> {}", exception.stage(), exception.getMessage(), exception);
            return exception.exitCode().value();
        }
    }

    private static void printBanner(ApplicationContext context) {
        try (InputStream input = AdfsCommand.class.getResourceAsStream("/banner.txt")) {
            if (input == null) {
                throw new ConfigurationException("启动横幅资源不存在: banner.txt");
            }

            var version = AdfsCommand.class.getAnnotation(Command.class).version()[0];
            var os = System.getProperty("os.name");
            var java = System.getProperty("java.version");
            var pid = ProcessHandle.current().pid();

            String banner = new String(input.readAllBytes(), StandardCharsets.UTF_8).formatted(version);
            System.err.println(CommandLine.Help.Ansi.AUTO.string(banner));
        } catch (IOException exception) {
            throw new ConfigurationException("读取启动横幅失败: banner.txt", exception);
        }
    }
}
