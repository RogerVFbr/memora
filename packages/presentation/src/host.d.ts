export {};
declare global {
  interface Window { MemoraHost?: { postMessage(message: 'awake:0' | 'awake:1'): void } }
}
