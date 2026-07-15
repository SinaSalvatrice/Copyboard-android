import type { Snippet } from '../types';

function normalizedChecklist(snippet: Snippet) {
  return (snippet.checklistItems ?? [])
    .map((item) => ({
      id: item.id,
      text: item.text.trim(),
      done: Boolean(item.done),
    }))
    .filter((item) => item.text.length > 0);
}

export function snippetSearchText(snippet: Snippet): string {
  const checklistText = normalizedChecklist(snippet).map((item) => item.text).join(' ');
  return [snippet.title, snippet.category, snippet.text, checklistText].join(' ').toLowerCase();
}

export function snippetPreviewText(snippet: Snippet): string {
  if (snippet.mode === 'checklist') {
    const items = normalizedChecklist(snippet);
    if (items.length === 0) {
      return 'No checklist items yet.';
    }

    const completed = items.filter((item) => item.done).length;
    const firstItems = items.slice(0, 2).map((item) => `${item.done ? 'x' : ' '} ${item.text}`);
    const suffix = items.length > 2 ? ` +${items.length - 2} more` : '';
    return `${completed}/${items.length} done • ${firstItems.join(' • ')}${suffix}`;
  }

  const compact = snippet.text.replace(/\s+/g, ' ').trim();
  if (!compact) {
    return 'Empty note';
  }

  return compact.length > 160 ? `${compact.slice(0, 160)}...` : compact;
}

export function snippetClipboardText(snippet: Snippet): string {
  if (snippet.mode === 'checklist') {
    const items = normalizedChecklist(snippet);
    if (items.length === 0) {
      return '';
    }

    return items
      .map((item) => `- [${item.done ? 'x' : ' '}] ${item.text}`)
      .join('\n');
  }

  return snippet.text;
}
