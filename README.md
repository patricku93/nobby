# NoBby

NoBby is an Android app that keeps track of a repeating medication schedule (for example a vaginal ring that stays in for a set number of days and is then taken out for a break). It shows a countdown, sends reminders, offers a home-screen widget and can optionally add the planned events to your device calendar. The app is available in English and German.

**Not medical advice.** NoBby is a reminder tool only. It does not know your product or your health. Always follow the product leaflet and ask your doctor or pharmacist about timing, missed doses and anything else.

## Download

- Latest APK: <https://github.com/patricku93/nobby/releases/latest/download/NoBby.apk>
- All versions: <https://github.com/patricku93/nobby/releases>

### Install (sideloading)

1. Open the APK link above on your phone.
2. When Android asks, allow your browser (or file manager) to "install unknown apps", then confirm the installation.
3. You can switch that permission off again afterwards.

The release APK is signed with the project's release key. It **cannot update** an installation that was built from source or installed from a debug build, because the signatures differ. Uninstall that version first. This deletes your NoBby data, because backups are turned off (`allowBackup=false`), so note down your schedule before you do.

## Privacy

- Your schedule and settings stay on your device. There is no account and no tracking.
- The only network access is a check against GitHub for a newer release. Nothing about you or your schedule is sent.
- Calendar access is optional. Without it the app works as usual; with it, NoBby writes its own events into the calendar you pick.

## Build from source

You need JDK 17 and the Android SDK.

```bash
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

## Releases

Releases are built by GitHub Actions. Push a tag of the form `vX.Y.Z` (major 1 or higher, minor and patch 0-99, no leading zeros), for example `v2.0.1`. The workflow runs the tests, builds the signed APK and publishes it as `NoBby.apk` on the releases page. It needs these repository secrets:

- `NOBBY_KEYSTORE_BASE64`
- `NOBBY_KEYSTORE_PASSWORD`
- `NOBBY_KEY_ALIAS`
- `NOBBY_KEY_PASSWORD`

## Auf Deutsch

NoBby ist eine Android-App, die einen wiederkehrenden Einnahmeplan (z. B. für einen Vaginalring) verfolgt: Countdown, Erinnerungen, Widget und optional Kalendereinträge. Die App ersetzt keine medizinische Beratung; richte dich immer nach dem Beipackzettel und frag Ärztin, Arzt oder Apotheke. Die Daten bleiben auf dem Gerät, das Netz wird nur für die Suche nach neuen Versionen auf GitHub genutzt. Zum Installieren die APK von der Release-Seite laden und im Browser "Unbekannte Apps installieren" erlauben. Eine signierte Release-APK kann eine Debug- oder selbst gebaute Installation nicht aktualisieren: erst deinstallieren (dabei werden die Daten gelöscht).
