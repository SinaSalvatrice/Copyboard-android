import { useState } from 'react';
import type { GitHubSyncSettings } from '../types';

interface AppSettingsValue {
  syncSettings: GitHubSyncSettings;
  themeMode: 'system' | 'light' | 'dark';
  autostartEnabled: boolean;
}

interface AppSettingsModalProps {
  initialValue: AppSettingsValue;
  onSave: (settings: AppSettingsValue) => void;
  onClose: () => void;
}

export function AppSettingsModal({ initialValue, onSave, onClose }: AppSettingsModalProps) {
  const [value, setValue] = useState(initialValue);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(event) => event.stopPropagation()}>
        <div className="modal-panel__header">
          <div>
            <p className="eyebrow">Settings</p>
            <h2>Copyboard preferences</h2>
          </div>
          <button type="button" className="ghost-button" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="settings-grid">
          <section className="settings-section">
            <h3>General</h3>
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={value.autostartEnabled}
                onChange={(event) => setValue({ ...value, autostartEnabled: event.target.checked })}
              />
              <span>Autostart Copyboard when Windows starts</span>
            </label>
            <label>
              <span>Theme</span>
              <select
                value={value.themeMode}
                onChange={(event) =>
                  setValue({
                    ...value,
                    themeMode: event.target.value as 'system' | 'light' | 'dark',
                  })
                }
              >
                <option value="system">Follow system</option>
                <option value="light">Light</option>
                <option value="dark">Dark</option>
              </select>
            </label>
          </section>

          <section className="settings-section">
            <h3>Git sync</h3>
            <p className="modal-panel__copy">
              Store a fine-grained token locally for now. The token is never hardcoded and can later be moved behind secure storage.
            </p>
            <label>
              <span>GitHub Owner</span>
              <input
                value={value.syncSettings.owner}
                onChange={(event) =>
                  setValue({
                    ...value,
                    syncSettings: { ...value.syncSettings, owner: event.target.value },
                  })
                }
              />
            </label>
            <label>
              <span>Repository</span>
              <input
                value={value.syncSettings.repo}
                onChange={(event) =>
                  setValue({
                    ...value,
                    syncSettings: { ...value.syncSettings, repo: event.target.value },
                  })
                }
              />
            </label>
            <label>
              <span>Branch</span>
              <input
                value={value.syncSettings.branch}
                onChange={(event) =>
                  setValue({
                    ...value,
                    syncSettings: { ...value.syncSettings, branch: event.target.value || 'main' },
                  })
                }
              />
            </label>
            <label>
              <span>Path</span>
              <input
                value={value.syncSettings.path}
                onChange={(event) =>
                  setValue({
                    ...value,
                    syncSettings: { ...value.syncSettings, path: event.target.value || 'copyboard-sync.json' },
                  })
                }
              />
            </label>
            <label>
              <span>Fine-grained token</span>
              <input
                type="password"
                value={value.syncSettings.token}
                onChange={(event) =>
                  setValue({
                    ...value,
                    syncSettings: { ...value.syncSettings, token: event.target.value },
                  })
                }
              />
            </label>
          </section>

          <section className="settings-section settings-suggestions">
            <h3>Useful suggestions</h3>
            <ul>
              <li>Keep your sync path as copyboard-sync.json to simplify cross-device sync.</li>
              <li>Use categories per workflow so floating search finds snippets faster.</li>
              <li>Pin floating mode only when doing repetitive copy tasks.</li>
              <li>Run pull before big edits and sync after finishing a session.</li>
            </ul>
          </section>
        </div>
        <div className="modal-panel__actions">
          <button type="button" className="primary-button" onClick={() => onSave(value)}>
            Save settings
          </button>
        </div>
      </div>
    </div>
  );
}