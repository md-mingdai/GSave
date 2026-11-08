# SaveGit 核心功能与自研源码解析

本文档介绍 SaveGit 的核心功能，并摘录其中自研逻辑的关键源代码。

SaveGit 是一个 Minecraft 1.21.8（Fabric）客户端模组，为单人世界提供**类 Git 的存档快照与回滚**能力。其最有价值的部分是没有依赖任何 NBT 库、完全自研的 Anvil 区域文件读写器，以及内容寻址的快照/去重/差异/恢复机制。

---

## 一、功能总览

### 快照创建

- **手动快照**：按热键（默认 `G`）立即创建一个快照。
- **自动快照**：按可配置间隔（默认 15 分钟，1–1440 可调）自动创建。
- **进世界基线快照**：进入世界约 5 秒后自动拍一个「世界加载基线」。
- **退出快照**：断开世界时在服务端线程同步创建「世界断开」快照，确保关闭前状态被记录。
- 快照前会先调用 `server.save()` 把区块刷到磁盘，保证数据一致。
- 快照内容覆盖**所有维度**（主世界、下界、末地及自定义维度）。

### 快照范围（可在配置中逐项开关）

- 地形区块（`region/*.mca`）
- 实体区块（`entities/*.mca`）
- 兴趣点区块（`poi/*.mca`）
- `level.dat` 世界元数据
- 玩家数据（`playerdata/*.dat`）

### 存储机制（类 Git）

- **内容寻址 blob 存储**：所有数据按 SHA-256 哈希命名存入 `blobs/`，相同内容自动去重。
- **区块级存储**：`.mca` 文件按单个区块拆分存储，只有真正变化的区块才产生新 blob。
- **整文件回退**：含原版超大区块（`.mcc`）的区域或 0 字节占位文件，按整文件存储。
- **增量差异（patch）**：每个快照保存与父快照的 manifest 差异（新增/修改/删除的区块与文件）。
- **存储位置**：全部数据放在 `<世界存档>/savegit/` 下，随世界一起移动、自包含。
- 存储布局：`HEAD`（当前提交）、`commits/`（提交元数据）、`manifests/`（文件→区块→blob 映射）、`blobs/`（去重数据）、`patches/`（差异）。

### 快照管理

- 每个快照记录提交 ID、父提交、时间戳、消息、世界名、MC 版本。
- 按「最大快照数」（默认 50）自动清理最旧的快照。
- HEAD 指向最新提交，构成线性历史。

### 恢复 / 回滚

- 打开快照列表，选择目标快照并确认后开始恢复。
- 恢复前**自动创建一个安全快照**，记录当前状态以防误操作。
- 恢复流程：回到标题界面 → 等待世界与服务端完全卸载 → 从 blob 重建所有 `.mca` 文件（重建 location 表）、复制回 `level.dat`/玩家数据等整文件。
- 恢复过程中显示**自定义进度界面**（进度条、百分比、当前文件、实时日志），完成后出现「完成」按钮。
- 恢复完成后玩家手动重新进入世界即可载入回滚后的状态。

### 界面与配置（MaLiLib）

- **设置界面**：总开关、自动快照间隔、最大快照数、五项快照范围开关、三个热键。
- **快照列表界面**：列出所有快照（ID、时间、消息、父提交），每条带「恢复」按钮，含「刷新」「返回」按钮；未加载世界或无快照时给出中文提示；恢复前有二次确认弹窗。
- **恢复进度界面**：实时进度条与日志。
- **三个可配置热键**：打开设置（默认无）、立即快照（`G`）、打开快照列表（`H`）。
- 配置持久化到 `config/savegit.json`。

### 技术特性

- 纯客户端实现，仅操作集成服务端的单人世界，无需 mixin / 访问拓宽器。
- 自研字节级 Anvil 区域读写器，**不依赖任何 NBT 库**，区块数据原样存取、往返字节完全一致。
- SHA-256 内容寻址 + 区块级去重，节省磁盘空间。

---

## 二、字节级 Anvil 区域读写器（自研，零依赖）

