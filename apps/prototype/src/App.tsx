import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Brand, Icon } from './Icons';
import { collections, defaults, loadPreferences, savePreferences, scenes } from './model';
import type { Panel, Photo, Preferences, Scenario } from './model';

function PhotoSlot({ photo, fit }: { photo: Photo; fit: Preferences['fit'] }) {
  const [failed, setFailed] = useState(false);
  return <div className="photo-slot">{failed ? <span className="asset-error">Imagem indisponível</span> : <img src={photo.src} alt={photo.alt} width={photo.width} height={photo.height} style={{ objectFit: fit }} draggable={false} onError={() => setFailed(true)} />}</div>;
}

const scenarioCopy = {
  preparing: { eyebrow: 'PREPARANDO A APRESENTAÇÃO', title: 'Cada memória no seu tempo.', body: 'Estamos preparando as próximas imagens. Neste laboratório, você controla quando continuar.', action: 'Concluir preparação simulada', icon: 'image' },
  offline: { eyebrow: 'CONEXÃO INTERROMPIDA', title: 'Uma pequena pausa.', body: 'Não foi possível carregar a próxima imagem. Verifique a conexão para continuar.', action: 'Simular reconexão', icon: 'wifi' },
  expired: { eyebrow: 'ACESSO À SELEÇÃO ENCERRADO', title: 'Vamos escolher de novo?', body: 'O acesso a esta seleção terminou. Será necessário selecionar conteúdo novamente para continuar.', action: 'Simular nova seleção', icon: 'refresh' },
  empty: { eyebrow: 'SUA PRÓXIMA HISTÓRIA', title: 'Dê espaço às suas memórias.', body: 'Ative uma coleção para começar. As coleções deste protótipo são demonstrativas.', action: 'Escolher coleções', icon: 'grid' },
} as const;

