import { LazyStore } from '@tauri-apps/plugin-store';
import { defaultPreferences, defaultStoredData, defaultSyncSettings } from './defaults';
import type { ChecklistItem, GitHubSyncSettings, Preferences, Snippet, StoredData } from '../types';

const store = new LazyStore('copyboard.store.json');
const STORE_KEY = 'copyboard';

function normalizeChecklistItem(item: Partial<ChecklistItem>): ChecklistItem | null {
  const text = item.text?.trim() ?? '';
  if (!text) {
    return null;
  }

  return {
    id: item.id ?? crypto.randomUUID(),
    text,
    done: Boolean(item.done),
  };
}

function normalizeSnippet(snippet: Partial<Snippet>): Snippet {
  const mode = snippet.mode === 'checklist' ? 'checklist' : 'text';
  const checklistItems = (snippet.checklistItems ?? [])
    .map((item) => normalizeChecklistItem(item))
    .filter((item): item is ChecklistItem => Boolean(item));

  return {
    id: snippet.id ?? crypto.randomUUID(),
    title: snippet.title?.trim() || 'Untitled',
    text: snippet.text ?? '',
    mode,
    checklistItems,
    category: snippet.category?.trim() || 'General',
    favorite: Boolean(snippet.favorite),
    updatedAt: snippet.updatedAt ?? new Date().toISOString(),
    deleted: snippet.deleted,
  };
}

function normalizePreferences(preferences?: Partial<Preferences>): Preferences {
  const themeMode = preferences?.themeMode;
  const floatingCollapseMode = preferences?.floatingCollapseMode;
  const floatingAnimation = preferences?.floatingAnimation;
  const floatingIconScale = preferences?.floatingIconScale;
  const floatingIconOpacity = preferences?.floatingIconOpacity;
  const floatingHoverDelayMs = preferences?.floatingHoverDelayMs;

  return {
    ...defaultPreferences(),
    ...preferences,
    themeMode: themeMode === 'light' || themeMode === 'dark' || themeMode === 'system' ? themeMode : 'system',
    floatingCollapseMode: floatingCollapseMode === 'stay-open' || floatingCollapseMode === 'icon' ? floatingCollapseMode : 'icon',
    floatingAnimation: floatingAnimation === 'vertical' || floatingAnimation === 'horizontal' || floatingAnimation === 'fade'
      ? floatingAnimation
      : 'fade',
    floatingIconScale: typeof floatingIconScale === 'number' ? Math.min(1.8, Math.max(0.7, floatingIconScale)) : 1,
    floatingIconOpacity: typeof floatingIconOpacity === 'number' ? Math.min(1, Math.max(0.2, floatingIconOpacity)) : 0.72,
    floatingHoverDelayMs: typeof floatingHoverDelayMs === 'number' ? Math.min(4000, Math.max(0, Math.round(floatingHoverDelayMs))) : 650,
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

function normalizeGroups(groups: string[] | undefined, snippets: Snippet[]): string[] {
  const fromSnippets = snippets.map((snippet) => snippet.category.trim()).filter(Boolean);
  const merged = [...(groups ?? []), ...fromSnippets]
    .map((group) => group.trim())
    .filter(Boolean)
    .map((group) => group || 'General');

  const unique = Array.from(new Set(merged));
  if (unique.length === 0) {
    return ['General'];
  }

  return unique.sort((left, right) => left.localeCompare(right));
}

function normalizeFolders(folders: string[] | undefined): string[] {
  return Array.from(new Set((folders ?? []).map((folder) => folder.trim()).filter(Boolean)))
    .sort((left, right) => left.localeCompare(right));
}

export function normalizeStoredData(data?: Partial<StoredData>): StoredData {
  const defaults = defaultStoredData();
  const snippets = (data?.snippets?.length ? data.snippets : defaults.snippets)
    .map(normalizeSnippet)
    .filter((snippet) => !snippet.deleted);
  const groups = normalizeGroups(data?.groups, snippets);
  const folders = normalizeFolders(data?.folders);
  const groupFolders = Object.fromEntries(groups.map((group) => {
    const folder = data?.groupFolders?.[group]?.trim() || null;
    return [group, folder && folders.includes(folder) ? folder : null];
  }));

  return {
    version: data?.version ?? 1,
    updatedAt: data?.updatedAt ?? defaults.updatedAt,
    snippets,
    groups,
    folders,
    groupFolders,
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
