'use client';

import { useState } from 'react';
import { AlertTriangle, Loader2, X } from 'lucide-react';
import { toast } from 'sonner';

interface DeadStockItem {
  sku: string;
  name: string;
  stock: number;
  daysOfSupply: number;
  severity: string;
  suggestedDiscountPct: number;
}

interface Props {
  item: DeadStockItem;
  onClose: () => void;
  onSuccess: () => void;
}

export function DeadStockIssueModal({ item, onClose, onSuccess }: Props) {
  const [discountPct, setDiscountPct] = useState(item.suggestedDiscountPct);
  const [memo, setMemo] = useState('');
  const [loading, setLoading] = useState(false);

  const isValid = discountPct >= 0 && discountPct <= 40;

  const handleSubmit = async () => {
    if (!isValid) return;
    setLoading(true);
    try {
      const res = await fetch(`/api/admin/ai-analyzer/dead-stock/${encodeURIComponent(item.sku)}/issue-coupon`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ discountPct, memo }),
      });
      if (res.status === 409) {
        toast.error('同一 SKU に対して 30 日以内にクーポンが発行済みです');
        return;
      }
      if (!res.ok) {
        toast.error(`エラー: HTTP ${res.status}`);
        return;
      }
      toast.success(`${item.sku} のクーポンを発行しました`);
      onSuccess();
    } catch {
      toast.error('クーポン発行に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" role="dialog" aria-modal="true" aria-label="クーポン発行確認">
      <div className="w-full max-w-md rounded-xl bg-card border shadow-lg p-6 space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between">
          <h4 className="font-semibold flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-500" />
            クーポン発行確認
          </h4>
          <button onClick={onClose} className="p-1 hover:bg-muted rounded" aria-label="閉じる">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Info */}
        <div className="text-sm space-y-1">
          <p><span className="text-muted-foreground">SKU:</span> <span className="font-mono">{item.sku}</span></p>
          <p><span className="text-muted-foreground">商品名:</span> {item.name}</p>
          <p><span className="text-muted-foreground">在庫:</span> {item.stock} 個 / DoS: {item.daysOfSupply} 日</p>
          <p><span className="text-muted-foreground">AI 推奨:</span> {item.suggestedDiscountPct}% OFF</p>
        </div>

        {/* Discount input */}
        <div>
          <label htmlFor="discount-pct" className="block text-sm font-medium mb-1">
            割引率 (0〜40%)
          </label>
          <input
            id="discount-pct"
            type="number"
            min={0}
            max={40}
            step={0.5}
            value={discountPct}
            onChange={(e) => setDiscountPct(Number(e.target.value))}
            className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 ${
              isValid ? 'focus:ring-primary' : 'border-red-500 focus:ring-red-500'
            }`}
          />
          {!isValid && (
            <p className="text-xs text-red-500 mt-1">0〜40% の範囲で入力してください</p>
          )}
        </div>

        {/* Memo */}
        <div>
          <label htmlFor="coupon-memo" className="block text-sm font-medium mb-1">メモ</label>
          <textarea
            id="coupon-memo"
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            maxLength={500}
            rows={2}
            className="w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="発行理由など..."
          />
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-2">
          <button
            onClick={onClose}
            className="rounded-lg border px-4 py-2 text-sm hover:bg-muted"
          >
            キャンセル
          </button>
          <button
            onClick={handleSubmit}
            disabled={!isValid || loading}
            className="rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50 flex items-center gap-2"
          >
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
            発行する
          </button>
        </div>
      </div>
    </div>
  );
}
