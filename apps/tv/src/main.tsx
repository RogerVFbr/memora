import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { demoCatalog } from '@memora/presentation/demoCatalog';
import { Presentation } from '@memora/presentation/Presentation';
import { usePresentation } from '@memora/presentation/usePresentation';
import '@memora/presentation/styles.css';
import './tv.css';

function TVApp() {
  const state = usePresentation(demoCatalog, 'memora.tv.preferences.v1');
  return <main className={`app tv-app theme-${state.preferences.theme}`}><Presentation state={state} catalog={demoCatalog} version={import.meta.env.VITE_APP_VERSION || 'desenvolvimento'} />{!state.storageAvailable ? <p className="tv-storage-error" role="status">Não foi possível salvar os ajustes neste dispositivo.</p> : null}</main>;
}
createRoot(document.getElementById('root')!).render(<StrictMode><TVApp /></StrictMode>);
