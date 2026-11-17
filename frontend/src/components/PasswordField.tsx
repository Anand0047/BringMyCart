import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

export function PasswordField({ label, value, onChange, autoComplete, id }: {
  label: string;
  value: string;
  onChange(value: string): void;
  autoComplete: string;
  id?: string;
}) {
  const [visible, setVisible] = useState(false);
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, '-');
  return <div className="block text-sm font-semibold text-slate-700">
    <label htmlFor={inputId}>{label}</label>
    <span className="relative mt-1.5 block">
      <input id={inputId} required type={visible ? 'text' : 'password'} autoComplete={autoComplete} value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 pr-12 font-normal outline-none transition focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
      <button type="button" aria-label={`${visible ? 'Hide' : 'Show'} ${label.toLowerCase()}`}
        aria-pressed={visible} onClick={() => setVisible((shown) => !shown)}
        className="absolute inset-y-0 right-2 grid w-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-500">
        {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
      </button>
    </span>
  </div>;
}
