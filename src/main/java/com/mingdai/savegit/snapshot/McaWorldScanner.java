package com.mingdai.savegit.snapshot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描世界存档中的全部维度及 region/entities/poi 区域文件。
 */
public final class McaWorldScanner {
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public record McaFileEntry(Path filePath, String relativePath, String dimensionId,
                               McaType type, int regionX, int regionZ) {
    }

    public enum McaType {
        REGION("region"),
        ENTITIES("entities"),
        POI("poi");

        public final String folderName;

        McaType(String folderName) {
            this.folderName = folderName;
        }
    }

    public List<McaFileEntry> scan(Path worldRoot, boolean scanRegion,
                                   boolean scanEntities, boolean scanPoi) throws IOException {
        Path root = worldRoot.toAbsolutePath().normalize();
        Map<String, Path> dimensions = new TreeMap<>();
        dimensions.put("minecraft:overworld", root);
        addIfDirectory(dimensions, "minecraft:the_nether", root.resolve("DIM-1"));
        addIfDirectory(dimensions, "minecraft:the_end", root.resolve("DIM1"));

        Path dimensionsDir = root.resolve("dimensions");
        if (Files.isDirectory(dimensionsDir)) {
            findCustomDimensions(dimensionsDir, dimensionsDir, dimensions);
        }

        List<McaFileEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Path> dimension : dimensions.entrySet()) {
            if (scanRegion) {
                scanType(root, dimension.getKey(), dimension.getValue(), McaType.REGION, entries);
            }
            if (scanEntities) {
                scanType(root, dimension.getKey(), dimension.getValue(), McaType.ENTITIES, entries);
            }
            if (scanPoi) {
                scanType(root, dimension.getKey(), dimension.getValue(), McaType.POI, entries);
            }
        }
        entries.sort(Comparator.comparing(McaFileEntry::relativePath));
        return entries;
    }

    public static List<Path> listExternalMccFiles(Path mcaFile) throws IOException {
        List<Path> result = new ArrayList<>();
        Path dir = mcaFile.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mcc")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    result.add(path);
                }
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return result;
    }

    private static void addIfDirectory(Map<String, Path> dimensions, String id, Path dir) {
        if (Files.isDirectory(dir)) {
            dimensions.put(id, dir);
        }
    }

    private static void findCustomDimensions(Path dimensionsDir, Path current,
                                             Map<String, Path> dimensions) throws IOException {
        boolean dimensionRoot = Files.isDirectory(current.resolve(McaType.REGION.folderName))
                || Files.isDirectory(current.resolve(McaType.ENTITIES.folderName))
                || Files.isDirectory(current.resolve(McaType.POI.folderName));
        if (dimensionRoot && !current.equals(dimensionsDir)) {
            String relative = dimensionsDir.relativize(current).toString().replace('\\', '/');
            int slash = relative.indexOf('/');
            String dimensionId;
            if (slash > 0) {
                dimensionId = relative.substring(0, slash) + ":" + relative.substring(slash + 1);
            } else {
                dimensionId = "minecraft:" + relative;
            }
            dimensions.put(dimensionId, current);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (name.equals(McaType.REGION.folderName)
                        || name.equals(McaType.ENTITIES.folderName)
                        || name.equals(McaType.POI.folderName)) {
                    continue;
                }
                findCustomDimensions(dimensionsDir, child, dimensions);
            }
        }
    }

    private static void scanType(Path worldRoot, String dimensionId, Path dimensionRoot,
                                 McaType type, List<McaFileEntry> entries) throws IOException {
        Path dir = dimensionRoot.resolve(type.folderName);
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mca")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));

        for (Path file : files) {
            Matcher matcher = REGION_FILE.matcher(file.getFileName().toString());
            if (!matcher.matches()) {
                continue;
            }
            int regionX;
            int regionZ;
            try {
                regionX = Integer.parseInt(matcher.group(1));
                regionZ = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            String relative = worldRoot.relativize(file.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
            entries.add(new McaFileEntry(file, relative, dimensionId, type, regionX, regionZ));
        }
    }
}
