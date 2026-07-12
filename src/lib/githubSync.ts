import type { GitHubSyncSettings, Snippet, SyncPayload } from '../types';

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

function toPayload(snippets: Snippet[]): SyncPayload {
  return {
    version: 1,
    updatedAt: new Date().toISOString(),
    snippets: snippets.map((snippet) => ({
      ...normalizeSnippet(snippet),
    })),
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
    return {
      version: 1,
      updatedAt: new Date().toISOString(),
      snippets: parsed.map((snippet) => normalizeSnippet(snippet as Partial<Snippet>)),
    };
  }

  if (!parsed || typeof parsed !== 'object' || !Array.isArray((parsed as SyncPayload).snippets)) {
    throw new GitHubSyncError('invalid-json', 'Remote JSON does not contain a valid snippets array.');
  }

  const payload = parsed as SyncPayload;
  return {
    version: typeof payload.version === 'number' ? payload.version : 1,
    updatedAt: payload.updatedAt ?? new Date().toISOString(),
    snippets: payload.snippets.map((snippet) => normalizeSnippet(snippet)),
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

export function mergeSnippets(localSnippets: Snippet[], remoteSnippets: Snippet[]): Snippet[] {
  const merged = new Map<string, Snippet>();

  const mergeOne = (snippet: Snippet) => {
    const current = merged.get(snippet.id);
    if (!current) {
      merged.set(snippet.id, normalizeSnippet(snippet));
      return;
    }

    const currentTime = Date.parse(current.updatedAt || '1970-01-01T00:00:00.000Z');
    const nextTime = Date.parse(snippet.updatedAt || '1970-01-01T00:00:00.000Z');
    if (Number.isNaN(nextTime) || currentTime >= nextTime) {
      return;
    }

    merged.set(snippet.id, normalizeSnippet(snippet));
  };

  localSnippets.forEach(mergeOne);
  remoteSnippets.forEach(mergeOne);

  return Array.from(merged.values())
    .filter((snippet) => !snippet.deleted)
    .sort((left, right) => {
      if (left.favorite !== right.favorite) {
        return left.favorite ? -1 : 1;
      }

      return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
    });
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
  const payload = {
    message: 'Sync Copyboard snippets',
    content: encodeBase64(JSON.stringify(toPayload(snippets), null, 2)),
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
  const merged = mergeSnippets(localSnippets, remote.payload?.snippets ?? []);
  const payload = {
    message: 'Sync Copyboard snippets',
    content: encodeBase64(JSON.stringify(toPayload(merged), null, 2)),
    branch: settings.branch,
    ...(remote.sha ? { sha: remote.sha } : {}),
  };

  await request(settings, buildContentsUrl(settings, false), {
    method: 'PUT',
    body: JSON.stringify(payload),
  });

  return merged;
}