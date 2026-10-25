/// <reference path="./host.d.ts" />
import { useEffect, useReducer, useRef, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { initialPlayback, playbackReducer, isRunning as playbackIsRunning, keepAwake } from './playback';
import { createDefaults, loadPreferences, savePreferences } from './model';
import type { Catalog, Panel, Preferences, Scenario } from './model';

export function usePresentation(catalog: Catalog, storageKey: string) {
  const { scenes } = catalog;
  const [preferences, setPreferences] = useState(() => loadPreferences(storageKey, catalog));
  const [index, setIndex] = useState(0);
  const [playback, dispatch] = useReducer(playbackReducer, !document.hidden, initialPlayback);
  const { playing, menu, panel, visible } = playback;
  function setPlaying(value: boolean | 'toggle') { dispatch({ type: 'playing', value }); }
  function setMenu(value: boolean | 'toggle') { dispatch({ type: 'menu', value }); }
  function setPanel(value: Panel) { dispatch({ type: 'panel', value }); }
  const [scenario, setScenario] = useState<Scenario>('ready');
  const [storageAvailable, setStorageAvailable] = useState(true);
  const [fullscreenError, setFullscreenError] = useState('');
  const nativeVisible = useRef(true);
  const stage = useRef<HTMLDivElement>(null);
  const dock = useRef<HTMLDivElement>(null);
  const drawer = useRef<HTMLElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const returnPanel = useRef<Panel>(null);
  const restoreFocus = useRef(false);
  const activeScenes = scenes.filter(s => preferences.activeCollections.includes(s.collectionId));
  const scene = activeScenes[index % Math.max(activeScenes.length, 1)] || scenes[0];
  const effectiveScenario = scenario === 'ready' && activeScenes.length === 0 ? 'empty' : scenario;
  const isRunning = playbackIsRunning(playback, effectiveScenario === 'ready');
  const shouldKeepAwake = keepAwake(playback, effectiveScenario === 'ready');

  function update<K extends keyof Preferences>(key: K, value: Preferences[K]) {
    setPreferences(p => ({ ...p, [key]: value }));
  }
  function move(direction: number) {
    setIndex(i => (i + direction + Math.max(activeScenes.length, 1)) % Math.max(activeScenes.length, 1));
  }
  function openPanel(value: Panel, trigger?: HTMLElement) {
    returnFocus.current = trigger || document.activeElement as HTMLElement;
    returnPanel.current = value;
    setPanel(value);
  }
  function closePanel() {
    restoreFocus.current = true;
    setPanel(null);
  }
  function closeMenu() { setMenu(false); stage.current?.focus(); }
  function start() { setPlaying('toggle'); closeMenu(); }
  function reset() {
    setPreferences(createDefaults(catalog)); setScenario('ready'); setIndex(0);
    setPlaying(false); setMenu(true); setPanel(null); setFullscreenError('');
  }
  async function fullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await stage.current?.requestFullscreen();
      setFullscreenError('');
    } catch { setFullscreenError('O navegador não permitiu a tela cheia.'); }
  }

  useEffect(() => { setStorageAvailable(savePreferences(preferences, storageKey)); }, [preferences, storageKey]);
  useEffect(() => {
    const onVisibility = () => dispatch({ type: 'visibility', visible: nativeVisible.current && !document.hidden });
    const onNativeVisibility = (event: Event) => {
      const value: unknown = (event as CustomEvent).detail;
      if (typeof value !== 'boolean') return;
      nativeVisible.current = value;
      onVisibility();
    };
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('memora:visibility', onNativeVisibility);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('memora:visibility', onNativeVisibility);
    };
  }, []);
  useEffect(() => {
    window.MemoraHost?.postMessage(shouldKeepAwake ? 'awake:1' : 'awake:0');
    return () => window.MemoraHost?.postMessage('awake:0');
  }, [shouldKeepAwake]);
  useEffect(() => {
    const onCommand = (event: Event) => {
      const command: unknown = (event as CustomEvent).detail;
      if (typeof command !== 'string' || !['menu', 'back', 'toggle', 'play', 'pause'].includes(command)) return;
      if (command === 'back' && panel) restoreFocus.current = true;
      if (['play', 'pause', 'toggle'].includes(command) && effectiveScenario !== 'ready') return;
      dispatch({ type: 'command', command });
      if (['play', 'pause', 'toggle'].includes(command) || (command === 'back' && !panel)) stage.current?.focus();
    };
    window.addEventListener('memora:command', onCommand);
    return () => window.removeEventListener('memora:command', onCommand);
  }, [panel, effectiveScenario]);
  useEffect(() => {
    if (!isRunning) return;
    const id = window.setTimeout(() => setIndex(i => (i + 1) % activeScenes.length), preferences.duration * 1000);
    return () => clearTimeout(id);
  }, [isRunning, index, activeScenes.length, preferences.duration]);
  useEffect(() => {
    if (panel) drawer.current?.querySelector<HTMLButtonElement>('button')?.focus();
    else if (restoreFocus.current) {
      restoreFocus.current = false;
      const target = returnFocus.current;
      if (target?.isConnected) target.focus();
      else {
        const replacement = dock.current?.querySelector<HTMLButtonElement>(`[data-panel="${returnPanel.current}"]`);
        (replacement || stage.current)?.focus();
      }
    }
    else if (menu && effectiveScenario === 'ready') dock.current?.querySelector<HTMLButtonElement>('[data-primary]')?.focus();
  }, [panel, menu, effectiveScenario, visible]);
  useEffect(() => {
    if (effectiveScenario !== 'ready' && !panel) stage.current?.focus();
  }, [effectiveScenario, panel]);

  function onStageKey(event: ReactKeyboardEvent<HTMLDivElement>) {
    const element = event.target as HTMLElement;
    const key = event.key;
    if (key === 'Escape' || key === 'Backspace') {
      event.preventDefault(); event.stopPropagation();
      if (panel) closePanel(); else if (menu) closeMenu(); else { setMenu(true); }
      return;
    }
    if (key === 'Enter' && element === stage.current && effectiveScenario === 'ready') {
      event.preventDefault(); setMenu('toggle'); return;
    }
    if (key === ' ' && !panel && (element === stage.current)) {
      event.preventDefault(); setPlaying('toggle'); return;
    }
    if (event.defaultPrevented) return;
    if (key === 'Tab' && panel) {
      const elements = Array.from(drawer.current?.querySelectorAll<HTMLElement>('button, select, input') || []);
      const current = elements.indexOf(element);
      if ((event.shiftKey && current <= 0) || (!event.shiftKey && current === elements.length - 1)) {
        event.preventDefault(); elements[event.shiftKey ? elements.length - 1 : 0]?.focus();
      }
      return;
    }
    if (!key.startsWith('Arrow')) return;
    if (element.tagName === 'SELECT' || element.tagName === 'INPUT') return;
    event.preventDefault();
    if (!menu && !panel && effectiveScenario === 'ready') {
      if (key === 'ArrowLeft') move(-1);
      else if (key === 'ArrowRight') move(1);
      else setMenu(true);
      return;
    }
    const scope = panel ? drawer.current : effectiveScenario !== 'ready' ? stage.current : dock.current;
    const elements = Array.from(scope?.querySelectorAll<HTMLElement>('button:not([disabled]), select, input') || []);
    const from = element.getBoundingClientRect();
    const x = from.x + from.width / 2; const y = from.y + from.height / 2;
    const horizontal = key === 'ArrowLeft' || key === 'ArrowRight';
    const positive = key === 'ArrowRight' || key === 'ArrowDown';
    const candidates = elements.filter(el => el !== element).map(el => {
      const r = el.getBoundingClientRect();
      const dx = r.x + r.width / 2 - x; const dy = r.y + r.height / 2 - y;
      const along = horizontal ? dx : dy; const across = horizontal ? dy : dx;
      return { el, along, score: Math.abs(along) + Math.abs(across) * 3 };
    }).filter(c => positive ? c.along > 5 : c.along < -5).sort((a, b) => a.score - b.score);
    (candidates[0]?.el || (element === stage.current ? elements[0] : null))?.focus();
  }

  return { preferences, update, index, setIndex, playing, setPlaying, menu, setMenu, panel, setPanel, scenario, setScenario, storageAvailable, fullscreenError, stage, dock, drawer, scene, effectiveScenario, isRunning, move, openPanel, closePanel, closeMenu, start, reset, fullscreen, onStageKey };
}
export type PresentationController = ReturnType<typeof usePresentation>;
