import { LazyStore } from '@tauri-apps/plugin-store';
import { defaultPreferences, defaultStoredData, defaultSyncSettings } from './defaults';
import type { GitHubSyncSettings, Preferences, Snippet, StoredData } from '../types';

const store = new LazyStore('copyboard.store.json');
const STORE_KEY = 'copyboard';

function normalizeSnippet(snippet: Partial<Snippet>): Snippet {
  return {
    id: snippet.id ?? crypto.randomUUID(),
    title: snippet.title?.trim() || 'Untitled',
    text: snippet.text ?? '',
    category: snippet.category?.trim() || 'General',
    favorite: Boolean(snippet.favorite),
    updatedAt: snippet.updatedAt ?? new Date().toISOString(),
    deleted: snippet.deleted,
  };
}

function normalizePreferences(preferences?: Partial<Preferences>): Preferences {
  return {
    ...defaultPreferences(),
    ...preferences,
    recentSnippetIds: preferences?.recentSnippetIds ?? [],
    usageCounts: preferences?.usageCounts ?? {},
    floatingPosition: preferences?.floatingPosition ?? null,
  };
}

function normalizeSyncSettings(settings?: Partial<GitHubSyncSettings>): GitHubSyncSettings {
  return {
    ...defaultSyncSettings(),
    ...settings,
  };
}

export function normalizeStoredData(data?: Partial<StoredData>): StoredData {
  const defaults = defaultStoredData();
  const snippets = (data?.snippets?.length ? data.snippets : defaults.snippets)
    .map(normalizeSnippet)
    .filter((snippet) => !snippet.deleted);

  return {
    version: data?.version ?? 1,
    updatedAt: data?.updatedAt ?? defaults.updatedAt,
    snippets,
    syncSettings: normalizeSyncSettings(data?.syncSettings),
    preferences: normalizePreferences(data?.preferences),
  };
}

export async function loadStoredData(): Promise<StoredData> {
  const raw = await store.get<StoredData | null>(STORE_KEY);
  if (!raw) {
    const defaults = defaultStoredData();
    await saveStoredData(defaults);
    return defaults;
  }

  const normalized = normalizeStoredData(raw);
  await saveStoredData(normalized);
  return normalized;
}

export async function saveStoredData(data: StoredData): Promise<void> {
  const normalized = normalizeStoredData({
    ...data,
    updatedAt: new Date().toISOString(),
  });

  await store.set(STORE_KEY, normalized);
  await store.save();
}

export function recordSnippetUsage(data: StoredData, snippetId: string): StoredData {
  const recent = [snippetId, ...data.preferences.recentSnippetIds.filter((id) => id !== snippetId)].slice(0, 12);
  const usageCounts = {
    ...data.preferences.usageCounts,
    [snippetId]: (data.preferences.usageCounts[snippetId] ?? 0) + 1,
  };

  return {
    ...data,
    preferences: {
      ...data.preferences,
      recentSnippetIds: recent,
      usageCounts,
    },
  };
}

export function upsertSnippet(snippets: Snippet[], draft: Snippet): Snippet[] {
  const next = normalizeSnippet({
    ...draft,
    updatedAt: new Date().toISOString(),
  });
  const index = snippets.findIndex((snippet) => snippet.id === next.id);

  if (index >= 0) {
    return snippets.map((snippet) => (snippet.id === next.id ? next : snippet));
  }

  return [next, ...snippets];
}

export function removeSnippet(snippets: Snippet[], snippetId: string): Snippet[] {
  return snippets.filter((snippet) => snippet.id !== snippetId);
}

export function isSyncConfigured(settings: GitHubSyncSettings): boolean {
  return Boolean(settings.owner && settings.repo && settings.branch && settings.path && settings.token);
}