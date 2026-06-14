# Minecraft Launcher

A custom Minecraft launcher built with **Java + JavaFX**, featuring a modern dashboard UI, Prism-like instances, a mod manager, 3D skin viewer, Fabric installation, automatic Java runtimes, Modrinth integration, and crash diagnostics.

> This project is still in development. The goal is to build a clean, modular, modern, and easy-to-maintain Minecraft launcher.

---

## Main Features

### Modern Interface

- Minimal dashboard-style UI.
- Current instance overview.
- Quick access to mods, content search, graphics pack, and cosmetics.
- Integrated console with ANSI/control-character cleanup.
- Card-based interface for instances, mods, and downloadable content.

### Prism-like Instance System

The launcher supports isolated instances, similar to Prism Launcher.

Example structure:

```text
.minecraft-launcher/
└── instances/
    └── Principal/
        ├── instance.json
        └── minecraft/
            ├── mods/
            ├── resourcepacks/
            ├── shaderpacks/
            ├── config/
            ├── saves/
            └── logs/
```

Each instance can have its own:

- Minecraft version.
- Allocated RAM.
- Mods.
- Shaders.
- Resource packs.
- Config files.
- Worlds.
- Notes.

Global Minecraft assets, libraries, and versions are still shared to save disk space.

### Instance Templates

When creating a new instance, the launcher can use templates such as:

- **Vanilla**
- **Fabric Performance**
- **Fabric Shaders**
- **PvP 1.8.9**
- **Custom Empty Instance**

Fabric templates can automatically install recommended mods.

### Import and Export Instances

Instances can be exported as `.zip` files and imported again later.

Exported content includes:

```text
instance.json
minecraft/mods/
minecraft/config/
minecraft/resourcepacks/
minecraft/shaderpacks/
```

Large folders such as `saves`, `logs`, and `screenshots` are excluded by default to avoid huge ZIP files.

### Mod Manager

The launcher includes an instance-based mod manager.

Features:

- List installed mods for the selected instance.
- Enable or disable mods without deleting them.
- Delete mods.
- Open the mods folder.
- Read mod name, version, description, and icon from `fabric.mod.json`.

Disabled mods are renamed from:

```text
mod.jar
```

to:

```text
mod.jar.disabled
```

### Modrinth Content Search

The launcher can search and install content from Modrinth.

Supported content types:

- Mods.
- Resource packs.
- Shaders.

Features:

- Search by text.
- Load popular content automatically.
- Display project icons.
- Display project names and descriptions.
- Detect already installed content.
- Install required dependencies when Modrinth provides them.
- Download files into the currently selected instance.

### Graphics Pack Installer

A quick installer for performance and visual improvements.

It can install:

- Sodium.
- Iris Shaders.
- Sodium Extra.
- Reese's Sodium Options.
- Indium.
- Entity Model Features.
- Entity Texture Features.
- Continuity.
- Complementary Reimagined.

### Native 3D Skin Viewer

The launcher includes a native JavaFX 3D skin viewer.

Features:

- 3D skin preview inside the app.
- Mouse rotation.
- Zoom with `Ctrl + mouse wheel`.
- Local `.png` skin support.
- Local `.png` cape preview.
- No WebView/WebGL dependency.

### Cosmetics Panel

The cosmetics panel allows selecting:

- Local skin file.
- Local cape file.
- Opening the skins folder.
- Opening the 3D skin viewer.

### Fabric Manager

Fabric can be installed directly from the launcher.

Features:

- Uses `meta.fabricmc.net`.
- Finds a compatible Fabric Loader.
- Downloads the Fabric profile JSON.
- Saves it to the global Minecraft versions folder.
- Refreshes the version list.

### Automatic Java Runtime Management

The launcher can download and use separate Java runtimes for Minecraft.

Example:

- The launcher can run on Java 21.
- Minecraft 1.26+ can run with Java 25.
- Older Minecraft versions can use Java 8/17/21 as needed.

Downloaded runtimes are stored in:

```text
.minecraft-launcher/runtimes/
```

This is similar to how launchers like Prism manage Java runtimes internally.

### Repair Installation

The launcher can repair the selected Minecraft version.

It can:

- Verify the version JSON.
- Verify the client `.jar`.
- Verify libraries.
- Re-extract natives.
- Verify assets.
- Redownload corrupted files when hashes are available.

### Crash Analyzer

When Minecraft exits with an error, the launcher analyzes the game log and attempts to identify common causes.

It can detect:

