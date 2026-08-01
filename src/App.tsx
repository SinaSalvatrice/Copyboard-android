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
import { snippetClipboardText, snippetSearchText } from './lib/noteFormat';
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
  return snippetSearchText(snippet).includes(lowered);
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

function normalizeGroupName(value: string) {
  return value.trim();
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
  const [newGroupName, setNewGroupName] = useState('');
  const [renameFromGroup, setRenameFromGroup] = useState('');
  const [renameToGroup, setRenameToGroup] = useState('');
  const [moveFromGroup, setMoveFromGroup] = useState('');
  const [moveToGroup, setMoveToGroup] = useState('');
  const [newFolderName, setNewFolderName] = useState('');

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

  useEffect(() => {
    if (!showSettings) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showSettings]);

  useEffect(() => {
    if (currentLabel !== 'main') {
      return;
    }

    const unlistenPromise = getCurrentWindow().onFocusChanged(({ payload: focused }) => {
      if (focused) {
        return;
      }

      void getCurrentWindow().isMinimized().then((minimized) => {
        if (minimized) {
          void invoke('show_floating_window');
        }
      });
    });

    return () => {
      void unlistenPromise.then((unlisten) => unlisten());
    };
  }, []);

  useEffect(() => {
    if (!data || data.groups.length === 0) {
      return;
    }

    setRenameFromGroup((current) => (data.groups.includes(current) ? current : data.groups[0]));
    setMoveFromGroup((current) => (data.groups.includes(current) ? current : data.groups[0]));
    setMoveToGroup((current) => {
      if (data.groups.includes(current)) {
        return current;
      }

      return data.groups[1] ?? data.groups[0];
    });
  }, [data]);

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
      await writeText(snippetClipboardText(snippet));
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

    const validCategory = data.groups.includes(editorSnippet.category)
      ? editorSnippet.category
      : (data.groups[0] ?? 'General');
    const sanitizedSnippet = {
      ...editorSnippet,
      category: validCategory,
    };

    const nextSnippets = upsertSnippet(data.snippets, sanitizedSnippet);
    const nextData = {
      ...data,
      snippets: sortSnippets(nextSnippets),
    };
    await persist(nextData);
    setStatus('Snippet saved.');
    const saved = nextData.snippets.find((snippet) => snippet.id === sanitizedSnippet.id);
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
    const draft = createEmptySnippet();
    setEditorSnippet({
      ...draft,
      category: data?.groups[0] ?? 'General',
    });
  };

  const handleCreateGroup = async () => {
    if (!data) {
      return;
    }

    const candidate = normalizeGroupName(newGroupName);
    if (!candidate) {
      setStatus('Group name is required.');
      return;
    }

    if (data.groups.some((group) => group.toLowerCase() === candidate.toLowerCase())) {
      setStatus('Group already exists.');
      return;
    }

    const nextGroups = [...data.groups, candidate].sort((left, right) => left.localeCompare(right));
    const nextData = {
      ...data,
      groups: nextGroups,
    };

    await persist(nextData);
    setEditorSnippet((current) => ({
      ...current,
      category: selectedId ? current.category : candidate,
    }));
    setNewGroupName('');
    setStatus(`Group "${candidate}" created.`);
  };

  const handleCreateFolder = async () => {
    if (!data) return;
    const candidate = normalizeGroupName(newFolderName);
    if (!candidate) {
      setStatus('Folder name is required.');
      return;
    }
    if (data.folders.some((folder) => folder.toLowerCase() === candidate.toLowerCase())) {
      setStatus('Folder already exists.');
      return;
    }
    await persist({ ...data, folders: [...data.folders, candidate].sort((a, b) => a.localeCompare(b)) });
    setNewFolderName('');
    setStatus(`Folder "${candidate}" created.`);
  };

  const handleAssignFolder = async (group: string, folder: string) => {
    if (!data) return;
    await persist({
      ...data,
      groupFolders: { ...data.groupFolders, [group]: folder || null },
    });
    setStatus(folder ? `Group "${group}" moved to "${folder}".` : `Group "${group}" removed from its folder.`);
  };

  const handleDeleteFolder = async (folder: string) => {
    if (!data) return;
    await persist({
      ...data,
      folders: data.folders.filter((item) => item !== folder),
      groupFolders: Object.fromEntries(Object.entries(data.groupFolders).map(([group, current]) => [
        group,
        current === folder ? null : current,
      ])),
    });
    setStatus(`Folder "${folder}" deleted; its groups are now unfiled.`);
  };

  const handleDeleteGroup = async (group: string) => {
    if (!data) {
      return;
    }

    if (data.groups.length <= 1) {
      setStatus('At least one group is required.');
      return;
    }

    if (data.snippets.some((snippet) => snippet.category === group)) {
      setStatus('Group is in use. Move snippets before deleting it.');
      return;
    }

    const nextGroups = data.groups.filter((item) => item !== group);
    const nextDefaultGroup = nextGroups[0] ?? 'General';
    const nextData = {
      ...data,
      groups: nextGroups,
    };

    await persist(nextData);
    if (category === group) {
      setCategory('all');
    }
    setEditorSnippet((current) => ({
      ...current,
      category: current.category === group ? nextDefaultGroup : current.category,
    }));
    setStatus(`Group "${group}" deleted.`);
  };

  const handleRenameGroup = async () => {
    if (!data) {
      return;
    }

    const source = renameFromGroup;
    const target = normalizeGroupName(renameToGroup);

    if (!source) {
      setStatus('Select a group to rename.');
      return;
    }

    if (!target) {
      setStatus('New group name is required.');
      return;
    }

    if (source.toLowerCase() === target.toLowerCase()) {
      setStatus('New group name must be different.');
      return;
    }

    if (data.groups.some((group) => group.toLowerCase() === target.toLowerCase())) {
      setStatus('Target group name already exists.');
      return;
    }

    const now = new Date().toISOString();
    const nextGroups = data.groups
      .map((group) => (group === source ? target : group))
      .sort((left, right) => left.localeCompare(right));
    const nextSnippets = data.snippets.map((snippet) => (
      snippet.category === source
        ? { ...snippet, category: target, updatedAt: now }
        : snippet
    ));

    const nextData = {
      ...data,
      groups: nextGroups,
      groupFolders: Object.fromEntries(Object.entries(data.groupFolders).map(([group, folder]) => [
        group === source ? target : group,
        folder,
      ])),
      snippets: sortSnippets(nextSnippets),
    };

    await persist(nextData);
    if (category === source) {
      setCategory(target);
    }
    setEditorSnippet((current) => ({
      ...current,
      category: current.category === source ? target : current.category,
    }));
    setRenameToGroup('');
    setStatus(`Group "${source}" renamed to "${target}".`);
  };

  const handleMoveGroupSnippets = async () => {
    if (!data) {
      return;
    }

    const source = moveFromGroup;
    const target = moveToGroup;

    if (!source || !target) {
      setStatus('Select source and target groups.');
      return;
    }

    if (source === target) {
      setStatus('Source and target groups must be different.');
      return;
    }

    const affected = data.snippets.filter((snippet) => snippet.category === source).length;
    if (affected === 0) {
      setStatus('No snippets to move for this group.');
      return;
    }

    const now = new Date().toISOString();
    const nextSnippets = data.snippets.map((snippet) => (
      snippet.category === source
        ? { ...snippet, category: target, updatedAt: now }
        : snippet
    ));

    const nextData = {
      ...data,
      snippets: sortSnippets(nextSnippets),
    };

    await persist(nextData);
    if (category === source) {
      setCategory(target);
    }
    setEditorSnippet((current) => ({
      ...current,
      category: current.category === source ? target : current.category,
    }));
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
        floatingCollapseMode,
        floatingAnimation,
        floatingIconScale,
        floatingIconOpacity,
        floatingHoverDelayMs,
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

  const categories = data.groups;
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
          {[...data.folders, null].map((folder) => {
            const folderGroups = categories.filter((group) => (data.groupFolders[group] ?? null) === folder);
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
        </div>
        <div className="group-manager">
          <p className="eyebrow">Group management</p>
          <div className="group-manager__create">
            <input value={newFolderName} onChange={(event) => setNewFolderName(event.target.value)} placeholder="New folder" />
            <button type="button" className="ghost-button" onClick={() => void handleCreateFolder()}>Add folder</button>
          </div>
          <div className="group-manager__list">
            {data.folders.map((folder) => (
              <div key={folder} className="group-manager__item">
                <strong>{folder}</strong>
                <button type="button" className="ghost-button" onClick={() => void handleDeleteFolder(folder)}>Remove</button>
              </div>
            ))}
          </div>
          <div className="group-manager__create">
            <input
              value={newGroupName}
              onChange={(event) => setNewGroupName(event.target.value)}
              placeholder="New group"
            />
            <button type="button" className="ghost-button" onClick={() => void handleCreateGroup()}>
              Add
            </button>
          </div>
          <div className="group-manager__list">
            {data.groups.map((group) => (
              <div key={group} className="group-manager__item">
                <span>{group}</span>
                <select value={data.groupFolders[group] ?? ''} onChange={(event) => void handleAssignFolder(group, event.target.value)}>
                  <option value="">No folder</option>
                  {data.folders.map((folder) => <option key={folder} value={folder}>{folder}</option>)}
                </select>
                <button type="button" className="ghost-button" onClick={() => void handleDeleteGroup(group)}>
                  Remove
                </button>
              </div>
            ))}
          </div>
            <div className="group-manager__rename">
              <select value={renameFromGroup} onChange={(event) => setRenameFromGroup(event.target.value)}>
                {data.groups.map((group) => (
                  <option key={group} value={group}>{group}</option>
                ))}
              </select>
              <input
                value={renameToGroup}
                onChange={(event) => setRenameToGroup(event.target.value)}
                placeholder="Rename to"
              />
              <button type="button" className="ghost-button" onClick={() => void handleRenameGroup()}>
                Rename
              </button>
            </div>
            <div className="group-manager__move">
              <select value={moveFromGroup} onChange={(event) => setMoveFromGroup(event.target.value)}>
                {data.groups.map((group) => (
                  <option key={group} value={group}>{group}</option>
                ))}
              </select>
              <span>to</span>
              <select value={moveToGroup} onChange={(event) => setMoveToGroup(event.target.value)}>
                {data.groups.map((group) => (
                  <option key={group} value={group}>{group}</option>
                ))}
              </select>
              <button type="button" className="ghost-button" onClick={() => void handleMoveGroupSnippets()}>
                Move snippets
              </button>
            </div>
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
            groups={data.groups}
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
            floatingCollapseMode: data.preferences.floatingCollapseMode,
            floatingAnimation: data.preferences.floatingAnimation,
            floatingIconScale: data.preferences.floatingIconScale,
            floatingIconOpacity: data.preferences.floatingIconOpacity,
            floatingHoverDelayMs: data.preferences.floatingHoverDelayMs,
          }}
          onSave={(settings) =>
            void handleSaveSettings(
              settings.syncSettings,
              settings.themeMode,
              settings.autostartEnabled,
              settings.floatingCollapseMode,
              settings.floatingAnimation,
              settings.floatingIconScale,
              settings.floatingIconOpacity,
              settings.floatingHoverDelayMs,
            )
          }
          onClose={() => setShowSettings(false)}
        />
      ) : null}
    </div>
  );
}
