import type { Snippet } from '../types';
import { snippetPreviewText } from '../lib/noteFormat';

interface SnippetListProps {
  snippets: Snippet[];
  selectedId: string | null;
  copiedId: string | null;
  onCopy: (snippet: Snippet) => void | Promise<void>;
  onSelect: (snippet: Snippet) => void;
}

export function SnippetList({ snippets, selectedId, copiedId, onCopy, onSelect }: SnippetListProps) {
  if (snippets.length === 0) {
    return <div className="empty-state">No snippets match the current filter.</div>;
  }

  return (
    <div className="snippet-list">
      {snippets.map((snippet) => (
        <button
          key={snippet.id}
          type="button"
          className={`snippet-card${selectedId === snippet.id ? ' snippet-card--selected' : ''}`}
          onClick={() => {
            onSelect(snippet);
            void onCopy(snippet);
          }}
        >
          <div className="snippet-card__header">
            <strong>{snippet.title}</strong>
            <span className="snippet-card__meta">
              {snippet.favorite ? '★ ' : ''}
              {snippet.mode === 'checklist' ? `${snippet.category} · checklist` : snippet.category}
            </span>
          </div>
          <p>{snippetPreviewText(snippet)}</p>
          <div className="snippet-card__footer">
            <span>{new Date(snippet.updatedAt).toLocaleString()}</span>
            <span className={`snippet-card__copied${copiedId === snippet.id ? ' is-visible' : ''}`}>Copied</span>
          </div>
        </button>
      ))}
    </div>
  );
}