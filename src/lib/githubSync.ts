import type { ChecklistItem, GitHubSyncSettings, Snippet, SyncPayload } from '../types';

interface GitHubContentsResponse {
  sha: string;
  content: string;
}

interface RemoteFile {
  sha: string | null;
  payload: SyncPayload | null;
}

export interface SyncState {
  snippets: Snippet[];
  groups: string[];
  folders: string[];
  groupFolders: Record<string, string | null>;
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
  const validFolders = new Set(folders);
  const result: Record<string, string | null> = {};

  for (const group of groups) {
    const folder = value?.[group] ?? null;
    result[group] = folder && validFolders.has(folder) ? folder : null;
  }

  return result;
}

function activeCategories(snippets: Snippet[]) {
  return snippets
    .filter((snippet) => !snippet.deleted)
    .map((snippet) => snippet.category);
}

function sanitizeMetadata(state: Pick<SyncState, 'groups' | 'folders' | 'groupFolders'>, snippets: Snippet[]) {
  const groups = normalizeNames([...state.groups, ...activeCategories(snippets)]);
  if (!groups.some((group) => group.toLowerCase() === 'general')) {
    groups.push('General');
    groups.sort((left, right) => left.localeCompare(right));
  }
  const folders = normalizeNames(state.folders);
  const groupFolders = normalizeGroupFolders(state.groupFolders, groups, folders);
  return { groups, folders, groupFolders };
}

function mergeMetadata(local: SyncState, remote: SyncPayload | null | undefined, preferLocal: boolean) {
  const remoteGroups = remote?.groups ?? [];
  const remoteFolders = remote?.folders ?? [];
  const groups = normalizeNames([
    ...local.groups,
    ...remoteGroups,
    ...activeCategories(local.snippets),
    ...activeCategories(remote?.snippets ?? []),
  ]);
  if (!groups.some((group) => group.toLowerCase() === 'general')) {
    groups.push('General');
    groups.sort((left, right) => left.localeCompare(right));
  }

  const folders = normalizeNames([...local.folders, ...remoteFolders]);
  const combinedAssignments = preferLocal
    ? { ...(remote?.groupFolders ?? {}), ...local.groupFolders }
    : { ...local.groupFolders, ...(remote?.groupFolders ?? {}) };
  const groupFolders = normalizeGroupFolders(combinedAssignments, groups, folders);
  return { groups, folders, groupFolders };
}

function toPayload(state: SyncState): SyncPayload {
  const snippets = state.snippets.map((snippet) => normalizeSnippet(snippet));
  const metadata = sanitizeMetadata(state, snippets);
  return {
    version: 2,
    updatedAt: new Date().toISOString(),
    snippets,
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
    const snippets = parsed.map((snippet) => normalizeSnippet(snippet as Partial<Snippet>));
    return toPayload({ snippets, groups: [], folders: [], groupFolders: {} });
  }

  if (!parsed || typeof parsed !== 'object' || !Array.isArray((parsed as Partial<SyncPayload>).snippets)) {
    throw new GitHubSyncError('invalid-json', 'Remote JSON does not contain a valid snippets array.');
  }

  const rawPayload = parsed as Partial<SyncPayload>;
  const snippets = (rawPayload.snippets ?? []).map((snippet) => normalizeSnippet(snippet));
  const metadata = sanitizeMetadata({
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
  return mergeSnippetsWithTombstones(localSnippets, remoteSnippets);
}

function mergeSyncState(local: SyncState, remote: SyncPayload | null | undefined, preferLocalMetadata: boolean): SyncState {
  const snippets = mergeSnippetsWithTombstones(local.snippets, remote?.snippets ?? []);
  const metadata = mergeMetadata(local, remote, preferLocalMetadata);
  return {
    snippets,
    ...metadata,
  };
}

async function writeRemote(settings: GitHubSyncSettings, remote: RemoteFile, state: SyncState) {
  const payload = {
    message: 'Sync Copyboard data',
    content: encodeBase64(JSON.stringify(toPayload(state), null, 2)),
    branch: settings.branch,
    ...(remote.sha ? { sha: remote.sha } : {}),
  };

  await request(settings, buildContentsUrl(settings, false), {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function pullFromGitHub(settings: GitHubSyncSettings, local: SyncState): Promise<SyncState> {
  const remote = await getRemoteFile(settings);
  if (!remote.payload) {
    throw new GitHubSyncError('not-found', 'copyboard-sync.json does not exist yet. Push once to create it.');
  }

  return mergeSyncState(local, remote.payload, false);
}

export async function pushToGitHub(settings: GitHubSyncSettings, local: SyncState) {
  const remote = await getRemoteFile(settings);
  const outgoing = mergeSyncState(local, remote.payload, true);
  await writeRemote(settings, remote, outgoing);
}

export async function syncWithGitHub(settings: GitHubSyncSettings, local: SyncState): Promise<SyncState> {
  const remote = await getRemoteFile(settings);
  const merged = mergeSyncState(local, remote.payload, true);
  await writeRemote(settings, remote, merged);
  return merged;
}
