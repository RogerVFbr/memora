import { useState } from 'react';
import { Brand, Icon } from '@memora/presentation/Icons';
import { demoCatalog, scenes } from '@memora/presentation/demoCatalog';
import type { Scenario } from '@memora/presentation/model';
import { usePresentation } from '@memora/presentation/usePresentation';
import { Presentation } from '@memora/presentation/Presentation';

const scenarioCopy = {
  preparing: { eyebrow: 'PREPARANDO A APRESENTAÇÃO', title: 'Cada memória no seu tempo.', body: 'Estamos preparando as próximas imagens. Neste laboratório, você controla quando continuar.', action: 'Concluir preparação simulada', icon: 'image' },
  offline: { eyebrow: 'CONEXÃO INTERROMPIDA', title: 'Uma pequena pausa.', body: 'Não foi possível carregar a próxima imagem. Verifique a conexão para continuar.', action: 'Simular reconexão', icon: 'wifi' },
  expired: { eyebrow: 'ACESSO À SELEÇÃO ENCERRADO', title: 'Vamos escolher de novo?', body: 'O acesso a esta seleção terminou. Será necessário selecionar conteúdo novamente para continuar.', action: 'Simular nova seleção', icon: 'refresh' },
  empty: { eyebrow: 'SUA PRÓXIMA HISTÓRIA', title: 'Dê espaço às suas memórias.', body: 'Ative uma coleção para começar. As coleções deste protótipo são demonstrativas.', action: 'Escolher coleções', icon: 'grid' },
} as const;

export default function App() {
  const state = usePresentation(demoCatalog, 'memora.prototype.preferences.v1');
  const { preferences, update, setIndex, setPlaying, setMenu, setPanel, scenario,
    setScenario, storageAvailable, fullscreenError, scene, effectiveScenario, reset, fullscreen } = state;
  const [lab, setLab] = useState(false);
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
        <Presentation state={state} catalog={demoCatalog} unavailable={effectiveScenario !== 'ready' && effectiveScenario !== 'empty' ? <div className="state-screen" role="status"><div className="state-symbol"><Icon name={scenarioCopy[effectiveScenario].icon} /></div><p className="eyebrow">{scenarioCopy[effectiveScenario].eyebrow}</p><h2>{scenarioCopy[effectiveScenario].title}</h2><p>{scenarioCopy[effectiveScenario].body}</p><button className="accent-button" onClick={() => {
            setScenario('ready'); setMenu(true);
          }}>{scenarioCopy[effectiveScenario].action}<Icon name="arrow" /></button><span className="simulation-label">SIMULAÇÃO · nenhuma operação no Google Fotos</span></div> : null} />
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
