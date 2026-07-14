import { useEffect, useRef, useState } from 'react';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow, LogicalPosition, LogicalSize } from '@tauri-apps/api/window';
import type { Preferences, Snippet } from '../types';
import { SearchBox } from './SearchBox';

const appWindow = getCurrentWindow();
const COLLAPSED_SIZE = new LogicalSize(120, 36);
const EXPANDED_SIZE = new LogicalSize(360, 420);

interface FloatingWindowProps {
  snippets: Snippet[];
  preferences: Preferences;
  copiedId: string | null;
  onCopy: (snippet: Snippet) => Promise<void>;
  onPinChange: (pinned: boolean) => void;
  onPositionChange: (x: number, y: number) => void;
  onOpenMain: () => void;
}

export function FloatingWindow({
  snippets,
  preferences,
  copiedId,
  onCopy,
  onPinChange,
  onPositionChange,
  onOpenMain,
}: FloatingWindowProps) {
  const [expanded, setExpanded] = useState(false);
  const [search, setSearch] = useState('');
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    void appWindow.setAlwaysOnTop(true);
    void appWindow.setDecorations(false);
    void appWindow.setSize(COLLAPSED_SIZE);

    if (preferences.floatingPosition) {
      void appWindow.setPosition(new LogicalPosition(preferences.floatingPosition.x, preferences.floatingPosition.y));
    }

    const unlistenPromises = [
      appWindow.onMoved(({ payload }) => {
        onPositionChange(payload.x, payload.y);
      }),
      listen('copyboard://expand-floating', () => {
        setExpanded(true);
      }),
    ];

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !preferences.floatingPinned) {
        setExpanded(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      void Promise.all(unlistenPromises).then((unlisteners) => {
        unlisteners.forEach((unlisten) => unlisten());
      });
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onPositionChange, preferences.floatingPinned, preferences.floatingPosition]);

  useEffect(() => {
    void appWindow.setSize(expanded ? EXPANDED_SIZE : COLLAPSED_SIZE);
  }, [expanded]);

  const clearTimer = () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const scheduleCollapse = () => {
    clearTimer();
    if (preferences.floatingPinned) {
      return;
    }

    timerRef.current = window.setTimeout(() => {
      setExpanded(false);
    }, 650);
  };

  const visible = snippets.filter((snippet) => {
    if (!search.trim()) {
      return true;
    }

    const query = search.trim().toLowerCase();
    return [snippet.title, snippet.text, snippet.category].some((value) => value.toLowerCase().includes(query));
  });

  const recent = preferences.recentSnippetIds
    .map((id) => snippets.find((snippet) => snippet.id === id))
    .filter((snippet): snippet is Snippet => Boolean(snippet));

  const frequent = [...snippets]
    .sort((left, right) => (preferences.usageCounts[right.id] ?? 0) - (preferences.usageCounts[left.id] ?? 0))
    .filter((snippet) => !recent.some((item) => item.id === snippet.id))
    .slice(0, 4);

  return (
    <div
      className={`floating-shell${expanded ? ' is-expanded' : ' is-collapsed'}`}
      onMouseEnter={() => {
        clearTimer();
        setExpanded(true);
      }}
      onMouseLeave={scheduleCollapse}
    >
      <div className="floating-pill" data-tauri-drag-region>
        <span>Copyboard</span>
        {expanded ? (
          <div className="floating-pill__actions">
            <button type="button" className="floating-pill__pin" onClick={onOpenMain}>
              App
            </button>
            <button
              type="button"
              className={`floating-pill__pin${preferences.floatingPinned ? ' is-active' : ''}`}
              onClick={() => onPinChange(!preferences.floatingPinned)}
            >
              {preferences.floatingPinned ? 'Pinned' : 'Pin'}
            </button>
          </div>
        ) : null}
      </div>

      {expanded ? (
        <div className="floating-panel">
          <SearchBox value={search} onChange={setSearch} placeholder="Search favorites and recent snippets" />

          <section>
            <div className="floating-panel__section-header">
              <h3>Favorites</h3>
            </div>
            <div className="floating-panel__list">
              {visible.filter((snippet) => snippet.favorite).slice(0, 6).map((snippet) => (
                <button
                  key={snippet.id}
                  type="button"
                  className="floating-panel__item"
                  onClick={async () => {
                    await onCopy(snippet);
                    if (!preferences.floatingPinned) {
                      setExpanded(false);
                    }
                  }}
                >
                  <strong>{snippet.title}</strong>
                  <span>{snippet.category}</span>
                  <em className={copiedId === snippet.id ? 'is-visible' : ''}>Copied</em>
                </button>
              ))}
            </div>
          </section>

          <section>
            <div className="floating-panel__section-header">
              <h3>Recent</h3>
            </div>
            <div className="floating-panel__list">
              {[...recent, ...frequent].slice(0, 8).map((snippet) => (
                <button
                  key={snippet.id}
                  type="button"
                  className="floating-panel__item"
                  onClick={async () => {
                    await onCopy(snippet);
                    if (!preferences.floatingPinned) {
                      setExpanded(false);
                    }
                  }}
                >
                  <strong>{snippet.title}</strong>
                  <span>{snippet.category}</span>
                  <em className={copiedId === snippet.id ? 'is-visible' : ''}>Copied</em>
                </button>
              ))}
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}