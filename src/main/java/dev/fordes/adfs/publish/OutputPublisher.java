package dev.fordes.adfs.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jakarta.inject.Singleton;

import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.publish.StagingWorkspace.Workspace;

@Singleton
public final class OutputPublisher {

    public void publish(Workspace workspace, PublishManifest manifest) {
        manifest.validate(workspace);
        try {
            StagingWorkspace.verifyStableParent(
                    workspace.parent(), workspace.outputDir(), workspace.previousDir());
            for (PublishManifest.Entry entry : manifest.entries()) {
                validateTarget(workspace.outputDir(), entry.path());
            }
            Files.createDirectories(workspace.outputDir());
            for (PublishManifest.Entry entry : manifest.entries()) {
                Path target = workspace.outputDir().resolve(entry.path());
                try {
                    validateTarget(workspace.outputDir(), entry.path());
                    Files.createDirectories(target.getParent());
                    Files.move(workspace.nextDir().resolve(entry.path()), target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    throw new OutputException("原子替换输出文件失败, 已发布文件保留新版本: " + target,
                            exception);
                }
            }
        } catch (OutputException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OutputException("准备输出文件发布失败: " + workspace.outputDir(), exception);
        }
    }

    private static void validateTarget(Path outputDir, Path relative) throws IOException {
        Path target = outputDir.resolve(relative).normalize();
        if (relative.isAbsolute() || target.equals(outputDir) || !target.startsWith(outputDir)) {
            throw new OutputException("输出文件路径越界: " + relative);
        }
        for (Path current = target; current.startsWith(outputDir); current = current.getParent()) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(current) || !current.toRealPath().equals(current)) {
                throw new OutputException("输出路径不得经过符号链接或目录联接: " + current);
            }
            boolean valid = current.equals(target)
                    ? Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                    : Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS);
            if (!valid) {
                throw new OutputException("输出路径存在文件与目录冲突: " + current);
            }
        }
    }
}
