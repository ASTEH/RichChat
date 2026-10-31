package dev.asteh.richchat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Локальные настройки интерфейса и анимации. */
public final class RichChatClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue PANEL_SCALE;
    public static final ModConfigSpec.BooleanValue ANIMATE_IN_CHAT;
    public static final ModConfigSpec.IntValue MAX_ANIMATED_ON_SCREEN;
    public static final ModConfigSpec.BooleanValue SHOW_BUTTON_IN_CHAT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("RichChat client settings").push("client");
        PANEL_SCALE = builder.comment("Масштаб панели выбора изображений.")
                .defineInRange("panelScale", 1.0D, 0.5D, 2.0D);
        ANIMATE_IN_CHAT = builder.comment("Проигрывать GIF непосредственно в чате.")
                .define("animateInChat", true);
        MAX_ANIMATED_ON_SCREEN = builder.comment("Максимальное число одновременно обновляемых GIF в видимой области.")
                .defineInRange("maxAnimatedOnScreen", 8, 0, 32);
        SHOW_BUTTON_IN_CHAT = builder.comment("Показывать кнопку RichChat в окне чата.")
                .define("showButtonInChat", true);
        builder.pop();

        SPEC = builder.build();
    }

    private RichChatClientConfig() {}
}
