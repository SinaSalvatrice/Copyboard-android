import { useState } from 'react';
import type { GitHubSyncSettings } from '../types';

interface GitHubSyncSettingsProps {
  initialValue: GitHubSyncSettings;
  onSave: (settings: GitHubSyncSettings) => void;
  onClose: () => void;
}

export function GitHubSyncSettingsModal({ initialValue, onSave, onClose }: GitHubSyncSettingsProps) {
  const [value, setValue] = useState(initialValue);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(event) => event.stopPropagation()}>
        <div className="modal-panel__header">
          <div>
            <p className="eyebrow">GitHub Sync</p>
            <h2>Repository settings</h2>
          </div>
          <button type="button" className="ghost-button" onClick={onClose}>
            Close
          </button>
        </div>
        <p className="modal-panel__copy">
          Store a fine-grained token locally for now. The token is never hardcoded and can later be moved behind secure storage.
        </p>
        <label>
          <span>GitHub Owner</span>
          <input value={value.owner} onChange={(event) => setValue({ ...value, owner: event.target.value })} />
        </label>
        <label>
          <span>Repository</span>
          <input value={value.repo} onChange={(event) => setValue({ ...value, repo: event.target.value })} />
        </label>
        <label>
          <span>Branch</span>
          <input value={value.branch} onChange={(event) => setValue({ ...value, branch: event.target.value || 'main' })} />
        </label>
        <label>
          <span>Path</span>
          <input value={value.path} onChange={(event) => setValue({ ...value, path: event.target.value || 'copyboard-sync.json' })} />
        </label>
        <label>
          <span>Fine-grained token</span>
          <input type="password" value={value.token} onChange={(event) => setValue({ ...value, token: event.target.value })} />
        </label>
        <div className="modal-panel__actions">
          <button type="button" className="primary-button" onClick={() => onSave(value)}>
            Save settings
          </button>
        </div>
      </div>
    </div>
  );
}