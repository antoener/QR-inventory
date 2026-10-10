import { useState, useEffect, useCallback } from 'react';

interface StoredRateLimit {
  endTime: number;
  totalSeconds: number;
}

interface UseRateLimitReturn {
  isRateLimited: boolean;
  remainingSeconds: number;
  progress: number;
  startCountdown: (totalSeconds: number, key?: 'login' | 'forgot-password') => void;
  reset: () => void;
}

const STORAGE_PREFIX = 'rateLimit:';
const LOGIN_KEY = 'login';
const FORGOT_PASSWORD_KEY = 'forgot-password';

function getStorageKey(key: string): string {
  return `${STORAGE_PREFIX}${key}`;
}

function loadFromStorage(storageKey: string): StoredRateLimit | null {
  try {
    const stored = localStorage.getItem(storageKey);
    if (!stored) return null;
    const parsed = JSON.parse(stored) as StoredRateLimit;
    if (Date.now() >= parsed.endTime) {
      localStorage.removeItem(storageKey);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function saveToStorage(storageKey: string, endTime: number, total: number): void {
  try {
    localStorage.setItem(storageKey, JSON.stringify({ endTime, totalSeconds: total }));
  } catch {
    // Ignore localStorage errors
  }
}

function clearStorage(storageKey: string): void {
  try {
    localStorage.removeItem(storageKey);
  } catch {
    // Ignore localStorage errors
  }
}

function saveLogin(endTime: number, total: number): void {
  saveToStorage(getStorageKey(LOGIN_KEY), endTime, total);
}

function saveForgot(endTime: number, total: number): void {
  saveToStorage(getStorageKey(FORGOT_PASSWORD_KEY), endTime, total);
}

function clearLogin(): void {
  clearStorage(getStorageKey(LOGIN_KEY));
}

function clearForgot(): void {
  clearStorage(getStorageKey(FORGOT_PASSWORD_KEY));
}

export function useRateLimit(key: string): UseRateLimitReturn {
  const storageKey = getStorageKey(key);

  const [isRateLimited, setIsRateLimited] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [progress, setProgress] = useState(0);

  const load = useCallback((): StoredRateLimit | null => {
    return loadFromStorage(storageKey);
  }, [storageKey]);

  const save = useCallback((endTime: number, total: number) => {
    saveToStorage(storageKey, endTime, total);
  }, [storageKey]);

  const clear = useCallback(() => {
    clearStorage(storageKey);
  }, [storageKey]);

  const startCountdown = useCallback((seconds: number) => {
    const endTime = Date.now() + seconds * 1000;
    setRemainingSeconds(seconds);
    setProgress(0);
    setIsRateLimited(true);
    save(endTime, seconds);
  }, [save]);

  const reset = useCallback(() => {
    setIsRateLimited(false);
    setRemainingSeconds(0);
    setProgress(0);
    clear();
  }, [clear]);

  useEffect(() => {
    const stored = load();
    if (stored) {
      const remaining = Math.ceil((stored.endTime - Date.now()) / 1000);
      if (remaining > 0) {
        setRemainingSeconds(remaining);
        setProgress(((stored.totalSeconds - remaining) / stored.totalSeconds) * 100);
        setIsRateLimited(true);
      } else {
        clear();
      }
    }
  }, [load, clear]);

  useEffect(() => {
    if (!isRateLimited) return;

    const interval = setInterval(() => {
      const stored = load();
      if (!stored) {
        reset();
        return;
      }

      const remaining = Math.ceil((stored.endTime - Date.now()) / 1000);
      if (remaining <= 0) {
        reset();
        return;
      }

      setRemainingSeconds(remaining);
      setProgress(((stored.totalSeconds - remaining) / stored.totalSeconds) * 100);
    }, 1000);

    return () => clearInterval(interval);
  }, [isRateLimited, load, reset]);

  return {
    isRateLimited,
    remainingSeconds,
    progress,
    startCountdown,
    reset,
  };
}

export function useAuthRateLimit(): UseRateLimitReturn {
  const loginStorageKey = getStorageKey(LOGIN_KEY);
  const forgotStorageKey = getStorageKey(FORGOT_PASSWORD_KEY);

  const [isRateLimited, setIsRateLimited] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [progress, setProgress] = useState(0);

  const loadLogin = useCallback((): StoredRateLimit | null => {
    return loadFromStorage(loginStorageKey);
  }, [loginStorageKey]);

  const loadForgot = useCallback((): StoredRateLimit | null => {
    return loadFromStorage(forgotStorageKey);
  }, [forgotStorageKey]);

  const startCountdown = useCallback((seconds: number, key: 'login' | 'forgot-password' = 'login') => {
    const endTime = Date.now() + seconds * 1000;
    setRemainingSeconds(seconds);
    setProgress(0);
    setIsRateLimited(true);
    if (key === 'login') {
      saveLogin(endTime, seconds);
    } else {
      saveForgot(endTime, seconds);
    }
  }, []);

  const reset = useCallback(() => {
    setIsRateLimited(false);
    setRemainingSeconds(0);
    setProgress(0);
    clearLogin();
    clearForgot();
  }, []);

  useEffect(() => {
    const loginStored = loadLogin();
    const forgotStored = loadForgot();

    const loginRemaining = loginStored
      ? Math.ceil((loginStored.endTime - Date.now()) / 1000)
      : 0;
    const forgotRemaining = forgotStored
      ? Math.ceil((forgotStored.endTime - Date.now()) / 1000)
      : 0;

    const hasLogin = loginRemaining > 0;
    const hasForgot = forgotRemaining > 0;

    if (hasLogin || hasForgot) {
      setIsRateLimited(true);

      if (hasLogin && (!hasForgot || loginRemaining >= forgotRemaining)) {
        setRemainingSeconds(loginRemaining);
        if (loginStored) {
          setProgress(((loginStored.totalSeconds - loginRemaining) / loginStored.totalSeconds) * 100);
        }
      } else if (hasForgot) {
        setRemainingSeconds(forgotRemaining);
        if (forgotStored) {
          setProgress(((forgotStored.totalSeconds - forgotRemaining) / forgotStored.totalSeconds) * 100);
        }
      }
    } else {
      if (loginStored && loginRemaining <= 0) clearLogin();
      if (forgotStored && forgotRemaining <= 0) clearForgot();
    }
  }, [loadLogin, loadForgot]);

  useEffect(() => {
    if (!isRateLimited) return;

    const interval = setInterval(() => {
      const loginStored = loadLogin();
      const forgotStored = loadForgot();

      const loginRemaining = loginStored
        ? Math.ceil((loginStored.endTime - Date.now()) / 1000)
        : 0;
      const forgotRemaining = forgotStored
        ? Math.ceil((forgotStored.endTime - Date.now()) / 1000)
        : 0;

      const hasLogin = loginRemaining > 0;
      const hasForgot = forgotRemaining > 0;

      if (!hasLogin && !hasForgot) {
        reset();
        return;
      }

      if (hasLogin && (!hasForgot || loginRemaining >= forgotRemaining)) {
        setRemainingSeconds(loginRemaining);
        if (loginStored) {
          setProgress(((loginStored.totalSeconds - loginRemaining) / loginStored.totalSeconds) * 100);
        }
      } else if (hasForgot) {
        setRemainingSeconds(forgotRemaining);
        if (forgotStored) {
          setProgress(((forgotStored.totalSeconds - forgotRemaining) / forgotStored.totalSeconds) * 100);
        }
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [isRateLimited, loadLogin, loadForgot, reset]);

  return {
    isRateLimited,
    remainingSeconds,
    progress,
    startCountdown,
    reset,
  };
}