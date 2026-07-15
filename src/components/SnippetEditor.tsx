import type { Snippet } from '../types';

interface SnippetEditorProps {
  snippet: Snippet;
  groups: string[];
  onChange: (snippet: Snippet) => void;
  onSave: () => void;
  onDelete: () => void;
  onNew: () => void;
}

export function SnippetEditor({ snippet, groups, onChange, onSave, onDelete, onNew }: SnippetEditorProps) {
  const addChecklistItem = () => {
    onChange({
      ...snippet,
      checklistItems: [
        ...snippet.checklistItems,
        { id: crypto.randomUUID(), text: '', done: false },
      ],
    });
  };

  const updateChecklistItem = (id: string, patch: { text?: string; done?: boolean }) => {
    onChange({
      ...snippet,
      checklistItems: snippet.checklistItems.map((item) => (
        item.id === id
          ? { ...item, ...patch }
          : item
      )),
    });
  };

  const removeChecklistItem = (id: string) => {
    onChange({
      ...snippet,
      checklistItems: snippet.checklistItems.filter((item) => item.id !== id),
    });
  };

  return (
    <section className="editor-panel">
      <div className="editor-panel__header">
        <div>
          <p className="eyebrow">Editor</p>
          <h2>{snippet.title.trim() || 'New snippet'}</h2>
        </div>
        <button type="button" className="ghost-button" onClick={onNew}>
          New snippet
        </button>
      </div>

      <label>
        <span>Title</span>
        <input
          value={snippet.title}
          onChange={(event) => onChange({ ...snippet, title: event.target.value })}
        />
      </label>

      <label>
        <span>Group</span>
        <select
          value={snippet.category}
          onChange={(event) => onChange({ ...snippet, category: event.target.value })}
        >
          {groups.map((group) => (
            <option key={group} value={group}>
              {group}
            </option>
          ))}
        </select>
      </label>

      <label>
        <span>Note type</span>
        <select
          value={snippet.mode}
          onChange={(event) => onChange({
            ...snippet,
            mode: event.target.value === 'checklist' ? 'checklist' : 'text',
          })}
        >
          <option value="text">Text</option>
          <option value="checklist">Checklist</option>
        </select>
      </label>

      {snippet.mode === 'text' ? (
        <label>
          <span>Text</span>
          <textarea
            value={snippet.text}
            onChange={(event) => onChange({ ...snippet, text: event.target.value })}
            rows={12}
          />
        </label>
      ) : (
        <div className="checklist-editor">
          <div className="checklist-editor__header">
            <span>Checklist items</span>
            <button type="button" className="ghost-button" onClick={addChecklistItem}>Add item</button>
          </div>
          <div className="checklist-editor__list">
            {snippet.checklistItems.map((item) => (
              <div key={item.id} className="checklist-editor__row">
                <input
                  type="checkbox"
                  checked={item.done}
                  onChange={(event) => updateChecklistItem(item.id, { done: event.target.checked })}
                />
                <input
                  value={item.text}
                  placeholder="Checklist item"
                  onChange={(event) => updateChecklistItem(item.id, { text: event.target.value })}
                />
                <button type="button" className="ghost-button" onClick={() => removeChecklistItem(item.id)}>
                  Remove
                </button>
              </div>
            ))}
            {snippet.checklistItems.length === 0 ? (
              <div className="empty-state">No checklist items yet.</div>
            ) : null}
          </div>
        </div>
      )}

      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={snippet.favorite}
          onChange={(event) => onChange({ ...snippet, favorite: event.target.checked })}
        />
        <span>Favorite</span>
      </label>

      <div className="editor-panel__actions">
        <button type="button" className="primary-button" onClick={onSave}>
          Save
        </button>
        <button
          type="button"
          className="ghost-button"
          onClick={() => onChange({ ...snippet, favorite: !snippet.favorite })}
        >
          {snippet.favorite ? 'Unfavorite' : 'Favorite'}
        </button>
        <button type="button" className="danger-button" onClick={onDelete}>
          Delete
        </button>
      </div>
    </section>
  );
}