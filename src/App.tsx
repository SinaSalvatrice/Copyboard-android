import { useEffect, useState } from 'react';
import { listen } from '@tauri-apps/api/event';
import { invoke } from '@tauri-apps/api/core';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { writeText } from '@tauri-apps/plugin-clipboard-manager';
import { disable as disableAutostart, enable as enableAutostart, isEnabled as isAutostartEnabled } from '@tauri-apps/plugin-autostart';
import { isRegistered, register, unregister } from '@tauri-apps/plugin-global-shortcut';
import { FloatingWindow } from './components/FloatingWindow';
import { AppSettingsModal } from './components/GitHubSyncSettings';
import { SearchBox } from './components/SearchBox';
import { SnippetEditor } from './components/SnippetEditor';
import { SnippetList } from './components/SnippetList';
import { createEmptySnippet } from './lib/defaults';
import { publishState, subscribeState } from './lib/copyboardChannel';
import { GitHubSyncError, pullFromGitHub, pushToGitHub, syncWithGitHub } from './lib/githubSync';
import { isSyncConfigured, loadStoredData, recordSnippetUsage, removeSnippet, saveStoredData, upsertSnippet } from './lib/snippetStore';
import type { CategoryFilter, GitHubSyncSettings, Snippet, StoredData } from './types';

const currentLabel = getCurrentWindow().label;

function sortSnippets(snippets: Snippet[]) {
  return [...snippets].sort((left, right) => {
    if (left.favorite !== right.favorite) {
      return left.favorite ? -1 : 1;
    }

    return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  });
}

function matchesQuery(snippet: Snippet, query: string) {
  if (!query.trim()) {
    return true;
  }

  const lowered = query.trim().toLowerCase();
  return [snippet.title, snippet.text, snippet.category].some((value) => value.toLowerCase().includes(lowered));
}

function matchesCategory(snippet: Snippet, category: CategoryFilter) {
  if (category === 'all') {
    return true;
  }

  if (category === 'favorites') {
    return snippet.favorite;
  }

  return snippet.category === category;
}

