package dev.asteh.richchat.mixin;

import dev.asteh.richchat.client.chat.ChatImageRenderer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Встраивает маркеры RichChat в модель сообщений ванильного чата. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    private static final Pattern RICHCHAT_MARKER = Pattern.compile(
            Pattern.quote("[[richchat_img:") + "([0-9a-fA-F-]{36})" + Pattern.quote("]]"));

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void richchat$replaceImageMarker(
            Component message,
            MessageSignature signature,
            GuiMessageTag originalTag,
            CallbackInfo callback
    ) {
        String raw = message.getString();
        Matcher matcher = RICHCHAT_MARKER.matcher(raw);
        if (!matcher.find()) {
            return;
        }

        String imageId = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
        String cleanText = raw.substring(0, matcher.start()).stripTrailing();
        if (cleanText.isBlank()) {
            cleanText = "Image";
        }

        // Переносы строк резервируют место под миниатюру в ванильном списке сообщений.
        Component placeholder = Component.literal(cleanText + "\n".repeat(8));
        GuiMessageTag richTag = new GuiMessageTag(
                0,
                null,
                Component.literal(cleanText),
                "richchat:" + imageId
        );

        ((ChatComponent) (Object) this).addMessage(placeholder, signature, richTag);
        callback.cancel();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At("TAIL")
    )
    private void richchat$renderImages(
            GuiGraphics graphics,
            int tickCount,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo callback
    ) {
        ChatImageRenderer.render(
                (ChatComponent) (Object) this,
                graphics,
                trimmedMessages,
                chatScrollbarPos,
                tickCount,
                focused
        );
    }

    @Inject(method = "handleChatQueueClicked(DD)Z", at = @At("HEAD"), cancellable = true)
    private void richchat$openPreview(double mouseX, double mouseY,
                                      CallbackInfoReturnable<Boolean> callback) {
        if (ChatImageRenderer.mouseClicked(mouseX, mouseY)) {
            callback.setReturnValue(true);
        }
    }
}
