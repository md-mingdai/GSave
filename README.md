# GSave

为 Minecraft **1.21.8** 单人世界提供类 Git 的存档快照与回滚能力的 Fabric 客户端模组。

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)
![Loader](https://img.shields.io/badge/Fabric%20Loader-%E2%89%A50.16.0-blue)
![License](https://img.shields.io/badge/License-GPL--3.0-orange)

## 功能

- **多种快照触发方式**：手动、定时、世界加载基线、断开世界时自动创建（可分别开关）
- **内容寻址去重**：所有数据按 SHA-256 存储为 packfile，相同区块内容只保存一份，快照之间的差异不会重复占用磁盘
- **区块级存储**：内置 Anvil（`.mca`）读写实现，按区块记录与还原；`.mcc` 外部区块与无法解析的文件自动回退为整文件快照
- **快照浏览**：快照列表、自动截图缩略图、大图查看（可用系统默认程序打开图片）
- **安全恢复**：恢复前默认创建一次安全快照；也可显式跳过（需要二次确认）
- **离线管理**：退出世界后，在单人游戏列表的「GSave 管理」中恢复或删除快照；删除任意快照会自动重算父子提交的补丁（rebase），历史保持线性
- **不卡游戏**：可选「游戏体验优先」（后台线程执行）或「保存速度优先」（服务端线程执行）
- **状态提示**：快照进行中在屏幕右下角常驻显示进度与原因
- **多语言界面**：跟随游戏语言自动切换，内置简体中文、繁體中文、English；设置基于 MaLiLib，兼容 Mod Menu

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.21.8 |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | 任意 1.21.8 版本 |
| [MaLiLib](https://masa.dy.fi/maven/sakura-ryoko/) | ≥ 0.25.7 |
| Java | 21 |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 可选，用于在模组列表中打开主界面 |

## 安装

1. 安装 Fabric Loader 与 Fabric API；
2. 将 `gsave-<版本>.jar`、MaLiLib 放入 `.minecraft/mods`；
3. 启动游戏，进入任意单人世界后按 **H** 打开主界面（热键可在 选项 → 控制 → 按键绑定 中修改）。

## 使用

### 主界面（H）

- **立即创建快照**：手动打一个快照点
- **查看快照并恢复**：快照列表，点击左侧缩略图看大图，右侧按钮恢复
- **设置**：开关各类自动快照、快照上限、执行优先级、纳入快照的数据范围
- **返回游戏**

主界面打开时会暂停游戏。

### 命令

| 命令 | 说明 |
| --- | --- |
| `/snapshot` | 显示命令帮助 |
| `/snapshot create` | 手动创建一次快照 |
| `/snapshot view` | 打开快照列表 |
| `/snapshot restore` | 打开快照列表，在界面中选择要恢复的快照 |
| `/snapshot restore <快照ID> [true/false]` | 直接恢复指定快照；`true`（默认）表示恢复前创建安全快照，`false` 表示不创建 |

快照 ID 支持前缀缩写，输入时按 TAB 可补全（悬浮提示会显示快照时间与消息）。

### 离线恢复与删除

退出世界后，在**单人游戏列表**中选择存档，点击底部的「GSave 管理」：

- 左侧缩略图可查看大图；
- 右侧可恢复或删除任意快照；
- 删除后，其子提交的父指针与补丁会自动重算，历史不会断裂。

> 游戏进行中不允许删除快照，列表中的删除按钮为灰色并显示提示——这是设计行为，避免在服务端持有世界时破坏存档。

## 存储结构

所有数据位于世界目录下的 `gsave/`：

```
<world>/gsave/
├── HEAD                # 当前最新提交
├── commits/<id>.json   # 提交元数据（父提交、manifest、时间、消息）
├── manifests/          # 文件清单（路径 → 内容哈希）
├── patches/            # 相邻提交之间的补丁
├── packs/              # packfile 数据块（≤64 MiB）+ 索引
│   ├── pack-000000.pack
│   └── pack-000000.idx
└── shots/<id>.png      # 快照截图（断开世界快照不含截图）
```

旧版本遗留的 `savegit/` 数据目录会在首次打开时自动迁移为 `gsave/`；旧配置文件 `config/savegit.json` 也会自动迁移为 `config/gsave.json`。

旧版本遗留的 `blobs/` 松散文件会在首次使用时自动迁移进 packfile 并校验摘要后清理。

## 构建

本项目在 GitHub Actions 上构建，需要 JDK 21 与 Gradle 9.x：

```bash
./gradlew build
```

产物位于 `build/libs/gsave-<版本>.jar`（`-sources.jar` 为源码包）。

## 许可证

本项目采用 **GNU General Public License v3.0**，详见 [LICENSE](LICENSE)。
