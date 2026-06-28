# Copyboard

Kleine Android-App als persönliches Snippet-Notizbuch:

- Textbausteine speichern
- suchen
- antippen = in die Zwischenablage kopieren
- lange drücken = bearbeiten oder löschen
- Favoriten im Homescreen-Widget anzeigen
- Widget-Buttons kopieren direkt in die Zwischenablage

Das Projekt ist bewusst schlank gehalten: **Kotlin + Android SDK**, keine Compose-, Room- oder AppCompat-Abhängigkeiten. Weniger hübsch, aber deutlich weniger anfällig.

## Projektstruktur

```text
copyboard-android/
├─ .github/workflows/android-build.yml
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/de/circuitcurios/copyboard/
│     │  ├─ MainActivity.kt
│     │  ├─ Snippet.kt
│     │  ├─ SnippetStore.kt
│     │  ├─ CopyboardWidgetProvider.kt
│     │  └─ CopySnippetReceiver.kt
│     └─ res/
│        ├─ drawable/
│        ├─ layout/widget_copyboard.xml
│        ├─ values/
│        └─ xml/copyboard_widget_info.xml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradle.properties
```

## Lokaler Build

### Mit Android Studio

1. Repository in Android Studio öffnen.
2. Gradle Sync abwarten.
3. `app` starten oder `Build > Build APK(s)` verwenden.

### Mit Gradle CLI

Dieses Repo enthält bewusst **keinen Gradle Wrapper**. Lokal kannst du entweder einen Wrapper ergänzen oder Gradle installiert haben.

```bash
gradle :app:assembleDebug
```

Der APK liegt danach hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions Build

Der Workflow liegt hier:

```text
.github/workflows/android-build.yml
```

Er baut bei Push, Pull Request und manuell über `workflow_dispatch` einen Debug-APK und lädt ihn als Artifact hoch:

```text
copyboard-debug-apk
```

## Widget benutzen

1. App einmal öffnen.
2. Snippets anlegen oder Defaults nutzen.
3. Snippets als Favorit markieren.
4. Homescreen lange drücken.
5. Widget hinzufügen: **Copyboard**.
6. Buttons antippen = Text wird kopiert.

## MVP-Grenzen

Aktuell absichtlich nicht enthalten:

- Cloud-Sync
- Passwort-/Secret-Speicher
- automatische Clipboard-History
- Import/Export UI
- Drag & Drop Sortierung
- Kategorien als separate Tabs

Das kann später sauber ergänzt werden.

## Warum keine automatische Clipboard-History?

Moderne Android-Versionen beschränken den Zugriff auf fremde Clipboard-Inhalte stark. Copyboard speichert deshalb eigene Textbausteine und schreibt diese bewusst in die Zwischenablage. Das ist stabiler und weniger gruselig.
