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
- Client imports local PNG/JPEG/GIF files into a per-category library, uploads selected images, and caches received images.
- New players receive recent image history within a configurable transfer budget.
- The media library uses an Aftermath-inspired charcoal / muted-olive / amber palette, responsive panels, custom hover/selected states, and a compact layout for smaller GUI sizes.
- Library controls include category navigation, filename search, favorites, preview, explicit import / send / delete actions, and grid scrolling.
- Sticker imports are limited to 128×128; GIFs are detected from the file rather than relying on the currently selected tab.
- Chat thumbnails draw inside the reserved chat entry; clicking opens a preview overlay.
- GIF animation reads frame timing and disposal metadata, composites partial frames, and loops in chat and preview.
- At most the configured number of GIFs are updated in the visible chat area.
- Decoded GIF frame memory is bounded to 64 MiB; GPU textures are held in a 48-entry LRU cache.

RichChat reuses Aftermath's visual language without requiring SDM UI Lib. The chat input button uses NeoForge ScreenEvent.Init.Post, and the client-only ChatComponent mixin adds per-message image metadata without changing normal chat input.

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

1. Open vanilla chat and click the RichChat media button beside the input field.
2. Select Photos, GIFs, or Stickers.
3. Press Import to open the in-game file browser. Navigate folders, paste a folder or image path into the location field, then select a PNG, JPG, JPEG, or GIF file.
4. Select an item in the grid; inspect its preview and details in the side panel.
5. Press Send to upload it to the server and share it with connected players.
6. Use the star button to add or remove a favorite; use Delete to remove the local copy.
7. Search by filename and scroll over the grid to browse more items.
8. Click an image in chat to open its preview.

The panel scale is configurable through the RichChat client config. GIF playback and the number of simultaneously animated chat images are configurable there as well.

## Limitations

- Import uses a built-in in-game file browser and does not depend on Java AWT or the operating system's native file dialog. It shows up to 500 folders/images in each directory for predictable performance.
- The default ImageIO runtime supports PNG, JPEG, and GIF. WebP needs an additional ImageIO provider.
- The UI has been build-checked but still needs manual testing in Minecraft at several GUI scales and aspect ratios.
- Server upload acceptance and multi-client sharing still require in-game validation.
