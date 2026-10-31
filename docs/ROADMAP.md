# RichChat roadmap

## Stage 0 — Project bootstrap

- [x] NeoForge 1.21.1 / Java 21 Gradle project
- [x] Server and client config specifications
- [x] RegisterPayloadHandlersEvent registration point
- [x] Ping/pong protocol smoke-test payloads
- [x] GitHub Actions build workflow
- [x] Fix ByteBufCodecs long codec against 1.21.1 API
- [ ] Confirm first CI build succeeds

## Stage 1 — Server upload and storage

- [x] Start-upload and chunk payloads
- [x] Enforce bounded C2S chunk codec (24 KiB)
- [x] In-memory upload session cap, timeout, and one session per player
- [x] Validate PNG/JPEG/GIF signatures and decode through ImageIO
- [x] Enforce file-size and image-dimension limits on server
- [x] Detect GIF animation and normalize photo/GIF categories
- [x] Apply sticker dimension limit (128×128)
- [x] Per-player upload cooldown
- [x] Store image bytes under the world save folder
- [x] Persist bounded image metadata history using SavedData
- [x] Broadcast metadata and S2C image chunks to online clients
- [ ] Client-side chunk assembly and display
- [ ] Replay history to joining clients

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
- [ ] Bounded history replay to reconnecting clients
- [ ] Dedicated-server classloading tests
- [ ] Singleplayer, LAN, and dedicated-server tests
- [ ] Large image, malformed image, reconnect, and anti-spam tests
- [ ] Build and release documentation
