'use client';

import { useCallback, useState } from 'react';
import { BarChart3, CloudSun, Loader2, TrendingUp } from 'lucide-react';
import { toast } from 'sonner';

// ── Types ──
type Horizon = 'NEXT_MONTH' | 'NEXT_SEASON' | 'NEXT_YEAR';

interface SkuForecast {
  sku: string;
  predictedUnits: number;
  stockNow: number;
  recommendOrder: number;
}

interface CategoryForecast {
  categoryId: string;
  categoryName: string;
  predictedDemandUnits: number;
  confidenceLow: number;
  confidenceHigh: number;
  yoyGrowth: number;
  topSkus: SkuForecast[];
}

interface ForecastResponse {
  horizonLabel: string;
  generatedAt: string;
  categories: CategoryForecast[];
  narrative: string;
  assumptions: string[];
}

const ALL_CATEGORIES = [
  { id: 'cat-ski', label: 'スキー本体' },
  { id: 'cat-boots', label: 'ブーツ' },
  { id: 'cat-wear', label: 'ウェア' },
  { id: 'cat-gloves', label: 'グローブ' },
  { id: 'cat-goggles', label: 'ゴーグル' },
  { id: 'cat-helmets', label: 'ヘルメット' },
  { id: 'cat-poles', label: 'ポール' },
];

