# SpearHelper

A small client-side Fabric mod for Minecraft **26.1 – 26.3**.

Hold a key (default **R**): every time your attack is fully charged, the mod selects the spear in your hotbar, attacks
with it and selects your previous slot again.

This is a port of **Spear-Swapper / Spear Helper** by **s0ftpulse**
([source](https://github.com/shuraa-lox/Spear-Swapper), [Modrinth](https://modrinth.com/mod/spear-helper)),
licensed CC0-1.0, to the newer Minecraft versions.

## Server-side opt-in

Following [Modrinth's content rules](https://modrinth.com/legal/rules) (section 3.3), the mod only works where it is
allowed:

- **Singleplayer:** always works.
- **Multiplayer servers:** the mod is **disabled** until the server allows it. When you hold the key on a server that
  has not allowed it, you see the message *"SpearHelper is disabled: this server has not allowed it"* and nothing happens.

A server allows the mod by sending the plugin message `spearhelper:allow` to the player (the content does not matter).
The permission is reset on every new connection.

### For server owners

Install the **SpearHelper Opt-In** plugin (`SpearHelperOptIn-1.1.2.jar`, Spigot / Paper) to allow the mod on your server.
It allows it for every player with the permission `spearhelper.allow` (given to everyone by default; deny it with your
permissions plugin to keep the mod off for some players). Without the plugin, the mod stays off on your server.

**Paper** and servers based on it (Purpur, Leaf, ...) reset the attack charge as soon as the held item changes
(`unsupported-settings.update-equipment-on-player-actions`), so the spear attack made in the same tick would be rejected.
Since plugin version 1.1.0, `spear-charge-on-paper: true` (config.yml, default) keeps the vanilla behaviour for players
allowed to use SpearHelper, only when they switch to a spear and right back to the previous slot. Set it to `false` to keep Paper's default. On Spigot it is
not needed.

## Download

| File | For |
|---|---|
| `spearhelper-1.1.0+26.1.x.jar` | client, Minecraft 26.1, 26.1.1, 26.1.2 |
| `spearhelper-1.1.0+26.2.jar` | client, Minecraft 26.2 |
| `spearhelper-1.1.0+26.3.jar` | client, Minecraft 26.3 |
| `SpearHelperOptIn-1.1.2.jar` | server plugin (Spigot / Paper) that allows the mod |

The client mod requires [Fabric Loader](https://fabricmc.net/), [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25.

## How to use

1. Put a spear in your hotbar.
2. Select a hotbar slot with an item that charges its attack quickly (for example bread or a golden carrot).
3. Hold the key (default **R**, can be changed in *Options → Controls → Spear Helper*).

## Changes compared to the original

- Server-side opt-in: disabled on multiplayer servers unless the server allows it
- Ported to Minecraft 26.1 – 26.3 (unobfuscated names, new key mapping API)
- Removed a broken mixin config entry (missing class)
- No more log spam every tick while the attack is charging
- License fixed to CC0-1.0 everywhere
- Marked as client-side only

## Building

`build.ps1` compiles the client mod with plain `javac` (JDK 25) against the official Minecraft client jars, Fabric
Loader and the Fabric API modules; see the comment at the top of the script for the folder layout of the libraries.

```powershell
.\build.ps1 -Jdk "C:\path\to\jdk-25\bin" -Libs ".\libs"
```

The plugin in `optin-plugin/` is a single class compiled against the Spigot / Paper API (Java 17+), with `plugin.yml`
from `optin-plugin/resources`.

## Authors

- [s0ftpulse](https://github.com/shuraa-lox) - original Spear-Swapper / Spear Helper
- [DriedApricots](https://github.com/DriedApricots) - port and server opt-in plugin
- [Claude](https://github.com/claude) (Anthropic) - co-author of the port and the plugin

## License

[CC0-1.0](LICENSE), same as the original mod.

The code of this port and the plugin were written with the help of AI (Claude).
