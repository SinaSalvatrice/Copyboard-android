export interface Snippet {
  id: string;
  title: string;
  text: string;
  category: string;
  favorite: boolean;
  updatedAt: string;
  deleted?: boolean;
}

export interface GitHubSyncSettings {
  owner: string;
  repo: string;
  branch: string;
  path: string;
  token: string;
}

export interface FloatingPosition {
  x: number;
  y: number;
}

export interface Preferences {
  hotkey: string;
  themeMode: 'system' | 'light' | 'dark';
  floatingPinned: boolean;
  floatingCollapseMode: 'icon' | 'stay-open';
  floatingAnimation: 'fade' | 'vertical' | 'horizontal';
  floatingIconScale: number;
  floatingIconOpacity: number;
  floatingHoverDelayMs: number;
  floatingPosition: FloatingPosition | null;
  recentSnippetIds: string[];
  usageCounts: Record<string, number>;
}

export interface StoredData {
  version: number;
  updatedAt: string;
  snippets: Snippet[];
  groups: string[];
  syncSettings: GitHubSyncSettings;
  preferences: Preferences;
}

export interface SyncPayload {
  version: number;
  updatedAt: string;
  snippets: Snippet[];
}

export type CategoryFilter = 'all' | 'favorites' | string;