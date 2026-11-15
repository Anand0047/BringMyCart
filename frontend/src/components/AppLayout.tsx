import { LogOut, ShoppingBag } from 'lucide-react';
import type { PropsWithChildren } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export function AppLayout({ children }: PropsWithChildren) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  async function signOut() {
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  }
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
          <Link className="flex items-center gap-2 text-lg font-bold tracking-tight text-slate-950" to="/dashboard">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-sky-600 text-white"><ShoppingBag size={19} /></span>
            BringMyCart
          </Link>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-slate-500 sm:block">{session?.email}</span>
            <button onClick={() => void signOut()} className="inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-100 hover:text-slate-950">
              <LogOut size={16} /> <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
