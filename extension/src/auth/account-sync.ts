import type { ExtensionUser } from '../shared/messages';

export type SyncStatus = 'ready' | 'signed_out' | 'sync_required' | 'synchronizing';

export function websiteEventStatus(event: 'login' | 'logout'): SyncStatus {
  return event === 'login' ? 'sync_required' : 'signed_out';
}

export function saveAllowed(status: SyncStatus): boolean {
  return status === 'ready';
}

export function profileMatchesSession(profile: ExtensionUser, sessionUserId: string): boolean {
  return Boolean(sessionUserId && profile.userId && profile.userId === sessionUserId);
}

export function displayUser(user: ExtensionUser): { greeting: string; account: string; initials: string } {
  const fullName = user.fullName?.trim();
  const email = user.email?.trim();
  const account = fullName || email || 'BringMyCart member';
  const firstName = fullName?.split(/\s+/)[0] || (email ? email.split('@')[0] : 'there');
  const initials = fullName?.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]?.toLocaleUpperCase()).join('')
    || email?.[0]?.toLocaleUpperCase() || 'B';
  return { greeting: `Hi, ${firstName}!`, account, initials };
}
