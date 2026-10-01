package dev.fordes.adfs.validation.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import dev.fordes.adfs.config.EffectiveConfig.DnsCacheConfig;
import dev.fordes.adfs.config.EffectiveConfig.DnsConfig;
import dev.fordes.adfs.rule.model.DomainName;

final class DnsjavaResolverTest {

    @Test
    void resolvesAddressFamiliesCnameAndReusesCache() throws IOException {
        try (DnsServer server = new DnsServer()) {
            DnsjavaResolver resolver = new DnsjavaResolver(server.config());
            assertEquals(DnsResult.VALID, resolver.resolve(new DomainName("v4.example")));
            int requests = server.requests.get();
            assertEquals(DnsResult.VALID, resolver.resolve(new DomainName("v4.example")));
            assertEquals(requests, server.requests.get());
            assertEquals(DnsResult.VALID, resolver.resolve(new DomainName("v6.example")));
            assertEquals(DnsResult.VALID, resolver.resolve(new DomainName("alias.example")));
            assertEquals(DnsResult.INVALID, resolver.resolve(new DomainName("missing.example")));
            assertTrue(resolver.cacheSize() > 0);
        }
    }

    @Test
    void returnsFailureAfterConfiguredRetry() throws IOException {
        try (DnsServer server = new DnsServer()) {
            DnsjavaResolver resolver = new DnsjavaResolver(server.config());
            assertEquals(DnsResult.FAILED, resolver.resolve(new DomainName("failure.example")));
        }
    }

    @Test
    void returnsFailureAfterAsyncTimeout() throws IOException {
        try (DnsServer server = new DnsServer()) {
            DnsjavaResolver resolver = new DnsjavaResolver(server.config(Duration.ofMillis(100)));
            assertEquals(DnsResult.FAILED, resolver.resolve(new DomainName("timeout.example")));
        }
    }

    private static final class DnsServer implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Future<Void> serving;
        private final AtomicInteger requests = new AtomicInteger();

        private DnsServer() throws SocketException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
            serving = executor.submit(() -> {
                serve();
                return null;
            });
        }

        private DnsConfig config() {
            return config(Duration.ofSeconds(1));
        }

        private DnsConfig config(Duration timeout) {
            return new DnsConfig(true, false, List.of("127.0.0.1:" + socket.getLocalPort()), 4,
                    timeout, 1, 4,
                    new DnsCacheConfig(1_024, Duration.ofMinutes(1), Duration.ofSeconds(30)));
        }

        private void serve() throws IOException {
            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(new byte[4_096], 4_096);
                try {
                    socket.receive(packet);
                } catch (SocketException exception) {
                    if (socket.isClosed()) {
                        return;
                    }
                    throw exception;
                }
                requests.incrementAndGet();
                Message query = new Message(Arrays.copyOf(packet.getData(), packet.getLength()));
                Message response = new Message(query.getHeader().getID());
                response.getHeader().setFlag(Flags.QR);
                response.getHeader().setFlag(Flags.AA);
                response.addRecord(query.getQuestion(), Section.QUESTION);
                Name name = query.getQuestion().getName();
                int type = query.getQuestion().getType();
                switch (name.toString()) {
                    case "v4.example." -> {
                        if (type == Type.A) {
                            response.addRecord(new ARecord(name, DClass.IN, 60,
                                    InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1})), Section.ANSWER);
                        }
                    }
                    case "v6.example." -> {
                        if (type == Type.AAAA) {
                            byte[] address = new byte[16];
                            address[15] = 1;
                            response.addRecord(new AAAARecord(name, DClass.IN, 60,
                                    InetAddress.getByAddress(address)), Section.ANSWER);
                        }
                    }
                    case "alias.example." -> response.addRecord(new CNAMERecord(name, DClass.IN, 60,
                            Name.fromString("v4.example.")), Section.ANSWER);
                    case "missing.example." -> response.getHeader().setRcode(Rcode.NXDOMAIN);
                    case "failure.example." -> response.getHeader().setRcode(Rcode.SERVFAIL);
                    case "timeout.example." -> {
                        continue;
                    }
                    default -> throw new IOException("测试 DNS 收到未声明的查询: name=" + name);
                }
                byte[] bytes = response.toWire();
                socket.send(new DatagramPacket(bytes, bytes.length, packet.getSocketAddress()));
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
            try (executor) {
                serving.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("等待测试 DNS 服务关闭时被中断", exception);
            } catch (ExecutionException exception) {
                throw new IOException("测试 DNS 服务失败", exception.getCause());
            }
        }
    }
}
