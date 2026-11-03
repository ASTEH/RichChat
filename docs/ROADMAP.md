# RichChat roadmap

## Stage 0 — Project bootstrap

- [x] NeoForge 1.21.1 / Java 21 Gradle project
- [x] Server and client config specifications
- [x] RegisterPayloadHandlersEvent registration point
- [x] GitHub Actions build workflow
- [x] CI build succeeds

## Stage 1 — Server upload and storage

- [x] Start-upload and chunk payloads
- [x] Enforce bounded C2S chunk codec
- [x] Upload session cap, timeout, and cooldown
- [x] Validate PNG/JPEG/GIF signatures and decode before saving
- [x] Enforce file-size and image-dimension limits
- [x] Store image bytes under the current world save folder
- [x] Persist bounded image metadata history
- [x] Broadcast new images to connected clients

## Stage 2 — Client image library and transport

- [x] Local image folders and JSON index
- [x] Local import, delete, favorite, and recent-use methods
- [x] Client file upload API
- [x] Receive, assemble, validate, and cache server images

## Stage 3 — History synchronization

- [x] Send bounded history metadata to joining clients
- [x] Replay image files as bounded S2C chunks
- [x] Limit history transfer size per login
- [x] Process history images sequentially on the client

## Stage 4 — Chat integration and picker

- [x] Chat image messages and per-message metadata
- [x] Inline thumbnails and preview overlay
- [x] Picker button and GIF / Photos / Stickers tabs
- [x] Native local import and explicit item selection
- [ ] Manual chat layout / hitbox tests at different GUI scales

## Stage 5 — GIF animation

- [x] Decode GIF frames and per-frame delays
- [x] Composite partial frames and disposal methods
- [x] Animate visible chat GIFs within configured concurrency limit
- [x] Animate GIF in preview
- [x] Bound decoded-frame memory and GPU texture cache
- [ ] Manual test with transparent GIFs and restore-to-background / restore-to-previous disposal

## Stage 6 — Modern media library UI

- [x] Reuse Aftermath's charcoal / muted-green / amber visual language
- [x] Responsive desktop and compact layouts
- [x] Search, favorites, file preview and explicit action buttons
- [x] Make panel scale setting affect the actual layout
- [ ] Manual test at different GUI scales, aspect ratios, and OS file picker configurations

## Stage 7 — Release hardening

- [ ] Optional WebP ImageIO provider
- [ ] Server upload memory budget and decoded-pixel budget
- [ ] Reconnect and dedicated-server manual tests
- [ ] Large image, malformed image, anti-spam, and mixed-chat tests
- [ ] Release jar and installation guide
