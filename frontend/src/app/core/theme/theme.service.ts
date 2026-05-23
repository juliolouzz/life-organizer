import { Injectable, effect, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'life-organizer.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly modeSig = signal<ThemeMode>(readInitialMode());

  readonly mode = this.modeSig.asReadonly();

  constructor() {
    // Persist on change and apply to <html>.
    effect(() => {
      const value = this.modeSig();
      const root = document.documentElement;
      root.classList.toggle('dark', value === 'dark');
      try {
        window.localStorage.setItem(STORAGE_KEY, value);
      } catch {
        /* localStorage may be disabled; ignore */
      }
    });
  }

  toggle(): void {
    this.modeSig.update((m) => (m === 'dark' ? 'light' : 'dark'));
  }

  set(mode: ThemeMode): void {
    this.modeSig.set(mode);
  }
}

function readInitialMode(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    /* ignore */
  }
  const prefersDark =
    typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}
