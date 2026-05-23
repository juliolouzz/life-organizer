import { LoadingService } from './loading.service';

describe('LoadingService', () => {
  let svc: LoadingService;

  beforeEach(() => {
    svc = new LoadingService();
  });

  it('is idle initially', () => {
    expect(svc.busy()).toBe(false);
  });

  it('becomes busy after start()', () => {
    svc.start();
    expect(svc.busy()).toBe(true);
  });

  it('only goes idle after a matching stop()', () => {
    svc.start();
    svc.start();
    svc.stop();
    expect(svc.busy()).toBe(true);
    svc.stop();
    expect(svc.busy()).toBe(false);
  });

  it('counter never goes below zero', () => {
    svc.stop();
    svc.stop();
    expect(svc.busy()).toBe(false);
    svc.start();
    expect(svc.busy()).toBe(true);
  });
});
