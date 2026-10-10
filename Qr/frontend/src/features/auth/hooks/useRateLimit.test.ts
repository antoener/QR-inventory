import { act, renderHook } from '@testing-library/react';
import { useRateLimit } from './useRateLimit';

const TEST_KEY = 'test-key';

describe('useRateLimit', () => {
  beforeEach(() => {
    localStorage.clear();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
    localStorage.clear();
  });

  it('debe iniciar con estado inicial sin rate limit', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    expect(result.current.isRateLimited).toBe(false);
    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.progress).toBe(0);
  });

  it('debe iniciar countdown y actualizar estado', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(10);
    });

    expect(result.current.isRateLimited).toBe(true);
    expect(result.current.remainingSeconds).toBe(10);
    expect(result.current.progress).toBe(0);
  });

  it('debe decrementar remainingSeconds cada segundo', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(10);
    });

    act(() => {
      jest.advanceTimersByTime(3000);
    });

    expect(result.current.remainingSeconds).toBe(7);
    expect(result.current.progress).toBe(30);
  });

  it('debe resetear cuando el tiempo llega a 0', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(2);
    });

    act(() => {
      jest.advanceTimersByTime(2000);
    });

    expect(result.current.isRateLimited).toBe(false);
    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.progress).toBe(0);
  });

  it('debe persistir en localStorage', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(10);
    });

    const stored = localStorage.getItem(`rateLimit:${TEST_KEY}`);
    expect(stored).not.toBeNull();

    const parsed = JSON.parse(stored!);
    expect(parsed.totalSeconds).toBe(10);
    expect(parsed.endTime).toBeGreaterThan(Date.now());
  });

  it('debe restaurar estado desde localStorage al montar', () => {
    const endTime = Date.now() + 5000;
    localStorage.setItem(`rateLimit:${TEST_KEY}`, JSON.stringify({ endTime, totalSeconds: 10 }));

    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    expect(result.current.isRateLimited).toBe(true);
    expect(result.current.remainingSeconds).toBe(5);
    expect(result.current.progress).toBe(50);
  });

  it('debe limpiar localStorage si el tiempo ya expiró al montar', () => {
    const endTime = Date.now() - 1000;
    localStorage.setItem(`rateLimit:${TEST_KEY}`, JSON.stringify({ endTime, totalSeconds: 10 }));

    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    expect(result.current.isRateLimited).toBe(false);
    expect(localStorage.getItem(`rateLimit:${TEST_KEY}`)).toBeNull();
  });

  it('debe resetear manualmente con reset()', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(10);
    });

    act(() => {
      result.current.reset();
    });

    expect(result.current.isRateLimited).toBe(false);
    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.progress).toBe(0);
    expect(localStorage.getItem(`rateLimit:${TEST_KEY}`)).toBeNull();
  });

  it('debe manejar keys diferentes independientemente', () => {
    const { result: result1 } = renderHook(() => useRateLimit('key1'));
    const { result: result2 } = renderHook(() => useRateLimit('key2'));

    act(() => {
      result1.current.startCountdown(10);
    });

    expect(result1.current.isRateLimited).toBe(true);
    expect(result2.current.isRateLimited).toBe(false);

    act(() => {
      result2.current.startCountdown(5);
    });

    expect(result1.current.remainingSeconds).toBe(10);
    expect(result2.current.remainingSeconds).toBe(5);
  });

  it('debe calcular progress correctamente (0% al inicio, 100% al final)', () => {
    const { result } = renderHook(() => useRateLimit(TEST_KEY));

    act(() => {
      result.current.startCountdown(10);
    });

    expect(result.current.progress).toBe(0);

    act(() => {
      jest.advanceTimersByTime(5000);
    });

    expect(result.current.progress).toBe(50);

    act(() => {
      jest.advanceTimersByTime(5000);
    });

    expect(result.current.isRateLimited).toBe(false);
    expect(result.current.progress).toBe(0);
  });
});