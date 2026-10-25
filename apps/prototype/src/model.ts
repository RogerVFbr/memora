export type Theme = 'cinema' | 'gallery';
export type Scenario = 'ready' | 'preparing' | 'offline' | 'expired' | 'empty';
export type Panel = 'collections' | 'settings' | null;
export interface Photo { id: string; src: string; alt: string; width: number; height: number }
export interface Scene { id: string; title: string; subtitle: string; layout: 'single' | 'pair' | 'mosaic'; photos: Photo[]; collectionId: string }
export interface Preferences {
  theme: Theme; fit: 'contain' | 'cover'; duration: number;
  metadata: boolean; motion: boolean; activeCollections: string[];
}

export const photos: Photo[] = [
  { id: 'coast', src: './media/coast.jpg', alt: 'Ondas chegam à praia ao entardecer', width: 1800, height: 1200 },
  { id: 'road', src: './media/mountain.jpg', alt: 'Estrada entre formações rochosas avermelhadas', width: 1800, height: 2700 },
  { id: 'deer', src: './media/deer.jpg', alt: 'Cervos em uma paisagem de floresta', width: 1800, height: 2971 },
  { id: 'forest', src: './media/forest.jpg', alt: 'Luz do sol atravessa uma floresta verde', width: 1800, height: 1199 },
];
export const collections = [
  { id: 'slow', name: 'Dias sem pressa', description: 'Mar, luz e pequenos intervalos', cover: photos[0] },
  { id: 'outside', name: 'Lá fora', description: 'Caminhos que merecem ficar', cover: photos[3] },
];
export const scenes: Scene[] = [
  { id: 'landscape', title: 'Dias sem pressa', subtitle: 'Uma fotografia. Espaço para sentir.', layout: 'single', photos: [photos[0]], collectionId: 'slow' },
  { id: 'portraits', title: 'Dois caminhos', subtitle: 'Retratos que conversam entre si.', layout: 'pair', photos: [photos[1], photos[2]], collectionId: 'outside' },
  { id: 'mosaic', title: 'Um pouco de cada instante', subtitle: 'Quatro imagens, uma pequena história.', layout: 'mosaic', photos: [photos[3], photos[0], photos[1], photos[2]], collectionId: 'outside' },
];
export const defaults: Preferences = { theme: 'cinema', fit: 'contain', duration: 12, metadata: true, motion: true, activeCollections: ['slow', 'outside'] };
const storageKey = 'memora.prototype.preferences.v1';
export function loadPreferences(): Preferences {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey) || 'null');
    if (!value || value.version !== 1) return { ...defaults };
    const p = value.preferences;
    return {
      theme: p.theme === 'gallery' ? 'gallery' : 'cinema',
      fit: p.fit === 'cover' ? 'cover' : 'contain',
      duration: [6, 12, 20].includes(p.duration) ? p.duration : defaults.duration,
      metadata: typeof p.metadata === 'boolean' ? p.metadata : true,
      motion: typeof p.motion === 'boolean' ? p.motion : true,
      activeCollections: Array.isArray(p.activeCollections) ? p.activeCollections.filter((id: unknown) => collections.some(c => c.id === id)) : defaults.activeCollections,
    };
  } catch { return { ...defaults }; }
}
export function savePreferences(preferences: Preferences): boolean {
  try { localStorage.setItem(storageKey, JSON.stringify({ version: 1, preferences })); return true; }
  catch { return false; }
}
