package dev.fordes.adfs.publish;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.publish.StagingWorkspace.Workspace;

public record PublishManifest(List<Entry> entries) {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String MANIFEST_NAME = "publish.manifest";

    public PublishManifest {
        entries = List.copyOf(entries);
    }

    public static PublishManifest create(Workspace workspace, List<OutputSpec> outputs) {
        Set<Path> expected = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (OutputSpec output : outputs) {
            Path relative = output.path().normalize();
            Path file = workspace.nextDir().resolve(relative).normalize();
            if (!file.startsWith(workspace.nextDir()) || !expected.add(relative)) {
                throw new OutputException("发布清单包含越界或重复路径: " + relative);
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new OutputException("声明的输出不是普通文件: " + file);
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
                entries.add(new Entry(relative, Files.size(file), hash(file)));
            } catch (IOException exception) {
                throw new OutputException("校验输出文件失败: " + file, exception);
            }
        }
        validateExactFiles(workspace.nextDir(), expected);
        entries.sort(Comparator.comparing(entry -> entry.path().toString()));
        PublishManifest manifest = new PublishManifest(entries);
        manifest.write(workspace.runDir().resolve(MANIFEST_NAME));
        return manifest;
    }

    public void validate(Workspace workspace) {
        Set<Path> expected = new HashSet<>();
        for (Entry entry : entries) {
            Path file = workspace.nextDir().resolve(entry.path()).normalize();
            if (!file.startsWith(workspace.nextDir()) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new OutputException("待发布输出状态已改变: " + file);
            }
            try {
                if (Files.size(file) != entry.size() || !hash(file).equals(entry.sha256())) {
                    throw new OutputException("待发布输出大小或 SHA-256 已改变: " + file);
                }
            } catch (IOException exception) {
                throw new OutputException("重新校验待发布输出失败: " + file, exception);
            }
            expected.add(entry.path());
        }
        validateExactFiles(workspace.nextDir(), expected);
    }

    private static void validateExactFiles(Path nextDir, Set<Path> expected) {
        try (Stream<Path> paths = Files.walk(nextDir)) {
            Set<Path> actual = paths.filter(path -> !path.equals(nextDir))
                    .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(nextDir::relativize)
                    .collect(Collectors.toUnmodifiableSet());
            if (!actual.equals(expected)) {
                throw new OutputException("待发布文件集合与配置不一致: " + expected + " --> " + actual);
            }
        } catch (IOException exception) {
            throw new OutputException("检查待发布文件集合失败: " + nextDir, exception);
        }
    }

    private void write(Path path) {
        StringBuilder content = new StringBuilder();
        for (Entry entry : entries) {
            content.append(entry.sha256()).append(' ')
                    .append(entry.size()).append(' ')
                    .append(entry.path().toString().replace('\\', '/')).append('\n');
        }
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new OutputException("写入发布清单失败: " + path, exception);
        }
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record Entry(Path path, long size, String sha256) {
    }
}
