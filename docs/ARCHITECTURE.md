# Architektur

## Ziel

Copyboard soll kein klassischer Clipboard-Manager sein, sondern ein bewusstes Snippet-Notizbuch.

## Datenmodell

```kotlin
data class Snippet(
    val id: String,
    val title: String,
    val text: String,
    val category: String,
    val favorite: Boolean
)
```

## Speicherung

Version 0.1 speichert alle Snippets als JSON in `SharedPreferences`.

Vorteile:

- keine Datenbank-Abhängigkeit
- kein KSP/KAPT
- einfacher Build
- ausreichend für kleine bis mittlere Snippet-Sammlungen

Später sinnvoll:

- Room Database
- JSON Export/Import
- Backup-Datei in Downloads

## Kopieren

Die App nutzt Androids `ClipboardManager#setPrimaryClip()`.

- In der App: `MainActivity.copySnippet()`
- Im Widget: `CopySnippetReceiver`

## Widget

Das Widget zeigt bis zu vier Favoriten. Wenn keine Favoriten existieren, werden die ersten vier Snippets verwendet.

Aktualisierung passiert nach Speichern/Löschen über:

```kotlin
CopyboardWidgetProvider.updateAll(context)
```
