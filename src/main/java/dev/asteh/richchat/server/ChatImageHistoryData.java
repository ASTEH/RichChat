package dev.asteh.richchat.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** История RichChat привязана к миру и хранит только метаданные; файлы лежат отдельно. */
public final class ChatImageHistoryData extends SavedData {
    private static final String DATA_KEY = "images";
    private static final int ABSOLUTE_HISTORY_LIMIT = 1000;

    public static final Factory<ChatImageHistoryData> FACTORY =
            new Factory<>(ChatImageHistoryData::new, ChatImageHistoryData::load, null);

    private final List<StoredImage> entries = new ArrayList<>();

    public static ChatImageHistoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "richchat_images");
    }

    public static ChatImageHistoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChatImageHistoryData data = new ChatImageHistoryData();
        ListTag list = tag.getList(DATA_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size() && i < ABSOLUTE_HISTORY_LIMIT; i++) {
            CompoundTag entry = list.getCompound(i);
            data.entries.add(new StoredImage(
                    entry.getString("id"),
                    entry.getString("sender"),
                    entry.getString("name"),
                    entry.getString("extension"),
                    entry.getInt("category"),
                    entry.getInt("width"),
                    entry.getInt("height"),
                    entry.getInt("size"),
                    entry.getLong("createdAt")
            ));
        }

        return data;
    }

    public void add(StoredImage image, int maxEntries) {
        if (maxEntries <= 0) {
            return;
        }

        entries.add(image);
        int limit = Math.min(maxEntries, ABSOLUTE_HISTORY_LIMIT);
        while (entries.size() > limit) {
            entries.remove(0);
        }
        setDirty();
    }

    public List<StoredImage> entries() {
        return List.copyOf(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StoredImage image : entries) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", image.id());
            entry.putString("sender", image.senderName());
            entry.putString("name", image.displayName());
            entry.putString("extension", image.extension());
            entry.putInt("category", image.category());
            entry.putInt("width", image.width());
            entry.putInt("height", image.height());
            entry.putInt("size", image.sizeBytes());
            entry.putLong("createdAt", image.createdAtMillis());
            list.add(entry);
        }
        tag.put(DATA_KEY, list);
        return tag;
    }
}
