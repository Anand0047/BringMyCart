import { AlertCircle, CheckCircle2, Clock3, LoaderCircle } from 'lucide-react';
import type { EnrichmentStatus } from '../api/types';

const styles: Record<EnrichmentStatus, string> = {
  PENDING: 'bg-amber-50 text-amber-800 ring-amber-200',
  PROCESSING: 'bg-sky-50 text-sky-800 ring-sky-200',
  COMPLETED: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  FAILED: 'bg-rose-50 text-rose-800 ring-rose-200'
};

export function StatusBadge({ status }: { status: EnrichmentStatus }) {
  const icon = status === 'COMPLETED' ? <CheckCircle2 size={14} /> : status === 'FAILED' ? <AlertCircle size={14} /> : status === 'PROCESSING' ? <LoaderCircle className="animate-spin" size={14} /> : <Clock3 size={14} />;
  return <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${styles[status]}`}>{icon}{status[0] + status.slice(1).toLowerCase()}</span>;
}
