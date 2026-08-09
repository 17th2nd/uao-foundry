package org.seventeenthsecond.uaofoundry.util;

import org.seventeenthsecond.uaofoundry.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FileOps {
    private FileOps() {}

    public static void writeJson(Path path, Object value) {
        writeText(path, Json.pretty(value));
    }

    public static void writeCanonicalJson(Path path, Object value) {
        writeText(path, Json.canonical(value) + "\n");
    }

    public static void writeText(Path path, String value) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, value, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to write " + path + ": " + ex.getMessage(), ex);
        }
    }

    public static Object readJson(Path path) {
        try {
            return Json.parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read " + path + ": " + ex.getMessage(), ex);
        }
    }

    public static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read " + path + ": " + ex.getMessage(), ex);
        }
    }

    public static String treeHash(Path root) {
        try {
            if (!Files.exists(root)) return Hashes.sha256("");
            List<Path> files;
            try (var stream = Files.walk(root)) {
                files = stream.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> root.relativize(p).toString())).toList();
            }
            StringBuilder content = new StringBuilder();
            for (Path file : files) {
                content.append(root.relativize(file).toString().replace('\\','/')).append('\0');
                content.append(Hashes.sha256(Files.readAllBytes(file))).append('\n');
            }
            return Hashes.sha256(content.toString());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to hash tree " + root + ": " + ex.getMessage(), ex);
        }
    }

    public static void copyTree(Path from, Path to) {
        try {
            if (!Files.exists(from)) return;
            try (var stream = Files.walk(from)) {
                for (Path source : stream.toList()) {
                    Path target = to.resolve(from.relativize(source).toString());
                    if (Files.isDirectory(source)) Files.createDirectories(target);
                    else {
                        if (target.getParent() != null) Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to copy tree " + from + " -> " + to + ": " + ex.getMessage(), ex);
        }
    }

    public static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            List<Path> paths = new ArrayList<>(stream.sorted(Comparator.reverseOrder()).toList());
            for (Path path : paths) Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to delete tree " + root + ": " + ex.getMessage(), ex);
        }
    }
}
