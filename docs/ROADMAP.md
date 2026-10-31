# RichChat roadmap

## Stage 0 — Project bootstrap

- [x] NeoForge 1.21.1 / Java 21 Gradle project
- [x] Server and client config specifications
- [x] RegisterPayloadHandlersEvent registration point
- [x] Ping/pong protocol smoke-test payloads
- [x] GitHub Actions build workflow
- [x] CI build succeeds

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
- [x] Broadcast new images to connected clients

## Stage 2 — Client image library and transport

- [x] Local image folders and JSON index
- [x] Local import, delete, favorite, and recent-use methods
- [x] Client file upload API with bounded chunking
- [x] Receive, assemble, validate, and cache server images

## Stage 3 — History synchronization

- [x] Send bounded history metadata to a joining client
- [x] Replay image files as bounded S2C chunks
- [x] Limit history transfer size per login
- [x] Process history images sequentially on the client

## Stage 4 — Chat integration and picker

- [x] Server-issued chat marker with UUID-backed GuiMessageTag metadata
- [x] Render inline thumbnails inside reserved chat rows
- [x] Click image to open the preview overlay
- [x] Add picker button using ScreenEvent.Init.Post
- [x] GIF / Photos / Stickers tabs and 4×5 scrollable grid
- [x] Native file import and automatic media-type classification
- [x] Right-click local item deletion
- [ ] Test chat layout and hitboxes at different GUI scales and scrollback positions

## Stage 5 — Animation and release hardening

- [ ] Decode GIF frames and per-frame delays
- [ ] Schedule visible GIFs with configured concurrent-animation limit
- [ ] Optional WebP ImageIO provider
- [ ] Server upload memory budget and decoded-pixel budget
- [ ] Reconnect and dedicated-server manual tests
- [ ] Large image, malformed image, anti-spam, and mixed-chat tests
- [ ] Release jar and installation guide
