package dev.fordes.adfs.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.InputSpec.HttpSource;
import dev.fordes.adfs.config.InputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.InputException;
import dev.fordes.adfs.testing.TestConfigs;

final class HttpSourceReaderTest {

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/rules.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/rules.txt", exchange -> respond(exchange, 200, "example.com\n"));
        server.createContext("/failure", exchange -> respond(exchange, 503, "temporarily unavailable"));
        server.start();
        baseUri = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void followsBoundedRedirectAndStreamsResponse() throws IOException {
        EffectiveConfig config = config(baseUri.resolve("/redirect"));

        try (SourceSession session = new HttpSourceReader().open(config.inputs().getFirst(), config)) {
            assertEquals("example.com\n",
                    new String(session.root().input().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(baseUri.resolve("/rules.txt"), session.root().location());
        }
    }

    @Test
    void reportsStatusAndBoundedResponseBody() {
        EffectiveConfig config = config(baseUri.resolve("/failure"));

        InputException exception = assertThrows(InputException.class,
                () -> new HttpSourceReader().open(config.inputs().getFirst(), config));

        assertTrue(exception.getMessage().contains("HTTP 503"));
        assertTrue(exception.getMessage().contains("temporarily unavailable"));
    }

    private EffectiveConfig config(URI uri) {
        Path placeholder = temporaryDirectory.resolve("placeholder.txt");
        EffectiveConfig base = TestConfigs.create(
                placeholder, RuleType.HOSTS, RuleDialect.NONE,
                temporaryDirectory.resolve("output"), List.of(), false);
        InputSpec input = new InputSpec("http-test", new HttpSource(uri), RuleType.HOSTS, RuleDialect.NONE);
        return new EffectiveConfig(
                base.outputDir(), base.inputLimits(), base.http(), base.rules(), base.conversion(), base.dns(),
                List.of(input), List.of());
    }

    @Test
    void rejectsIncludeRedirectOutsideRootDirectory() {
        server.createContext("/filters/root.txt", exchange -> respond(exchange, 200, "root\n"));
        server.createContext("/filters/child.txt", exchange -> {
            exchange.getResponseHeaders().add("Location", "/rules.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        EffectiveConfig config = config(baseUri.resolve("/filters/root.txt"));
        try (SourceSession session = new HttpSourceReader().open(config.inputs().getFirst(), config)) {
            InputException exception = assertThrows(InputException.class,
                    () -> session.openInclude(session.root(), "child.txt"));
            assertTrue(exception.getMessage().contains("越过根来源基址"));
            assertThrows(InputException.class, () -> session.openInclude(session.root(), "%2e%2e/rules.txt"));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
