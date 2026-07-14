import { useEffect, useRef, useState } from 'react';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow, LogicalPosition, LogicalSize } from '@tauri-apps/api/window';
import type { Preferences, Snippet } from '../types';
import { SearchBox } from './SearchBox';

const appWindow = getCurrentWindow();
const EXPANDED_SIZE = new LogicalSize(360, 420);
const FLOATING_ANIMATION_MS = 180;
const BASE_ICON_SIZE = 48;

function iconSize(scale: number) {
  const size = Math.round((BASE_ICON_SIZE + 8) * scale);
  return new LogicalSize(size, size);
}

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
  const [expanded, setExpanded] = useState(preferences.floatingCollapseMode === 'stay-open');
  const [search, setSearch] = useState('');
  const timerRef = useRef<number | null>(null);
  const animationRef = useRef<number | null>(null);
  const [animationState, setAnimationState] = useState<'idle' | 'expanding' | 'collapsing'>('idle');

  const clearAnimation = () => {
    if (animationRef.current !== null) {
      window.clearTimeout(animationRef.current);
      animationRef.current = null;
    }
  };

  const showPanel = () => {
    clearTimer();
    clearAnimation();
    void appWindow.setSize(EXPANDED_SIZE);
    setExpanded(true);
    setAnimationState('expanding');
    animationRef.current = window.setTimeout(() => {
      setAnimationState('idle');
      animationRef.current = null;
    }, FLOATING_ANIMATION_MS);
  };

  const collapseToIcon = () => {
    clearTimer();
    if (preferences.floatingPinned || preferences.floatingCollapseMode === 'stay-open') {
      return;
    }

    clearAnimation();
    setAnimationState('collapsing');
    animationRef.current = window.setTimeout(() => {
      setExpanded(false);
      setAnimationState('idle');
      animationRef.current = null;
      void appWindow.setSize(iconSize(preferences.floatingIconScale));
    }, FLOATING_ANIMATION_MS);
  };

  useEffect(() => {
    void appWindow.setAlwaysOnTop(true);
    void appWindow.setDecorations(false);
    void appWindow.setSize(expanded ? EXPANDED_SIZE : iconSize(preferences.floatingIconScale));

    if (preferences.floatingPosition) {
      void appWindow.setPosition(new LogicalPosition(preferences.floatingPosition.x, preferences.floatingPosition.y));
    }

    const unlistenPromises = [
      appWindow.onMoved(({ payload }) => {
        onPositionChange(payload.x, payload.y);
      }),
      listen('copyboard://expand-floating', () => {
        showPanel();
      }),
    ];

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !preferences.floatingPinned) {
        collapseToIcon();
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      void Promise.all(unlistenPromises).then((unlisteners) => {
        unlisteners.forEach((unlisten) => unlisten());
      });
      clearAnimation();
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onPositionChange, preferences.floatingCollapseMode, preferences.floatingIconScale, preferences.floatingPinned, preferences.floatingPosition]);

  useEffect(() => {
    if (preferences.floatingCollapseMode === 'stay-open' && !expanded) {
      showPanel();
      return;
    }

    if (!expanded) {
      void appWindow.setSize(iconSize(preferences.floatingIconScale));
    }
  }, [expanded, preferences.floatingCollapseMode, preferences.floatingIconScale]);

  const clearTimer = () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const scheduleCollapse = () => {
    clearTimer();
    if (preferences.floatingPinned || preferences.floatingCollapseMode === 'stay-open') {
      return;
    }

    timerRef.current = window.setTimeout(() => {
      collapseToIcon();
    }, preferences.floatingHoverDelayMs);
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
      className={`floating-shell is-${expanded ? 'expanded' : 'icon'} is-${animationState} animation-${preferences.floatingAnimation}`}
      style={{
        '--floating-icon-scale': String(preferences.floatingIconScale),
        '--floating-icon-opacity': String(preferences.floatingIconOpacity),
      } as React.CSSProperties}
      onMouseEnter={() => {
        showPanel();
      }}
      onMouseLeave={scheduleCollapse}
    >
      {expanded ? (
        <>
          <div className="floating-pill" data-tauri-drag-region>
            <span>Copyboard</span>
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
          </div>

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
        </>
      ) : null}

      {!expanded ? (
        <div className="floating-icon" data-tauri-drag-region title="Open Copyboard floating mode">
          <span>CB</span>
        </div>
      ) : null}
    </div>
  );
}