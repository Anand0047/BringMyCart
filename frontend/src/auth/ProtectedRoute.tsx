import { Navigate, Outlet } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';
import { useAuth } from './useAuth';

export function ProtectedRoute() {
  const { session, restoring } = useAuth();
  if (restoring) return <div className="grid min-h-screen place-items-center"><LoaderCircle className="animate-spin text-sky-600" aria-label="Restoring session" /></div>;
  return session ? <Outlet /> : <Navigate to="/login" replace />;
}
