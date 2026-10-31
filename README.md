# RichChat

A standalone Minecraft 1.21.1 / NeoForge 21.1.x mod that adds shared chat images, animated GIFs, and stickers.

## Development target

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21

The project is being developed in small buildable stages. The first milestone establishes the MDK/Gradle project, configuration, and the versioned CustomPacketPayload protocol. Chat rendering and image upload features will be added in subsequent commits after their 1.21.1 APIs are verified.

## Build

Use a Java 21 JDK and run `gradlew build` (Windows: `gradlew.bat build`).
