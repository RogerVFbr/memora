import type { Panel } from './model';

export interface PlaybackState { playing: boolean; menu: boolean; panel: Panel; visible: boolean }
export type PlaybackAction =
  | { type: 'playing'; value: boolean | 'toggle' }
  | { type: 'menu'; value: boolean | 'toggle' }
  | { type: 'panel'; value: Panel }
  | { type: 'visibility'; visible: boolean }
  | { type: 'command'; command: string };
export function initialPlayback(visible: boolean): PlaybackState {
  return { playing: false, menu: true, panel: null, visible };
}
export function playbackReducer(state: PlaybackState, action: PlaybackAction): PlaybackState {
  switch (action.type) {
    case 'playing': return { ...state, playing: state.visible && (action.value === 'toggle' ? !state.playing : action.value) };
    case 'menu': return { ...state, menu: action.value === 'toggle' ? !state.menu : action.value };
    case 'panel': return { ...state, panel: action.value };
    case 'visibility': return action.visible === state.visible ? state : initialPlayback(action.visible);
    case 'command':
      switch (action.command) {
        case 'menu': return { ...state, panel: null, menu: true };
        case 'back': return state.panel ? { ...state, panel: null } : { ...state, menu: !state.menu };
        case 'toggle': return { ...state, playing: state.visible && !state.playing, menu: false, panel: null };
        case 'play': return { ...state, playing: state.visible, menu: false, panel: null };
        case 'pause': return { ...state, playing: false, menu: false, panel: null };
        default: return state;
      }
  }
}
export function isRunning(state: PlaybackState, ready: boolean): boolean {
  return ready && state.visible && state.playing && !state.menu && state.panel === null;
}
export function keepAwake(state: PlaybackState, ready: boolean): boolean {
  return state.visible && (state.menu || state.panel !== null || isRunning(state, ready));
}