- Incompatible Java version.
- Missing mod dependencies.
- Duplicate mods.
- Incompatible mods.
- Mixin errors.
- Out of memory errors.
- OpenGL/graphics driver issues.
- Missing files.
- Network/download issues.

The crash dialog shows:

- Probable cause.
- Recommended solution.
- Technical details.
- Copy diagnostic button.

---

## Technologies Used

- Java
- JavaFX
- Gson
- Modrinth API v2
- Fabric Meta API
- JavaFX 3D
- Eclipse Adoptium API

---

## Project Structure

Approximate structure:

```text
src/main/java/launcher/
├── Main.java
├── LauncherUI.java
├── MinecraftLauncher.java
├── VersionManager.java
├── VersionEntry.java
├── FabricManager.java
├── ModrinthClient.java
├── Profile.java
├── ProfileManager.java
├── Instance.java
├── InstanceManager.java
├── JavaRuntimeManager.java
├── SkinViewer3D.java
└── CrashAnalyzer.java

src/main/resources/
└── style.css
```

---

## Requirements

- Java with JavaFX support.
- JavaFX configured in the project.
- Internet connection for:
  - Downloading Minecraft metadata.
  - Downloading assets and libraries.
  - Downloading mods.
  - Downloading Java runtimes.
  - Querying Modrinth, Fabric, Mojang, and Adoptium APIs.

Recommended Maven dependencies:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>21.0.2</version>
</dependency>

<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-web</artifactId>
    <version>21.0.2</version>
</dependency>

<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

Depending on your build setup, you may also need JavaFX modules such as:

```text
javafx.controls
javafx.graphics
javafx.web
```

---

## Folders Used

### Launcher Data Folder

```text
%USERPROFILE%\.minecraft-launcher
```

Contains:

```text
launcher.log
profiles.json
instances/
runtimes/
icon-cache/
```

### Global Minecraft Folder

```text
%APPDATA%\.minecraft
```

Used for shared global data:

```text
versions/
libraries/
assets/
```

### Instance Game Folder

```text
%USERPROFILE%\.minecraft-launcher\instances\InstanceName\minecraft
```

Contains instance-specific data:

```text
mods/
resourcepacks/
shaderpacks/
config/
saves/
logs/
```

---

## Basic Usage

1. Open the launcher.
2. Create or select an instance.
3. Select a Minecraft version.
4. Optionally install Fabric.
5. Install mods, shaders, or resource packs.
6. Adjust RAM.
7. Press **Play**.

---

## Creating an Instance

Click the instance selector and choose a template:

- Vanilla.
- Fabric Performance.
- Fabric Shaders.
- PvP 1.8.9.
- Custom Empty Instance.

Each instance is isolated from the others.

---

## Installing Mods

From the **Search** panel:

1. Choose the content type: Mods, Resource Packs, or Shaders.
2. Search by name or browse popular content.
3. Select a result.
4. Click install.

Files are downloaded into the currently selected instance.

---

## Enabling and Disabling Mods

From the **Mods** panel:

1. Select a mod.
2. Click enable or disable.

The launcher renames the mod file automatically:

```text
mod.jar
mod.jar.disabled
```

---

## Repairing a Version

If a version fails or has corrupted files:

1. Select the version.
2. Click **Repair**.
3. Wait for verification and redownloads to finish.

---

## Logs

The launcher log is stored at:

```text
.minecraft-launcher/launcher.log
```

The integrated console shows live output and cleans ANSI/control characters when possible.

---

## Notes

- Instance folders are used for mods, resource packs, shaders, configs, saves, and logs.
- Minecraft versions, libraries, and assets remain global to reduce disk usage.
- Some Modrinth icons may be WebP/SVG. The launcher attempts to convert or proxy them, otherwise it uses a fallback icon.
- Java runtimes are downloaded automatically when required.
- Fabric installation is global because Minecraft versions are stored globally.

---

## Suggested Roadmap

- Light/dark theme switch.
- More customizable dashboard.
- Installed shader manager.
- Installed resource pack manager.
- Real download progress for mods, Java runtimes, assets, and libraries.
- Quilt support.
- Forge/NeoForge support.
- Modpack importing.
- Advanced export options.
- Automatic incompatible mod detection.
- Per-instance Java/JVM arguments.
- Per-instance icon picker.

---

## APIs and Services

This launcher uses:

- Mojang/Piston Meta for Minecraft version metadata.
- Fabric Meta API for Fabric profile installation.
- Modrinth API for mods, resource packs, and shaders.
- Eclipse Adoptium API for Java runtimes.

---

## License

Personal project / work in progress.

Add your preferred license here, for example MIT.
