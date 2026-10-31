# RichChat roadmap

## Stage 0 — Project bootstrap

- [x] NeoForge 1.21.1 / Java 21 Gradle project
- [x] Server and client config specifications
- [x] RegisterPayloadHandlersEvent registration point
- [x] Ping/pong protocol smoke-test payloads
- [x] GitHub Actions build workflow
- [ ] Confirm first CI build succeeds

## Stage 1 — Server image protocol

- [ ] Upload metadata and chunk payloads
- [ ] Bounded in-memory upload sessions and timeouts
- [ ] Validate signatures, decoded format, dimensions, byte count, and animation frame count
- [ ] Per-player cooldown and per-player concurrent-transfer limits
- [ ] Persist accepted images under the world save directory
- [ ] Broadcast image metadata and chunks to connected clients
- [ ] Send bounded chat image history to newly joined players

## Stage 2 — Client image library and decoding

- [ ] Local image folders: gifs/photos/stickers
- [ ] JSON image index with metadata, favorite, and recent fields
- [ ] PNG/JPEG/GIF decoding with frame timing
- [ ] Optional WebP reader only when a compatible ImageIO provider is present
- [ ] Dynamic texture lifecycle and bounded LRU cache

## Stage 3 — Chat integration

- [ ] Chat message model carrying image references (not arbitrary untrusted text markers)
- [ ] Render inline thumbnails and reserve message height
- [ ] Correct scrollback and mouse hit testing
- [ ] Click-to-preview overlay
- [ ] Preserve all ordinary vanilla messages

## Stage 4 — Picker GUI

- [ ] GIF / Photos / Stickers tabs
- [ ] Scrollable 4×5 inventory-style grid
- [ ] Add file using native file picker
- [ ] Automatic media-type classification
- [ ] Sticker dimension policy and warning flow
- [ ] Context delete and favorite/recent actions

## Stage 5 — Animation, compatibility, and release

- [ ] Frame scheduler based on per-frame GIF delays
- [ ] Visible-animation cap
- [ ] Dedicated-server classloading tests
- [ ] Singleplayer, LAN, and dedicated-server tests
- [ ] Large image, malformed image, reconnect, and anti-spam tests
- [ ] Build and release documentation
