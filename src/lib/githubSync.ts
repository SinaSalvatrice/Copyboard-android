import type { ChecklistItem, GitHubSyncSettings, Snippet, SyncPayload } from '../types';

interface GitHubContentsResponse {
  sha: string;
  content: string;
}

interface RemoteFile {
  sha: string | null;
  payload: SyncPayload | null;
}

export class GitHubSyncError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
    this.name = 'GitHubSyncError';
  }
}

function normalizeSnippet(snippet: Partial<Snippet>): Snippet {
  const mode = snippet.mode === 'checklist' ? 'checklist' : 'text';
  const checklistItems = (snippet.checklistItems ?? [])
    .map((item) => {
      const text = item.text?.trim() ?? '';
      if (!text) {
        return null;
      }

      return {
        id: item.id ?? crypto.randomUUID(),
        text,
        done: Boolean(item.done),
      } satisfies ChecklistItem;
    })
    .filter((item): item is ChecklistItem => Boolean(item));

  return {
    id: snippet.id ?? crypto.randomUUID(),
    title: snippet.title?.trim() || 'Untitled',
    text: snippet.text ?? '',
    mode,
    checklistItems,
    category: snippet.category?.trim() || 'General',
    favorite: Boolean(snippet.favorite),
    updatedAt: snippet.updatedAt ?? new Date(0).toISOString(),
    deleted: Boolean(snippet.deleted),
  };
}

function normalizeNames(values: string[] | undefined) {
  return Array.from(new Set((values ?? []).map((value) => value.trim()).filter(Boolean)))
    .sort((left, right) => left.localeCompare(right));
}

function normalizeGroupFolders(
  value: Record<string, string | null> | undefined,
  groups: string[],
  folders: string[],
) {
  const validGroups = new Set(groups);
  const validFolders = new Set(folders);
  const result: Record<string, string | null> = {};

  for (const group of groups) {
    const folder = value?.[group] ?? null;
    result[group] = folder && validGroups.has(group) && validFolders.has(folder) ? folder : null;
  }

  return result;
}

function payloadMetadata(base: SyncPayload | null | undefined, snippets: Snippet[]) {
  const activeCategories = snippets
    .filter((snippet) => !snippet.deleted)
    .map((snippet) => snippet.category);
  const groups = normalizeNames([...(base?.groups ?? []), ...activeCategories]);
  if (!groups.some((group) => group.toLowerCase() === 'general')) {
    groups.push('General');
    groups.sort((left, right) => left.localeCompare(right));
  }
  const folders = normalizeNames(base?.folders);
  const groupFolders = normalizeGroupFolders(base?.groupFolders, groups, folders);
  return { groups, folders, groupFolders };
}

function toPayload(snippets: Snippet[], base?: SyncPayload | null): SyncPayload {
  const normalized = snippets.map((snippet) => normalizeSnippet(snippet));
  const metadata = payloadMetadata(base, normalized);
  return {
    version: 2,
    updatedAt: new Date().toISOString(),
    snippets: normalized,
    ...metadata,
  };
}

function parsePayload(raw: string): SyncPayload {
  let parsed: unknown;

  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new GitHubSyncError('invalid-json', 'Invalid JSON in remote copyboard-sync.json.');
  }

  if (Array.isArray(parsed)) {
    return toPayload(parsed.map((snippet) => normalizeSnippet(snippet as Partial<Snippet>)));
  }

  if (!parsed || typeof parsed !== 'object' || !Array.isArray((parsed as Partial<SyncPayload>).snippets)) {
    throw new GitHubSyncError('invalid-json', 'Remote JSON does not contain a valid snippets array.');
  }

  const rawPayload = parsed as Partial<SyncPayload>;
  const snippets = (rawPayload.snippets ?? []).map((snippet) => normalizeSnippet(snippet));
  const metadata = payloadMetadata({
    version: rawPayload.version ?? 1,
    updatedAt: rawPayload.updatedAt ?? new Date(0).toISOString(),
    snippets,
    groups: rawPayload.groups ?? [],
    folders: rawPayload.folders ?? [],
    groupFolders: rawPayload.groupFolders ?? {},
  }, snippets);

  return {
    version: typeof rawPayload.version === 'number' ? rawPayload.version : 1,
    updatedAt: rawPayload.updatedAt ?? new Date(0).toISOString(),
    snippets,
    ...metadata,
  };
}

function encodeBase64(value: string): string {
  return btoa(unescape(encodeURIComponent(value)));
}

function decodeBase64(value: string): string {
  return decodeURIComponent(escape(atob(value.replace(/\n/g, ''))));
}

function buildContentsUrl(settings: GitHubSyncSettings, withRef: boolean) {
  const path = settings.path
    .split('/')
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment))
    .join('/');
  const base = `https://api.github.com/repos/${encodeURIComponent(settings.owner)}/${encodeURIComponent(settings.repo)}/contents/${path}`;
  return withRef ? `${base}?ref=${encodeURIComponent(settings.branch)}` : base;
}

