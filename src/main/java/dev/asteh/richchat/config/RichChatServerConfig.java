package dev.asteh.richchat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Серверные ограничения RichChat. Значения проверяются на сервере, клиент им не доверяет. */
public final class RichChatServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_FILE_SIZE_KB;
    public static final ModConfigSpec.IntValue MAX_WIDTH;
    public static final ModConfigSpec.IntValue MAX_HEIGHT;
    public static final ModConfigSpec.IntValue ANTI_SPAM_COOLDOWN_MS;
    public static final ModConfigSpec.BooleanValue KEEP_HISTORY;
    public static final ModConfigSpec.IntValue HISTORY_SIZE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("RichChat server policy").push("server");
        ENABLED = builder.comment("Включить отправку и показ изображений.")
                .define("enabled", true);
        MAX_FILE_SIZE_KB = builder.comment("Максимальный размер исходного файла в KiB.")
                .defineInRange("maxFileSizeKb", 2048, 16, 8192);
        MAX_WIDTH = builder.comment("Максимальная ширина изображения в пикселях.")
                .defineInRange("maxWidth", 512, 1, 8192);
        MAX_HEIGHT = builder.comment("Максимальная высота изображения в пикселях.")
                .defineInRange("maxHeight", 512, 1, 8192);
        ANTI_SPAM_COOLDOWN_MS = builder.comment("Минимальный интервал между загрузками одного игрока.")
                .defineInRange("antiSpamCooldownMs", 3000, 0, 60000);
        KEEP_HISTORY = builder.comment("Сохранять последние изображения для новых подключившихся игроков.")
                .define("keepHistory", true);
        HISTORY_SIZE = builder.comment("Максимальное количество изображений в истории.")
                .defineInRange("historySize", 100, 0, 1000);
        builder.pop();

        SPEC = builder.build();
    }

    private RichChatServerConfig() {}
}
