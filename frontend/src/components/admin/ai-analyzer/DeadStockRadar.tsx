'use client';

import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Loader2, Package, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { DeadStockIssueModal } from './DeadStockIssueModal';

// ── Types ──
type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM';

interface DeadStockItem {
  sku: string;
  name: string;
  stock: number;
  sales30: number;
  sales90: number;
  daysOfSupply: number;
  severity: Severity;
  suggestedDiscountPct: number;
  aiReason: string | null;
}

interface DeadStockData {
  generatedAt: string;
  items: DeadStockItem[];
  narrative: string;
}

const SEVERITY_COLORS: Record<Severity, string> = {
  CRITICAL: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
  HIGH: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  MEDIUM: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
};

export function DeadStockRadar() {
  const [data, setData] = useState<DeadStockData | null>(null);
  const [loading, setLoading] = useState(false);
  const [severityFilter, setSeverityFilter] = useState<Severity | 'ALL'>('ALL');
  const [issueTarget, setIssueTarget] = useState<DeadStockItem | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/ai-analyzer/dead-stock');
      if (!res.ok) {
        toast.error(`エラー: HTTP ${res.status}`);
        return;
      }
      setData(await res.json());
    } catch {
      toast.error('滞留在庫データの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const filtered = data?.items.filter(
    (item) => severityFilter === 'ALL' || item.severity === severityFilter
  ) ?? [];

  return (
    <div className="rounded-xl border bg-card shadow-sm">
      {/* Header */}
      <div className="border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-amber-500" />
          <h3 className="font-semibold">滞留在庫レーダー (F4)</h3>
          <button onClick={fetchData} disabled={loading} className="ml-auto p-1 hover:bg-muted rounded" aria-label="再取得">
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
        <p className="text-muted-foreground text-sm mt-1">
          一定期間販売のない在庫を自動検出し、重大度別に分類します。AI がマークダウン対応策を提案し、在庫回転率の改善を支援します。
        </p>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-2 px-4 py-2 border-b">
        <span className="text-sm text-muted-foreground">Severity:</span>
        {(['ALL', 'CRITICAL', 'HIGH', 'MEDIUM'] as const).map((s) => (
          <button
            key={s}
            onClick={() => setSeverityFilter(s)}
            className={`rounded-full px-3 py-1 text-xs border transition-colors ${
              severityFilter === s ? 'bg-primary text-primary-foreground' : 'bg-background hover:bg-muted'
            }`}
            aria-pressed={severityFilter === s}
          >
            {s}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading && !data ? (
        <div className="flex items-center justify-center p-8">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="p-4 space-y-4">
          {/* Table */}
          {filtered.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm" aria-label="滞留在庫一覧">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2 px-2">SKU</th>
                    <th className="py-2 px-2">商品名</th>
                    <th className="py-2 px-2 text-right">在庫</th>
                    <th className="py-2 px-2 text-right">DoS</th>
                    <th className="py-2 px-2">評価</th>
                    <th className="py-2 px-2 text-right">推奨割引</th>
                    <th className="py-2 px-2" />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((item) => (
                    <tr key={item.sku} className="border-b hover:bg-muted/50">
                      <td className="py-2 px-2 font-mono text-xs">{item.sku}</td>
                      <td className="py-2 px-2">{item.name}</td>
                      <td className="py-2 px-2 text-right">{item.stock}</td>
                      <td className="py-2 px-2 text-right">{item.daysOfSupply}</td>
                      <td className="py-2 px-2">
                        <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_COLORS[item.severity]}`}>
                          {item.severity}
                        </span>
                      </td>
                      <td className="py-2 px-2 text-right font-medium">{item.suggestedDiscountPct}% OFF</td>
                      <td className="py-2 px-2">
                        <button
                          onClick={() => setIssueTarget(item)}
                          className="rounded bg-primary px-2 py-1 text-xs text-primary-foreground hover:bg-primary/90"
                          aria-label={`${item.sku} のクーポン発行を提案`}
                        >
                          クーポン発行を提案
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center text-muted-foreground py-8">
              <Package className="h-8 w-8 mx-auto mb-2 opacity-50" />
              <p>該当する滞留在庫はありません</p>
            </div>
          )}

          {/* Narrative */}
          {data?.narrative && (
            <div className="rounded-lg bg-muted p-3 text-sm whitespace-pre-wrap" aria-label="AI 分析コメント">
              {data.narrative}
            </div>
          )}
        </div>
      )}

      {/* Issue Modal */}
      {issueTarget && (
        <DeadStockIssueModal
          item={issueTarget}
          onClose={() => setIssueTarget(null)}
          onSuccess={() => { setIssueTarget(null); fetchData(); }}
        />
      )}
    </div>
  );
}