这是整个模组的基石。区块数据从不解压/重压缩，原样保留磁盘字节，保证快照往返**完全一致**。

### 2.1 从 8 KiB 头部解析 1024 个 location 表项

每个表项 4 字节：高 24 位是扇区偏移，低 8 位是扇区数。`255` 是原版用来标记「区块存在外部 `.mcc` 文件」的哨兵值。

```java
// mca/RawMca.java:812
public static Location[] readLocations(byte[] header) {
    Location[] locations = new Location[LOCATION_COUNT];

    if (header.length < HEADER_BYTES) {
        // 比 header 还短的文件（如原版懒创建的 0 字节占位 region）
        // 视为空：所有区块都不存在。填满每个槽位，调用方永远不会看到 null。
        for (int i = 0; i < LOCATION_COUNT; i++) {
            locations[i] = new Location(0, 0);
        }
        return locations;
    }

    for (int i = 0; i < LOCATION_COUNT; i++) {
        int b0 = header[i * 4] & 0xFF;
        int b1 = header[i * 4 + 1] & 0xFF;
        int b2 = header[i * 4 + 2] & 0xFF;
        int b3 = header[i * 4 + 3] & 0xFF;
        int entry = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        locations[i] = new Location(entry >>> 8, entry & 0xFF);
    }
    return locations;
}
```

### 2.2 整文件读入内存后按表项随机寻址读取区块

关键点：location 表**不按区块 index 有序**——后面的 index 可能指向前面的扇区，前向流无法回退 seek，所以必须先把整个文件（最多约 4 MiB）读进内存。

```java
// mca/RawMca.java:783
public static Map<Integer, byte[]> readChunks(Path file) throws IOException {
    byte[] data = Files.readAllBytes(file);          // 整文件 slurp 进内存
    Location[] locations = readLocations(data);
    Map<Integer, byte[]> chunks = new TreeMap<>();

    for (int i = 0; i < LOCATION_COUNT; i++) {
        Location location = locations[i];
        if (location.isPresent() == false || location.isExternal()) {
            continue;                                 // 跳过空槽和外部 .mcc 区块
        }
        long position = (long) location.offset * SECTOR_BYTES;
        byte[] record = readChunkRecord(data, position);
        if (record != null) {
            chunks.put(i, record);                    // 原始记录字节，不解析
        }
    }
    return chunks;
}
```

### 2.3 读取单条区块记录（含长度/截断校验）

```java
// mca/RawMca.java:844
private static byte[] readChunkRecord(byte[] data, long position) throws IOException {
    int pos = (int) position;
    if (pos < 0 || pos + 4 > data.length) {
        throw new EOFException("Unexpected end of region file");
    }

    int length = ((data[pos] & 0xFF) << 24)
            | ((data[pos + 1] & 0xFF) << 16)
            | ((data[pos + 2] & 0xFF) << 8)
            | (data[pos + 3] & 0xFF);

    // 前 4 字节是载荷长度（含压缩类型字节）
    if (length < 1 || length > MAX_CHUNK_RECORD_BYTES - 4) {
        throw new IOException("Invalid chunk record length: " + length);
    }
    if (pos + 4 + length > data.length) {
        throw new EOFException("Truncated chunk record");
    }

    byte[] record = new byte[4 + length];
    System.arraycopy(data, pos, record, 0, 4 + length);
    return record;   // [4字节长度][压缩类型][载荷] 原样返回
}
```

### 2.4 从区块记录重建整个 region 文件（恢复用）

按 4 KiB 扇区对齐写入，重建 location 表，缺失的区块写为「不存在」。写入后回过头把 location 表覆盖到文件头。