function syncErrorMessage(error: unknown) {
  if (error instanceof GitHubSyncError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'An unknown error occurred.';
}

function toTauriShortcut(hotkey: string) {
  return hotkey.replace(/^Ctrl/i, 'CommandOrControl');
}

function resolveTheme(mode: 'system' | 'light' | 'dark') {
  if (mode !== 'system') {
    return mode;
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export default function App() {
  const [data, setData] = useState<StoredData | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editorSnippet, setEditorSnippet] = useState<Snippet>(createEmptySnippet());
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [status, setStatus] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [autostartEnabled, setAutostartEnabled] = useState(false);

  useEffect(() => {
    void loadStoredData().then((loaded) => {
      setData(loaded);
      setSelectedId(loaded.snippets[0]?.id ?? null);
      setEditorSnippet(loaded.snippets[0] ?? createEmptySnippet());
    });

    const unsubscribe = subscribeState((incoming) => {
      setData(incoming);
      if (selectedId) {
        const match = incoming.snippets.find((snippet) => snippet.id === selectedId);
        if (match) {
          setEditorSnippet(match);
        }
      }
    });

    const unlistenPromise = listen('copyboard://sync-request', async () => {
      await handleSync();
    });

    return () => {
      unsubscribe();
      void unlistenPromise.then((unlisten) => unlisten());
    };
  }, []);

  useEffect(() => {
    if (!data || currentLabel !== 'main') {
      return;
    }

    const hotkey = toTauriShortcut(data.preferences.hotkey);
    let disposed = false;

    void (async () => {
      try {
        if (!(await isRegistered(hotkey))) {
          await register(hotkey, async () => {
            await invoke('show_floating_window');
          });
        }
      } catch {
        if (!disposed) {
          setStatus(`Unable to register global hotkey ${data.preferences.hotkey}.`);
        }
      }
    })();

    return () => {
      disposed = true;
      void unregister(hotkey);
    };
  }, [data]);

  useEffect(() => {
    if (!data) {
      return;
    }

    const apply = () => {
      const theme = resolveTheme(data.preferences.themeMode);
      document.documentElement.dataset.theme = theme;
    };

    apply();

    if (data.preferences.themeMode !== 'system') {
      return;
    }

    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = () => apply();
    query.addEventListener('change', listener);

    return () => {
      query.removeEventListener('change', listener);
    };
  }, [data]);

  useEffect(() => {
    if (currentLabel !== 'main') {
      return;
    }

    void isAutostartEnabled()
      .then((enabled) => setAutostartEnabled(enabled))
      .catch(() => setStatus('Autostart state is unavailable.'));
  }, []);

  const persist = async (nextData: StoredData) => {
    setData(nextData);
    await saveStoredData(nextData);
    publishState(nextData);
  };

  const selectSnippet = (snippet: Snippet) => {
    setSelectedId(snippet.id);
    setEditorSnippet(snippet);
  };

  const handleCopy = async (snippet: Snippet) => {
    try {
      await writeText(snippet.text);
      setCopiedId(snippet.id);
      setStatus(`Copied "${snippet.title}".`);
      window.setTimeout(() => setCopiedId((current) => (current === snippet.id ? null : current)), 1200);

      if (data) {
        const nextData = recordSnippetUsage(data, snippet.id);
        await persist(nextData);
      }
    } catch {
      setStatus('Clipboard write failed.');
    }
  };

  const handleSave = async () => {
    if (!data) {
      return;
    }

    const nextSnippets = upsertSnippet(data.snippets, editorSnippet);
    const nextData = {
      ...data,
      snippets: sortSnippets(nextSnippets),
    };
    await persist(nextData);
    setStatus('Snippet saved.');
    const saved = nextData.snippets.find((snippet) => snippet.id === editorSnippet.id);
    if (saved) {
      selectSnippet(saved);
    }
  };

  const handleDelete = async () => {
    if (!data || !selectedId) {
      return;
    }

    const nextSnippets = removeSnippet(data.snippets, selectedId);
    const nextSelected = nextSnippets[0] ?? createEmptySnippet();
    const nextData = {
      ...data,
      snippets: nextSnippets,
    };
    await persist(nextData);
    setSelectedId(nextSnippets[0]?.id ?? null);
    setEditorSnippet(nextSelected);
    setStatus('Snippet deleted.');
  };

  const handleNew = () => {
    setSelectedId(null);
    setEditorSnippet(createEmptySnippet());
  };

  const handleSaveSettings = async (
    settings: GitHubSyncSettings,
    themeMode: 'system' | 'light' | 'dark',
    enableLaunchOnStartup: boolean,
  ) => {
    if (!data) {
      return;
    }

    const nextData = {
      ...data,
      syncSettings: {
        ...settings,
        branch: settings.branch || 'main',
        path: settings.path || 'copyboard-sync.json',
      },
      preferences: {
        ...data.preferences,
        themeMode,
      },
    };

    try {
      if (enableLaunchOnStartup) {
        await enableAutostart();
      } else {
        await disableAutostart();
      }
      setAutostartEnabled(enableLaunchOnStartup);
    } catch {
      setStatus('Settings saved, but autostart could not be updated.');
    }

    await persist(nextData);
    setShowSettings(false);
    setStatus('Settings saved.');
  };

  const ensureSyncReady = () => {
    if (!data || !isSyncConfigured(data.syncSettings)) {
      setStatus('GitHub sync is incomplete. Configure owner, repo, branch, path, and token first.');
      return false;
    }

    return true;
  };

  const handlePull = async () => {
    if (!data || !ensureSyncReady()) {
      return;
    }

    try {
      const merged = await pullFromGitHub(data.syncSettings, data.snippets);
      const nextData = {
        ...data,
        snippets: sortSnippets(merged),
      };
      await persist(nextData);
      setStatus('Pull successful. Local snippets merged with GitHub.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  };

  const handlePush = async () => {
    if (!data || !ensureSyncReady()) {
      return;
    }

    try {
      await pushToGitHub(data.syncSettings, data.snippets);
      setStatus('Push successful. copyboard-sync.json updated on GitHub.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  };

  async function handleSync() {
    if (!data || !ensureSyncReady()) {
      return;
    }

    try {
      const merged = await syncWithGitHub(data.syncSettings, data.snippets);
      const nextData = {
        ...data,
        snippets: sortSnippets(merged),
      };
      await persist(nextData);
      setStatus('Sync successful. Pulled, merged, and pushed snippets.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  }

  const handleFloatingPin = async (pinned: boolean) => {
    if (!data) {
      return;
    }

    const nextData = {
      ...data,
      preferences: {
        ...data.preferences,
        floatingPinned: pinned,
      },
    };
    await persist(nextData);
  };

  const handleFloatingPosition = async (x: number, y: number) => {
    if (!data) {
      return;
    }

    const nextData = {
      ...data,
      preferences: {
        ...data.preferences,
        floatingPosition: { x, y },
      },
    };
    await persist(nextData);
  };

  if (!data) {
    return <div className="app-shell app-shell--loading">Loading Copyboard…</div>;
  }

  if (currentLabel === 'floating') {
    return (
      <FloatingWindow
        snippets={sortSnippets(data.snippets)}
        preferences={data.preferences}
        copiedId={copiedId}
        onCopy={handleCopy}
        onPinChange={handleFloatingPin}
        onPositionChange={handleFloatingPosition}
        onOpenMain={() => invoke('show_main_window')}
      />
    );
  }

  const categories = Array.from(new Set(data.snippets.map((snippet) => snippet.category))).sort((left, right) => left.localeCompare(right));
  const visibleSnippets = sortSnippets(data.snippets)
    .filter((snippet) => matchesCategory(snippet, category))
    .filter((snippet) => matchesQuery(snippet, search));

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Collections</p>
          <h1>Copyboard</h1>
          <p className="sidebar__copy">Fast personal snippets with GitHub sync, a floating mini-mode, tray support, and a global shortcut.</p>
        </div>
        <div className="category-list">
          <button type="button" className={category === 'all' ? 'is-active' : ''} onClick={() => setCategory('all')}>
            All snippets
          </button>
          <button type="button" className={category === 'favorites' ? 'is-active' : ''} onClick={() => setCategory('favorites')}>
            Favorites
          </button>
          {categories.map((item) => (
            <button key={item} type="button" className={category === item ? 'is-active' : ''} onClick={() => setCategory(item)}>
              {item}
            </button>
          ))}
        </div>
      </aside>

      <main className="main-panel">
        <section className="toolbar">
          <SearchBox value={search} onChange={setSearch} placeholder="Search title, text, or category" />
          <div className="toolbar__actions">
            <button type="button" className="ghost-button" onClick={handleNew}>New snippet</button>
            <button type="button" className="ghost-button" onClick={() => setShowSettings(true)}>Settings</button>
            <button type="button" className="ghost-button" onClick={handlePull}>Pull</button>
            <button type="button" className="ghost-button" onClick={handlePush}>Push</button>
            <button type="button" className="primary-button" onClick={() => void handleSync()}>Sync</button>
            <button type="button" className="ghost-button" onClick={() => invoke('show_floating_window')}>Activate floating mode</button>
          </div>
        </section>

        <section className="status-bar">
          <span>{status || `Hotkey: ${data.preferences.hotkey}`}</span>
        </section>

        <div className="content-grid">
          <section className="snippet-panel">
            <div className="snippet-panel__header">
              <div>
                <p className="eyebrow">Snippet list</p>
                <h2>{visibleSnippets.length} visible</h2>
              </div>
            </div>
            <SnippetList
              snippets={visibleSnippets}
              selectedId={selectedId}
              copiedId={copiedId}
              onCopy={handleCopy}
              onSelect={selectSnippet}
            />
          </section>

          <SnippetEditor
            snippet={editorSnippet}
            onChange={setEditorSnippet}
            onSave={() => void handleSave()}
            onDelete={() => void handleDelete()}
            onNew={handleNew}
          />
        </div>
      </main>

      {showSettings ? (
        <AppSettingsModal
          initialValue={{
            syncSettings: data.syncSettings,
            themeMode: data.preferences.themeMode,
            autostartEnabled,
          }}
          onSave={(settings) =>
            void handleSaveSettings(settings.syncSettings, settings.themeMode, settings.autostartEnabled)
          }
          onClose={() => setShowSettings(false)}
        />
      ) : null}
    </div>
  );
}