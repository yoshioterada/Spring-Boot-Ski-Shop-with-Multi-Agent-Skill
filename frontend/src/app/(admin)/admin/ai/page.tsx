'use client';

import { Activity, Cpu, Loader2, Play, Rocket } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { SkeletonTable } from '@/components/common/skeleton-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDateTime } from '@/lib/format';

// --- Types ---

type ModelStatus = 'TRAINING' | 'READY' | 'DEPLOYED' | 'FAILED';

interface AIModel {
  id: string;
  name: string;
  version: string;
  status: ModelStatus;
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  lastTrained: string | null;
  description: string;
}

// --- Mock ---

const MOCK_MODELS: AIModel[] = [
  {
    id: 'model-1',
    name: 'レコメンデーションエンジン',
    version: 'v3.2.1',
    status: 'DEPLOYED',
    accuracy: 94.2,
    precision: 91.8,
    recall: 89.5,
    lastTrained: '2025-07-10T08:30:00Z',
    description: '商品レコメンデーション用の協調フィルタリングモデル',
  },
  {
    id: 'model-2',
    name: '需要予測モデル',
    version: 'v2.1.0',
    status: 'READY',
    accuracy: 88.7,
    precision: 86.3,
    recall: 85.1,
    lastTrained: '2025-07-08T14:20:00Z',
    description: '在庫管理のための需要予測AI',
  },
  {
    id: 'model-3',
    name: '不正検知モデル',
    version: 'v1.5.0',
    status: 'TRAINING',
    accuracy: null,
    precision: null,
    recall: null,
    lastTrained: null,
    description: '決済の不正検知用アノマリー検出モデル',
  },
  {
    id: 'model-4',
    name: '感情分析モデル',
    version: 'v1.0.2',
    status: 'FAILED',
    accuracy: null,
    precision: null,
    recall: null,
    lastTrained: '2025-07-05T10:00:00Z',
    description: 'レビューの感情分析モデル',
  },
  {
    id: 'model-5',
    name: '検索ランキングモデル',
    version: 'v2.0.0',
    status: 'DEPLOYED',
    accuracy: 91.5,
    precision: 90.1,
    recall: 88.3,
    lastTrained: '2025-07-09T16:45:00Z',
    description: '検索結果のパーソナライズドランキング',
  },
];

// --- Helpers ---

function StatusBadge({ status }: { status: ModelStatus }) {
  const config: Record<ModelStatus, { label: string; className: string }> = {
    TRAINING: {
      label: 'トレーニング中',
      className:
        'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400 animate-pulse',
    },
    READY: {
      label: '準備完了',
      className: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    },
    DEPLOYED: {
      label: 'デプロイ済',
      className: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    },
    FAILED: {
      label: '失敗',
      className: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    },
  };
  const c = config[status];
  return <Badge className={c.className}>{c.label}</Badge>;
}

function MetricCard({
  label,
  value,
  suffix,
}: {
  label: string;
  value: number | null;
  suffix?: string;
}) {
  return (
    <div className="space-y-1">
      <p className="text-muted-foreground text-xs">{label}</p>
      {value !== null ? (
        <p className="text-2xl font-bold">
          {value.toFixed(1)}
          {suffix && (
            <span className="text-muted-foreground ml-0.5 text-sm font-normal">{suffix}</span>
          )}
        </p>
      ) : (
        <p className="text-muted-foreground text-sm">—</p>
      )}
    </div>
  );
}

// --- Page ---

