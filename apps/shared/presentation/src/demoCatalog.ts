import type { Catalog, Collection, Photo, Scene } from './model';

export const photos: Photo[] = [
  { id: 'coast', src: './media/coast.jpg', alt: 'Ondas chegam à praia ao entardecer', width: 1800, height: 1200 },
  { id: 'road', src: './media/mountain.jpg', alt: 'Estrada entre formações rochosas avermelhadas', width: 1800, height: 2700 },
  { id: 'deer', src: './media/deer.jpg', alt: 'Cervos em uma paisagem de floresta', width: 1800, height: 2971 },
  { id: 'forest', src: './media/forest.jpg', alt: 'Luz do sol atravessa uma floresta verde', width: 1800, height: 1199 },
];
export const collections: Collection[] = [
  { id: 'slow', name: 'Dias sem pressa', description: 'Mar, luz e pequenos intervalos', cover: photos[0] },
  { id: 'outside', name: 'Lá fora', description: 'Caminhos que merecem ficar', cover: photos[3] },
];
export const scenes: Scene[] = [
  { id: 'landscape', title: 'Dias sem pressa', subtitle: 'Uma fotografia. Espaço para sentir.', layout: 'single', photos: [photos[0]], collectionId: 'slow' },
  { id: 'portraits', title: 'Dois caminhos', subtitle: 'Retratos que conversam entre si.', layout: 'pair', photos: [photos[1], photos[2]], collectionId: 'outside' },
  { id: 'mosaic', title: 'Um pouco de cada instante', subtitle: 'Quatro imagens, uma pequena história.', layout: 'mosaic', photos: [photos[3], photos[0], photos[1], photos[2]], collectionId: 'outside' },
];
export const demoCatalog: Catalog = { scenes, collections };
