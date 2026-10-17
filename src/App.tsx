import { useEffect, useRef, useState } from 'react';
import { listen } from '@tauri-apps/api/event';
import { invoke } from '@tauri-apps/api/core';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { readText, writeText } from '@tauri-apps/plugin-clipboard-manager';
import { disable as disableAutostart, enable as enableAutostart, isEnabled as isAutostartEnabled } from '@tauri-apps/plugin-autostart';
import { isRegistered, register, unregister } from '@tauri-apps/plugin-global-shortcut';
import { FloatingWindow } from './components/FloatingWindow';
import { AppSettingsModal } from './components/GitHubSyncSettings';
import { LibraryManager } from './components/LibraryManager';
import { SearchBox } from './components/SearchBox';
import { SnippetEditor } from './components/SnippetEditor';
import { SnippetList } from './components/SnippetList';
import { createEmptySnippet } from './lib/defaults';
import { publishState, subscribeState } from './lib/copyboardChannel';
import { GitHubSyncError, pullFromGitHub, pushToGitHub, syncWithGitHub } from './lib/githubSync';
import { snippetClipboardText, snippetSearchText } from './lib/noteFormat';
import { isSyncConfigured, loadStoredData, recordSnippetUsage, removeSnippet, saveStoredData, upsertSnippet } from './lib/snippetStore';
import type { CategoryFilter, GitHubSyncSettings, Snippet, StoredData } from './types';

const currentLabel = getCurrentWindow().label;
type SortMode = 'smart' | 'updated' | 'title' | 'usage';

function sortSnippets(snippets: Snippet[]) {
  return [...snippets].sort((left, right) => {
    if (left.favorite !== right.favorite) return left.favorite ? -1 : 1;
    return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  });
}

function activeSnippets(snippets: Snippet[]) {
  return snippets.filter((snippet) => !snippet.deleted);
}

function matchesQuery(snippet: Snippet, query: string) {
  if (!query.trim()) return true;
  return snippetSearchText(snippet).includes(query.trim().toLowerCase());
}

function syncErrorMessage(error: unknown) {
  if (error instanceof GitHubSyncError) return error.message;
  if (error instanceof Error) return error.message;
  return 'An unknown error occurred.';
}

function toTauriShortcut(hotkey: string) {
  return hotkey.replace(/^Ctrl/i, 'CommandOrControl');
}