export default function App() {
  const [preferences, setPreferences] = useState(loadPreferences);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [menu, setMenu] = useState(true);
  const [panel, setPanel] = useState<Panel>(null);
  const [lab, setLab] = useState(false);
  const [scenario, setScenario] = useState<Scenario>('ready');
  const [storageAvailable, setStorageAvailable] = useState(true);
  const [fullscreenError, setFullscreenError] = useState('');
  const [visible, setVisible] = useState(!document.hidden);
  const stage = useRef<HTMLDivElement>(null);
  const dock = useRef<HTMLDivElement>(null);
  const drawer = useRef<HTMLElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const returnPanel = useRef<Panel>(null);
  const restoreFocus = useRef(false);
  const activeScenes = scenes.filter(s => preferences.activeCollections.includes(s.collectionId));
  const scene = activeScenes[index % Math.max(activeScenes.length, 1)] || scenes[0];
  const effectiveScenario = scenario === 'ready' && activeScenes.length === 0 ? 'empty' : scenario;
  const isRunning = playing && !menu && !panel && visible && effectiveScenario === 'ready';

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
  function start() { setPlaying(p => !p); closeMenu(); }
  function reset() {
    setPreferences({ ...defaults }); setScenario('ready'); setIndex(0);
    setPlaying(false); setMenu(true); setPanel(null); setFullscreenError('');
  }
  async function fullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await stage.current?.requestFullscreen();
      setFullscreenError('');
    } catch { setFullscreenError('O navegador não permitiu a tela cheia.'); }
  }

  useEffect(() => { setStorageAvailable(savePreferences(preferences)); }, [preferences]);
  useEffect(() => {
    const onVisibility = () => setVisible(!document.hidden);
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, []);
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
  }, [panel, menu, effectiveScenario]);
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
      event.preventDefault(); setMenu(m => !m); return;
    }
    if (key === ' ' && !panel && (element === stage.current)) {
      event.preventDefault(); setPlaying(p => !p); return;
    }
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

  return <div className={`app theme-${preferences.theme}`}>
    <header className="studio-header">
      <a className="brand-link" href="#main" aria-label="Memora, ir para o protótipo"><Brand /></a>
      <span className="header-caption">LABORATÓRIO VISUAL <span> / </span> 01</span>
      <div className="header-actions"><span className="local-badge"><i /> LOCAL · DADOS MOCK</span><button className={`text-button ${lab ? 'selected' : ''}`} aria-expanded={lab} aria-controls="laboratory" onClick={() => setLab(l => !l)}><Icon name="lab" /> Laboratório</button></div>
    </header>

    <main id="main">
      <section className="intro"><div><p className="eyebrow">UM LUGAR PARA REVISITAR</p><h1>O tempo passa.{' '}<em>A memória fica.</em></h1><p className="intro-copy">Explore uma nova forma de dar vida às suas fotografias.</p></div>
        <div className="theme-switch" role="group" aria-label="Direção visual"><button aria-pressed={preferences.theme === 'cinema'} onClick={() => update('theme', 'cinema')}><span className="theme-dot warm" /> Cinema acolhedor</button><button aria-pressed={preferences.theme === 'gallery'} onClick={() => update('theme', 'gallery')}><span className="theme-dot neutral" /> Galeria neutra</button></div>
      </section>

      <section className="preview-section" aria-label="Simulação de TV">
        <div className="preview-heading"><span><i /> PREVIEW DA EXPERIÊNCIA</span><span>16:9 <span className="dot-separator">·</span> CONTROLE POR TECLADO</span></div>
        <div className={`tv-stage ${panel ? 'has-panel' : ''}`} ref={stage} tabIndex={0} aria-label="Apresentação de fotos. Enter abre controles, setas navegam, Escape volta." onKeyDown={onStageKey}>
          {effectiveScenario === 'ready' ? <>
            <div key={scene.id} className={`scene layout-${scene.layout} fit-${preferences.fit} ${preferences.motion ? 'motion-enabled' : ''} ${isRunning ? 'running' : ''}`} style={{ '--scene-duration': `${preferences.duration}s` } as React.CSSProperties}>
              {scene.photos.map(photo => <PhotoSlot key={photo.id} photo={photo} fit={preferences.fit} />)}
            </div>
            <div className="scene-shade" />
            {menu || panel ? <div className="tv-topline"><Brand /><span className="tv-demo">ACERVO DEMONSTRATIVO</span></div> : null}
            {preferences.metadata && !panel ? <div className={`scene-caption ${menu ? 'with-menu' : ''}`}><span className="eyebrow">{collections.find(c => c.id === scene.collectionId)?.name} <span className="caption-line" /></span><h2>{scene.title}</h2><p>Fotografia demonstrativa <span>·</span> Sem data de captura informada</p></div> : null}
            {!menu && !panel ? <button className="reveal-menu" onClick={() => setMenu(true)}><span>OK</span> Controles</button> : null}
            {menu && !panel ? <div className="playback-menu"><div className="transport" ref={dock} role="toolbar" aria-label="Controles de reprodução">
              <button aria-label="Cena anterior" onClick={() => move(-1)}><Icon name="previous" /></button>
              <button className="primary-transport" data-primary aria-label={playing ? 'Pausar apresentação' : 'Iniciar apresentação'} onClick={start}><Icon name={playing ? 'pause' : 'play'} /></button>
              <button aria-label="Próxima cena" onClick={() => move(1)}><Icon name="next" /></button>
              <span className="toolbar-divider" />
              <button data-panel="collections" aria-label="Coleções" onClick={e => openPanel('collections', e.currentTarget)}><Icon name="grid" /><span>Coleções</span></button>
              <button data-panel="settings" aria-label="Ajustes" onClick={e => openPanel('settings', e.currentTarget)}><Icon name="settings" /><span>Ajustes</span></button>
              <span className="toolbar-divider" />
              <button aria-label="Fechar controles" onClick={closeMenu}><Icon name="close" /></button>
            </div><p className="transport-hint">{playing ? 'Apresentação suspensa enquanto você navega' : 'Pronto para começar'} <span>·</span> ESC para voltar</p></div> : null}
            <div className="scene-number"><span>{String(scenes.indexOf(scene) + 1).padStart(2, '0')}</span> / 03</div>
            {isRunning ? <div key={`${scene.id}-${preferences.duration}`} className="scene-progress" style={{ animationDuration: `${preferences.duration}s` }} /> : null}
          </> : <div className="state-screen" role="status"><div className="state-symbol"><Icon name={scenarioCopy[effectiveScenario].icon} /></div><p className="eyebrow">{scenarioCopy[effectiveScenario].eyebrow}</p><h2>{scenarioCopy[effectiveScenario].title}</h2><p>{scenarioCopy[effectiveScenario].body}</p><button className="accent-button" onClick={e => {
            if (effectiveScenario === 'empty') { setScenario('ready'); openPanel('collections', e.currentTarget); }
            else { setScenario('ready'); setMenu(true); }
          }}>{scenarioCopy[effectiveScenario].action}<Icon name="arrow" /></button><span className="simulation-label">SIMULAÇÃO · nenhuma operação no Google Fotos</span></div>}

          {panel ? <div className="drawer-scrim"><aside className="drawer" ref={drawer} role="dialog" aria-modal="true" aria-labelledby="drawer-title">
            <header><div><p className="eyebrow">DO SEU JEITO</p><h2 id="drawer-title">{panel === 'collections' ? 'Suas coleções' : 'Ajustes da apresentação'}</h2></div><button className="icon-button" aria-label="Fechar painel" onClick={closePanel}><Icon name="close" /></button></header>
            {panel === 'collections' ? <><p className="drawer-description">Escolha o que faz parte deste momento.</p><div className="collection-list">{collections.map(collection => <button key={collection.id} className="collection-card" aria-pressed={preferences.activeCollections.includes(collection.id)} onClick={() => { update('activeCollections', preferences.activeCollections.includes(collection.id) ? preferences.activeCollections.filter(id => id !== collection.id) : [...preferences.activeCollections, collection.id]); setIndex(0); }}><img src={collection.cover.src} alt="" /><span><strong>{collection.name}</strong><small>{collection.description}</small><small>Seleção local · demonstração</small></span><span className="collection-check">{preferences.activeCollections.includes(collection.id) ? <Icon name="check" /> : null}</span></button>)}</div><p className="drawer-note">Ativar uma coleção inclui suas cenas na apresentação. Não há sincronização com álbuns do Google neste protótipo.</p></> : <div className="settings-list">
              <label className="setting-row"><span><strong>Tempo por cena</strong><small>Um ritmo para suas memórias</small></span><select value={preferences.duration} onChange={e => update('duration', Number(e.target.value))}><option value={6}>6 segundos</option><option value={12}>12 segundos</option><option value={20}>20 segundos</option></select></label>
              <label className="setting-row"><span><strong>Enquadramento</strong><small>{preferences.fit === 'contain' ? 'Preserva a fotografia inteira' : 'Preenche o espaço com recorte'}</small></span><select value={preferences.fit} onChange={e => update('fit', e.target.value as Preferences['fit'])}><option value="contain">Preservar</option><option value="cover">Preencher</option></select></label>
              <button className="setting-row toggle-row" role="switch" aria-checked={preferences.metadata} onClick={() => update('metadata', !preferences.metadata)}><span><strong>Informações na tela</strong><small>Título e contexto da imagem</small></span><span className="switch"><i /></span></button>
              <button className="setting-row toggle-row" role="switch" aria-checked={preferences.motion} onClick={() => update('motion', !preferences.motion)}><span><strong>Movimento suave</strong><small>Respeita a preferência de movimento reduzido</small></span><span className="switch"><i /></span></button>
              <p className="drawer-note">Preferências salvas apenas neste navegador. O tempo e os efeitos são valores de exploração visual, ainda sujeitos a avaliação.</p>
            </div>}
            <footer><span className="keyboard-key">ESC</span> Voltar à apresentação</footer>
          </aside></div> : null}
        </div>
        <div className="preview-footer"><span><span className="keyboard-key">↑ ↓ ← →</span> navegar <span className="keyboard-key">ENTER</span> selecionar <span className="keyboard-key">ESC</span> voltar <span className="keyboard-key">ESPAÇO</span> play / pausa</span><button className="text-button" onClick={fullscreen}><Icon name="expand" /> Tela cheia</button></div>
        {fullscreenError ? <p role="alert">{fullscreenError}</p> : null}
      </section>

      {lab ? <section id="laboratory" className="laboratory" aria-labelledby="lab-title"><div><p className="eyebrow">FORA DA INTERFACE DA TV</p><h2 id="lab-title">Bancada de experimentos</h2><p>Mesmas fotografias. Novos jeitos de sentir.</p></div><label>Estado simulado<select value={scenario} onChange={e => { setScenario(e.target.value as Scenario); setPanel(null); setPlaying(false); }}><option value="ready">Conteúdo disponível</option><option value="preparing">Preparando imagens</option><option value="offline">Falha de conexão</option><option value="expired">Seleção expirada</option><option value="empty">Nenhuma coleção</option></select></label><button className="outline-button" onClick={reset}><Icon name="refresh" /> Reiniciar demonstração</button><p className="lab-note">Mock independente · sem OAuth, API, player nativo ou dados pessoais.</p></section> : null}

      <section className="scene-library" aria-labelledby="scenes-title"><div className="section-heading"><div><p className="eyebrow">COMPOSIÇÕES</p><h2 id="scenes-title">Cada lembrança encontra seu espaço.</h2></div><span>03 ESTUDOS VISUAIS</span></div>
        <div className="scene-cards">{scenes.map((s, i) => <button key={s.id} className={`scene-card ${s.id === scene.id && effectiveScenario === 'ready' ? 'active' : ''}`} aria-pressed={s.id === scene.id && effectiveScenario === 'ready'} onClick={() => {
          if (!preferences.activeCollections.includes(s.collectionId)) update('activeCollections', [...preferences.activeCollections, s.collectionId]);
          const next = scenes.filter(item => preferences.activeCollections.includes(item.collectionId) || item.collectionId === s.collectionId);
          setIndex(next.findIndex(item => item.id === s.id)); setScenario('ready'); setPlaying(false); setPanel(null); setMenu(true);
        }}><div className={`card-preview layout-${s.layout}`}>{s.photos.map(p => <img key={p.id} src={p.src} alt="" loading="lazy" />)}<span className="card-badge">{['INDIVIDUAL', 'DUPLA DE RETRATOS', 'MOSAICO'][i]}</span></div><div className="card-copy"><span className="card-index">0{i + 1}</span><span><strong>{s.title}</strong><small>{s.subtitle}</small></span><span className="card-arrow"><Icon name="arrow" /></span></div></button>)}</div>
      </section>
      {!storageAvailable ? <p role="status">O armazenamento do navegador está indisponível. Os ajustes valem apenas nesta visita.</p> : null}
    </main>
    <footer className="studio-footer"><Brand /><p>Feito para olhar de novo.</p><span className="footer-version">PROTÓTIPO DE CONCEITO <span>·</span> v0.1</span></footer>
  </div>;
}
