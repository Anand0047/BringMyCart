import { ExternalLink, RefreshCw, Trash2, Star, ImageOff } from 'lucide-react';
import type { SavedProduct } from '../api/types';
import { StatusBadge } from './StatusBadge';

interface ProductCardProps {
  product: SavedProduct;
  onRefresh(product: SavedProduct): void;
  onDelete(product: SavedProduct): void;
  busy?: boolean;
}

function price(value: number | null, currency: string | null) {
  if (value === null) return null;
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency ?? 'INR', maximumFractionDigits: 2 }).format(value);
}

function timestamp(value: string | null) {
  return value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : null;
}

export function ProductCard({ product, onRefresh, onDelete, busy }: ProductCardProps) {
  const pending = product.enrichmentStatus === 'PENDING' || product.enrichmentStatus === 'PROCESSING';
  const finalPrice = price(product.finalPrice, product.currency);
  const originalPrice = price(product.initialPrice, product.currency);
  const fetched = timestamp(product.metadataFetchedAt);
  const canStrike = product.initialPrice !== null && product.finalPrice !== null && product.initialPrice > product.finalPrice;
  const refreshLabel = product.enrichmentStatus === 'FAILED' ? 'Retry' : 'Refresh';

  return (
    <article className="flex h-full min-h-[31rem] min-w-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
      <div className="relative grid h-52 shrink-0 place-items-center overflow-hidden bg-slate-100 p-5">
        {product.imageUrl ? <img className="max-h-full max-w-full object-contain" src={product.imageUrl} alt={product.title ?? `Amazon product ${product.asin}`} /> : <ImageOff className="text-slate-400" size={36} aria-label="Product image unavailable" />}
        <div className="absolute left-3 top-3"><StatusBadge status={product.enrichmentStatus} /></div>
      </div>
      <div className="flex min-w-0 flex-1 flex-col p-4">
        <p className="truncate text-xs font-semibold uppercase tracking-wide text-sky-700">{product.brand ?? 'Amazon India product'}</p>
        <h2 className="mt-1 min-h-12 break-words text-base font-bold leading-6 text-slate-900 line-clamp-2">{product.title ?? `Product metadata is being retrieved (${product.asin})`}</h2>
        <div className="mt-3 flex min-h-14 flex-wrap content-start items-baseline gap-x-2 gap-y-1">
          {finalPrice && <span className="text-xl font-bold leading-7 text-slate-950">{finalPrice}</span>}
          {canStrike && <span className="text-sm text-slate-400 line-through">{originalPrice}</span>}
          {product.discountPercentage !== null && product.discountPercentage > 0 && <span className="rounded-md bg-emerald-50 px-1.5 py-1 text-xs font-bold text-emerald-700">{product.discountPercentage}% OFF</span>}
          {!finalPrice && <span className="pt-1 text-sm text-slate-500">Price unavailable</span>}
        </div>
        <div className="mt-1 flex min-h-12 flex-wrap content-start items-center gap-x-3 gap-y-1 text-sm text-slate-600">
          {product.rating !== null && <span className="inline-flex items-center gap-1"><Star size={15} className="fill-amber-400 text-amber-400" />{product.rating.toFixed(1)}</span>}
          {product.reviewsCount !== null && <span>{product.reviewsCount.toLocaleString('en-IN')} ratings</span>}
          {product.availability && <span className="font-medium text-emerald-700">{product.availability}</span>}
        </div>
        <p className="mt-2 min-h-8 text-xs leading-4 text-slate-500">{fetched ? `Last checked ${fetched}` : pending ? 'Retrieving permitted product metadata…' : product.enrichmentStatus === 'FAILED' ? 'Metadata retrieval failed. You can retry manually.' : 'Metadata unavailable.'}</p>
        <div className="mt-auto grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-2 pt-4">
          <a className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={product.canonicalUrl} target="_blank" rel="noreferrer"><ExternalLink size={15} /> Amazon</a>
          <button onClick={() => onRefresh(product)} disabled={busy || pending} aria-label={`${refreshLabel} ${product.title ?? product.asin}`} className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"><RefreshCw className={busy ? 'animate-spin' : ''} size={18} /></button>
          <button onClick={() => onDelete(product)} disabled={busy} aria-label={`Delete ${product.title ?? product.asin}`} className="rounded-lg border border-rose-100 p-2 text-rose-600 hover:bg-rose-50 disabled:cursor-not-allowed disabled:text-rose-300"><Trash2 size={18} /></button>
        </div>
      </div>
    </article>
  );
}
