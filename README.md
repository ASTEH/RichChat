# RichChat

A standalone Minecraft 1.21.1 / NeoForge 21.1.x mod for shared chat images, animated GIFs, and stickers.

## Development target

- Minecraft 1.21.1
- NeoForge 21.1.x (project target: 21.1.265)
- Java 21
- Gradle 9.2.1 / ModDevGradle 2.0.148

## Current status

The repository currently contains the project bootstrap: Gradle/NeoForge setup, the mod entry point, server/client configuration specs, resource metadata, Mixin configuration, and a small CustomPacketPayload ping/pong protocol smoke-test.

**Image upload, image history, chat rendering, the picker GUI, and GIF playback are not implemented yet.** They are being added in separate buildable stages; the diagnostic ping/pong payloads will be removed when the image-transfer protocol replaces them.

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

## Configuration

- Server policy: NeoForge SERVER config, `richchat-server.toml` (world/serverconfig).
- Client UI: NeoForge CLIENT config, `richchat-client.toml`.

The server config is deliberately server-scoped rather than a COMMON config: upload limits and moderation rules must be authoritative on the server.

## Development

Work is organized in stage branches. See [docs/ROADMAP.md](docs/ROADMAP.md) for the implementation sequence.
