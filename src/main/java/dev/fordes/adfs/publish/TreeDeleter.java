package dev.fordes.adfs.publish;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

final class TreeDeleter extends SimpleFileVisitor<Path> {

    static void delete(Path root) throws IOException {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(root, new TreeDeleter());
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
        if (failure != null) {
            throw failure;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
    }
}
