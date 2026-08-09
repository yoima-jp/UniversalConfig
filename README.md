# Universal Config

<p align="center">
  <strong>Save, reuse, and share your Minecraft settings across instances and versions.</strong>
</p>

<p align="center">
  <a href="https://modrinth.com/project/universal-config">
    <img src="https://img.shields.io/badge/Modrinth-Universal%20Config-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth">
  </a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/universal-config/">
    <img src="https://img.shields.io/badge/CurseForge-Universal%20Config-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge">
  </a>
  <a href="https://github.com/yoima-jp/UniversalConfig/issues/new/choose">
    <img src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Issues">
  </a>
</p>


Universal Config is a client-side mod that lets you save your Minecraft settings, keybinds, and mod configuration files as reusable profiles and share them across different instances and Minecraft versions.

You no longer need to reconfigure your keybinds and mod settings every time you create a new instance.

## Features

- Save your current settings as a profile
- Store keybinds and other `options.txt` settings
- Store mod configuration files from the `config` folder
- Browse saved profiles from an in-game interface
- Review profile contents before applying them
- Duplicate, export, and delete profiles
- Set a default profile
- Schedule a profile to be applied on the next launch
- Restore previous settings from a backup

## Safe Profile Application

Universal Config does not simply overwrite your entire `options.txt` file.

Instead, saved settings are merged into your current configuration individually, allowing settings that only exist in the current instance to remain untouched.

Keybinds are also handled separately for better compatibility.

Your current configuration is backed up before a scheduled profile is applied.

## What Is Not Included

Universal Config is not designed to copy an entire Minecraft instance.

The following data is not included in profiles:

- Mods
- Worlds
- Logs
- Crash reports
- Resource packs
- Shader packs
- Screenshots

Universal Config is specifically designed for **sharing and reusing Minecraft configurations**.

## Version Compatibility

Profiles can be used across different Minecraft versions and instances.

However, full compatibility cannot always be guaranteed, as it depends on the mods being used and their configuration formats.

Some settings may not work correctly if the required mod is not installed or if a mod update has changed its configuration format.

Universal Config shows profile information and compatibility warnings before a profile is applied so you can review what will be changed.

## How to Use

Open Universal Config from the Minecraft title screen.

From the profile screen, you can:

- Save your current settings as a new profile
- Browse existing profiles
- Select a profile saved from another instance

When you select a profile, you can review the following information before applying it:

- The Minecraft version the profile was created with
- Mod loader
- Whether the profile contains keybinds, Minecraft client settings, and/or mod configuration files
- Compatibility warnings

Once confirmed, the selected profile is scheduled to be applied the next time Minecraft starts.

Your current configuration is backed up beforehand, and the profile is merged into your settings before Minecraft loads them.

## Profile Management

Profiles can be:

- Created
- Duplicated
- Opened in their folder
- Deleted
- Set as the default profile

Profiles are stored in the portable `.ucp` format and can be moved to another PC or shared with other players.

## Profile Storage

By default, shared profiles are stored in the following locations:

### Windows

```text
%APPDATA%\.universal-config\
```

### macOS / Linux

```text
~/.universal-config/
```

You can change the profile storage location from the Universal Config screen, but using the default location is recommended for most users.

## Downloads

Official distribution pages:

- [Modrinth](https://modrinth.com/project/universal-config)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/universal-config/)

These pages may remain unavailable until the first public release is published.

## Issues and Feature Requests

Found a bug, have a feature idea, or want to request compatibility with another Minecraft version, mod loader, launcher, modpack, or mod?

Please use the appropriate GitHub Issue form:

- **Bug report** — report crashes, broken behavior, profile application problems, or other defects
- **Feature request** — suggest new functionality or improvements
- **Compatibility / version support** — request support for a Minecraft version, mod loader, launcher, modpack, or another mod

[**Open a new issue →**](https://github.com/yoima-jp/UniversalConfig/issues/new/choose)

When reporting a bug, include the Minecraft version, mod loader, Universal Config version, reproduction steps, and relevant logs or crash reports whenever possible.

## Contributing

Bug reports, compatibility reports, feature suggestions, and pull requests are welcome.

Before opening a new issue, please check the existing issues to avoid duplicates.
