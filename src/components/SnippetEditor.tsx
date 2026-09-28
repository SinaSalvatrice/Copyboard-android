import { useEffect, useRef, useState } from 'react';
import type { Snippet } from '../types';

interface SpeechRecognitionAlternativeLike {
  transcript: string;
  confidence: number;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionResultListLike {
  length: number;
  [index: number]: SpeechRecognitionResultLike;
}

interface SpeechRecognitionEventLike extends Event {
  resultIndex: number;
  results: SpeechRecognitionResultListLike;
}

interface SpeechRecognitionErrorEventLike extends Event {
  error: string;
  message?: string;
}

interface SpeechRecognitionLike {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  }
}

interface SnippetEditorProps {
  snippet: Snippet;
  groups: string[];
  onChange: (snippet: Snippet) => void;
  onSave: () => void;
  onDelete: () => void;
  onNew: () => void;
}

const SPEECH_LANGUAGES = [
  { value: 'de-DE', label: 'DE' },
  { value: 'ru-RU', label: 'RU' },
  { value: 'en-US', label: 'EN' },
];

function speechErrorMessage(error: string) {
  switch (error) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'Mikrofonzugriff wurde nicht erlaubt.';
    case 'audio-capture':
      return 'Kein Mikrofon verfügbar.';
    case 'network':
      return 'Spracherkennung konnte den Dienst nicht erreichen.';
    case 'no-speech':
      return 'Keine Sprache erkannt.';
    default:
      return 'Spracherkennung wurde beendet.';
  }
}

export function SnippetEditor({ snippet, groups, onChange, onSave, onDelete, onNew }: SnippetEditorProps) {
  const [speechLanguage, setSpeechLanguage] = useState(
    () => localStorage.getItem('copyboard-speech-language') || 'de-DE',
  );
  const [isListening, setIsListening] = useState(false);
  const [speechError, setSpeechError] = useState('');
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const snippetRef = useRef(snippet);
  const baseTextRef = useRef('');
  const baseChecklistRef = useRef(snippet.checklistItems);
  const dictatedChecklistIdRef = useRef('');
  const dictationModeRef = useRef<'text' | 'checklist'>('text');

  useEffect(() => {
    snippetRef.current = snippet;
  }, [snippet]);

  useEffect(() => () => {
    recognitionRef.current?.abort();
    recognitionRef.current = null;
  }, []);

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

  const stopDictation = () => {
    recognitionRef.current?.stop();
    setIsListening(false);
  };

  const startDictation = () => {
    const SpeechRecognition = window.SpeechRecognition ?? window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setSpeechError('Spracherkennung ist in dieser WebView2-Version nicht verfügbar.');
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = speechLanguage;

    recognitionRef.current?.abort();
    recognitionRef.current = recognition;
    dictationModeRef.current = snippet.mode;
    baseTextRef.current = snippet.text;
    baseChecklistRef.current = [...snippet.checklistItems];
    dictatedChecklistIdRef.current = crypto.randomUUID();
    setSpeechError('');

    recognition.onresult = (event) => {
      let transcript = '';
      for (let index = 0; index < event.results.length; index += 1) {
        transcript += event.results[index]?.[0]?.transcript ?? '';
      }
      transcript = transcript.trim();
      if (!transcript) {
        return;
      }

      const current = snippetRef.current;
      if (dictationModeRef.current === 'checklist') {
        onChange({
          ...current,
          checklistItems: [
            ...baseChecklistRef.current,
            {
              id: dictatedChecklistIdRef.current,
              text: transcript,
              done: false,
            },
          ],
        });
        return;
      }

      const base = baseTextRef.current;
      const separator = base && !/\s$/.test(base) ? ' ' : '';
      onChange({
        ...current,
        text: `${base}${separator}${transcript}`,
      });
    };

    recognition.onerror = (event) => {
      setSpeechError(speechErrorMessage(event.error));
      setIsListening(false);
    };

    recognition.onend = () => {
      recognitionRef.current = null;
      setIsListening(false);
    };

    try {
      recognition.start();
      setIsListening(true);
    } catch {
      recognitionRef.current = null;
      setIsListening(false);
      setSpeechError('Spracherkennung konnte nicht gestartet werden.');
    }
  };

  const toggleDictation = () => {
    if (isListening) {
      stopDictation();
    } else {
      startDictation();
    }
  };

  const changeSpeechLanguage = (value: string) => {
    setSpeechLanguage(value);
    localStorage.setItem('copyboard-speech-language', value);
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

      <div className="speech-toolbar" aria-label="Speech to text">
        <button
          type="button"
          className={`speech-button${isListening ? ' is-listening' : ''}`}
          onClick={toggleDictation}
          title={isListening ? 'Diktat stoppen' : 'Sprache in Text umwandeln'}
        >
          <span aria-hidden="true">{isListening ? '■' : '🎙'}</span>
          <span>{isListening ? 'Stop' : 'Diktieren'}</span>
        </button>
        <label className="speech-language">
          <span>Sprache</span>
          <select
            value={speechLanguage}
            disabled={isListening}
            onChange={(event) => changeSpeechLanguage(event.target.value)}
          >
            {SPEECH_LANGUAGES.map((language) => (
              <option key={language.value} value={language.value}>
                {language.label}
              </option>
            ))}
          </select>
        </label>
        {speechError ? <span className="speech-error" role="status">{speechError}</span> : null}
      </div>

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
