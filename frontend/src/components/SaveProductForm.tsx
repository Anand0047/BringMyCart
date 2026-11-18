import { Link2, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';

interface SaveProductFormProps {
  onSave(url: string): Promise<void>;
  disabled?: boolean;
}

export function SaveProductForm({ onSave, disabled }: SaveProductFormProps) {
  const [url, setUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!url.trim()) return;
    setSubmitting(true);
    try { await onSave(url.trim()); setUrl(''); } finally { setSubmitting(false); }
  }

  return (
    <form onSubmit={submit} className="rounded-2xl border border-sky-100 bg-gradient-to-r from-sky-50 to-white p-4 shadow-sm sm:flex sm:items-end sm:gap-3">
      <label className="block flex-1 text-sm font-semibold text-slate-700">
        Save an Amazon India, Flipkart, or Myntra product
        <span className="mt-1 flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm focus-within:border-sky-500 focus-within:ring-2 focus-within:ring-sky-100">
          <Link2 size={17} className="text-slate-400" />
          <input value={url} onChange={(event) => setUrl(event.target.value)} type="url" required placeholder="Paste a supported product URL" className="min-w-0 flex-1 border-0 p-0 text-sm outline-none placeholder:text-slate-400" />
        </span>
      </label>
      <button disabled={disabled || submitting} className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-sky-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:bg-slate-300 sm:mt-0 sm:w-auto">
        <Plus size={17} /> {submitting ? 'Saving…' : 'Save product'}
      </button>
    </form>
  );
}