```java
// mca/RawMca.java:880
public static void write(Path file, Map<Integer, byte[]> records) throws IOException {
    byte[] header = new byte[HEADER_BYTES];
    long position = HEADER_BYTES;

    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
        out.write(header);                            // 先占位 8 KiB 头部

        for (int i = 0; i < LOCATION_COUNT; i++) {
            byte[] record = records.get(i);
            if (record == null || record.length < 5) {
                continue;                             // 该 index 的区块不存在
            }

            long recordLength = ((long) (record[0] & 0xFF) << 24)
                    | ((record[1] & 0xFF) << 16)
                    | ((record[2] & 0xFF) << 8)
                    | (record[3] & 0xFF);
            if (recordLength != record.length - 4 || recordLength > MAX_CHUNK_RECORD_BYTES - 4) {
                throw new IOException("Invalid chunk record at index " + i);
            }

            long offset = ((position + SECTOR_BYTES - 1) / SECTOR_BYTES) * SECTOR_BYTES; // 向上对齐到扇区
            int sectorCount = (int) Math.max(1, (record.length + SECTOR_BYTES - 1) / SECTOR_BYTES);

            if (offset / SECTOR_BYTES > 0xFFFFFF || sectorCount > 255) {
                throw new IOException("Chunk " + i + " too large for region file");
            }

            while (position < offset) {               // 补零填充到扇区边界
                out.write(0);
                position++;
            }
            out.write(record);
            position += record.length;

            int entry = (int) ((offset / SECTOR_BYTES) << 8) | sectorCount;
            header[i * 4]     = (byte) (entry >>> 24); // 回填 location 表
            header[i * 4 + 1] = (byte) (entry >>> 16);
            header[i * 4 + 2] = (byte) (entry >>> 8);
            header[i * 4 + 3] = (byte) entry;
        }
        out.flush();
    }

    // 把 location 表写回文件开头
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(header);
        channel.position(0);
        // FileChannel.write 不保证一次写完整个 buffer，循环直到 location 表完整落盘
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
```

### 2.5 外部 `.mcc` 大区块检测

```java
// mca/RawMca.java:760
public static boolean hasExternalChunks(Path file) throws IOException {
    byte[] data = Files.readAllBytes(file);
    for (Location location : readLocations(data)) {
        if (location.isExternal()) {   // offset>0 且 sectorCount==255
            return true;
        }
    }
    return false;
}
```

---

## 三、SHA-256 内容寻址 + 去重 blob 存储

所有数据（区块记录、`level.dat`、玩家数据、回退的 `.mca`/`.mcc`）统一存成按哈希命名的 blob，天然去重。

```java
// snapshot/BlobStore.java:86
public String store(byte[] bytes) {
    String hash = RawMca.hashBytes(bytes);
    return store(bytes, hash);
}

public String store(byte[] bytes, String hash) {
    Path blobPath = blobPath(hash);
    if (Files.exists(blobPath) == false) {   // 已存在则跳过写入 → 去重
        Files.createDirectories(blobPath.getParent());
        Files.write(blobPath, bytes);
    }
    return hash;
}

// 整文件流式入库
public String store(Path file) throws IOException {
    String hash = RawMca.hashFile(file);
    Path blobPath = blobPath(hash);
    if (Files.exists(blobPath) == false) {
        Files.createDirectories(blobPath.getParent());
        copyFile(file, blobPath);
    }
    return hash;
}

// 分片路径：<world>/savegit/blobs/<前2位>/<完整hash>
public Path blobPath(String hash) {
    if (hash.length() < 2) {
        throw new IllegalArgumentException("Hash too short");
    }
    return blobsDir.resolve(hash.substring(0, 2)).resolve(hash);
}
```

---

## 四、确定性的 Manifest 数据模型

全程用 `TreeMap`，保证 JSON 序列化（从而 manifest 哈希）确定——同样的世界内容永远算出同一个 manifestId，这是去重与差异比对的基础。

```java
// snapshot/Manifest.java:1257
public static class FileEntry {
    public String type;   // "mca" 或 "whole"
    public String path;   // 相对世界根目录
    public String hash;   // 整文件 blob 哈希
    public String dimension;
    public String mcaType; // region / entities / poi
    public int regionX, regionZ;
    public Map<Integer, String> chunks = new TreeMap<>();  // 区块 index → blob 哈希
}

public final Map<String, FileEntry> files = new TreeMap<>();  // 确定性遍历顺序

public String computeHash() {
    return RawMca.hashBytes(toJson().getBytes(StandardCharsets.UTF_8));
}
```

---

