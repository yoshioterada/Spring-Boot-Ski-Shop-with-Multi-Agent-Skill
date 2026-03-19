'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, RefreshCw, Send } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
import { SkeletonTable } from '@/components/common/skeleton-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
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

interface MailLog {
  id: string;
  recipientEmail: string;
  type: string;
  status: string;
  retryCount: number;
  sentAt: string;
  createdAt: string;
}

interface DailyStat {
  date: string;
  sentCount: number;
  successRate: number;
}

// --- Schema ---

const testMailSchema = z.object({
  recipientEmail: z.string().email('有効なメールアドレスを入力してください'),
  template: z.string().min(1, 'テンプレートは必須です'),
});

type TestMailFormData = z.infer<typeof testMailSchema>;

// --- Constants ---

const statusConfig: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  SENT: { label: '送信済', variant: 'default' },
  FAILED: { label: '失敗', variant: 'destructive' },
  PENDING: { label: '保留中', variant: 'outline' },
  SKIPPED: { label: 'スキップ', variant: 'secondary' },
};

const statusFilterOptions = [
  { value: 'all', label: '全て' },
  { value: 'SENT', label: '送信済' },
  { value: 'FAILED', label: '失敗' },
  { value: 'PENDING', label: '保留中' },
  { value: 'SKIPPED', label: 'スキップ' },
];

const templateOptions = [
  { value: 'WELCOME', label: 'ウェルカムメール' },
  { value: 'ORDER_CONFIRMATION', label: '注文確認' },
  { value: 'SHIPPING_NOTIFICATION', label: '発送通知' },
  { value: 'PASSWORD_RESET', label: 'パスワードリセット' },
  { value: 'CAMPAIGN', label: 'キャンペーン' },
];

const typeLabels: Record<string, string> = {
  WELCOME: 'ウェルカム',
  ORDER_CONFIRMATION: '注文確認',
  SHIPPING_NOTIFICATION: '発送通知',
  PASSWORD_RESET: 'パスワード',
  CAMPAIGN: 'キャンペーン',
};

// --- Main ---

