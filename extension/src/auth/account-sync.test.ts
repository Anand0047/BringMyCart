import { describe, expect, it } from 'vitest';
import { displayUser, profileMatchesSession, saveAllowed, websiteEventStatus } from './account-sync';

describe('extension account synchronization decisions', () => {
  it('blocks saves while a website account change makes identity uncertain', () => {
    expect(websiteEventStatus('login')).toBe('sync_required');
    expect(websiteEventStatus('logout')).toBe('signed_out');
    expect(saveAllowed('sync_required')).toBe(false);
    expect(saveAllowed('synchronizing')).toBe(false);
    expect(saveAllowed('signed_out')).toBe(false);
    expect(saveAllowed('ready')).toBe(true);
  });

  it('accepts only backend profile identity matching the extension session', () => {
    const profile = { userId: 'user-a', email: 'a@example.com', fullName: 'Anand Raj', role: 'CUSTOMER' };
    expect(profileMatchesSession(profile, 'user-a')).toBe(true);
    expect(profileMatchesSession(profile, 'user-b')).toBe(false);
  });

  it('provides safe identity text for named and legacy accounts', () => {
    expect(displayUser({ userId: 'id', email: 'anand@example.com', fullName: 'Anand Raj', role: 'CUSTOMER' }))
      .toEqual({ greeting: 'Hi, Anand!', account: 'Anand Raj', initials: 'AR' });
    expect(displayUser({ userId: 'id', email: 'legacy@example.com', fullName: null, role: 'CUSTOMER' }))
      .toEqual({ greeting: 'Hi, legacy!', account: 'legacy@example.com', initials: 'L' });
  });
});
