'use client';

import { Loader2, Search, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export interface PickedProduct {
  id: string;
  name: string;
  price?: number | null;
}

interface ProductPickerProps {
  /** 選択済み商品 */
  selected: PickedProduct[];
  /** 選択リストを更新するコールバック */
  onChange: (products: PickedProduct[]) => void;
  /** 入力欄のプレースホルダ */
  placeholder?: string;
  /** 1 件のみに制限したい場合 true */
  singleSelect?: boolean;
}

/**
 * 商品名でインクリメンタル検索し、候補から選択して ID を取得するピッカー。
 * 内部的には `/api/admin/products?keyword=...&size=10` を叩き、name と id を抽出する。
 */
export function ProductPicker({
  selected,
  onChange,
  placeholder = '商品名で検索（例: ATOMIC）',
  singleSelect = false,
}: ProductPickerProps) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<PickedProduct[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const k = keyword.trim();
    if (k.length < 2) {
      setResults([]);
      setOpen(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetch(
          `/api/admin/products?keyword=${encodeURIComponent(k)}&size=10`,
          { cache: 'no-store' },
        );
        const data = await res.json();
        const list = Array.isArray(data?.content) ? data.content : [];
        setResults(
          list.map((p: { id: string; name: string; price?: number }) => ({
            id: p.id,
            name: p.name,
            price: p.price ?? null,
          })),
        );
        setOpen(true);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [keyword]);

  const isSelected = (id: string) => selected.some((p) => p.id === id);

  const addProduct = (p: PickedProduct) => {
    if (singleSelect) {
      onChange([p]);
    } else if (!isSelected(p.id)) {
      onChange([...selected, p]);
    }
    setKeyword('');
    setResults([]);
    setOpen(false);
  };

  const removeProduct = (id: string) => {
    onChange(selected.filter((p) => p.id !== id));
  };

  return (
    <div className="space-y-2">
      <div className="relative">
        <div className="relative">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={placeholder}
            className="pl-8"
            onFocus={() => results.length > 0 && setOpen(true)}
          />
          {loading && (
            <Loader2 className="absolute right-2 top-2.5 h-4 w-4 animate-spin text-muted-foreground" />
          )}
        </div>
        {open && results.length > 0 && (
          <div className="absolute z-10 mt-1 w-full max-h-80 overflow-y-auto rounded-md border bg-popover shadow-lg">
            {results.map((p) => {
              const already = isSelected(p.id);
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => addProduct(p)}
                  disabled={already && !singleSelect}
                  className="w-full text-left px-3 py-2 text-sm hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-between gap-2"
                >
                  <span className="truncate">{p.name}</span>
                  <span className="text-xs text-muted-foreground font-mono whitespace-nowrap">
                    {p.price != null ? `¥${Math.round(p.price).toLocaleString('ja-JP')}` : ''}
                    {already && <span className="ml-2">追加済</span>}
                  </span>
                </button>
              );
            })}
          </div>
        )}
        {open && !loading && keyword.trim().length >= 2 && results.length === 0 && (
          <div className="absolute z-10 mt-1 w-full rounded-md border bg-popover shadow-lg p-3 text-sm text-muted-foreground">
            該当する商品が見つかりませんでした
          </div>
        )}
      </div>

      {selected.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {selected.map((p) => (
            <Badge
              key={p.id}
              variant="secondary"
              className="gap-1 px-2 py-1 max-w-full"
            >
              <span className="truncate max-w-[20rem]">{p.name}</span>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-4 w-4"
                onClick={() => removeProduct(p.id)}
                aria-label={`${p.name} を削除`}
              >
                <X className="h-3 w-3" />
              </Button>
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
}
