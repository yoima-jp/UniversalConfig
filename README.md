# Universal Config — Forge

Universal Config is a client-side Forge mod for saving Minecraft keybinds, client options, and mod configuration files as portable profiles shared across instances.

- Modrinth: https://modrinth.com/project/universal-config
- Source: https://github.com/yoima-jp/UniversalConfig
- License: MIT

## Supported Forge builds

| Minecraft | Forge used to build | Java | Project directory |
| --- | --- | --- | --- |
| 1.7.10 | 10.13.4.1614 | 8 | `1.7.10/` |
| 1.8.9 | 11.15.1.2318 | 8 | `1.8.9/` |
| 1.12.2 | 14.23.5.2864 | 8 | `1.12.2/` |
| 1.16.5 | 36.2.42 | 8 | `1.16.5/` |
| 1.18.2 | 40.3.12 | 17 | `1.18.2/` |
| 1.19.2 | 43.5.2 | 17 | `1.19.2/` |
| 1.19.4 | 45.4.5 | 17 | `1.19.4/` |
| 1.20.1 | 47.4.22 | 17 | `1.20.1/` |
| 1.20.4 | 49.2.9 | 17 | `1.20.4/` |
| 1.20.6 | 50.2.10 | 21 | `1.20.6/` |
| 1.21.1 | 52.1.16 | 21 | `1.21.1/` |
| 1.21.4 | 54.1.18 | 21 | `1.21.4/` |
| 26.1.1–26.1.x | 63.0.2 | 25 | `26.1.x/` |
| 26.2 | 65.1.0 | 25 | `26.2/` |

Most distribution jars declare the exact Minecraft release they were compiled against. The `26.1.x/` build is compiled against Minecraft 26.1.1 and declares compatibility with releases from 26.1.1 up to, but not including, 26.2.

## Features

The behavior baseline is `origin/fabric` at `12588fe`. The `26.2/` implementation is the reference for current development; Java 8 versions use the equivalent Fabric 1.16.5 core. Loader entry points, Minecraft API bindings, and Forge restart classpath resolution remain specific to Forge. Do not introduce separate Forge feature policies or data limits.

- Create, inspect, rename, reorder, duplicate, delete, and select shared `.ucp` profiles.
- Save keybindings separately from the remaining `options.txt` values and merge by key on apply.
- Save selected files under `config/` while rejecting unsafe or internal paths.
- Set or clear a default profile and apply it exactly once on the first launch of an instance.
- Show compatibility warnings and planned file/key changes before scheduling an apply.
- Apply scheduled profiles early during startup and always create a `.ucbackup` first.
- Open backups even when there are no profiles; confirm before restoring and return to the list with the result.
- Reload Minecraft options after startup apply or restore so shutdown does not overwrite imported values.
- Restart Prism Launcher/MultiMC, ATLauncher, and GDLauncher Carbon instances when their process metadata identifies the instance; otherwise reuse the current Java launch when safely discoverable.
- Use the same ZIP size limits, checksum handling, option merging, and profile rules as Fabric.

Each Minecraft version directory is a fully standalone Gradle project. It contains its own profile implementation, loader integration, UI, resources, tests where supported by that generation's toolchain, license, Gradle Wrapper, and distribution Jar. No version reads source code or build inputs from another version or from a shared source directory.

## Build and verification

Use the wrapper inside each target directory. Examples:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
cd 1.20.1
.\gradlew.bat check build
```

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'
cd 1.7.10
.\gradlew.bat check build
```

The root wrapper verifies that every listed version is a complete, independent project and does not reference removed shared source directories:

```powershell
.\gradlew.bat clean check
```

Distribution jars are written to `<version>/build/libs/`, except the 1.7.10 jar, which is written to `1.7.10/build/distributions/`. Do not distribute `*-sources.jar`.

The CI matrix builds and tests all 14 version projects independently
and uploads one artifact per Minecraft version. The root check only verifies project structure; it does not compile each version.

For a client smoke test, run the target project's wrapper with `runClient`. Check the title-screen entry,
profile creation, item icons, apply confirmation, and Escape navigation, then inspect `run/logs/latest.log`.
Forge 1.20.4 requires classes and resources in one development output directory; its build script configures this
so `runClient` can discover the Mod entry point as well as `mods.toml`.

## Usage

1. Put the jar matching the exact Minecraft release in the instance's `mods/` directory.
2. Open Universal Config from the title-screen button or mod configuration screen on modern Forge, or press the Universal Config keybinding on legacy Forge.
3. Create or select a profile and review the compatibility/diff screen.
4. Schedule the profile, then restart. The profile is applied before Minecraft loads client settings.
5. If necessary, restore the automatically created backup from the backup screen.

Scheduled imports are stored in the current instance at `config/universal_config_pending_import.json`. Shared files and logs default to `%APPDATA%\.universal-config\` on Windows.

## Safety

Profiles do not include `mods/`, `saves/`, `logs/`, `crash-reports/`, `resourcepacks/`, `shaderpacks/`, or `screenshots/`. Universal Config's own settings and pending-import file are also excluded. File operations are logged without file contents or credentials.
