# Copyboard

Copyboard is now a two-surface repository:

- Android app in Kotlin for quick snippet storage and widget access
- Windows desktop app in Tauri + Vite + React + TypeScript

The desktop app keeps the existing Copyboard idea intact: a personal snippet notebook with direct clipboard copy, GitHub-backed sync, and a floating mini-mode for fast access.

## Features

### Desktop

- Snippet list with favorites first
- Search across title, text, and category
- Category filters
- Editor for create, update, favorite, and delete
- Click any snippet to copy `snippet.text` to the system clipboard
- GitHub sync through `copyboard-sync.json`
- Floating always-on-top mini window
- Hover-to-expand floating panel
- Tray icon with quick actions
- Global hotkey, default `Ctrl+Alt+C`

### Android

- Save text snippets
- Search
- Tap to copy to clipboard
- Long press to edit or delete
- Favorites on the homescreen widget

## Repository Structure

```text
Copyboard-android/
├─ .github/
│  └─ workflows/
│     ├─ build.yml
│     └─ desktop-build.yml
├─ app/
│  └─ src/main/
│     ├─ java/de/circuitcurios/copyboard/
│     └─ res/
├─ src/
│  ├─ components/
│  └─ lib/
├─ src-tauri/
│  ├─ capabilities/
│  ├─ icons/
│  ├─ src/
│  └─ tauri.conf.json
├─ package.json
├─ tsconfig.json
├─ vite.config.ts
├─ build.gradle.kts
├─ settings.gradle.kts
└─ README.md
```

## Desktop Sync File Schema

Copyboard respects the existing JSON-based sync model. If no richer schema is present, the desktop app uses:

```json
{
	"version": 1,
	"updatedAt": "2026-07-12T12:00:00.000Z",
	"snippets": [
		{
			"id": "uuid",
			"title": "string",
			"text": "string",
			"category": "string",
			"favorite": true,
			"updatedAt": "2026-07-12T12:00:00.000Z"
		}
	]
}
```

Merge behavior:

- Compare snippets by `id`
- Newer `updatedAt` wins
- No hard-delete propagation unless a `deleted` flag is introduced later
- `Sync` does pull, merge, then push

## GitHub Sync Setup

Open the desktop app and configure:

- GitHub Owner
- Repository
- Branch, default `main`
- Path, default `copyboard-sync.json`
- Fine-grained token

The token is never hardcoded. It is stored locally in the app store for now so the sync layer stays isolated and can later be switched to secure storage without rewriting the feature.

### Fine-Grained Token Permissions

Create a fine-grained GitHub token with these minimum permissions:

1. Repository access: select the repository that contains `copyboard-sync.json`
2. Repository permissions: `Contents` set to `Read and write`
3. Repository permissions: `Metadata` set to `Read-only`

Recommended file path:

- `copyboard-sync.json`

The app uses the GitHub Contents API:

- `GET` to read the sync file
- `PUT` to update it with the current `sha`
- If the file does not exist yet, the first push creates it

## Floating Mode

The desktop app includes a dedicated `floating` Tauri window.

Behavior:

- Collapsed state is a small pill, approximately `120x36`
- Expanded state is a compact panel, approximately `360x420`
- Borderless and always-on-top
- Draggable
- On mouse enter, the pill expands
- On mouse leave, it collapses after a short delay
- Returning the mouse cancels the collapse timer
- Clicking a snippet copies it immediately
- `Esc` collapses the panel when it is not pinned
- Position is remembered between launches

The expanded panel focuses on:

- Search
- Favorites
- Recent and frequently used snippets

## Global Hotkey

Default hotkey:

- `Ctrl+Alt+C`

On desktop, this hotkey shows and focuses the floating panel. The hotkey is stored in app preferences so it can be made user-configurable later without restructuring the app.

## Tray Behavior

The Tauri desktop app adds a tray icon with these actions:

- Open Copyboard
- Show/hide floating mode
- Sync
- Exit

Closing the main window hides it instead of immediately terminating the app, which keeps the tray and floating mode available.

## Local Desktop Build

### Prerequisites

Install locally:

- Node.js 20+
- npm 10+
- Rust stable with Cargo
- Tauri Windows prerequisites, including WebView2 and Visual Studio C++ build tools

### Install dependencies

```bash
npm install
```

### Run desktop app in development

```bash
npm run tauri:dev
```

### Build desktop frontend only

```bash
npm run build
```

### Build Windows desktop bundle

```bash
npm run tauri:build
```

Typical bundle output:

```text
src-tauri/target/release/bundle/
```

This workflow currently targets Windows artifacts such as NSIS and MSI bundles, plus the release executable when available.

## GitHub Actions

### Android

Workflow:

- `.github/workflows/build.yml`

Builds a debug APK and uploads it as an artifact.

### Desktop Windows

Workflow:

- `.github/workflows/desktop-build.yml`

It runs:

1. `npm install`
2. `npm run build`
3. `npm run tauri:build`

Artifacts uploaded:

- Windows NSIS bundle
- Windows MSI bundle
- Release `.exe` when produced
- Frontend `dist/`

### Start a GitHub Actions Build

You can trigger either workflow by:

1. Pushing to `main` or `master`
2. Opening a pull request
3. Running `workflow_dispatch` manually in GitHub Actions

## Local Android Build

### Android Studio

1. Open the repository in Android Studio.
2. Wait for Gradle sync.
3. Run `app` or use `Build > Build APK(s)`.

### Gradle CLI

This repository intentionally does not include a Gradle wrapper.

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Desktop sync errors are surfaced in the UI, including token problems, missing repo/file, permission issues, network failures, and invalid JSON.
- The current desktop token storage is local, not hardcoded, and intentionally encapsulated so secure storage can be added later.
- The desktop and floating windows share the same stored snippet data and synchronize through the Tauri store plus a local broadcast channel.
