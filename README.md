# GameNative for Android Auto

Private fork of [GameNative](https://github.com/utkarshdalal/GameNative) that shows the same app on an Android Auto head unit. The head unit draws GameNative’s own interface. This is a projection app, in the same sense as a phone screen shown on the car display. It is not a media-browser service and it does not use the Android for Cars template screens.

The phone build is unchanged when Android Auto is not connected. While a car session is running, opening the app on the phone switches to a black **Wireless controller capture** screen and forwards a Bluetooth controller to the game.

## Attribution

This is a fork. It is not the upstream GameNative project, and it is not an official release from that project.

- Upstream: [utkarshdalal/GameNative](https://github.com/utkarshdalal/GameNative)
- Author of GameNative: Utkarsh Dalal
- This fork tracks that repository as `upstream` and keeps its history
- License: [GNU GPL-3.0](LICENSE), the same license as upstream. Copyright in the original work stays with its authors. Changes in this fork are released under GPL-3.0 as well
- Third-party components bundled with GameNative are listed in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES)
- Android Auto projection support uses `app/libs/aauto.aar` (`com.google.android.apps.auto.sdk`). That library is not part of upstream GameNative. It is what lets the head unit launch the projection activity

Upstream description, in short: GameNative runs PC games you already own on Steam, Epic, GOG, and Amazon directly on Android, with cloud saves, controller and touch controls, and shared game configs. Compatibility notes for the original project live at [gamenative.app/compatibility](https://gamenative.app/compatibility). Support for the original app is on the [GameNative Discord](https://discord.gg/2hKv4VfZfE).

## What this fork adds

- The head unit shows the real GameNative screens: library, installs, and the game
- Install and message dialogs are drawn inside the car screen, so they can be used from the head unit
- A **Side Menu** button on the game screen opens the left-hand menu
- A phone-side capture screen relays a Bluetooth controller into the game

## Install

Build a modern debug APK (see [Building](#building)) and install it on the phone. In Android Auto’s app list, open GameNative. The head unit has to allow this app. Whether unknown sources are allowed is a setting on the head unit. The app does not turn that on.

Sign in to Steam on the phone or on the head unit. Sign in to GOG, Epic, and Amazon on the phone first. Those stores finish login by returning a result to a phone screen, and the head unit cannot complete that step.

## Wireless controller

A Bluetooth controller is delivered to whichever window is in front on the device it is paired to. While Android Auto is running, that window is Android Auto itself, so the controller drives the car interface instead of the game. The head unit also does not hand stick movement to the projected app.

The capture screen on the phone is the workaround. Leave it open and in front.

1. Pair the controller to the **phone**, not the car. A controller paired only to the head unit never reaches this app.
2. Start GameNative from Android Auto and leave it open on the head unit.
3. On the phone, open GameNative yourself. As soon as the car session is running, the phone replaces the normal app with a black screen that says **Wireless controller capture**.
4. Leave that screen in front. The phone display stays on while the capture screen is open. The power button can still turn it off. If Android Auto covers the phone, bring the capture screen back to the front or the controller will drive Android Auto again.
5. Buttons and stick movement are forwarded to the game on the head unit.
6. The guide button opens the game’s left menu once the game is running.
7. When you disconnect Android Auto, the phone returns to the normal GameNative interface.

On the head unit, during a game:

- **Side Menu** appears at the top right for 3 seconds when the game starts, and again for 3 seconds each time you touch the screen. It then hides.
- If on-screen touch controls are visible, Side Menu sits at the top center so it stays clear of those buttons.
- Side Menu opens the left-hand menu. **Disable mouse input** is in that menu.
- AI debug prompts are not shown on the head unit. Those cards are separate windows, and the car display cannot click them.

## Updates

The app checks this repository’s latest GitHub release, not the upstream GameNative update server. A release tag looks like `v1.2.1.23`: version name `1.2.1`, version code `23`. The phone offers an update only when that version code is higher than the installed app. The release has to include an `.apk` file.

While this repository is private, phones cannot see those releases. Make the repository public when you want the in-app notice to work.

## Building

From the repository root, with the Android SDK installed:

```bat
gradlew.bat :app:assembleModernDebug --no-configuration-cache
```

The APK is written to:

`app/build/outputs/apk/modern/debug/app-modern-debug.apk`

`app/libs/aauto.aar` has to be present. `local.properties` is not in git. Point it at your SDK:

```properties
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

An optional SteamGridDB key can be added there as `STEAMGRIDDB_API_KEY`. Artwork for custom games is skipped without it. Everything else still builds.

## Upstream

To pull later GameNative changes:

```bat
git fetch upstream
git merge upstream/master
```

Bugs in Android Auto projection and the controller capture belong with this fork. Everything else belongs with [upstream GameNative](https://github.com/utkarshdalal/GameNative).

## License

[GPL-3.0](LICENSE). See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) for other components.

This software is for playing games you legally own.