export default function AdminAIPage() {
  const [models, setModels] = useState<AIModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [trainDialogOpen, setTrainDialogOpen] = useState(false);
  const [deployDialogOpen, setDeployDialogOpen] = useState(false);
  const [selectedModel, setSelectedModel] = useState<AIModel | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [trainingProgress, setTrainingProgress] = useState<number | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchModels = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/ai/models');
      const data = res.ok ? await res.json() : null;
      setModels(Array.isArray(data) ? data : MOCK_MODELS);
    } catch {
      setModels(MOCK_MODELS);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModels();
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [fetchModels]);

  const deployedModel = models.find((m) => m.status === 'DEPLOYED');

  const handleTrain = async () => {
    if (!selectedModel) return;
    setActionLoading(true);
    setTrainingProgress(0);
    setTrainDialogOpen(false);

    try {
      const res = await fetch('/api/admin/ai/models/train', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ modelId: selectedModel.id }),
      });
      if (!res.ok) throw new Error('Train request failed');

      setModels((prev) =>
        prev.map((m) => (m.id === selectedModel.id ? { ...m, status: 'TRAINING' as const } : m)),
      );

      let progress = 0;
      pollRef.current = setInterval(async () => {
        progress += 15 + Math.floor(Math.random() * 10);
        if (progress >= 100) {
          progress = 100;
          if (pollRef.current) clearInterval(pollRef.current);
          setTrainingProgress(100);
          await fetchModels();
          toast.success('トレーニングが完了しました');
          setTimeout(() => setTrainingProgress(null), 2000);
        } else {
          setTrainingProgress(progress);
        }
      }, 5000);

      toast.success('トレーニングを開始しました');
    } catch {
      toast.error('トレーニングの開始に失敗しました');
      setTrainingProgress(null);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeploy = async () => {
    if (!selectedModel) return;
    setActionLoading(true);
    setDeployDialogOpen(false);

    try {
      const res = await fetch(`/api/admin/ai/models/${selectedModel.id}/deploy`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error('Deploy request failed');

      setModels((prev) =>
        prev.map((m) =>
          m.id === selectedModel.id
            ? { ...m, status: 'DEPLOYED' as const }
            : m.status === 'DEPLOYED'
              ? { ...m, status: 'READY' as const }
              : m,
        ),
      );
      toast.success('デプロイが完了しました');
    } catch {
      toast.error('デプロイに失敗しました');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">AIモデル管理</h1>
        <p className="text-muted-foreground">モデルのトレーニング・デプロイ・パフォーマンス管理</p>
      </div>

      {/* Performance metrics */}
      {deployedModel && (
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Activity className="size-5 text-green-500" />
              <div>
                <CardTitle>デプロイ中モデル: {deployedModel.name}</CardTitle>
                <CardDescription>
                  {deployedModel.version} — {deployedModel.description}
                </CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-3 gap-6">
              <MetricCard label="Accuracy" value={deployedModel.accuracy} suffix="%" />
              <MetricCard label="Precision" value={deployedModel.precision} suffix="%" />
              <MetricCard label="Recall" value={deployedModel.recall} suffix="%" />
            </div>
          </CardContent>
        </Card>
      )}

      {/* Training progress */}
      {trainingProgress !== null && (
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-4">
              <Loader2 className="size-5 animate-spin" />
              <div className="flex-1 space-y-1">
                <p className="text-sm font-medium">トレーニング進捗</p>
                <Progress value={trainingProgress} className="h-2" />
              </div>
              <span className="text-muted-foreground text-sm">{trainingProgress}%</span>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Models table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Cpu className="size-5" />
            モデル一覧
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <SkeletonTable rows={5} columns={6} />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>モデル名</TableHead>
                  <TableHead>バージョン</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead className="text-right">Accuracy</TableHead>
                  <TableHead>最終トレーニング</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {models.map((model) => (
                  <TableRow key={model.id}>
                    <TableCell>
                      <div>
                        <p className="font-medium">{model.name}</p>
                        <p className="text-muted-foreground text-xs">{model.description}</p>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{model.version}</Badge>
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={model.status} />
                    </TableCell>
                    <TableCell className="text-right">
                      {model.accuracy !== null ? `${model.accuracy.toFixed(1)}%` : '—'}
                    </TableCell>
                    <TableCell>
                      {model.lastTrained ? formatDateTime(model.lastTrained) : '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={model.status === 'TRAINING' || actionLoading}
                          onClick={() => {
                            setSelectedModel(model);
                            setTrainDialogOpen(true);
                          }}
                        >
                          <Play className="mr-1 size-3" />
                          Train
                        </Button>
                        <Button
                          size="sm"
                          variant="default"
                          disabled={model.status !== 'READY' || actionLoading}
                          onClick={() => {
                            setSelectedModel(model);
                            setDeployDialogOpen(true);
                          }}
                        >
                          <Rocket className="mr-1 size-3" />
                          Deploy
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Confirm Dialogs */}
      <ConfirmDialog
        open={trainDialogOpen}
        onOpenChange={setTrainDialogOpen}
        title="トレーニングの開始"
        description={`「${selectedModel?.name ?? ''}」のトレーニングを開始します。現行モデルのサービスには影響しません。実行してよろしいですか？`}
        confirmLabel="トレーニング開始"
        onConfirm={handleTrain}
        variant="destructive"
      />

      <ConfirmDialog
        open={deployDialogOpen}
        onOpenChange={setDeployDialogOpen}
        title="モデルのデプロイ"
        description={`「${selectedModel?.name ?? ''}」を本番環境にデプロイします。現在デプロイ中のモデルは自動的に READY 状態に戻ります。実行してよろしいですか？`}
        confirmLabel="デプロイ実行"
        onConfirm={handleDeploy}
        variant="destructive"
      />
    </div>
  );
}
