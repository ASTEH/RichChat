# RichChat

A standalone Minecraft 1.21.1 / NeoForge 21.1.x mod for shared chat images, animated GIFs, and stickers.

## Development target

- Minecraft 1.21.1
- NeoForge 21.1.x (project target: 21.1.265)
- Java 21
- Gradle 9.2.1 / ModDevGradle 2.0.148

## Current status

The server upload pipeline and client transfer/cache pipeline are implemented and compile in CI:

- Server accepts upload metadata and bounded 24 KiB C2S chunks.
- Server validates PNG/JPEG/GIF signatures and decodes images before saving.
- Server stores accepted image bytes under the current world save folder and stores bounded history metadata using SavedData.
- Server broadcasts image metadata and 48 KiB S2C chunks to online clients.
- Client assembles incoming chunks and caches validated received images.
- Client exposes an asynchronous file upload method for the upcoming picker.
- Local image library supports photos/GIFs/stickers, JSON metadata, favorite flags, recent-use timestamps, import, and delete.

**Not implemented yet:** inline chat rendering, chat history replay to players joining later, picker GUI, preview overlay, and GIF frame playback. The transfer pipeline alone does not yet draw images in the visible chat.

## Build

Use a Java 21 JDK and Gradle 9.2.1.

```powershell
gradle build
```

This repository does not yet contain the Gradle Wrapper JAR/scripts. To generate a local wrapper once:

```powershell
gradle wrapper --gradle-version 9.2.1
.\gradlew.bat build
```

On Linux/macOS, use `./gradlew build` after generating the wrapper.

## Storage

- User library: `.minecraft/richchat/images/{gifs,photos,stickers}/`
- User index: `.minecraft/richchat/images/index.json`
- Received server images: `.minecraft/richchat/images/cache/{gifs,photos,stickers}/`
- Server image files: `<world-save>/richchat/images/`
- Server metadata history: `<world-save>/data/richchat_images.dat`

## Configuration

- Server policy: NeoForge SERVER config, `richchat-server.toml` (world/serverconfig).
- Client UI: NeoForge CLIENT config, `richchat-client.toml`.

The server config is authoritative for upload limits and moderation rules.