## 五、Patch 生成器（两个 manifest 的 git 式差异）

对比新旧 manifest，产出区块级的新增/修改/删除，以及整文件的增删。特别处理了「同一文件在 `whole` 和 `mca` 两种表示间翻转」的情况（如空占位 region 后来长出真实区块）。

```java
// snapshot/PatchGenerator.java:1378
Map<Integer, String> allChunks = new HashMap<>();
allChunks.putAll(oldEntry.chunks);
allChunks.putAll(newEntry.chunks);

for (Map.Entry<Integer, String> e : allChunks.entrySet()) {
    int idx = e.getKey();
    String oldHash = oldEntry.chunks.get(idx);
    String newHash = newEntry.chunks.get(idx);

    if (oldHash == null) {              // 新增
        cc.type = Patch.ChunkChange.Type.ADDED;
        cc.blobHash = newHash;
    } else if (newHash == null) {       // 删除
        cc.type = Patch.ChunkChange.Type.REMOVED;
    } else if (!oldHash.equals(newHash)) {  // 修改
        cc.type = Patch.ChunkChange.Type.MODIFIED;
        cc.blobHash = newHash;
    }
    fc.chunks.put(idx, cc);
}
```

```java
// snapshot/PatchGenerator.java:1437  判定「文件是否变化」
private static boolean entriesEqual(Manifest.FileEntry a, Manifest.FileEntry b) {
    if (a.type == null || b.type == null) return false;
    if (a.type.equals("whole") && b.type.equals("whole")) {
        return Objects.equals(a.hash, b.hash);
    }
    // 在 whole 与 mca 之间翻转（如空占位 region 长出真实区块）视为变化
    if (!a.type.equals("mca") || !b.type.equals("mca")) return false;
    if (!Objects.equals(a.dimension, b.dimension)) return false;
    if (!Objects.equals(a.mcaType, b.mcaType)) return false;
    if (a.regionX != b.regionX || a.regionZ != b.regionZ) return false;
    if (a.chunks.size() != b.chunks.size()) return false;
    for (Map.Entry<Integer, String> e : a.chunks.entrySet()) {
        String other = b.chunks.get(e.getKey());
        if (other == null || !other.equals(e.getValue())) return false;
    }
    return true;
}
```

---

## 六、快照流程（含并发锁、region 回退策略）

### 6.1 串行化锁——四条并发触发路径的护栏

手动热键、自动调度线程、基线线程、关闭时同步快照可能同时触发，而 repository 要写 HEAD/commit/manifest，并发会破坏线性历史。

```java
// snapshot/WorldSnapshotter.java:52
private static final Object SNAPSHOT_LOCK = new Object();

public Commit takeSnapshot(String message) throws IOException {
    synchronized (SNAPSHOT_LOCK) {
        return takeSnapshotLocked(message);
    }
}
```

### 6.2 单个 `.mca` 的快照策略：优先区块级，必要时整文件回退

```java
// snapshot/WorldSnapshotter.java:2151
private void snapshotMcaFile(Manifest manifest, Path worldRoot, McaWorldScanner.McaFileEntry entry)
        throws IOException {
    String relativePath = worldRoot.relativize(entry.filePath).toString().replace('\\', '/');
    long size = Files.size(entry.filePath);

    // 太小（0 字节占位）或含外部 .mcc 大区块 → 整文件回退，并连带所有同级 .mcc
    if (size < RawMca.HEADER_BYTES || RawMca.hasExternalChunks(entry.filePath)) {
        snapshotWholeFile(manifest, worldRoot, entry.filePath);
        for (Path mcc : McaWorldScanner.listExternalMccFiles(entry.filePath)) {
            snapshotWholeFile(manifest, worldRoot, mcc);
        }
        return;
    }

    // 正常路径：区块级存储，逐区块哈希入库
    Map<Integer, byte[]> chunkRecords = RawMca.readChunks(entry.filePath);
    Map<Integer, String> chunkHashes = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> chunk : chunkRecords.entrySet()) {
        String hash = repo.getBlobStore().store(chunk.getValue());
        chunkHashes.put(chunk.getKey(), hash);
    }
    manifest.addMcaFile(relativePath, entry.dimensionId, entry.type.folderName,
            entry.regionX, entry.regionZ, chunkHashes);
}
```

