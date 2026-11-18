import { describe, expect, test } from 'vitest';
import { callbackMatches, callbackResponseIsValid, createCodeChallenge, randomUrlSafe } from './pkce';

describe('Chrome extension PKCE helpers', () => {
  test('creates RFC 7636 S256 challenge from verifier', async () => {
    const challenge = await createCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk');
    expect(challenge).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  test('creates unpredictable URL-safe values of requested entropy length', () => {
    const state = randomUrlSafe(32);
    expect(state).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(randomUrlSafe(32)).not.toBe(state);
  });

  test('accepts only the exact HTTPS callback origin and path', () => {
    const expected = 'https://mlogpmpajiokfmjnlmehmpgphbbflpem.chromiumapp.org/bmc-auth';
    expect(callbackMatches(expected, expected)).toBe(true);
    expect(callbackMatches('https://attacker.chromiumapp.org/bmc-auth', expected)).toBe(false);
    expect(callbackMatches(`${expected}/extra`, expected)).toBe(false);
    expect(callbackResponseIsValid(`${expected}?code=one&state=expected`, expected, 'expected')).toBe(true);
    expect(callbackResponseIsValid(`${expected}?code=one&state=wrong`, expected, 'expected')).toBe(false);
    expect(callbackResponseIsValid(`${expected}?code=one&state=expected&state=expected`, expected, 'expected')).toBe(false);
  });
});
