import type { GitHubSyncSettings, Preferences, Snippet, StoredData } from '../types';

function timestamp() {
  return new Date().toISOString();
}

export function defaultSyncSettings(): GitHubSyncSettings {
  return {
    owner: '',
    repo: '',
    branch: 'main',
    path: 'copyboard-sync.json',
    token: '',
  };
}

export function defaultPreferences(): Preferences {
  return {
    hotkey: 'Ctrl+Alt+C',
    themeMode: 'system',
    floatingPinned: false,
    floatingCollapseMode: 'icon',
    floatingAnimation: 'fade',
    floatingIconScale: 1,
    floatingIconOpacity: 0.72,
    floatingHoverDelayMs: 650,
    floatingPosition: null,
    recentSnippetIds: [],
    usageCounts: {},
  };
}

export function defaultSnippets(): Snippet[] {
  const createdAt = timestamp();
  return [
    {
      id: crypto.randomUUID(),
      title: 'Etsy Tags - Aluminium Ring',
      text: 'adjustable band, aluminum jewelry, handmade metal, industrial style, minimalist, unisex jewelry, nickel free, brushed aluminum, everyday piece, made to order',
      mode: 'text',
      checklistItems: [],
      category: 'Etsy',
      favorite: true,
      updatedAt: createdAt,
    },
    {
      id: crypto.randomUUID(),
      title: 'Material DE',
      text: 'Nickelfreier, sehr leichter Aluminiumschmuck, von Hand gebogen, flachgehämmert und poliert.',
      mode: 'text',
      checklistItems: [],
      category: 'Schmuck',
      favorite: true,
      updatedAt: createdAt,
    },
    {
      id: crypto.randomUUID(),
      title: 'Material EN',
      text: 'Nickel-free, lightweight aluminum jewelry, bent, flattened and polished by hand.',
      mode: 'text',
      checklistItems: [],
      category: 'Jewelry',
      favorite: true,
      updatedAt: createdAt,
    },
    {
      id: crypto.randomUUID(),
      title: 'Customer Reply EN',
      text: 'Thank you so much for your message. I will check this and get back to you.',
      mode: 'text',
      checklistItems: [],
      category: 'Customer',
      favorite: true,
      updatedAt: createdAt,
    },
    {
      id: crypto.randomUUID(),
      title: 'AI Prompt - Jewelry Photo',
      text: 'product photo of handmade aluminum jewelry, industrial neutral background, soft directional light, natural shadows, realistic surface texture, no redesign',
      mode: 'text',
      checklistItems: [],
      category: 'Prompts',
      favorite: false,
      updatedAt: createdAt,
    },
  ];
}

export function defaultGroups(): string[] {
  return Array.from(new Set(defaultSnippets().map((snippet) => snippet.category))).sort((left, right) => left.localeCompare(right));
}

export function defaultStoredData(): StoredData {
  const now = timestamp();
  return {
    version: 1,
    updatedAt: now,
    snippets: defaultSnippets(),
    groups: defaultGroups(),
    folders: [],
    groupFolders: {},
    syncSettings: defaultSyncSettings(),
    preferences: defaultPreferences(),
  };
}

export function createEmptySnippet(): Snippet {
  return {
    id: crypto.randomUUID(),
    title: '',
    text: '',
    mode: 'text',
    checklistItems: [],
    category: 'General',
    favorite: false,
    updatedAt: timestamp(),
  };
}
