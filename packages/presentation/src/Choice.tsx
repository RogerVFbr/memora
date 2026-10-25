export function Choice<T extends string | number>({ label, value, options, onChange, remote }: {
  label: string; value: T; options: { value: T; label: string }[]; onChange(value: T): void; remote: boolean;
}) {
  const index = Math.max(0, options.findIndex(option => option.value === value));
  const selected = options[index];
  const change = (direction: number) => onChange(options[(index + direction + options.length) % options.length].value);
  if (!remote) return <select aria-label={label} value={value} onChange={event => onChange(options.find(option => String(option.value) === event.target.value)!.value)}>{options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select>;
  return <button type="button" className="choice-control" aria-label={`${label}: ${selected.label}`} onClick={() => change(1)} onKeyDown={event => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault(); event.stopPropagation(); change(event.key === 'ArrowRight' ? 1 : -1);
  }}><span aria-hidden="true">‹</span><span>{selected.label}</span><span aria-hidden="true">›</span></button>;
}
