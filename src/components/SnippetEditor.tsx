import type { Snippet } from '../types';

interface SnippetEditorProps {
  snippet: Snippet;
  onChange: (snippet: Snippet) => void;
  onSave: () => void;
  onDelete: () => void;
  onNew: () => void;
}

export function SnippetEditor({ snippet, onChange, onSave, onDelete, onNew }: SnippetEditorProps) {
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
        <span>Category</span>
        <input
          value={snippet.category}
          onChange={(event) => onChange({ ...snippet, category: event.target.value })}
        />
      </label>

      <label>
        <span>Text</span>
        <textarea
          value={snippet.text}
          onChange={(event) => onChange({ ...snippet, text: event.target.value })}
          rows={12}
        />
      </label>

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