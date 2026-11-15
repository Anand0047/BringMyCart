import { LoaderCircle, PackageOpen } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { errorMessage } from '../api/client';
import { savedProductsApi } from '../api/savedProductsApi';
import type { PageResponse, SavedProduct } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { ProductCard } from '../components/ProductCard';
import { SaveProductForm } from '../components/SaveProductForm';

export function DashboardPage() {
  const [page, setPage] = useState<PageResponse<SavedProduct> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async (requestedPage = pageNumber, quiet = false) => {
    if (!quiet) setLoading(true);
    try { setPage(await savedProductsApi.list(requestedPage)); setError(null); }
    catch (exception) { setError(errorMessage(exception, 'Unable to load your saved products.')); }
    finally { if (!quiet) setLoading(false); }
  }, [pageNumber]);

  useEffect(() => { void load(pageNumber); }, [load, pageNumber]);

  const needsPolling = useMemo(() => page?.content.some((product) => product.enrichmentStatus === 'PENDING' || product.enrichmentStatus === 'PROCESSING') ?? false, [page]);
  useEffect(() => {
    if (!needsPolling) return;
    const timer = window.setInterval(() => { void load(pageNumber, true); }, 10_000);
    return () => window.clearInterval(timer);
  }, [load, needsPolling, pageNumber]);

  async function save(url: string) {
    try {
      await savedProductsApi.save(url);
      setNotice('Product saved. Metadata is being retrieved in the background.');
      setPageNumber(0);
      await load(0, true);
    } catch (exception) { setError(errorMessage(exception, 'Unable to save this product.')); }
  }

  async function refresh(product: SavedProduct) {
    setBusyId(product.id);
    try {
      await savedProductsApi.refresh(product.id);
      setNotice('Metadata refresh started.');
      await load(pageNumber, true);
    } catch (exception) { setError(errorMessage(exception, 'Unable to refresh this product.')); }
    finally { setBusyId(null); }
  }

  async function remove(product: SavedProduct) {
    if (!window.confirm(`Remove ${product.title ?? 'this product'} from your saved products?`)) return;
    setBusyId(product.id);
    try {
      await savedProductsApi.remove(product.id);
      setNotice('Product removed.');
      await load(pageNumber, true);
    } catch (exception) { setError(errorMessage(exception, 'Unable to remove this product.')); }
    finally { setBusyId(null); }
  }

  return <AppLayout>
    <section className="mb-7 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-semibold uppercase tracking-widest text-sky-700">Centralized wishlist</p><h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">Saved products</h1><p className="mt-2 text-slate-600">Keep your Amazon India finds in one place.</p></div>{page && <p className="text-sm text-slate-500">{page.totalElements} saved {page.totalElements === 1 ? 'product' : 'products'}</p>}</section>
    <SaveProductForm onSave={save} disabled={loading} />
    {notice && <p role="status" className="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}
    {loading && !page ? <div className="grid min-h-64 place-items-center"><LoaderCircle className="animate-spin text-sky-600" aria-label="Loading saved products" /></div> : page?.content.length === 0 ? <EmptyState /> : <>
      <div className="mt-7 grid auto-rows-fr gap-5 sm:grid-cols-2 xl:grid-cols-3">{page?.content.map((product) => <ProductCard key={product.id} product={product} busy={busyId === product.id} onRefresh={refresh} onDelete={remove} />)}</div>
      {page && page.totalPages > 1 && <nav aria-label="Saved product pages" className="mt-8 flex items-center justify-center gap-3"><button disabled={page.first} onClick={() => setPageNumber(page.page - 1)} className="rounded-lg border px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:text-slate-300">Previous</button><span className="text-sm text-slate-600">Page {page.page + 1} of {page.totalPages}</span><button disabled={page.last} onClick={() => setPageNumber(page.page + 1)} className="rounded-lg border px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:text-slate-300">Next</button></nav>}
    </>}
  </AppLayout>;
}

function EmptyState() {
  return <div className="mt-7 grid min-h-64 place-items-center rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center"><div><PackageOpen className="mx-auto text-slate-400" size={38} /><h2 className="mt-3 font-bold text-slate-800">Your wishlist is ready</h2><p className="mt-1 text-sm text-slate-500">Paste an Amazon India product URL above to save your first item.</p></div></div>;
}
