# RichChat

A standalone Minecraft 1.21.1 / NeoForge 21.1.x mod for shared chat images, animated GIFs, and stickers.

## Development target

- Minecraft 1.21.1
- NeoForge 21.1.x (project target: 21.1.265)
- Java 21
- Gradle 9.2.1 / ModDevGradle 2.0.148

## Current status

Implemented in this development branch:

- Server validates, stores, broadcasts, and retains bounded image history.
- Client uploads local files, receives server chunks, and caches image files locally.
- New players receive recent image history within a configurable transfer budget.
- The chat screen has a RichChat picker button with GIF / Photos / Stickers tabs, a 4×5 scrollable grid, file import, and right-click deletion.
- Chat thumbnails draw inside the reserved chat entry; clicking opens a preview overlay.
- GIF animation reads frame timing and disposal metadata, composites partial frames, and loops in chat and preview.
- At most the configured number of GIFs are updated in the visible chat area.
- Decoded GIF frame memory is bounded to 64 MiB; GPU textures are held in a 48-entry LRU cache.

The chat input button uses NeoForge ScreenEvent.Init.Post. The client-only ChatComponent mixin adds per-message image metadata without changing normal chat input.

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

## Storage

- User library: `.minecraft/richchat/images/{gifs,photos,stickers}/`
- User index: `.minecraft/richchat/images/index.json`
- Received server images: `.minecraft/richchat/images/cache/{gifs,photos,stickers}/`
- Server image files: `<world-save>/richchat/images/`
- Server metadata history: `<world-save>/data/richchat_images.dat`

## Controls

1. Open vanilla chat and click the image button beside the input field.
2. Select GIF, Photos, or Stickers.
3. Click Add to import a local image.
4. Left-click an item to upload it to the server.
5. Right-click an item to delete it from the local library.
6. Scroll over the grid to browse more items.
7. Click an image in chat to open its preview.

## Limitations

- The native file picker uses Java AWT and requires a desktop environment.
- The default ImageIO runtime supports PNG, JPEG, and GIF. WebP needs an additional ImageIO provider.
- Chat layout and click hitboxes still need manual checks at multiple GUI scales.
