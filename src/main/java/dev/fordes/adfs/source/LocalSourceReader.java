package dev.fordes.adfs.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Singleton;

import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.InputSpec.LocalSource;
import dev.fordes.adfs.config.InputSpec;
import dev.fordes.adfs.error.InputException;

@Singleton
public final class LocalSourceReader implements SourceReader {

    @Override
    public boolean supports(InputSpec input) {
        return input.source() instanceof LocalSource;
    }

    @Override
    public SourceSession open(InputSpec input, EffectiveConfig config) {
        LocalSource source = (LocalSource) input.source();
        SourceSessionState state = new SourceSessionState(
                config.inputLimits().maxSize(), config.rules().preprocessor().maxIncludeDepth());
        return new LocalSession(input.name(), source.path(), state);
    }
}

final class LocalSession implements SourceSession {

    private final String name;
    private final SourceSessionState state;
    private final Path rootDirectory;
    private final SourceStream root;

    LocalSession(String name, Path path, SourceSessionState state) {
        this.name = name;
        this.state = state;
        try {
            this.rootDirectory = path.toAbsolutePath().normalize().getParent().toRealPath();
        } catch (IOException exception) {
            throw new InputException("无法解析本地来源目录: " + name + " --> " + path, exception);
        }
        this.root = open(path, 0);
    }

    @Override
    public SourceStream root() {
        return root;
    }

    @Override
    public SourceStream openInclude(SourceStream parent, String reference) {
        Path requested;
        try {
            requested = Path.of(reference);
        } catch (java.nio.file.InvalidPathException exception) {
            throw new InputException("include 路径非法: " + reference, exception);
        }
        if (requested.isAbsolute()) {
            throw new InputException("include 只接受相对路径: " + reference);
        }
        Path parentPath = Path.of(parent.location());
        try {
            Path includePath = parentPath.getParent().resolve(reference).normalize().toRealPath();
            if (!includePath.startsWith(rootDirectory)) {
                throw new InputException("include 路径越过根来源目录: " + reference);
            }
            return open(includePath, parent.includeDepth() + 1);
        } catch (IOException exception) {
            throw new InputException("无法解析 include 路径: " + reference, exception);
        }
    }

    private SourceStream open(Path path, int depth) {
        state.requireDepth(depth, path.toString());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new InputException("本地输入不存在、不是普通文件或不可读: " + name + " --> " + path);
        }
        try {
            Path realPath = path.toRealPath();
            InputStream input = Files.newInputStream(path);
            return state.register(realPath.toUri(), path.toString(), depth, input);
        } catch (IOException exception) {
            throw new InputException("打开本地输入失败: " + name + " --> " + path, exception);
        }
    }

    @Override
    public void invalidRule() {
        state.invalidRule();
    }

    @Override
    public dev.fordes.adfs.report.InputMetrics metrics(long rules) {
        return state.metrics(rules);
    }

    @Override
    public void close() {
        state.close();
    }
}
