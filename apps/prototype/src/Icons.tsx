type IconName = 'play' | 'pause' | 'previous' | 'next' | 'grid' | 'settings' | 'close' | 'expand' | 'lab' | 'arrow' | 'check' | 'image' | 'wifi' | 'refresh';
const paths: Record<IconName, React.ReactNode> = {
  play: <path d="m9 5 11 7-11 7z" />,
  pause: <><path d="M8 5v14M16 5v14" /></>,
  previous: <><path d="m16 5-10 7 10 7zM4 5v14" /></>,
  next: <><path d="m8 5 10 7-10 7zM20 5v14" /></>,
  grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  settings: <><path d="M4 7h16M4 17h16" /><circle cx="9" cy="7" r="3" /><circle cx="16" cy="17" r="3" /></>,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  expand: <path d="M9 3H3v6m12-6h6v6M3 15v6h6m12-6v6h-6" />,
  lab: <><path d="M9 3h6m-5 0v6l-6 10q-1 2 2 2h12q3 0 2-2L14 9V3M8 14h8" /></>,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  check: <path d="m5 12 4 4L19 6" />,
  image: <><rect x="3" y="3" width="18" height="18" rx="3" /><circle cx="8" cy="8" r="1" /><path d="m3 17 6-6 4 4 3-3 5 5" /></>,
  wifi: <><path d="M3 8q9-8 18 0M6 12q6-5 12 0m-9 4q3-2 6 0M3 3l18 18" /></>,
  refresh: <><path d="M20 11a8 8 0 1 0-2 7M20 4v7h-7" /></>,
};
export function Icon({ name }: { name: IconName }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
export function Brand() {
  return <span className="brand"><span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>memora<span className="brand-period">.</span></span>;
}