function resolveTheme(mode: 'system' | 'light' | 'dark') {
  if (mode !== 'system') return mode;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function normalizeGroupName(value: string) {
  return value.trim();
}

function viewLabel(category: CategoryFilter) {
  if (category === 'all') return 'All snippets';
  if (category === 'favorites') return 'Favorites';
  if (category === 'recent') return 'Recently used';
  if (category === 'frequent') return 'Frequently used';
  return category;
}

export default function App() {
  const [data, setData] = useState<StoredData | null>(null);
  const dataRef = useRef<StoredData | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editorSnippet, setEditorSnippet] = useState<Snippet>(createEmptySnippet());
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [sortMode, setSortMode] = useState<SortMode>('smart');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [status, setStatus] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [showLibraryManager, setShowLibraryManager] = useState(false);
  const [autostartEnabled, setAutostartEnabled] = useState(false);

  const chooseSelection = (incoming: StoredData, preferredId: string | null) => {
    const visible = sortSnippets(activeSnippets(incoming.snippets));
    const match = preferredId ? visible.find((snippet) => snippet.id === preferredId) : null;
    const next = match ?? visible[0] ?? null;
    setSelectedId(next?.id ?? null);
    setEditorSnippet(next ?? createEmptySnippet());
  };

  const persist = async (nextData: StoredData) => {
    dataRef.current = nextData;
    setData(nextData);
    await saveStoredData(nextData);
    publishState(nextData);
  };

  useEffect(() => {
    void loadStoredData().then((loaded) => {
      dataRef.current = loaded;
      setData(loaded);
      chooseSelection(loaded, null);
    });

    const unsubscribe = subscribeState((incoming) => {
      dataRef.current = incoming;
      setData(incoming);
      setSelectedId((current) => {
        const visible = sortSnippets(activeSnippets(incoming.snippets));
        const next = visible.find((snippet) => snippet.id === current) ?? visible[0] ?? null;
        setEditorSnippet(next ?? createEmptySnippet());
        return next?.id ?? null;
      });
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
    dataRef.current = data;
  }, [data]);

  useEffect(() => {
    if (!data || currentLabel !== 'main') return;
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
        if (!disposed) setStatus(`Unable to register global hotkey ${data.preferences.hotkey}.`);
      }
    })();

    return () => {
      disposed = true;
      void unregister(hotkey);
    };
  }, [data?.preferences.hotkey]);

  useEffect(() => {
    if (!data) return;
    const apply = () => {
      document.documentElement.dataset.theme = resolveTheme(data.preferences.themeMode);
    };
    apply();
    if (data.preferences.themeMode !== 'system') return;
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    query.addEventListener('change', apply);
    return () => query.removeEventListener('change', apply);
  }, [data?.preferences.themeMode]);

  useEffect(() => {
    if (currentLabel !== 'main') return;
    void isAutostartEnabled()
      .then(setAutostartEnabled)
      .catch(() => setStatus('Autostart state is unavailable.'));
  }, []);

  useEffect(() => {
    if (!showSettings && !showLibraryManager) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showSettings, showLibraryManager]);

  useEffect(() => {
    if (currentLabel !== 'main') return;
    const unlistenPromise = getCurrentWindow().onFocusChanged(({ payload: focused }) => {
      if (focused) return;
      void getCurrentWindow().isMinimized().then((minimized) => {
        if (minimized) void invoke('show_floating_window');
      });
    });
    return () => {
      void unlistenPromise.then((unlisten) => unlisten());
    };
  }, []);

  useEffect(() => {
    if (currentLabel !== 'main') return;
    const onKeyDown = (event: KeyboardEvent) => {
      const modifier = event.ctrlKey || event.metaKey;
      if (!modifier) return;

      if (event.key.toLowerCase() === 'k') {
        event.preventDefault();
        document.querySelector<HTMLInputElement>('.search-box__input')?.focus();
      } else if (event.key.toLowerCase() === 'n') {
        event.preventDefault();
        handleNew();
      } else if (event.shiftKey && event.key.toLowerCase() === 'v') {
        event.preventDefault();
        void handleCaptureClipboard();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const selectSnippet = (snippet: Snippet) => {
    setSelectedId(snippet.id);
    setEditorSnippet(snippet);
  };

  const handleCopy = async (snippet: Snippet) => {
    if (snippet.deleted) return;
    try {
      await writeText(snippetClipboardText(snippet));
      setCopiedId(snippet.id);
      setStatus(`Copied "${snippet.title}".`);
      window.setTimeout(() => setCopiedId((current) => (current === snippet.id ? null : current)), 1200);
      const current = dataRef.current;
      if (current) await persist(recordSnippetUsage(current, snippet.id));
    } catch {
      setStatus('Clipboard write failed.');
    }
  };

  const handleNew = () => {
    const current = dataRef.current;
    setSelectedId(null);
    setEditorSnippet({
      ...createEmptySnippet(),
      category: current?.groups[0] ?? 'General',
    });
  };

  const handleCaptureClipboard = async () => {
    const current = dataRef.current;
    if (!current) return;
    try {
      const clipboard = (await readText()).trim();
      if (!clipboard) {
        setStatus('Clipboard is empty.');
        return;
      }
      const firstLine = clipboard.split(/\r?\n/).find((line) => line.trim())?.trim() ?? 'Clipboard';
      const title = firstLine.length > 56 ? `${firstLine.slice(0, 53)}…` : firstLine;
      const draft: Snippet = {
        ...createEmptySnippet(),
        title,
        text: clipboard,
        category: current.groups[0] ?? 'General',
      };
      const nextSnippets = upsertSnippet(current.snippets, draft);
      const nextData = { ...current, snippets: sortSnippets(nextSnippets) };
      await persist(nextData);
      const saved = nextSnippets.find((snippet) => snippet.id === draft.id) ?? draft;
      selectSnippet(saved);
      setStatus(`Captured clipboard as "${saved.title}".`);
    } catch {
      setStatus('Clipboard capture failed.');
    }
  };

  const handleSave = async () => {
    const current = dataRef.current;
    if (!current) return;
    const validCategory = current.groups.includes(editorSnippet.category)
      ? editorSnippet.category
      : (current.groups[0] ?? 'General');
    const sanitizedSnippet = { ...editorSnippet, category: validCategory, deleted: false };
    const nextSnippets = upsertSnippet(current.snippets, sanitizedSnippet);
    const nextData = { ...current, snippets: sortSnippets(nextSnippets) };
    await persist(nextData);
    setStatus('Snippet saved.');
    const saved = nextSnippets.find((snippet) => snippet.id === sanitizedSnippet.id && !snippet.deleted);
    if (saved) selectSnippet(saved);
  };

  const handleDelete = async () => {
    const current = dataRef.current;
    if (!current || !selectedId) return;
    const nextSnippets = removeSnippet(current.snippets, selectedId);
    const nextData = { ...current, snippets: nextSnippets };
    const nextSelected = sortSnippets(activeSnippets(nextSnippets))[0] ?? null;
    await persist(nextData);
    setSelectedId(nextSelected?.id ?? null);
    setEditorSnippet(nextSelected ?? createEmptySnippet());
    setStatus('Snippet deleted.');
  };

  const handleCreateGroup = async (name: string) => {
    const current = dataRef.current;
    if (!current) return;
    const candidate = normalizeGroupName(name);
    if (!candidate) {
      setStatus('Group name is required.');
      return;
    }
    if (current.groups.some((group) => group.toLowerCase() === candidate.toLowerCase())) {
      setStatus('Group already exists.');
      return;
    }
    const nextGroups = [...current.groups, candidate].sort((left, right) => left.localeCompare(right));
    await persist({ ...current, groups: nextGroups, groupFolders: { ...current.groupFolders, [candidate]: null } });
    setStatus(`Group "${candidate}" created.`);
  };

  const handleCreateFolder = async (name: string) => {
    const current = dataRef.current;
    if (!current) return;
    const candidate = normalizeGroupName(name);
    if (!candidate) {
      setStatus('Folder name is required.');
      return;
    }
    if (current.folders.some((folder) => folder.toLowerCase() === candidate.toLowerCase())) {
      setStatus('Folder already exists.');
      return;
    }
    await persist({ ...current, folders: [...current.folders, candidate].sort((left, right) => left.localeCompare(right)) });
    setStatus(`Folder "${candidate}" created.`);
  };

  const handleAssignFolder = async (group: string, folder: string) => {
    const current = dataRef.current;
    if (!current) return;
    await persist({ ...current, groupFolders: { ...current.groupFolders, [group]: folder || null } });
    setStatus(folder ? `Group "${group}" moved to "${folder}".` : `Group "${group}" removed from its folder.`);
  };

  const handleDeleteFolder = async (folder: string) => {
    const current = dataRef.current;
    if (!current) return;
    await persist({
      ...current,
      folders: current.folders.filter((item) => item !== folder),
      groupFolders: Object.fromEntries(Object.entries(current.groupFolders).map(([group, assigned]) => [
        group,
        assigned === folder ? null : assigned,
      ])),
    });
    setStatus(`Folder "${folder}" deleted; its groups are now unfiled.`);
  };

  const handleDeleteGroup = async (group: string) => {
    const current = dataRef.current;
    if (!current) return;
    if (current.groups.length <= 1) {
      setStatus('At least one group is required.');
      return;
    }
    if (current.snippets.some((snippet) => !snippet.deleted && snippet.category === group)) {
      setStatus('Group is in use. Move snippets before deleting it.');
      return;
    }
    const nextGroups = current.groups.filter((item) => item !== group);
    const nextGroupFolders = { ...current.groupFolders };
    delete nextGroupFolders[group];
    await persist({ ...current, groups: nextGroups, groupFolders: nextGroupFolders });
    if (category === group) setCategory('all');
    setEditorSnippet((snippet) => ({
      ...snippet,
      category: snippet.category === group ? (nextGroups[0] ?? 'General') : snippet.category,
    }));
    setStatus(`Group "${group}" deleted.`);
  };

  const handleRenameGroup = async (source: string, targetValue: string) => {
    const current = dataRef.current;
    if (!current) return;
    const target = normalizeGroupName(targetValue);
    if (!source || !target) {
      setStatus('Select a group and enter a new name.');
      return;
    }
    if (source.toLowerCase() === target.toLowerCase()) {
      setStatus('New group name must be different.');
      return;
    }
    if (current.groups.some((group) => group.toLowerCase() === target.toLowerCase())) {
      setStatus('Target group name already exists.');
      return;
    }

    const now = new Date().toISOString();
    const nextGroups = current.groups.map((group) => (group === source ? target : group)).sort((a, b) => a.localeCompare(b));
    const nextSnippets = current.snippets.map((snippet) => (
      !snippet.deleted && snippet.category === source
        ? { ...snippet, category: target, updatedAt: now }
        : snippet
    ));
    const nextGroupFolders = Object.fromEntries(Object.entries(current.groupFolders).map(([group, folder]) => [
      group === source ? target : group,
      folder,
    ]));
    await persist({ ...current, groups: nextGroups, groupFolders: nextGroupFolders, snippets: sortSnippets(nextSnippets) });
    if (category === source) setCategory(target);
    setEditorSnippet((snippet) => ({ ...snippet, category: snippet.category === source ? target : snippet.category }));
    setStatus(`Group "${source}" renamed to "${target}".`);
  };

  const handleMoveGroupSnippets = async (source: string, target: string) => {
    const current = dataRef.current;
    if (!current) return;
    if (!source || !target) {
      setStatus('Select source and target groups.');
      return;
    }
    if (source === target) {
      setStatus('Source and target groups must be different.');
      return;
    }
    const affected = current.snippets.filter((snippet) => !snippet.deleted && snippet.category === source).length;
    if (affected === 0) {
      setStatus('No snippets to move for this group.');
      return;
    }
    const now = new Date().toISOString();
    const nextSnippets = current.snippets.map((snippet) => (
      !snippet.deleted && snippet.category === source
        ? { ...snippet, category: target, updatedAt: now }
        : snippet
    ));
    await persist({ ...current, snippets: sortSnippets(nextSnippets) });
    if (category === source) setCategory(target);
    setEditorSnippet((snippet) => ({ ...snippet, category: snippet.category === source ? target : snippet.category }));
    setStatus(`Moved ${affected} snippet(s) from "${source}" to "${target}".`);
  };

  const handleSaveSettings = async (
    settings: GitHubSyncSettings,
    themeMode: 'system' | 'light' | 'dark',
    enableLaunchOnStartup: boolean,
    floatingCollapseMode: 'icon' | 'stay-open',
    floatingAnimation: 'fade' | 'vertical' | 'horizontal',
    floatingIconScale: number,
    floatingIconOpacity: number,
    floatingHoverDelayMs: number,
  ) => {
    const current = dataRef.current;
    if (!current) return;
    const nextData: StoredData = {
      ...current,
      syncSettings: {
        ...settings,
        branch: settings.branch || 'main',
        path: settings.path || 'copyboard-sync.json',
      },
      preferences: {
        ...current.preferences,
        themeMode,
        floatingCollapseMode,
        floatingAnimation,
        floatingIconScale,
        floatingIconOpacity,
        floatingHoverDelayMs,
      },
    };
    try {
      if (enableLaunchOnStartup) await enableAutostart();
      else await disableAutostart();
      setAutostartEnabled(enableLaunchOnStartup);
    } catch {
      setStatus('Settings saved, but autostart could not be updated.');
    }
    await persist(nextData);
    setShowSettings(false);
    setStatus('Settings saved.');
  };

  const ensureSyncReady = (current: StoredData | null) => {
    if (!current || !isSyncConfigured(current.syncSettings)) {
      setStatus('GitHub sync is incomplete. Configure owner, repo, branch, path, and token first.');
      return false;
    }
    return true;
  };

  const applySyncResult = async (
    current: StoredData,
    result: Pick<StoredData, 'snippets' | 'groups' | 'folders' | 'groupFolders'>,
    message: string,
  ) => {
    const nextData: StoredData = {
      ...current,
      ...result,
      snippets: sortSnippets(result.snippets),
    };
    await persist(nextData);
    chooseSelection(nextData, selectedId);
    setStatus(message);
  };

  const handlePull = async () => {
    const current = dataRef.current;
    if (!ensureSyncReady(current) || !current) return;
    try {
      const merged = await pullFromGitHub(current.syncSettings, current);
      await applySyncResult(current, merged, 'Pull successful. Local data merged with GitHub.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  };

  const handlePush = async () => {
    const current = dataRef.current;
    if (!ensureSyncReady(current) || !current) return;
    try {
      await pushToGitHub(current.syncSettings, current);
      setStatus('Push successful. copyboard-sync.json updated on GitHub.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  };

  async function handleSync() {
    const current = dataRef.current;
    if (!ensureSyncReady(current) || !current) return;
    try {
      const merged = await syncWithGitHub(current.syncSettings, current);
      await applySyncResult(current, merged, 'Sync successful. Snippets and collections merged with GitHub.');
    } catch (error) {
      setStatus(syncErrorMessage(error));
    }
  }

  const handleFloatingPin = async (pinned: boolean) => {
    const current = dataRef.current;
    if (!current) return;
    await persist({ ...current, preferences: { ...current.preferences, floatingPinned: pinned } });
  };

  const handleFloatingPosition = async (x: number, y: number) => {
    const current = dataRef.current;
    if (!current) return;
    await persist({ ...current, preferences: { ...current.preferences, floatingPosition: { x, y } } });
  };

  if (!data) {
    return <div className="app-shell app-shell--loading">Loading Copyboard…</div>;
  }

  const active = activeSnippets(data.snippets);

  if (currentLabel === 'floating') {
    return (
      <FloatingWindow
        snippets={sortSnippets(active)}
        preferences={data.preferences}
        copiedId={copiedId}
        onCopy={handleCopy}
        onPinChange={handleFloatingPin}
        onPositionChange={handleFloatingPosition}
        onOpenMain={() => invoke('show_main_window')}
      />
    );
  }

  let visibleSnippets = [...active];
  if (category === 'favorites') {
    visibleSnippets = visibleSnippets.filter((snippet) => snippet.favorite);
  } else if (category === 'recent') {
    const recentOrder = new Map(data.preferences.recentSnippetIds.map((id, index) => [id, index]));
    visibleSnippets = visibleSnippets
      .filter((snippet) => recentOrder.has(snippet.id))
      .sort((left, right) => (recentOrder.get(left.id) ?? 999) - (recentOrder.get(right.id) ?? 999));
  } else if (category === 'frequent') {
    visibleSnippets = visibleSnippets
      .filter((snippet) => (data.preferences.usageCounts[snippet.id] ?? 0) > 0)
      .sort((left, right) => (data.preferences.usageCounts[right.id] ?? 0) - (data.preferences.usageCounts[left.id] ?? 0));
  } else if (category !== 'all') {
    visibleSnippets = visibleSnippets.filter((snippet) => snippet.category === category);
  }

  visibleSnippets = visibleSnippets.filter((snippet) => matchesQuery(snippet, search));

  if (sortMode === 'updated') {
    visibleSnippets.sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
  } else if (sortMode === 'title') {
    visibleSnippets.sort((left, right) => left.title.localeCompare(right.title));
  } else if (sortMode === 'usage') {
    visibleSnippets.sort((left, right) => (data.preferences.usageCounts[right.id] ?? 0) - (data.preferences.usageCounts[left.id] ?? 0));
  } else if (category !== 'recent' && category !== 'frequent') {
    visibleSnippets = sortSnippets(visibleSnippets);
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Library</p>
          <h1>Copyboard</h1>
          <p className="sidebar__copy">Fast snippets, notes and checklists across Windows and Android.</p>
        </div>

        <nav className="category-list" aria-label="Copyboard library">
          <button type="button" className={category === 'all' ? 'is-active' : ''} onClick={() => setCategory('all')}>All snippets</button>
          <button type="button" className={category === 'favorites' ? 'is-active' : ''} onClick={() => setCategory('favorites')}>★ Favorites</button>
          <button type="button" className={category === 'recent' ? 'is-active' : ''} onClick={() => setCategory('recent')}>Recently used</button>
          <button type="button" className={category === 'frequent' ? 'is-active' : ''} onClick={() => setCategory('frequent')}>Frequently used</button>

          {[...data.folders, null].map((folder) => {
            const folderGroups = data.groups.filter((group) => (data.groupFolders[group] ?? null) === folder);
            if (folderGroups.length === 0) return null;
            return (
              <div className="category-folder" key={folder ?? 'unfiled'}>
                <span>{folder ?? 'Other groups'}</span>
                {folderGroups.map((item) => (
                  <button key={item} type="button" className={category === item ? 'is-active' : ''} onClick={() => setCategory(item)}>
                    {item}
                  </button>
                ))}
              </div>
            );
          })}
        </nav>

        <div className="sidebar__bottom">
          <button type="button" className="sidebar__utility" onClick={() => setShowLibraryManager(true)}>Manage library</button>
          <div className="sidebar__shortcuts">
            <span>Ctrl+K Search</span>
            <span>Ctrl+N New</span>
            <span>Ctrl+Shift+V Capture</span>
          </div>
        </div>
      </aside>

      <main className="main-panel">
        <section className="toolbar">
          <SearchBox value={search} onChange={setSearch} placeholder="Search title, text, checklist, or group" />
          <div className="toolbar__actions">
            <button type="button" className="primary-button" onClick={handleNew}>New</button>
            <button type="button" className="ghost-button" onClick={() => void handleCaptureClipboard()}>Capture clipboard</button>
            <button type="button" className="ghost-button" onClick={() => setShowSettings(true)}>Settings</button>
            <button type="button" className="ghost-button" onClick={handlePull}>Pull</button>
            <button type="button" className="ghost-button" onClick={handlePush}>Push</button>
            <button type="button" className="ghost-button" onClick={() => void handleSync()}>Sync</button>
            <button type="button" className="ghost-button" onClick={() => invoke('show_floating_window')}>Floating</button>
          </div>
        </section>

        <section className="status-bar">
          <span>{status || `Hotkey: ${data.preferences.hotkey}`}</span>
        </section>

        <div className="content-grid">
          <section className="snippet-panel">
            <div className="snippet-panel__header">
              <div>
                <p className="eyebrow">{viewLabel(category)}</p>
                <h2>{visibleSnippets.length} snippets</h2>
              </div>
              <label className="snippet-sort">
                <span>Sort</span>
                <select value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
                  <option value="smart">Smart</option>
                  <option value="updated">Updated</option>
                  <option value="title">Title</option>
                  <option value="usage">Usage</option>
                </select>
              </label>
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
            groups={data.groups}
            onChange={setEditorSnippet}
            onSave={() => void handleSave()}
            onDelete={() => void handleDelete()}
            onNew={handleNew}
          />
        </div>
      </main>

      {showLibraryManager ? (
        <LibraryManager
          groups={data.groups}
          folders={data.folders}
          groupFolders={data.groupFolders}
          onClose={() => setShowLibraryManager(false)}
          onCreateFolder={handleCreateFolder}
          onDeleteFolder={handleDeleteFolder}
          onCreateGroup={handleCreateGroup}
          onAssignFolder={handleAssignFolder}
          onDeleteGroup={handleDeleteGroup}
          onRenameGroup={handleRenameGroup}
          onMoveGroupSnippets={handleMoveGroupSnippets}
        />
      ) : null}

      {showSettings ? (
        <AppSettingsModal
          initialValue={{
            syncSettings: data.syncSettings,
            themeMode: data.preferences.themeMode,
            autostartEnabled,
            floatingCollapseMode: data.preferences.floatingCollapseMode,
            floatingAnimation: data.preferences.floatingAnimation,
            floatingIconScale: data.preferences.floatingIconScale,
            floatingIconOpacity: data.preferences.floatingIconOpacity,
            floatingHoverDelayMs: data.preferences.floatingHoverDelayMs,
          }}
          onSave={(settings) => void handleSaveSettings(
            settings.syncSettings,
            settings.themeMode,
            settings.autostartEnabled,
            settings.floatingCollapseMode,
            settings.floatingAnimation,
            settings.floatingIconScale,
            settings.floatingIconOpacity,
            settings.floatingHoverDelayMs,
          )}
          onClose={() => setShowSettings(false)}
        />
      ) : null}
    </div>
  );
}
