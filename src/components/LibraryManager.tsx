import { useEffect, useState } from 'react';

interface LibraryManagerProps {
  groups: string[];
  folders: string[];
  groupFolders: Record<string, string | null>;
  onClose: () => void;
  onCreateFolder: (name: string) => void | Promise<void>;
  onDeleteFolder: (folder: string) => void | Promise<void>;
  onCreateGroup: (name: string) => void | Promise<void>;
  onAssignFolder: (group: string, folder: string) => void | Promise<void>;
  onDeleteGroup: (group: string) => void | Promise<void>;
  onRenameGroup: (source: string, target: string) => void | Promise<void>;
  onMoveGroupSnippets: (source: string, target: string) => void | Promise<void>;
}

export function LibraryManager({
  groups,
  folders,
  groupFolders,
  onClose,
  onCreateFolder,
  onDeleteFolder,
  onCreateGroup,
  onAssignFolder,
  onDeleteGroup,
  onRenameGroup,
  onMoveGroupSnippets,
}: LibraryManagerProps) {
  const [newFolder, setNewFolder] = useState('');
  const [newGroup, setNewGroup] = useState('');
  const [renameFrom, setRenameFrom] = useState(groups[0] ?? '');
  const [renameTo, setRenameTo] = useState('');
  const [moveFrom, setMoveFrom] = useState(groups[0] ?? '');
  const [moveTo, setMoveTo] = useState(groups[1] ?? groups[0] ?? '');

  useEffect(() => {
    setRenameFrom((current) => (groups.includes(current) ? current : (groups[0] ?? '')));
    setMoveFrom((current) => (groups.includes(current) ? current : (groups[0] ?? '')));
    setMoveTo((current) => (groups.includes(current) ? current : (groups[1] ?? groups[0] ?? '')));
  }, [groups]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section className="modal-panel library-manager" onClick={(event) => event.stopPropagation()}>
        <header className="modal-panel__header">
          <div>
            <p className="eyebrow">Library</p>
            <h2>Manage collections</h2>
          </div>
          <button type="button" className="ghost-button" onClick={onClose}>Close</button>
        </header>

        <div className="library-manager__grid">
          <section className="library-manager__section">
            <h3>Folders</h3>
            <div className="library-manager__create">
              <input value={newFolder} onChange={(event) => setNewFolder(event.target.value)} placeholder="New folder" />
              <button
                type="button"
                className="primary-button"
                onClick={() => {
                  const value = newFolder.trim();
                  if (!value) return;
                  void Promise.resolve(onCreateFolder(value)).then(() => setNewFolder(''));
                }}
              >
                Add
              </button>
            </div>
            <div className="library-manager__items">
              {folders.length === 0 ? <p className="library-manager__empty">No folders yet.</p> : null}
              {folders.map((folder) => (
                <div className="library-manager__row" key={folder}>
                  <strong>{folder}</strong>
                  <button type="button" className="ghost-button" onClick={() => void onDeleteFolder(folder)}>Remove</button>
                </div>
              ))}
            </div>
          </section>

          <section className="library-manager__section">
            <h3>Groups</h3>
            <div className="library-manager__create">
              <input value={newGroup} onChange={(event) => setNewGroup(event.target.value)} placeholder="New group" />
              <button
                type="button"
                className="primary-button"
                onClick={() => {
                  const value = newGroup.trim();
                  if (!value) return;
                  void Promise.resolve(onCreateGroup(value)).then(() => setNewGroup(''));
                }}
              >
                Add
              </button>
            </div>
            <div className="library-manager__items">
              {groups.map((group) => (
                <div className="library-manager__row library-manager__row--group" key={group}>
                  <strong>{group}</strong>
                  <select value={groupFolders[group] ?? ''} onChange={(event) => void onAssignFolder(group, event.target.value)}>
                    <option value="">No folder</option>
                    {folders.map((folder) => <option key={folder} value={folder}>{folder}</option>)}
                  </select>
                  <button type="button" className="ghost-button" onClick={() => void onDeleteGroup(group)}>Remove</button>
                </div>
              ))}
            </div>
          </section>
        </div>

        <div className="library-manager__tools">
          <section className="library-manager__section">
            <h3>Rename group</h3>
            <div className="library-manager__tool-row">
              <select value={renameFrom} onChange={(event) => setRenameFrom(event.target.value)}>
                {groups.map((group) => <option key={group} value={group}>{group}</option>)}
              </select>
              <input value={renameTo} onChange={(event) => setRenameTo(event.target.value)} placeholder="New name" />
              <button
                type="button"
                className="ghost-button"
                onClick={() => {
                  const target = renameTo.trim();
                  if (!renameFrom || !target) return;
                  void Promise.resolve(onRenameGroup(renameFrom, target)).then(() => setRenameTo(''));
                }}
              >
                Rename
              </button>
            </div>
          </section>

          <section className="library-manager__section">
            <h3>Move all snippets</h3>
            <div className="library-manager__tool-row">
              <select value={moveFrom} onChange={(event) => setMoveFrom(event.target.value)}>
                {groups.map((group) => <option key={group} value={group}>{group}</option>)}
              </select>
              <span>to</span>
              <select value={moveTo} onChange={(event) => setMoveTo(event.target.value)}>
                {groups.map((group) => <option key={group} value={group}>{group}</option>)}
              </select>
              <button type="button" className="ghost-button" onClick={() => void onMoveGroupSnippets(moveFrom, moveTo)}>Move</button>
            </div>
          </section>
        </div>
      </section>
    </div>
  );
}
