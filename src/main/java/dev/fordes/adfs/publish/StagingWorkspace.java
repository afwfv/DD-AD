package dev.fordes.adfs.publish;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import dev.fordes.adfs.error.OutputException;

@Singleton
public final class StagingWorkspace {

    private static final String LOCK_SUFFIX = ".adfs.lock";
    private static final String PREVIOUS_SUFFIX = ".adfs.previous";
    private static final String TEMP_PREFIX = ".adfs-";
    private static final String NEXT_NAME = "next";

    public Workspace open(Path outputDir) {
        Path parent = outputDir.getParent();
        String outputName = outputDir.getFileName().toString();
        Path lockPath = parent.resolve("." + outputName + LOCK_SUFFIX);
        Path previousDir = parent.resolve("." + outputName + PREVIOUS_SUFFIX);
        try {
            FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = acquireLock(lockChannel, lockPath);
            try {
                verifyStableParent(parent, outputDir, previousDir);
                recover(outputDir, previousDir);
                Path runDir = Files.createTempDirectory(parent, TEMP_PREFIX);
                try {
                    Path nextDir = Files.createDirectory(runDir.resolve(NEXT_NAME));
                    verifyFileStores(parent, outputDir, previousDir, runDir);
                    verifyAtomicMove(runDir);
                    return new Workspace(parent, outputDir, previousDir, runDir, nextDir, lockChannel, lock);
                } catch (IOException | RuntimeException exception) {
                    try {
                        TreeDeleter.delete(runDir);
                    } catch (IOException cleanupFailure) {
                        exception.addSuppressed(cleanupFailure);
                    }
                    throw exception;
                }
            } catch (IOException | RuntimeException exception) {
                release(lock, lockChannel, exception);
                throw exception;
            }
        } catch (OutputException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OutputException("创建输出工作区失败: " + outputDir, exception);
        }
    }

    private static FileLock acquireLock(FileChannel channel, Path lockPath) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new OutputException("输出目录已由另一进程锁定: " + lockPath);
            }
            return lock;
        } catch (IOException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new OutputException("输出目录已由当前进程锁定: " + lockPath, exception);
        }
    }

    static void verifyStableParent(Path parent, Path outputDir, Path previousDir) throws IOException {
        Path realParent = parent.toRealPath();
        if (!realParent.equals(parent) || Files.isSymbolicLink(parent)) {
            throw new OutputException("output-dir 的父目录不得经过符号链接或目录联接: " + parent);
        }
        validateDirectoryState(outputDir, "正式输出目录");
        validateDirectoryState(previousDir, "恢复目录");
    }

    private static void validateDirectoryState(Path path, String label) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new OutputException(label + "必须是非符号链接目录: " + path);
        }
    }

    private static void recover(Path outputDir, Path previousDir) throws IOException {
        boolean hasOutput = Files.exists(outputDir, LinkOption.NOFOLLOW_LINKS);
        boolean hasPrevious = Files.exists(previousDir, LinkOption.NOFOLLOW_LINKS);
        if (!hasPrevious) {
            return;
        }
        if (hasOutput) {
            TreeDeleter.delete(previousDir);
            return;
        }
        moveAtomic(previousDir, outputDir);
    }

    private static void verifyFileStores(Path parent, Path outputDir, Path previousDir, Path runDir) throws IOException {
        FileStore parentStore = Files.getFileStore(parent);
        if (!parentStore.equals(Files.getFileStore(runDir))) {
            throw new OutputException("运行目录与 output-dir 父目录不在同一文件系统");
        }
        if (Files.exists(outputDir) && !parentStore.equals(Files.getFileStore(outputDir))) {
            throw new OutputException("正式输出目录与运行目录不在同一文件系统");
        }
        if (Files.exists(previousDir) && !parentStore.equals(Files.getFileStore(previousDir))) {
            throw new OutputException("恢复目录与运行目录不在同一文件系统");
        }
    }

    private static void verifyAtomicMove(Path runDir) throws IOException {
        Path source = Files.createDirectory(runDir.resolve("atomic-probe"));
        Path target = runDir.resolve("atomic-probe-moved");
        try {
            moveAtomic(source, target);
            moveAtomic(target, source);
        } finally {
            TreeDeleter.delete(source);
            TreeDeleter.delete(target);
        }
    }

    static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new OutputException("文件系统不支持所需的原子目录移动: " + source + " --> " + target,
                    exception);
        }
    }

    private static void release(FileLock lock, FileChannel channel, Throwable original) {
        try {
            lock.close();
        } catch (IOException exception) {
            original.addSuppressed(exception);
        }
        try {
            channel.close();
        } catch (IOException exception) {
            original.addSuppressed(exception);
        }
    }

    public static final class Workspace implements AutoCloseable {

        private final Path parent;
        private final Path outputDir;
        private final Path previousDir;
        private final Path runDir;
        private final Path nextDir;
        private final FileChannel lockChannel;
        private final FileLock lock;
        private boolean closed;

        private Workspace(
                Path parent,
                Path outputDir,
                Path previousDir,
                Path runDir,
                Path nextDir,
                FileChannel lockChannel,
                FileLock lock) {
            this.parent = parent;
            this.outputDir = outputDir;
            this.previousDir = previousDir;
            this.runDir = runDir;
            this.nextDir = nextDir;
            this.lockChannel = lockChannel;
            this.lock = lock;
        }

        public Path outputDir() {
            return outputDir;
        }

        public Path previousDir() {
            return previousDir;
        }

        public Path runDir() {
            return runDir;
        }

        public Path nextDir() {
            return nextDir;
        }

        Path parent() {
            return parent;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            List<OutputException> failures = new ArrayList<>();
            try {
                TreeDeleter.delete(runDir);
            } catch (IOException exception) {
                failures.add(new OutputException("清理运行目录失败: " + runDir, exception));
            }
            try {
                lock.close();
            } catch (IOException exception) {
                failures.add(new OutputException("释放输出目录锁失败: " + outputDir, exception));
            }
            try {
                lockChannel.close();
            } catch (IOException exception) {
                failures.add(new OutputException("关闭输出锁文件失败: " + outputDir, exception));
            }
            if (!failures.isEmpty()) {
                OutputException first = failures.getFirst();
                failures.stream().skip(1).forEach(first::addSuppressed);
                throw first;
            }
        }
    }
}