---

## 七、恢复流程：从 blob 重建世界

### 7.1 等 world 与集成服务端完全卸载后，在后台线程执行恢复

```java
// snapshot/SaveGitSnapshotManager.java:1747
public static void checkPendingRestore() {
    if (pendingRestore == false) return;

    MinecraftClient client = MinecraftClient.getInstance();
    // 等客户端世界和集成服务端都消失
    if (client.world != null || client.getServer() != null) return;

    // 立刻清标志位，保证此分支只跑一次
    pendingRestore = false;
    final String commitId = pendingRestoreCommitId;
    final Path worldDir = pendingRestoreWorldDir;
    pendingRestoreCommitId = null;
    pendingRestoreWorldDir = null;

    // 服务端已卸载、世界目录可安全重写，切换到实时进度屏
    MinecraftClient.getInstance().setScreen(new RestoreProgressScreen());

    // 恢复放后台线程：重磁盘 I/O（读所有区块 blob、重写所有 .mca）
    // 若在 END_CLIENT_TICK 的渲染线程上跑会卡死数秒
    new Thread(() -> {
        try {
            SnapshotRepository repo = new SnapshotRepository(worldDir);
            Commit commit = repo.getCommit(commitId);
            if (commit == null) {
                throw new RuntimeException("找不到快照提交: " + commitId);
            }
            WorldRestorer restorer = new WorldRestorer(repo, worldDir);
            restorer.restore(commit);
        } catch (Exception e) {
            RestoreProgress.fail(e.getMessage());
        }
    }, "SaveGit-Restore").start();
}
```

### 7.2 逐文件还原——区块记录 → region 文件，整文件 → 直接拷贝

```java
// snapshot/WorldRestorer.java:1969
public void restore(Commit commit) throws IOException {
    Manifest manifest = repo.getManifest(commit.manifestId);
    if (manifest == null) {
        throw new RuntimeException("Manifest not found: " + commit.manifestId);
    }

    var entries = manifest.getAllEntries();
    RestoreProgress.begin(entries.size());
    int index = 0;

    for (Manifest.FileEntry entry : entries) {
        RestoreProgress.startFile(index, entry.path);
        Path target = worldRoot.resolve(entry.path).toAbsolutePath();

        if ("mca".equals(entry.type)) {
            restoreMcaFile(entry, target);
        } else if ("whole".equals(entry.type)) {
            restoreWholeFile(entry, target);
        }
        index++;
    }
    RestoreProgress.finish();
}

// 用 blob 里的区块记录重建 region 文件（location 表由 RawMca.write 重建）
private void restoreMcaFile(Manifest.FileEntry entry, Path target) throws IOException {
    Map<Integer, byte[]> records = new TreeMap<>();
    for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
        byte[] record = repo.getBlobStore().load(chunk.getValue());
        records.put(chunk.getKey(), record);
    }
    Files.createDirectories(target.getParent());
    RawMca.write(target, records);
}
```

---

## 八、核心逻辑小结

| 模块 | 自研要点 |
|---|---|
| `RawMca` | 解析 8 KiB location 表、整文件 slurp 后随机寻址、字节原样存取、扇区对齐重建 region、`.mcc` 大区块检测 |
| `BlobStore` | SHA-256 内容寻址 + 分片 + 写前判重去重 |
| `Manifest` | TreeMap 确定性序列化 → 稳定 manifestId |
| `PatchGenerator` | manifest 差异 → 区块级 ADDED/MODIFIED/REMOVED，处理 whole/mca 表示翻转 |
| `WorldSnapshotter` | 并发锁 + 区块级/整文件双策略 |
| `WorldRestorer` + `SaveGitSnapshotManager` | 等世界卸载后后台线程重建 region 文件 |

整套逻辑的核心价值在于：**不依赖任何 NBT/MCA 第三方库**，把区块当作不透明字节处理，从而彻底规避重序列化漂移，实现真正的字节级快照与恢复。
