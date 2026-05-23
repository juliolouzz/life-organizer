import { TokenStore } from './token-store';

describe('TokenStore', () => {
  let store: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    store = new TokenStore();
  });

  it('returns null when no refresh token has been stored', () => {
    expect(store.readRefresh()).toBeNull();
  });

  it('persists and reads back a refresh token', () => {
    store.writeRefresh('abc.def.ghi');
    expect(store.readRefresh()).toBe('abc.def.ghi');
  });

  it('clear() removes the token', () => {
    store.writeRefresh('value');
    store.clear();
    expect(store.readRefresh()).toBeNull();
  });
});
