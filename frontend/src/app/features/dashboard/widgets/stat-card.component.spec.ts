import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { AuthService } from '../../../core/auth/auth.service';
import { StatCardComponent } from './stat-card.component';

/**
 * Regression coverage for the bug shipped in feat/custom-month-boundary and
 * fixed alongside the dashboard-totals work:
 *
 *   StatCardComponent.rendered was a `computed()` reading the plain @Input
 *   `value`. Signal change-detection does not track non-signal properties,
 *   so once the first render cached a value, every subsequent parent update
 *   was ignored - the EXPENSES card kept showing last period's total even
 *   after the dashboard fetched a new range.
 *
 * These tests pin the contract: when the parent assigns a new [value], the
 * rendered text MUST update on the next change-detection pass.
 */
describe('StatCardComponent', () => {
  let fixture: ComponentFixture<StatCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatCardComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            // Currency-aware MoneyBrlPipe reads these; static stubs are enough.
            currencySymbol: signal('R$'),
            currencyLocale: signal('pt-BR'),
            currentUser: signal({ currency: 'BRL' })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StatCardComponent);
    // Use setInput so OnPush change detection picks up @Input mutations like
    // a real parent binding would (mutating component.foo directly bypasses
    // the input-tracking that OnPush views rely on).
    fixture.componentRef.setInput('icon', 'trending_up');
    fixture.componentRef.setInput('label', 'Income');
  });

  function textOf(selector: string): string {
    const el = fixture.nativeElement.querySelector(selector) as HTMLElement | null;
    return (el?.textContent ?? '').trim();
  }

  it('renders the initial value formatted via MoneyBrlPipe', () => {
    fixture.componentRef.setInput('value', 1000);
    fixture.detectChanges();
    expect(textOf('.value')).toContain('R$');
    expect(textOf('.value')).toContain('1.000,00');
  });

  it('updates the rendered amount when [value] changes (regression test)', () => {
    fixture.componentRef.setInput('value', 5423.23);
    fixture.detectChanges();
    expect(textOf('.value')).toContain('5.423,23');

    // Simulate the parent passing a new value (e.g. dashboard fetched a new period).
    fixture.componentRef.setInput('value', 2622.96);
    fixture.detectChanges();
    // Regression: rendered amount must reflect the latest [value] input,
    // not the first render. Previously this assertion failed because the
    // component's `rendered` was a computed reading a non-signal @Input
    // and stayed stuck on the first value forever.
    expect(textOf('.value')).toContain('2.622,96');
  });

  it('renders a dash when value is null AND hidden is true', () => {
    fixture.componentRef.setInput('value', null);
    fixture.componentRef.setInput('hidden', true);
    fixture.detectChanges();
    expect(textOf('.value')).toBe('—');
  });

  it('prefers formattedValue over value when both are set', () => {
    fixture.componentRef.setInput('value', 1000);
    fixture.componentRef.setInput('formattedValue', '+12.5%');
    fixture.detectChanges();
    expect(textOf('.value')).toContain('+12.5%');
    expect(textOf('.value')).not.toContain('R$');
  });

  it('shows the skeleton loader when loading is true', () => {
    fixture.componentRef.setInput('value', 1000);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBeGreaterThan(0);
  });
});
