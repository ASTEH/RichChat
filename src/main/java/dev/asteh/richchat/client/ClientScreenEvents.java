package dev.asteh.richchat.client;

import dev.asteh.richchat.client.gui.ImagePickerScreen;
import dev.asteh.richchat.config.RichChatClientConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Кнопка RichChat добавляется через ScreenEvent, без вмешательства в отправку ванильного текста. */
public final class ClientScreenEvents {
    private ClientScreenEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientScreenEvents::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ChatScreen chat)
                || !RichChatClientConfig.SHOW_BUTTON_IN_CHAT.get()) {
            return;
        }

        // Освобождаем место справа в поле ввода, чтобы кнопка не перекрывала текст.
        for (var listener : event.getListenersList()) {
            if (listener instanceof EditBox editBox) {
                editBox.setWidth(Math.max(100, editBox.getWidth() - 26));
                break;
            }
        }

        Button pickerButton = Button.builder(
                        net.minecraft.network.chat.Component.literal("▧"),
                        button -> net.minecraft.client.Minecraft.getInstance()
                                .setScreen(new ImagePickerScreen(chat)))
                .bounds(chat.width - 24, chat.height - 40, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        net.minecraft.network.chat.Component.translatable("richchat.chat.open_picker")))
                .build();
        event.addListener(pickerButton);
    }
}
