'use client';

import { Search, X } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';

import { Input } from '@/components/ui/input';

interface SearchResult {
  productId: string;
  name: string;
  brand: string;
  price: number;
}

interface SearchBarProps {
  className?: string;
  placeholder?: string;
  variant?: 'header' | 'standalone';
}

export function SearchBar({
  className = '',
  placeholder = 'スキー用品を検索...',
  variant = 'header',
}: SearchBarProps) {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<SearchResult[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const fetchSuggestions = useCallback(async (q: string) => {
    if (q.length < 2) {
      setSuggestions([]);
      return;
    }
    setIsLoading(true);
    try {
      const res = await fetch(`/api/search/autocomplete?query=${encodeURIComponent(q)}`);
      if (res.ok) {
        const data = await res.json();
        setSuggestions(data.results || []);
      }
    } catch {
      // Silently fail autocomplete
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => fetchSuggestions(query), 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query, fetchSuggestions]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      router.push(`/search?q=${encodeURIComponent(query.trim())}` as never);
      setShowSuggestions(false);
    }
  };

  const handleSelectSuggestion = (productId: string) => {
    router.push(`/product/${productId}` as never);
    setShowSuggestions(false);
    setQuery('');
  };

  const isHeader = variant === 'header';

  const suggestionsId = 'search-suggestions-listbox';
  const hasSuggestions = showSuggestions && suggestions.length > 0;

  return (
    <form onSubmit={handleSubmit} className={`relative ${className}`} role="search">
      <div className="relative">
        <Search
          className={`absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 ${
            isHeader ? 'text-white/60' : 'text-muted-foreground'
          }`}
          aria-hidden="true"
        />
        <Input
          ref={inputRef}
          type="search"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setShowSuggestions(true);
          }}
          onFocus={() => setShowSuggestions(true)}
          onBlur={() => setTimeout(() => setShowSuggestions(false), 200)}
          placeholder={placeholder}
          aria-label="商品検索"
          role="combobox"
          aria-expanded={hasSuggestions}
          aria-controls={suggestionsId}
          aria-autocomplete="list"
          className={`pr-10 pl-10 ${
            isHeader
              ? 'border-white/20 bg-white/10 text-white placeholder:text-white/60 focus:bg-white/20'
              : ''
          }`}
        />
        {query && (
          <button
            type="button"
            onClick={() => {
              setQuery('');
              setSuggestions([]);
              inputRef.current?.focus();
            }}
            className={`absolute top-1/2 right-3 -translate-y-1/2 ${
              isHeader
                ? 'text-white/60 hover:text-white'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <X className="h-4 w-4" />
            <span className="sr-only">検索をクリア</span>
          </button>
        )}
      </div>

      {/* Suggestions dropdown */}
      {hasSuggestions && (
        <div
          className="bg-popover absolute top-full left-0 z-50 mt-1 w-full rounded-md border shadow-lg"
          aria-live="polite"
        >
          <ul id={suggestionsId} role="listbox" className="max-h-64 overflow-auto py-1">
            {suggestions.map((item) => (
              <li key={item.productId} role="option" aria-selected={false}>
                <button
                  type="button"
                  onMouseDown={() => handleSelectSuggestion(item.productId)}
                  className="hover:bg-muted flex w-full items-center gap-3 px-4 py-2 text-left text-sm"
                >
                  <Search className="text-muted-foreground h-3 w-3 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{item.name}</p>
                    <p className="text-muted-foreground text-xs">{item.brand}</p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {showSuggestions && isLoading && query.length >= 2 && (
        <div className="bg-popover text-muted-foreground absolute top-full left-0 z-50 mt-1 w-full rounded-md border p-4 text-center text-sm shadow-lg">
          検索中...
        </div>
      )}
    </form>
  );
}
