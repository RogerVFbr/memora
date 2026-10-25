export type Theme = 'cinema' | 'gallery';
export type Scenario = 'ready' | 'preparing' | 'offline' | 'expired' | 'empty';
export type Panel = 'collections' | 'settings' | null;
export interface Photo { id: string; src: string; alt: string; width: number; height: number }
export interface Scene { id: string; title: string; subtitle: string; layout: 'single' | 'pair' | 'mosaic'; photos: Photo[]; collectionId: string }
export interface Preferences {
  theme: Theme; fit: 'contain' | 'cover'; duration: number;
  metadata: boolean; motion: boolean; activeCollections: string[];
}

export interface Collection { id: string; name: string; description: string; cover: Photo }
export interface Catalog { scenes: Scene[]; collections: Collection[] }

export function createDefaults(catalog: Catalog): Preferences { return { theme: 'cinema', fit: 'contain', duration: 12, metadata: true, motion: true, activeCollections: catalog.collections.map(c => c.id) }; }
export function loadPreferences(storageKey: string, catalog: Catalog): Preferences {
  const defaults = createDefaults(catalog);
  try {
    const value = JSON.parse(localStorage.getItem(storageKey) || 'null');
    if (!value || value.version !== 1) return { ...defaults };
    const p = value.preferences;
    if (!p || typeof p !== 'object' || Array.isArray(p)) return defaults;
    return {
      theme: p.theme === 'gallery' ? 'gallery' : 'cinema',
      fit: p.fit === 'cover' ? 'cover' : 'contain',
      duration: [6, 12, 20].includes(p.duration) ? p.duration : defaults.duration,
      metadata: typeof p.metadata === 'boolean' ? p.metadata : true,
      motion: typeof p.motion === 'boolean' ? p.motion : true,
      activeCollections: Array.isArray(p.activeCollections) ? p.activeCollections.filter((id: unknown) => catalog.collections.some(c => c.id === id)) : defaults.activeCollections,
    };
  } catch { return { ...defaults }; }
}
export function savePreferences(preferences: Preferences, storageKey: string): boolean {
  try {
    const previous = localStorage.getItem(storageKey);
    if (previous) {
      try {
        const parsed = JSON.parse(previous);
        if (parsed && typeof parsed.version === 'number' && parsed.version > 1) return false;
      } catch { /* Malformed data may be replaced by validated defaults. */ }
    }
    localStorage.setItem(storageKey, JSON.stringify({ version: 1, preferences }));
    return true;
  }
  catch { return false; }
}
