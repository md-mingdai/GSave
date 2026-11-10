package com.mingdai.savegit.util;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

/**
 * 统一的多语言入口：所有玩家可见文案都从这里取翻译。
 */
public final class L10n {
    public static final String REASON_MANUAL = "gsave.snapshot.reason.manual";
    public static final String REASON_SCHEDULED = "gsave.snapshot.reason.scheduled";
    public static final String REASON_BASELINE = "gsave.snapshot.reason.baseline";
    public static final String REASON_DISCONNECT = "gsave.snapshot.reason.disconnect";
    public static final String REASON_SAFETY = "gsave.snapshot.reason.safety";

    private L10n() {
    }

    public static String tr(String key, Object... args) {
        return I18n.translate(key, args);
    }

    public static Text text(String key, Object... args) {
        return Text.translatable(key, args);
    }

    /**
     * 提交消息以 gsave. 开头的视为翻译键，其余（旧版本写入的文本）原样显示。
     */
    public static String commitMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return message.startsWith("gsave.") ? I18n.translate(message) : message;
    }
}
