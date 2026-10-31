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
- Chat messages containing server-issued image identifiers become tagged RichChat entries.
- The chat screen has a RichChat picker button.
- The picker has GIF / Photos / Stickers tabs, a scrollable 4×5 grid, native file import, automatic file-type detection, and right-click deletion.
- Chat thumbnails draw inside the reserved chat entry; clicking opens a preview overlay.
- The dynamic texture cache is bounded to 48 entries.

**Not implemented yet:** animated GIF frame playback. GIF files currently display their first frame. WebP is also not enabled by the standard ImageIO runtime.

The chat input button is registered with NeoForge ScreenEvent.Init.Post rather than a ChatScreen mixin. The client-only ChatComponent mixin adds per-message image metadata without changing how normal chat input is sent.

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

1. Open the vanilla chat and click the small image button next to the input field.
2. Select GIF, Photos, or Stickers.
3. Click Add to import a local image.
4. Left-click an item to upload it to the server.
5. Right-click an item to delete it from the local library.
6. Scroll over the grid to browse more items.
7. Click an image in chat to open its preview.

## Limitations

- Animated GIFs currently render their first frame only.
- WebP requires an additional ImageIO provider.
- The native file picker uses Java AWT and requires a desktop environment.
- Thumbnail placement and hit-testing need manual checks at different GUI scales, chat opacity settings, and with many wrapped chat messages.
