package dev.fordes.adfs.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import dev.fordes.adfs.error.InputException;
import dev.fordes.adfs.error.OutputException;

/** 完整下载成功后才向解析器暴露输入; 临时文件随输入流关闭而删除。 */
record DownloadedSource(InputStream input, long size) {

    static DownloadedSource receive(InputStream input, long maximum, long expected, String source) throws IOException {
        Path path;
        try {
            path = Files.createTempFile(".adfs-http-", ".tmp");
        } catch (IOException exception) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw new OutputException("创建 HTTP 下载暂存文件失败: " + source, exception);
        }
        try {
            long size = 0;
            try (input; OutputStream output = openOutput(path)) {
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > maximum - size) {
                        throw new InputException("HTTP 输入及 include 累计大小超过上限: " + source);
                    }
                    size += count;
                    try {
                        output.write(buffer, 0, count);
                    } catch (IOException exception) {
                        throw new OutputException("写入 HTTP 下载暂存文件失败: " + source, exception);
                    }
                }
            }
            if (expected >= 0 && size != expected) {
                throw new IOException("HTTP 响应体不完整: " + expected + " --> " + size);
            }
            try {
                return new DownloadedSource(Files.newInputStream(path, StandardOpenOption.DELETE_ON_CLOSE), size);
            } catch (IOException exception) {
                throw new OutputException("打开 HTTP 下载暂存文件失败: " + source, exception);
            }
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static OutputStream openOutput(Path path) {
        try {
            return new java.io.BufferedOutputStream(Files.newOutputStream(path)) {
                @Override
                public void close() {
                    try {
                        super.close();
                    } catch (IOException exception) {
                        throw new OutputException("关闭 HTTP 下载暂存写入端失败: " + path, exception);
                    }
                }
            };
        } catch (IOException exception) {
            throw new OutputException("打开 HTTP 下载暂存写入端失败: " + path, exception);
        }
    }
}
