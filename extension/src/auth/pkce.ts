export function randomUrlSafe(bytes = 32): string {
  const buffer = crypto.getRandomValues(new Uint8Array(bytes));
  let binary = '';
  for (const byte of buffer) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export async function createCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  let binary = '';
  for (const byte of new Uint8Array(digest)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export function callbackMatches(actual: string, expected: string): boolean {
  try {
    const a = new URL(actual); const e = new URL(expected);
    return a.protocol === 'https:' && a.origin === e.origin && a.pathname === e.pathname
      && !a.search && !a.hash && !e.search && !e.hash;
  } catch { return false; }
}

export function callbackResponseIsValid(callback: string, expected: string, state: string): boolean {
  try {
    const actual = new URL(callback); const target = new URL(expected);
    return actual.protocol === 'https:' && actual.origin === target.origin && actual.pathname === target.pathname
      && actual.searchParams.size === 2 && actual.searchParams.get('state') === state
      && Boolean(actual.searchParams.get('code')) && !actual.hash;
  } catch { return false; }
}
