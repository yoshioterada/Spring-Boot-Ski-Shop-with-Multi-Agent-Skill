'use client';

import { useCallback, useEffect, useState } from 'react';
import { Loader2, RefreshCw, Search, Tag } from 'lucide-react';
import { toast } from 'sonner';

interface BrandSuggestion {
  brand: string;
  model: string;
}

interface Opportunity {
  rank: number;
  normalizedKeyword: string;
  searchCount: number;
  lastSearchedAt: string;
  estimatedCategory: string | null;
  estimatedGenderTarget: string | null;
  estimatedLossJpy: number;
  priority: string;
  narrative: string | null;
  suggestedBrands: BrandSuggestion[];
  relatedExistingSkus: string[];
}

interface Summary {
  totalZeroHitQueries: number;
  totalSearchVolume: number;
  estimatedTotalLossJpy: number;
  topCategoryGap: string;
}

interface ZeroHitData {
  generatedAt: string;
  periodDays: number;
  summary: Summary;
  opportunities: Opportunity[];
}

const PRIORITY_COLORS: Record<string, string> = {
  HIGH: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
  MEDIUM: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  LOW: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
};

export function ZeroHitRadar() {
  const [data, setData] = useState<ZeroHitData | null>(null);
  const [loading, setLoading] = useState(false);
  const [days, setDays] = useState(30);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch(`/api/admin/ai-analyzer/zero-hit-opportunities?days=${days}`);
      if (!res.ok) {
        toast.error(`エラー: HTTP ${res.status}`);
        return;
      }
      setData(await res.json());
    } catch {
      toast.error('機会発見データの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleDismiss = async (rank: number, reason: string) => {
    try {
      const res = await fetch(`/api/admin/ai-analyzer/zero-hit-opportunities/${rank}/dismiss`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason, memo: '' }),
      });
      if (!res.ok) {
        toast.error(`エラー: HTTP ${res.status}`);
        return;
      }
      toast.success('対象外としてマークしました');
      fetchData();
    } catch {
      toast.error('操作に失敗しました');
    }
  };

  const fmt = (n: number) =>
    n >= 1000000 ? `¥${(n / 1000000).toFixed(2)}M` : `¥${(n / 1000).toFixed(1)}K`;

  return (
    <div className="rounded-xl border bg-card shadow-sm">
      {/* Header */}
      <div className="flex items-center gap-2 border-b px-4 py-3">
        <Search className="h-5 w-5 text-blue-500" />
        <h3 className="font-semibold">機会発見レーダー (F5)</h3>
        <div className="ml-auto flex items-center gap-2">
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="rounded border px-2 py-1 text-sm bg-background"
            aria-label="期間選択"
          >
            <option value={7}>7日</option>
            <option value={30}>30日</option>
            <option value={90}>90日</option>
          </select>
          <button onClick={fetchData} disabled={loading} className="p-1 hover:bg-muted rounded" aria-label="再取得">
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      <p className="text-muted-foreground text-sm px-4 pt-2">
        検索されたが商品が見つからなかった「ゼロヒット」キーワードを分析し、品揃えの機会損失を可視化します。AI が仕入れ候補ブランド・モデルを提案します。
      </p>

      {/* Summary */}
      {data?.summary && (
        <div className="grid grid-cols-3 gap-4 border-b px-4 py-3 text-sm">
          <div>
            <p className="text-muted-foreground">ゼロヒット件数</p>
            <p className="text-lg font-bold">{data.summary.totalZeroHitQueries}</p>
          </div>
          <div>
            <p className="text-muted-foreground">検索ボリューム</p>
            <p className="text-lg font-bold">{data.summary.totalSearchVolume}</p>
          </div>
          <div>
            <p className="text-muted-foreground">推定機会損失</p>
            <p className="text-lg font-bold text-red-600">{fmt(data.summary.estimatedTotalLossJpy)}</p>
          </div>
        </div>
      )}

      {/* Content */}
      {loading && !data ? (
        <div className="flex items-center justify-center p-8">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="p-4 space-y-3">
          {data?.opportunities && data.opportunities.length > 0 ? (
            data.opportunities.map((opp) => (
              <div key={opp.rank} className="rounded-lg border p-3 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground text-sm">#{opp.rank}</span>
                  <span className="font-medium">{opp.normalizedKeyword}</span>
                  <span className={`ml-auto rounded-full px-2 py-0.5 text-xs font-medium ${PRIORITY_COLORS[opp.priority] ?? ''}`}>
                    {opp.priority}
                  </span>
                </div>

                <div className="flex gap-4 text-sm text-muted-foreground">
                  <span>検索 {opp.searchCount} 回</span>
                  <span>推定損失 {fmt(opp.estimatedLossJpy)}</span>
                </div>

                {opp.narrative && (
                  <p className="text-sm bg-muted rounded p-2">{opp.narrative}</p>
                )}

                {opp.suggestedBrands.length > 0 && (
                  <div className="flex gap-2 flex-wrap">
                    {opp.suggestedBrands.map((b, i) => (
                      <span key={i} className="inline-flex items-center gap-1 rounded border px-2 py-0.5 text-xs">
                        <Tag className="h-3 w-3" /> {b.brand} {b.model}
                      </span>
                    ))}
                  </div>
                )}

                <div className="flex gap-2">
                  <button
                    onClick={() => handleDismiss(opp.rank, 'ALREADY_PROCURING')}
                    className="rounded border px-2 py-1 text-xs hover:bg-muted"
                  >
                    仕入検討中にマーク
                  </button>
                  <button
                    onClick={() => handleDismiss(opp.rank, 'NOT_OUR_TARGET')}
                    className="rounded border px-2 py-1 text-xs hover:bg-muted"
                  >
                    対象外
                  </button>
                </div>
              </div>
            ))
          ) : (
            <div className="text-center text-muted-foreground py-8">
              <Search className="h-8 w-8 mx-auto mb-2 opacity-50" />
              <p>ゼロヒット機会は検出されませんでした</p>
            </div>
          )}

          {/* D-F5-08: AI 提案注記 */}
          <p className="text-xs text-muted-foreground border-t pt-2">
            ※ AI による一般的な市場知識からの提案であり、取扱可否は別途調査が必要です。
          </p>
        </div>
      )}
    </div>
  );
}