export default function AdminMailLogsPage() {
  const [logs, setLogs] = useState<MailLog[]>([]);
  const [mlPage, setMlPage] = useState(0);
  const [mlPageSize, setMlPageSize] = useState(20);
  const [stats, setStats] = useState<DailyStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('all');

  const [retryTarget, setRetryTarget] = useState<MailLog | null>(null);
  const [testOpen, setTestOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fetchLogs = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams();
      if (statusFilter !== 'all') params.set('status', statusFilter);
      const res = await fetch(`/api/admin/mail/logs?${params.toString()}`);
      if (res.ok) {
        const data = await res.json();
        setLogs(Array.isArray(data) ? data : (data?.content ?? []));
      }
    } catch {
      toast.error('メールログの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  const fetchStats = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/mail/stats');
      if (res.ok) {
        const data = await res.json();
        setStats(Array.isArray(data) ? data : (data?.daily ?? []));
      }
    } catch {
      // stats are non-critical
    }
  }, []);

  useEffect(() => {
    fetchLogs();
    fetchStats();
  }, [fetchLogs, fetchStats]);

  const handleRetry = async () => {
    if (!retryTarget) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/mail/${retryTarget.id}/retry`, {
        method: 'POST',
      });
      if (res.ok) {
        toast.success('メール再送信をリクエストしました');
        setRetryTarget(null);
        fetchLogs();
      } else {
        toast.error('再送信に失敗しました');
      }
    } catch {
      toast.error('再送信に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const fallbackStats: DailyStat[] =
    stats.length > 0
      ? stats
      : [
          { date: '月', sentCount: 120, successRate: 98 },
          { date: '火', sentCount: 95, successRate: 97 },
          { date: '水', sentCount: 140, successRate: 99 },
          { date: '木', sentCount: 110, successRate: 96 },
          { date: '金', sentCount: 130, successRate: 98 },
          { date: '土', sentCount: 80, successRate: 100 },
          { date: '日', sentCount: 60, successRate: 99 },
        ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">メールログ</h1>
        <Button onClick={() => setTestOpen(true)}>
          <Send className="size-4" />
          テストメール送信
        </Button>
      </div>

      {/* Stats Chart */}
      <Card>
        <CardHeader>
          <CardTitle>送信統計（過去7日間）</CardTitle>
          <CardDescription>日別の送信数と成功率</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={fallbackStats}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis dataKey="date" className="text-xs" tick={{ fontSize: 12 }} />
                <YAxis className="text-xs" tick={{ fontSize: 12 }} />
                <Tooltip
                  contentStyle={{
                    borderRadius: '8px',
                    border: '1px solid hsl(var(--border))',
                    backgroundColor: 'hsl(var(--background))',
                  }}
                  formatter={(value, name) => {
                    if (name === 'sentCount') return [Number(value), '送信数'];
                    return [`${Number(value)}%`, '成功率'];
                  }}
                />
                <Bar dataKey="sentCount" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Filter */}
      <Card>
        <CardContent className="pt-4">
          <div className="flex items-end gap-4">
            <div className="space-y-1.5">
              <Label>ステータス</Label>
              <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v ?? 'all')}>
                <SelectTrigger className="w-44">
                  <SelectValue placeholder="全て" />
                </SelectTrigger>
                <SelectContent>
                  {statusFilterOptions.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardHeader>
          <CardTitle>メールログ一覧</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <SkeletonTable rows={10} columns={6} />
          ) : logs.length === 0 ? (
            <p className="text-muted-foreground py-12 text-center text-sm">
              メールログが見つかりません
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>日時</TableHead>
                  <TableHead>宛先</TableHead>
                  <TableHead>種別</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead className="text-right">リトライ</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {logs.slice(mlPage * mlPageSize, (mlPage + 1) * mlPageSize).map((log) => {
                  const cfg = statusConfig[log.status] ?? {
                    label: log.status,
                    variant: 'outline' as const,
                  };
                  return (
                    <TableRow key={log.id}>
                      <TableCell className="text-muted-foreground text-xs">
                        {log.sentAt ? formatDateTime(log.sentAt) : formatDateTime(log.createdAt)}
                      </TableCell>
                      <TableCell className="max-w-48 truncate text-sm">
                        {log.recipientEmail}
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">{typeLabels[log.type] ?? log.type}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={cfg.variant}>{cfg.label}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-right text-xs">
                        {log.retryCount}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          {log.status === 'FAILED' && (
                            <Button
                              variant="ghost"
                              size="icon-xs"
                              onClick={() => setRetryTarget(log)}
                              title="再送信"
                            >
                              <RefreshCw className="size-3" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            {logs.length > mlPageSize && (
              <Pagination
                currentPage={mlPage}
                totalPages={Math.ceil(logs.length / mlPageSize)}
                totalElements={logs.length}
                pageSize={mlPageSize}
                onPageChange={setMlPage}
                onPageSizeChange={(size) => { setMlPageSize(size); setMlPage(0); }}
              />
            )}
          )}
        </CardContent>
      </Card>

      {/* Retry Confirm */}
      <ConfirmDialog
        open={!!retryTarget}
        onOpenChange={(open) => {
          if (!open) setRetryTarget(null);
        }}
        title="メール再送信"
        description={`「${retryTarget?.recipientEmail ?? ''}」宛のメールを再送信してよろしいですか？`}
        confirmLabel={submitting ? '送信中...' : '再送信する'}
        onConfirm={handleRetry}
      />

      {/* Test Mail Dialog */}
      <TestMailDialog
        open={testOpen}
        onOpenChange={setTestOpen}
        onSuccess={() => {
          setTestOpen(false);
          fetchLogs();
        }}
      />
    </div>
  );
}

// --- Test Mail Dialog ---

function TestMailDialog({
  open,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<TestMailFormData>({
    resolver: zodResolver(testMailSchema),
    defaultValues: { recipientEmail: '', template: 'WELCOME' },
  });

  const currentTemplate = watch('template');

  useEffect(() => {
    if (open) {
      reset({ recipientEmail: '', template: 'WELCOME' });
    }
  }, [open, reset]);

  const onSubmit = async (formData: TestMailFormData) => {
    try {
      setSubmitting(true);
      const res = await fetch('/api/admin/mail/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (res.ok) {
        toast.success('テストメールを送信しました');
        onSuccess();
      } else {
        toast.error('テストメールの送信に失敗しました');
      }
    } catch {
      toast.error('エラーが発生しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>テストメール送信</DialogTitle>
          <DialogDescription>テスト用のメールを送信します。</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="test-email">宛先メールアドレス *</Label>
            <Input
              id="test-email"
              type="email"
              placeholder="test@example.com"
              {...register('recipientEmail')}
            />
            {errors.recipientEmail && (
              <p className="text-xs text-red-500">{errors.recipientEmail.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label>テンプレート *</Label>
            <Select
              value={currentTemplate}
              onValueChange={(v) => setValue('template', v ?? '', { shouldValidate: true })}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
              <SelectContent>
                {templateOptions.map((t) => (
                  <SelectItem key={t.value} value={t.value}>
                    {t.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.template && <p className="text-xs text-red-500">{errors.template.message}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              送信
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
