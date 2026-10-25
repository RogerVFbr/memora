import { useState } from 'react';
import type { ReactNode } from 'react';
import { Brand, Icon } from './Icons';
import { Choice } from './Choice';
import type { Catalog, Photo, Preferences } from './model';
import type { PresentationController } from './usePresentation';

function PhotoSlot({ photo, fit }: { photo: Photo; fit: Preferences['fit'] }) {
  const [failed, setFailed] = useState(false);
  return <div className="photo-slot">{failed ? <span className="asset-error">Imagem indisponível</span> : <img src={photo.src} alt={photo.alt} width={photo.width} height={photo.height} style={{ objectFit: fit }} draggable={false} onError={() => setFailed(true)} />}</div>;
}

export function Presentation({ state, catalog, version, unavailable, remote = false }: {
  state: PresentationController; catalog: Catalog; version?: string; unavailable?: ReactNode; remote?: boolean;
}) {
  const { scenes, collections } = catalog;
  const { preferences, update, playing, menu, setMenu, panel, setIndex, stage, dock, drawer,
    scene, effectiveScenario, isRunning, move, openPanel, closePanel, closeMenu, start, onStageKey } = state;
  return (
        <div className={`tv-stage ${panel ? 'has-panel' : ''}`} ref={stage} tabIndex={0} aria-label="Apresentação de fotos. Enter abre controles, setas navegam, Escape volta." onKeyDown={onStageKey}>
          {effectiveScenario === 'ready' ? <>
            <div key={scene.id} className={`scene layout-${scene.layout} fit-${preferences.fit} ${preferences.motion ? 'motion-enabled' : ''} ${isRunning ? 'running' : ''}`} style={{ '--scene-duration': `${preferences.duration}s` } as React.CSSProperties}>
              {scene.photos.map(photo => <PhotoSlot key={photo.id} photo={photo} fit={preferences.fit} />)}
            </div>
            <div className="scene-shade" />
            {menu || panel ? <div className="tv-topline"><Brand /><span className="tv-demo">ACERVO DEMONSTRATIVO{version ? ` · ${version}` : ''}</span></div> : null}
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
            <div className="scene-number"><span>{String(scenes.indexOf(scene) + 1).padStart(2, '0')}</span> / {String(scenes.length).padStart(2, '0')}</div>
            {isRunning ? <div key={`${scene.id}-${preferences.duration}`} className="scene-progress" style={{ animationDuration: `${preferences.duration}s` }} /> : null}
          </> : effectiveScenario === 'empty' ? <div className="state-screen" role="status"><div className="state-symbol"><Icon name="grid" /></div><p className="eyebrow">SUA PRÓXIMA HISTÓRIA</p><h2>Dê espaço às suas memórias.</h2><p>Ative uma coleção demonstrativa para começar.</p><button className="accent-button" onClick={e => openPanel('collections', e.currentTarget)}>Escolher coleções<Icon name="arrow" /></button></div> : unavailable}

          {panel ? <div className="drawer-scrim"><aside className="drawer" ref={drawer} role="dialog" aria-modal="true" aria-labelledby="drawer-title">
            <header><div><p className="eyebrow">DO SEU JEITO</p><h2 id="drawer-title">{panel === 'collections' ? 'Suas coleções' : 'Ajustes da apresentação'}</h2></div><button className="icon-button" aria-label="Fechar painel" onClick={closePanel}><Icon name="close" /></button></header>
            {panel === 'collections' ? <><p className="drawer-description">Escolha o que faz parte deste momento.</p><div className="collection-list">{collections.map(collection => <button key={collection.id} className="collection-card" aria-pressed={preferences.activeCollections.includes(collection.id)} onClick={() => { update('activeCollections', preferences.activeCollections.includes(collection.id) ? preferences.activeCollections.filter(id => id !== collection.id) : [...preferences.activeCollections, collection.id]); setIndex(0); }}><img src={collection.cover.src} alt="" /><span><strong>{collection.name}</strong><small>{collection.description}</small><small>Seleção local · demonstração</small></span><span className="collection-check">{preferences.activeCollections.includes(collection.id) ? <Icon name="check" /> : null}</span></button>)}</div><p className="drawer-note">Ativar uma coleção inclui suas cenas na apresentação. As coleções são demonstrativas, sem sincronização com álbuns do Google.</p></> : <div className="settings-list">
              <label className="setting-row"><span><strong>Aparência</strong><small>Direção visual da apresentação</small></span><Choice label="Aparência" remote={remote} value={preferences.theme} onChange={value => update('theme', value)} options={[{value:'cinema',label:'Cinema acolhedor'},{value:'gallery',label:'Galeria neutra'}]} /></label>
              <label className="setting-row"><span><strong>Tempo por cena</strong><small>Um ritmo para suas memórias</small></span><Choice label="Tempo por cena" remote={remote} value={preferences.duration} onChange={value => update('duration', value)} options={[6,12,20].map(value => ({value,label:`${value} segundos`}))} /></label>
              <label className="setting-row"><span><strong>Enquadramento</strong><small>{preferences.fit === 'contain' ? 'Preserva a fotografia inteira' : 'Preenche o espaço com recorte'}</small></span><Choice label="Enquadramento" remote={remote} value={preferences.fit} onChange={value => update('fit', value)} options={[{value:'contain',label:'Preservar'},{value:'cover',label:'Preencher'}]} /></label>
              <button className="setting-row toggle-row" role="switch" aria-checked={preferences.metadata} onClick={() => update('metadata', !preferences.metadata)}><span><strong>Informações na tela</strong><small>Título e contexto da imagem</small></span><span className="switch"><i /></span></button>
              <button className="setting-row toggle-row" role="switch" aria-checked={preferences.motion} onClick={() => update('motion', !preferences.motion)}><span><strong>Movimento suave</strong><small>Respeita a preferência de movimento reduzido</small></span><span className="switch"><i /></span></button>
              <p className="drawer-note">Preferências demonstrativas salvas neste dispositivo.</p>
            </div>}
            <footer><span className="keyboard-key">ESC</span> Voltar à apresentação</footer>
          </aside></div> : null}
        </div>
  );
}