export function SeasonalForecastPanel() {
  const [horizon, setHorizon] = useState<Horizon>('NEXT_SEASON');
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [considerWeather, setConsiderWeather] = useState(true);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ForecastResponse | null>(null);

  const toggleCategory = (id: string) => {
    setSelectedCategories((prev) =>
      prev.includes(id) ? prev.filter((c) => c !== id) : [...prev, id]
    );
  };

  const generateForecast = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/ai-analyzer/seasonal-forecast', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          horizon,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          considerWeather,
        }),
      });
      if (!res.ok) {
        toast.error(`エラー: HTTP ${res.status}`);
        return;
      }
      setResult(await res.json());
    } catch {
      toast.error('予測の生成に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [horizon, selectedCategories, considerWeather]);

  return (
    <div className="rounded-xl border bg-card shadow-sm">
      {/* Header */}
      <div className="border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <TrendingUp className="h-5 w-5 text-primary" />
          <h3 className="font-semibold">季節予測 (F2)</h3>
        </div>
        <p className="text-muted-foreground text-sm mt-1">
          気象データと過去の販売実績をもとに、カテゴリ別の需要を予測します。シーズン前の仕入れ計画や在庫調整の意思決定に活用できます。
        </p>
      </div>

      {/* Controls */}
      <div className="p-4 space-y-4">
        {/* Horizon */}
        <div>
          <label className="block text-sm font-medium mb-1">予測期間</label>
          <div className="flex gap-2">
            {([['NEXT_MONTH', '来月'], ['NEXT_SEASON', '来シーズン'], ['NEXT_YEAR', '来年']] as const).map(
              ([val, label]) => (
                <button
                  key={val}
                  onClick={() => setHorizon(val)}
                  className={`rounded-lg px-3 py-1.5 text-sm border transition-colors ${
                    horizon === val
                      ? 'bg-primary text-primary-foreground border-primary'
                      : 'bg-background hover:bg-muted'
                  }`}
                  aria-pressed={horizon === val}
                >
                  {label}
                </button>
              )
            )}
          </div>
        </div>

        {/* Categories */}
        <div>
          <label className="block text-sm font-medium mb-1">カテゴリ（未選択=全て）</label>
          <div className="flex flex-wrap gap-2">
            {ALL_CATEGORIES.map((cat) => (
              <button
                key={cat.id}
                onClick={() => toggleCategory(cat.id)}
                className={`rounded-full px-3 py-1 text-xs border transition-colors ${
                  selectedCategories.includes(cat.id)
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-background hover:bg-muted'
                }`}
                aria-pressed={selectedCategories.includes(cat.id)}
              >
                {cat.label}
              </button>
            ))}
          </div>
        </div>

        {/* Weather toggle */}
        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            id="consider-weather"
            checked={considerWeather}
            onChange={(e) => setConsiderWeather(e.target.checked)}
            className="rounded border"
          />
          <label htmlFor="consider-weather" className="text-sm flex items-center gap-1">
            <CloudSun className="h-4 w-4" />
            気象長期予報を考慮
          </label>
        </div>

        {/* Generate button */}
        <button
          onClick={generateForecast}
          disabled={loading}
          className="rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50 flex items-center gap-2"
          aria-label="予測を生成"
        >
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <BarChart3 className="h-4 w-4" />}
          予測を生成
        </button>
      </div>

      {/* Results */}
      {result && (
        <div className="border-t p-4 space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="font-medium">{result.horizonLabel}</h4>
            <span className="text-xs text-muted-foreground">
              生成: {new Date(result.generatedAt).toLocaleString('ja-JP')}
            </span>
          </div>

          {/* Category table */}
          <div className="overflow-x-auto">
            <table className="w-full text-sm" aria-label="カテゴリ別予測">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2 px-2">カテゴリ</th>
                  <th className="py-2 px-2 text-right">予測需要</th>
                  <th className="py-2 px-2 text-right">信頼区間</th>
                  <th className="py-2 px-2 text-right">YoY</th>
                </tr>
              </thead>
              <tbody>
                {result.categories.map((cat) => (
                  <tr key={cat.categoryId} className="border-b hover:bg-muted/50">
                    <td className="py-2 px-2 font-medium">{cat.categoryName}</td>
                    <td className="py-2 px-2 text-right">{cat.predictedDemandUnits.toLocaleString()}</td>
                    <td className="py-2 px-2 text-right">
                      <span className="text-muted-foreground">
                        {cat.confidenceLow.toLocaleString()}〜{cat.confidenceHigh.toLocaleString()}
                      </span>
                    </td>
                    <td className={`py-2 px-2 text-right ${cat.yoyGrowth >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {cat.yoyGrowth >= 0 ? '+' : ''}{(cat.yoyGrowth * 100).toFixed(1)}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Top SKUs */}
          {result.categories.some((c) => c.topSkus.length > 0) && (
            <details className="group">
              <summary className="cursor-pointer text-sm font-medium text-primary">
                SKU 別推奨発注を表示
              </summary>
              <div className="mt-2 overflow-x-auto">
                <table className="w-full text-sm" aria-label="SKU別推奨発注">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-1 px-2">SKU</th>
                      <th className="py-1 px-2 text-right">予測</th>
                      <th className="py-1 px-2 text-right">現在庫</th>
                      <th className="py-1 px-2 text-right">推奨発注</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.categories.flatMap((cat) =>
                      cat.topSkus.map((sku) => (
                        <tr key={sku.sku} className="border-b hover:bg-muted/50">
                          <td className="py-1 px-2 font-mono text-xs">{sku.sku}</td>
                          <td className="py-1 px-2 text-right">{sku.predictedUnits}</td>
                          <td className="py-1 px-2 text-right">{sku.stockNow}</td>
                          <td className={`py-1 px-2 text-right font-medium ${sku.recommendOrder > 0 ? 'text-amber-600' : ''}`}>
                            {sku.recommendOrder}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </details>
          )}

          {/* Narrative */}
          {result.narrative && (
            <div className="rounded-lg bg-muted p-3 text-sm whitespace-pre-wrap" aria-label="AI による分析">
              {result.narrative}
            </div>
          )}

          {/* Assumptions */}
          {result.assumptions.length > 0 && (
            <div className="text-xs text-muted-foreground">
              <p className="font-medium mb-1">前提条件:</p>
              <ul className="list-disc pl-4 space-y-0.5">
                {result.assumptions.map((a, i) => (
                  <li key={i}>{a}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
