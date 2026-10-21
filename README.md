# HalloApp

Minimale Android-App, die nur „Hallo“ anzeigt.

## Tech-Stack

- Kotlin + Jetpack Compose (Material 3)
- Min SDK 24 (Android 7.0), Target SDK 34
- Gradle 8.7, AGP 8.5.2

## APK herunterladen und installieren

1. Öffne den Tab **Actions** dieses Repositories.
2. Wähle den neuesten erfolgreichen **Android CI**-Run.
3. Lade unten das Artefakt **halloapp-debug-apk** herunter (enthält `app-debug.apk`).
4. Entpacke die ZIP-Datei und übertrage die APK auf dein Gerät.
5. Installiere sie („Unbekannte Quellen“ für deinen Browser/Dateimanager erlauben, falls gefragt).

Die APK ist mit dem **Debug-Key** signiert und daher direkt installierbar – kein eigener Keystore nötig. Für einen Play-Store-Release würde man später einen eigenen Keystore verwenden.

## Lokal bauen

```bash
./gradlew assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`
