# catLauncher

A categorised one-dimensional list launcher for Android, forked from
[tmtLauncher](https://github.com/tinnamchoi/tmtLauncher) by Timothy Choi (GPL-3.0).

The list of labelled categories is still the whole interface. What changed is that
you arrange it in place instead of editing a config file.

## Screenshots

<p>
<img alt="Home: favorites block, categories and the live wallpaper" src="metadata/en-US/images/phoneScreenshots/01-home.png" width="19%"/>
<img alt="Long-press menu with the app's own shortcut" src="metadata/en-US/images/phoneScreenshots/02-menu.png" width="19%"/>
<img alt="Dragging a row to reorder" src="metadata/en-US/images/phoneScreenshots/03-drag.png" width="19%"/>
<img alt="Search filtering the list" src="metadata/en-US/images/phoneScreenshots/04-search.png" width="19%"/>
<img alt="Settings sheet" src="metadata/en-US/images/phoneScreenshots/05-settings.png" width="19%"/>
</p>

## Features

* One scrolling list of apps and shortcuts under labelled category headers, over your wallpaper.
* Long-press an item: favourite or hide it, move it to a category (or a new one), launch one of
  the app's own shortcuts (or add it to the list), change its icon or name, open app info, uninstall.
* Favorites block at the top and a Hidden block at the bottom (shown on request, or when searching).
* Long-press and drag: reorder within a category, or drop into another one. A category keeps
  "newest at the bottom" order until you rearrange it by hand.
* Long-press a header: rename, move up/down, delete, reset to automatic order.
* Long-press empty space (or a widget) for settings: widgets, wallpaper, icons, import/export,
  default-launcher settings.
* Search box that filters the list; "Go" on the keyboard opens the first match.
* Flings land on the next category header (configurable: down only, both ways, or off).
* Icon packs (ADW/Nova format), globally or per app.
* Optional live wallpaper that pans with the list, swappable by broadcast for automation.
* Home widgets through AppWidgetHost, including the bind-permission and configure flows.
* Shortcut pinning from other apps (`ACTION_CONFIRM_PIN_SHORTCUT`, pinned widgets, and the legacy
  `INSTALL_SHORTCUT` broadcast). New installs and pinned shortcuts land in *Uncategorized*, which
  always sits last.
* Work-profile apps are listed alongside personal ones.
* The config is JSON; export it to the clipboard or share it, and paste it back to import.

## Building

Standard Android Studio / Gradle project. `./gradlew assembleDebug` needs an Android SDK with
platform 36; `./gradlew testDebugUnitTest` runs the model tests.

## Releasing

Pushing a tag like `v2.0.0` runs the release workflow, which builds a signed APK and attaches it
to a GitHub release. It needs four repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore,
`base64 -w0 release.jks`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD`. Locally, set the same names as environment variables (with
`RELEASE_KEYSTORE` pointing at the file) to get a signed `assembleRelease`.

## Automation

The optional live wallpaper takes its image from a broadcast, e.g. from Tasker's Send Intent:
action `io.github.hate9.catlauncher.SET_WALLPAPER`, extra `path:/storage/emulated/0/img.jpg`,
package `io.github.hate9.catlauncher`, target Broadcast Receiver.

## License

GPL-3.0. Copyright (C) 2025 Timothy Choi (tmtLauncher), (C) 2026 catLauncher contributors.
