# Universal Config

Universal Config is a Fabric client mod for sharing Minecraft keybinds and mod configuration files across multiple instances through portable profiles.

Project pages:

- Modrinth: https://modrinth.com/project/universal-config

## Current Scope

- Minecraft target for this build: Fabric 1.19
- Internal mod id: `universal_config`
- Profile extension: `.ucp` as ZIP
- Backup extension: `.ucbackup` as ZIP
- Default shared folder: `%APPDATA%\.universal-config\`
- Compatibility mode: warning-based best effort

The core profile logic is separated from Fabric UI code under `com.example.universalconfig.core`, so Forge, legacy-version adapters, CLI tools, or launcher integrations can reuse the same profile format later.

## Features

- Create a profile from the current instance.
- Open the profile list from Universal Config's configuration button in Mod Menu.
- Store profile metadata in `manifest.json`.
- Store keybinds from `options.txt` without overwriting the full file.
- Store every non-keybind setting from `options.txt` without requiring a version-specific allowlist.
- Store selected `config/` files as profile entries.
- List shared `.ucp` profiles outside the current Minecraft instance.
- Show load-time warnings for Minecraft version, loader, loader version, and untested versions.
- Show planned keybind/client option changes and added or replaced config files.
- Schedule profile import from the title menu and apply it on the next Minecraft start.
- Create a required `.ucbackup` before applying a scheduled profile.
- Restore files from a `.ucbackup`.
- Verify `checksums.json` when loading profiles.
- Reject unsafe ZIP entries such as absolute paths and parent traversal.
- Log profile and file operations to `logs/universal-config.log` in the shared Universal Config folder.
- Reload and rewrite Minecraft client options after the next-start import or backup restore, so Minecraft does not overwrite imported keybinds on shutdown.

## Build

Java 17 or newer is required. Use the committed Gradle Wrapper so every developer and CI use the project-defined Gradle version.

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The remapped mod jar is generated under:

```txt
build/libs/
```

## Usage

1. Install the generated jar in a Fabric 1.19 client instance.
2. Open Minecraft and use the `Universal Config` button on the title menu, or its configuration button in Mod Menu.
3. Use the profile list screen to create, inspect, schedule, duplicate, delete, or export profiles.
4. Review warnings, then choose the next-start import reservation button.
5. Restart Minecraft. Universal Config applies the reserved profile during Fabric pre-launch, before Minecraft reads `options.txt` and common config files, and creates a backup first.
6. Use the backup screen to restore prior settings if needed.

Scheduled imports are stored in the current instance at:

```txt
config/universal_config_pending_import.json
```

The reservation is deleted automatically after a successful startup import. It can also be cleared from the profile list screen before restarting.

## Safety Notes

Universal Config does not replace the full `options.txt`. It merges every stored option by key, preserves settings that only exist in the current instance, and handles keybind rows separately for format compatibility. A backup is created before a scheduled profile is applied.

Universal Config intentionally does not include these folders:

- `mods/`
- `saves/`
- `logs/`
- `crash-reports/`
- `resourcepacks/`
- `shaderpacks/`
- `screenshots/`

Profiles are for configuration sharing, not complete instance cloning.

Before sharing a `.ucp` or `.ucbackup`, review its contents. Configuration files
may contain server addresses, usernames, API keys, or third-party configuration
data. Only share files that you have permission to redistribute. Generated
archives enforce per-entry and total uncompressed-size limits when read.

Universal Config logs replace known local roots with placeholders before writing
diagnostic paths. Logs can still contain operation names, profile names, and
error details, so review them before posting publicly.

## Development Layout

- Persistent paths and profile archive entry names are defined in `UniversalConfigFormat`.
- Minecraft option validation and config file restrictions are defined in `MinecraftConfigPolicy`.
- Filesystem path construction is defined in `UniversalConfigPaths`.

When changing option validation or a shared file name, update the corresponding definition first and add a focused test. Do not duplicate these values in adapters or screens.

## Verification

```powershell
.\gradlew.bat check
```

To launch the development client on Windows:

```powershell
.\gradlew.bat runClient
```

The current tests cover ZIP Slip rejection, keybind and client-option extraction, and the shared format/policy definitions.

## Logs

File and profile operations are written to:

```txt
%APPDATA%\.universal-config\logs\latest.log
%APPDATA%\.universal-config\logs\launches\universal-config-<launch-id>.log
```

`latest.log` is replaced on each Minecraft launch. The `launches/` directory keeps one separate log file per launch.

The log includes profile listing, ZIP reads and writes, config exports and imports, pending import scheduling, startup import execution, backups, restores, deletes, exports, and client option reload attempts.

Universal Config internal files such as `config/universal_config_settings.json` and `config/universal_config_pending_import.json` are skipped during profile export, import, and backup.

## Discord通知

GitHubのIssueとPRの作成・再オープン・クローズなどをDiscordへ通知できます。

リポジトリの `Settings → Secrets and variables → Actions` に、次の2つのRepository secretを登録してください。

- `DISCORD_ISSUE_WEBHOOK`: Issue通知用のDiscord Webhook URL
- `DISCORD_PR_WEBHOOK`: PR通知用のDiscord Webhook URL

Webhook URLはソースコード、Issue、チャットなどへ貼り付けず、漏えいした場合はDiscord側で再生成してください。
