# RichChat

A standalone Minecraft 1.21.1 / NeoForge 21.1.x mod for shared chat images, animated GIFs, and stickers.

## Development target

- Minecraft 1.21.1
- NeoForge 21.1.x (project target: 21.1.265)
- Java 21
- Gradle 9.2.1 / ModDevGradle 2.0.148

## Current status

The server upload, storage, client transfer, and history-sync pipelines compile in CI:

- Server accepts upload metadata and bounded 24 KiB C2S chunks.
- Server validates PNG/JPEG/GIF signatures and decodes images before saving.
- Server stores accepted image bytes under the current world save folder and stores bounded history metadata using SavedData.
- Server broadcasts new images to connected clients.
- A joining player receives saved image history, constrained by the configurable total transfer budget (default 8 MiB per login).
- Client assembles the incoming history sequentially and caches validated images.
- Client library supports local import, delete, favorites, recent-use timestamps, and a JSON index.
- Client exposes an asynchronous file upload method for the upcoming picker.

**Not implemented yet:** inline chat rendering, picker GUI, preview overlay, and GIF frame playback. Image files are transferred and cached, but are not yet drawn in the visible chat.

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

The server config is authoritative for upload limits, moderation, and join-history transfer budgets.
