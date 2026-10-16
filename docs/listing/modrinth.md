# Modrinth listing

The project's text and settings. This file does not publish anything; the project page is filled in from it. See [setting up publishing](../releasing.md#setting-up-publishing).

The description's images use raw repository URLs, which load once the repository is public. Copy the description from its banner image to its last line.

## Project settings

| Field        | Value                                                            |
| ------------ | ---------------------------------------------------------------- |
| Project type | Plugin                                                           |
| Name         | BlueMapSignPosts                                                 |
| URL slug     | `bluemapsignposts`                                               |
| Summary      | Allows players to add BlueMap markers using signs and banners    |
| Categories   | Utility, Social; Transportation as an additional hidden category |
| Loaders      | Paper, Purpur, Folia, set per version by the release workflow    |
| Environment  | Server-side only                                                 |
| License      | GPL-3.0                                                          |
| Source code  | https://github.com/lewisgibson/bluemap-sign-posts                |
| Issues       | https://github.com/lewisgibson/bluemap-sign-posts/issues         |
| Wiki         | https://github.com/lewisgibson/bluemap-sign-posts/tree/main/docs |
| Icon         | [`icon-512.png`](icon-512.png)                                   |
| Dependencies | BlueMap, required, set per version by the release workflow       |

## Gallery

Upload these images in this order:

| File                                                       | Title                  | Description                                             | Featured |
| ---------------------------------------------------------- | ---------------------- | ------------------------------------------------------- | :------: |
| [`../images/banner.png`](../images/banner.png)             | BlueMapSignPosts       | A [map] sign in game and markers on the web map         |   Yes    |
| [`../images/sign-editor.png`](../images/sign-editor.png)   | Writing a signpost     | [map] on the first line and the place's name underneath |          |
| [`../images/in-game.png`](../images/in-game.png)           | Added to the map       | Confirmation in chat after writing a signpost           |          |
| [`../images/marker-popup.png`](../images/marker-popup.png) | On the web map         | A BlueMap marker showing the place's name and its owner |          |
| [`../images/leafsmp-map.png`](../images/leafsmp-map.png)   | A live survival server | Places added to the map by players                      |          |

## Description

![BlueMapSignPosts](https://raw.githubusercontent.com/lewisgibson/bluemap-sign-posts/main/docs/images/banner.png)

A Paper plugin that allows players to add markers to your [BlueMap](https://modrinth.com/plugin/bluemap) web map using signs and banners. Write `[map]` on the first line of a sign and the name of the place underneath, or place a banner renamed in an anvil, and a marker with that name appears on the map at that block. Edit the sign to rename the marker, and break it to remove it.

![Places marked by players on a survival server](https://raw.githubusercontent.com/lewisgibson/bluemap-sign-posts/main/docs/images/leafsmp-map.png)

## How it works

- Put `[map]` alone on the first line of a sign and the place's name on the lines below. Capitals don't matter.
- Click Done. The player sees `Added to the map: <name>` in chat and the marker is on the map straight away.
- Edit the name to rename the marker. Break the sign, or take `[map]` off it, to remove the marker.
- A banner renamed in an anvil works the same way when it is placed.

![The marker's popup on BlueMap](https://raw.githubusercontent.com/lewisgibson/bluemap-sign-posts/main/docs/images/marker-popup.png)

## Features

- Markers follow their blocks. Breaking, editing, explosions, pistons and losing the block underneath all remove them, and anything changed without an event is checked the next time its chunk loads.
- Markers come back by themselves after a restart or a BlueMap reload, from the plugin's own store: SQLite by default, or PostgreSQL, MySQL or MariaDB.
- Limits to suit your server: a default for everyone, permission tiers, per-player limits set by command, and an optional playtime requirement for new players.
- The chat messages and the marker popup are yours to change.
- Signs of every wood, standing, on a wall or hanging, on either side, and markers in the Nether and the End on their own maps.

## Requirements

- Paper or Purpur 26.3. Folia is supported in the code and will be tested once Folia 26.3 is released.
- BlueMap 5.24 or newer, installed on the same server.

## Install

Install BlueMap, put the BlueMapSignPosts jar in your server's `plugins` folder and restart. The defaults work as they are.

## Commands

Players can run `/signposts list` to see their own markers. Admins with `bluemapsignposts.admin` can list everyone's, remove a marker without touching the block, set a player's limit and reload the settings. The [documentation](https://github.com/lewisgibson/bluemap-sign-posts/tree/main/docs) covers every command, permission and setting.

## Support

Ask questions and report bugs on [GitHub issues](https://github.com/lewisgibson/bluemap-sign-posts/issues). The [troubleshooting guide](https://github.com/lewisgibson/bluemap-sign-posts/blob/main/docs/troubleshooting.md) covers the common problems.

This is an unofficial, community plugin. It is not affiliated with, authorised by, or endorsed by BlueMap, PaperMC or Mojang.
