'use client';

import { Moon, Sun } from 'lucide-react';
import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { Button } from '@/components/ui/button';

function subscribe(callback: () => void): () => void {
  window.addEventListener('storage', callback);
  const mql = window.matchMedia('(prefers-color-scheme: dark)');
  mql.addEventListener('change', callback);
  return () => {
    window.removeEventListener('storage', callback);
    mql.removeEventListener('change', callback);
  };
}

function getSnapshot(): 'light' | 'dark' {
  const saved = localStorage.getItem('theme');
  if (saved === 'dark' || saved === 'light') return saved;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function getServerSnapshot(): 'light' | 'dark' {
  return 'light';
}

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  const toggle = useCallback(() => {
    const current = getSnapshot();
    const next = current === 'light' ? 'dark' : 'light';
    localStorage.setItem('theme', next);
    document.documentElement.classList.toggle('dark', next === 'dark');
    window.dispatchEvent(new Event('storage'));
  }, []);

  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="テーマ切替">
      {theme === 'light' ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />}
    </Button>
  );
}
