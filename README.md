# Riichi Tutor

A single-player Riichi (Japanese) Mahjong game for Android. You play against three computer
opponents while a built-in tutor explains the rules and suggests moves as you go. It is made for
complete beginners and works fully offline.

## Install on your phone

The ready-to-install app is [`release/RiichiTutor.apk`](release/RiichiTutor.apk).

1. On your Android phone (Android 8 or newer), open this repository page in the browser, go to
   `release/RiichiTutor.apk` and tap the download button.
   (Or copy the file to the phone by USB cable, email or cloud drive.)
2. Open the downloaded file. Android will say it blocks installs from this source: tap
   **Settings**, switch on **Allow from this source**, go back and tap **Install**.
3. If Play Protect asks, choose **Install anyway**. The app is not on the Play Store, so this
   warning is expected.
4. Open **Riichi Tutor**. The first start takes you through a short lesson, then into a game.

Later versions install over the old one and keep your saved game.

## What is in the game

- Standard Riichi rules: 136 tiles, no red fives, 25,000 starting points, East-only game by
  default with an East + South option.
- Chi, pon, open/added/concealed kan, riichi, ippatsu, tsumo, ron (several players may ron the
  same tile), furiten, dora and ura dora, dead wall, exhaustive draws with tenpai payments,
  dealer repeats, repeat counters and riichi bets.
- All standard yaku and yakuman with han/fu scoring.
- Three computer players, Easy or Normal.
- Tutor with three levels (full guidance, hints on request, off): explains what happened, what
  to do and why, shows how far the hand is from ready and which tiles help, and warns about
  having no yaku, furiten and dangerous discards.
- A six-page interactive lesson, a word list, a yaku list, and a step-by-step score explanation
  after every hand.
- Optional numbers and letters on the tiles.
- The game is saved after every move and can be resumed.

Left out on purpose to keep the ruleset simple: red fives, abortive draws, nagashi mangan,
double yakuman, liability (pao) and extra rounds when nobody reaches 30,000.

## Project layout

- `engine/` is pure Kotlin with no Android code: rules, scoring, computer players and tutor
  logic, with unit tests.
- `app/` is the Android app (Kotlin, Jetpack Compose).

## Build and test

Requires JDK 21 and the Android SDK (platform 37).

```
./gradlew :engine:test            # rules, scoring, AI and tutor tests
./gradlew :app:testDebugUnitTest  # plays a whole game through the app and saves screenshots to app/build/shots
./gradlew :app:assembleRelease    # builds app/build/outputs/apk/release/app-release.apk
```

Builds are signed with the key in `app/debug.keystore`. It is kept in the repository so that
every build can be installed over the previous one; it is not suitable for a Play Store release.