async function request(settings: GitHubSyncSettings, input: RequestInfo, init?: RequestInit) {
  const response = await fetch(input, {
    ...init,
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${settings.token}`,
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': '2022-11-28',
      ...(init?.headers ?? {}),
    },
  });

  if (response.ok) {
    return response;
  }

  if (response.status === 401) {
    throw new GitHubSyncError('token-missing', 'GitHub token is invalid or missing.');
  }

  if (response.status === 403) {
    const remaining = response.headers.get('x-ratelimit-remaining');
    if (remaining === '0') {
      throw new GitHubSyncError('rate-limit', 'GitHub API rate limit reached.');
    }
    throw new GitHubSyncError('permission-missing', 'GitHub denied access. Check fine-grained token permissions.');
  }

  if (response.status === 404) {
    throw new GitHubSyncError('not-found', 'Repository or sync file not found.');
  }

  if (response.status === 422) {
    throw new GitHubSyncError('invalid-request', 'GitHub rejected the sync request. Check branch and file path.');
  }

  throw new GitHubSyncError('network-error', `GitHub request failed with status ${response.status}.`);
}

async function getRemoteFile(settings: GitHubSyncSettings): Promise<RemoteFile> {
  try {
    const response = await request(settings, buildContentsUrl(settings, true), {
      method: 'GET',
    });
    const json = (await response.json()) as GitHubContentsResponse;
    return {
      sha: json.sha,
      payload: parsePayload(decodeBase64(json.content)),
    };
  } catch (error) {
    if (error instanceof GitHubSyncError && error.code === 'not-found') {
      return {
        sha: null,
        payload: null,
      };
    }
    throw error;
  }
}

function sortSnippets(snippets: Snippet[]) {
  return [...snippets].sort((left, right) => {
    if (left.favorite !== right.favorite) {
      return left.favorite ? -1 : 1;
    }
    return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  });
}

function mergeSnippetsWithTombstones(localSnippets: Snippet[], remoteSnippets: Snippet[]): Snippet[] {
  const merged = new Map<string, Snippet>();

  const mergeOne = (snippet: Snippet) => {
    const candidate = normalizeSnippet(snippet);
    const current = merged.get(candidate.id);
    if (!current) {
      merged.set(candidate.id, candidate);
      return;
    }

    const currentTime = Date.parse(current.updatedAt || new Date(0).toISOString());
    const nextTime = Date.parse(candidate.updatedAt || new Date(0).toISOString());
    if (Number.isNaN(nextTime) || currentTime > nextTime) {
      return;
    }

    merged.set(candidate.id, candidate);
  };

  localSnippets.forEach(mergeOne);
  remoteSnippets.forEach(mergeOne);
  return sortSnippets(Array.from(merged.values()));
}

export function mergeSnippets(localSnippets: Snippet[], remoteSnippets: Snippet[]): Snippet[] {
  return mergeSnippetsWithTombstones(localSnippets, remoteSnippets)
    .filter((snippet) => !snippet.deleted);
}

export async function pullFromGitHub(settings: GitHubSyncSettings, localSnippets: Snippet[]) {
  const remote = await getRemoteFile(settings);
  if (!remote.payload) {
    throw new GitHubSyncError('not-found', 'copyboard-sync.json does not exist yet. Push once to create it.');
  }

  return mergeSnippets(localSnippets, remote.payload.snippets);
}

export async function pushToGitHub(settings: GitHubSyncSettings, snippets: Snippet[]) {
  const remote = await getRemoteFile(settings);
  const remoteTombstones = remote.payload?.snippets.filter((snippet) => snippet.deleted) ?? [];
  const outgoing = mergeSnippetsWithTombstones(snippets, remoteTombstones);
  const payload = {
    message: 'Sync Copyboard snippets',
    content: encodeBase64(JSON.stringify(toPayload(outgoing, remote.payload), null, 2)),
    branch: settings.branch,
    ...(remote.sha ? { sha: remote.sha } : {}),
  };

  await request(settings, buildContentsUrl(settings, false), {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function syncWithGitHub(settings: GitHubSyncSettings, localSnippets: Snippet[]) {
  const remote = await getRemoteFile(settings);
  const mergedWithTombstones = mergeSnippetsWithTombstones(localSnippets, remote.payload?.snippets ?? []);
  const payload = {
    message: 'Sync Copyboard snippets',
    content: encodeBase64(JSON.stringify(toPayload(mergedWithTombstones, remote.payload), null, 2)),
    branch: settings.branch,
    ...(remote.sha ? { sha: remote.sha } : {}),
  };

  await request(settings, buildContentsUrl(settings, false), {
    method: 'PUT',
    body: JSON.stringify(payload),
  });

  return mergedWithTombstones.filter((snippet) => !snippet.deleted);
}
