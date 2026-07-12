import type { StoredData } from '../types';

const channel = typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('copyboard-state')
  : null;

export function publishState(data: StoredData) {
  channel?.postMessage(data);
}

export function subscribeState(handler: (data: StoredData) => void) {
  if (!channel) {
    return () => undefined;
  }

  const listener = (event: MessageEvent<StoredData>) => handler(event.data);
  channel.addEventListener('message', listener);

  return () => {
    channel.removeEventListener('message', listener);
  };
}