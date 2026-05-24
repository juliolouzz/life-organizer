import { Router, UrlTree } from '@angular/router';
import { TestBed } from '@angular/core/testing';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

/**
 * Regression coverage for the QA-found bug:
 *
 *   Guarded routes redirected anonymous users to /login but threw away the
 *   originally requested URL. After signing in the user landed on the
 *   default /dashboard instead of the page they were actually heading to.
 *
 * Contract:
 *  - authenticated -> true
 *  - anonymous     -> UrlTree pointing at /login with ?returnUrl=<original>
 */
describe('authGuard', () => {
  let isAuthenticated = false;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => isAuthenticated }
        }
      ]
    });
    router = TestBed.inject(Router);
  });

  function run(targetUrl: string): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      authGuard(
        // route snapshot - unused by the guard
        {} as unknown as Parameters<typeof authGuard>[0],
        // router state snapshot - the guard reads .url
        { url: targetUrl } as unknown as Parameters<typeof authGuard>[1]
      )
    ) as boolean | UrlTree;
  }

  it('allows the activation when the user is authenticated', () => {
    isAuthenticated = true;
    expect(run('/transactions')).toBe(true);
  });

  it('returns a UrlTree to /login with ?returnUrl when anonymous', () => {
    isAuthenticated = false;
    const result = run('/transactions') as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    const tree = router.parseUrl(router.serializeUrl(result));
    expect(tree.root.children['primary'].segments[0].path).toBe('login');
    expect(tree.queryParams['returnUrl']).toBe('/transactions');
  });

  it('does NOT include returnUrl when the target IS /login (avoid self-loop)', () => {
    isAuthenticated = false;
    const result = run('/login') as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.queryParams['returnUrl']).toBeUndefined();
  });
});
