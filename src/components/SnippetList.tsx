import type { KeyboardEvent, MouseEvent } from 'react';
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

  const copy = (event: MouseEvent | KeyboardEvent, snippet: Snippet) => {
    event.stopPropagation();
    void onCopy(snippet);
  };

  return (
    <div className="snippet-list" role="listbox" aria-label="Snippets">
      {snippets.map((snippet) => {
        const selected = selectedId === snippet.id;
        const copied = copiedId === snippet.id;
        return (
          <div
            key={snippet.id}
            className={`snippet-card${selected ? ' snippet-card--selected' : ''}`}
            role="option"
            aria-selected={selected}
            tabIndex={0}
            onClick={() => onSelect(snippet)}
            onDoubleClick={(event) => copy(event, snippet)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault();
                copy(event, snippet);
              } else if (event.key === ' ') {
                event.preventDefault();
                onSelect(snippet);
              }
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
              <div className="snippet-card__actions">
                <span className={`snippet-card__copied${copied ? ' is-visible' : ''}`}>Copied</span>
                <button
                  type="button"
                  className="snippet-card__copy"
                  aria-label={`Copy ${snippet.title}`}
                  title="Copy snippet"
                  onClick={(event) => copy(event, snippet)}
                >
                  Copy
                </button>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
