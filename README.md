# Universal Config

Universal Config is a Fabric client mod prototype for sharing Minecraft keybinds and mod configuration files across multiple instances through portable profiles.

## Current Scope

- Minecraft target for this build: Fabric 1.20.1
- Internal mod id: `universal_config`
- Profile extension: `.ucp` as ZIP
- Backup extension: `.ucbackup` as ZIP
- Default shared folder: `%APPDATA%\.universal-config\`
- Compatibility mode: warning-based best effort

The core profile logic is separated from Fabric UI code under `com.example.universalconfig.core`, so Forge, legacy-version adapters, CLI tools, or launcher integrations can reuse the same profile format later.

## Features

- Create a profile from the current instance.
- Store profile metadata in `manifest.json`.
- Store keybinds from `options.txt` without overwriting the full file.
- Store safe client option fragments from `options.txt`, including language, sound categories, GUI scale, FOV, gamma, subtitles, narrator, mouse options, and model part toggles.
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

```powershell
gradle build
```

The remapped mod jar is generated under:

```txt
build/libs/
```

## Usage

1. Install the generated jar in a Fabric 1.20.1 client instance.
2. Open Minecraft and use the `Universal Config` button on the title menu.
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

Universal Config does not overwrite the full `options.txt`. It only applies known safe option keys and keybind rows.

Universal Config intentionally does not include these folders:

- `mods/`
- `saves/`
- `logs/`
- `crash-reports/`
- `resourcepacks/`
- `shaderpacks/`
- `screenshots/`

Profiles are for configuration sharing, not complete instance cloning.

## Development Layout

- Persistent paths and profile archive entry names are defined in `UniversalConfigFormat`.
- Minecraft option allowlists and config file restrictions are defined in `MinecraftConfigPolicy`.
- Filesystem path construction is defined in `UniversalConfigPaths`.

When adding a supported option or changing a file name, update the corresponding definition first and add a focused test. Do not duplicate these values in adapters or screens.

## Verification

```powershell
gradle check
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
