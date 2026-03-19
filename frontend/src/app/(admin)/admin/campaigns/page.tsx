'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, Pencil, Play, Plus, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { SkeletonTable } from '@/components/common/skeleton-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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
import { formatDate } from '@/lib/format';

// --- Types ---

interface Campaign {
  id: string;
  name: string;
  description: string;
  type: string;
  status: string;
  startDate: string;
  endDate: string;
  rules: string;
}

// --- Schema ---

const campaignSchema = z.object({
  name: z.string().min(1, 'キャンペーン名は必須です').max(100, '100文字以内で入力してください'),
  description: z.string().max(500, '500文字以内で入力してください'),
  type: z.string().min(1, 'タイプは必須です'),
  startDate: z.string().min(1, '開始日は必須です'),
  endDate: z.string().min(1, '終了日は必須です'),
  rules: z.string(),
});

type CampaignFormData = z.infer<typeof campaignSchema>;

// --- Constants ---

const statusConfig: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  DRAFT: { label: '下書き', variant: 'outline' },
  ACTIVE: { label: '有効', variant: 'default' },
  ENDED: { label: '終了', variant: 'secondary' },
};

const typeOptions = [
  { value: 'PERCENTAGE', label: '割合割引' },
  { value: 'FIXED_AMOUNT', label: '固定額割引' },
];

const statusFilterOptions = [
  { value: 'all', label: '全て' },
  { value: 'DRAFT', label: '下書き' },
  { value: 'ACTIVE', label: '有効' },
  { value: 'ENDED', label: '終了' },
];

// --- Main ---

export default function AdminCampaignsPage() {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('all');

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Campaign | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Campaign | null>(null);
  const [activateTarget, setActivateTarget] = useState<Campaign | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchCampaigns = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams();
      if (statusFilter !== 'all') params.set('status', statusFilter);
      const res = await fetch(`/api/admin/campaigns?${params.toString()}`);
      if (res.ok) {
        const data = await res.json();
        setCampaigns(Array.isArray(data) ? data : (data?.content ?? []));
      }
    } catch {
      toast.error('キャンペーン一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchCampaigns();
  }, [fetchCampaigns]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/campaigns/${deleteTarget.id}`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('キャンペーンを削除しました');
        setDeleteTarget(null);
        fetchCampaigns();
      } else {
        const err = await res.json().catch(() => null);
        const code = err?.code ?? err?.errorCode ?? '';
        if (code === 'CMP-4221') {
          toast.error('期間が一致しないため削除できません');
        } else if (code === 'CMP-4222') {
          toast.error('有効なキャンペーンは削除できません');
        } else {
          toast.error(err?.message ?? '削除に失敗しました');
        }
      }
    } catch {
      toast.error('削除に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleActivate = async () => {
    if (!activateTarget) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/campaigns/${activateTarget.id}/activate`, {
        method: 'PUT',
      });
      if (res.ok) {
        toast.success('キャンペーンを有効化しました');
        setActivateTarget(null);
        fetchCampaigns();
      } else {
        toast.error('有効化に失敗しました');
      }
    } catch {
      toast.error('有効化に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">キャンペーン管理</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" />
          新規作成
        </Button>
      </div>

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
          <CardTitle>キャンペーン一覧</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <SkeletonTable rows={5} columns={6} />
          ) : campaigns.length === 0 ? (
            <p className="text-muted-foreground py-12 text-center text-sm">
              キャンペーンが見つかりません
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>キャンペーン名</TableHead>
                  <TableHead>タイプ</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead>開始日</TableHead>
                  <TableHead>終了日</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {campaigns.map((c) => {
                  const cfg = statusConfig[c.status] ?? {
                    label: c.status,
                    variant: 'outline' as const,
                  };
                  return (
                    <TableRow key={c.id}>
                      <TableCell className="font-medium">{c.name}</TableCell>
                      <TableCell>{c.type === 'PERCENTAGE' ? '割合割引' : '固定額割引'}</TableCell>
                      <TableCell>
                        <Badge variant={cfg.variant}>{cfg.label}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {c.startDate ? formatDate(c.startDate) : '-'}
                      </TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {c.endDate ? formatDate(c.endDate) : '-'}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          {c.status === 'DRAFT' && (
                            <Button
                              variant="ghost"
                              size="icon-xs"
                              onClick={() => setActivateTarget(c)}
                              title="有効化"
                            >
                              <Play className="size-3" />
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="icon-xs"
                            onClick={() => setEditTarget(c)}
                            title="編集"
                          >
                            <Pencil className="size-3" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon-xs"
                            onClick={() => setDeleteTarget(c)}
                            title="削除"
                          >
                            <Trash2 className="size-3 text-red-500" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Create Dialog */}
      <CampaignFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => {
          setCreateOpen(false);
          fetchCampaigns();
        }}
      />

      {/* Edit Dialog */}
      <CampaignFormDialog
        open={!!editTarget}
        onOpenChange={(open) => {
          if (!open) setEditTarget(null);
        }}
        campaign={editTarget ?? undefined}
        onSuccess={() => {
          setEditTarget(null);
          fetchCampaigns();
        }}
      />

      {/* Activate Confirm */}
      <ConfirmDialog
        open={!!activateTarget}
        onOpenChange={(open) => {
          if (!open) setActivateTarget(null);
        }}
        title="キャンペーンを有効化"
        description={`「${activateTarget?.name ?? ''}」を有効化してよろしいですか？`}
        confirmLabel={submitting ? '処理中...' : '有効化する'}
        onConfirm={handleActivate}
      />

      {/* Delete Confirm */}
      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title="キャンペーンを削除"
        description={`「${deleteTarget?.name ?? ''}」を削除してよろしいですか？この操作は元に戻せません。`}
        confirmLabel={submitting ? '削除中...' : '削除する'}
        onConfirm={handleDelete}
        variant="destructive"
      />
    </div>
  );
}

// --- Campaign Form Dialog ---

function CampaignFormDialog({
  open,
  onOpenChange,
  campaign,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  campaign?: Campaign;
  onSuccess: () => void;
}) {
  const isEdit = !!campaign;
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CampaignFormData>({
    resolver: zodResolver(campaignSchema),
    defaultValues: {
      name: '',
      description: '',
      type: 'PERCENTAGE',
      startDate: '',
      endDate: '',
      rules: '{}',
    },
  });

  const currentType = watch('type');

  useEffect(() => {
    if (open && campaign) {
      reset({
        name: campaign.name,
        description: campaign.description ?? '',
        type: campaign.type,
        startDate: campaign.startDate?.slice(0, 10) ?? '',
        endDate: campaign.endDate?.slice(0, 10) ?? '',
        rules: campaign.rules ?? '{}',
      });
    } else if (open && !campaign) {
      reset({
        name: '',
        description: '',
        type: 'PERCENTAGE',
        startDate: '',
        endDate: '',
        rules: '{}',
      });
    }
  }, [open, campaign, reset]);

  const onSubmit = async (formData: CampaignFormData) => {
    try {
      setSubmitting(true);
      const url = isEdit ? `/api/admin/campaigns/${campaign.id}` : '/api/admin/campaigns';
      const method = isEdit ? 'PUT' : 'POST';

      let parsedRules = {};
      try {
        parsedRules = JSON.parse(formData.rules || '{}');
      } catch {
        toast.error('ルールのJSON形式が不正です');
        setSubmitting(false);
        return;
      }

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...formData, rules: parsedRules }),
      });
      if (res.ok) {
        toast.success(isEdit ? 'キャンペーンを更新しました' : 'キャンペーンを作成しました');
        onSuccess();
      } else {
        toast.error(isEdit ? '更新に失敗しました' : '作成に失敗しました');
      }
    } catch {
      toast.error('エラーが発生しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'キャンペーンを編集' : '新規キャンペーン作成'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'キャンペーン情報を編集します。' : '新しいキャンペーンを作成します。'}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="camp-name">キャンペーン名 *</Label>
            <Input id="camp-name" {...register('name')} />
            {errors.name && <p className="text-xs text-red-500">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="camp-desc">説明</Label>
            <Input id="camp-desc" {...register('description')} />
            {errors.description && (
              <p className="text-xs text-red-500">{errors.description.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label>タイプ *</Label>
            <Select
              value={currentType}
              onValueChange={(v) => setValue('type', v ?? '', { shouldValidate: true })}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
              <SelectContent>
                {typeOptions.map((t) => (
                  <SelectItem key={t.value} value={t.value}>
                    {t.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.type && <p className="text-xs text-red-500">{errors.type.message}</p>}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="camp-start">開始日 *</Label>
              <Input id="camp-start" type="date" {...register('startDate')} />
              {errors.startDate && (
                <p className="text-xs text-red-500">{errors.startDate.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="camp-end">終了日 *</Label>
              <Input id="camp-end" type="date" {...register('endDate')} />
              {errors.endDate && <p className="text-xs text-red-500">{errors.endDate.message}</p>}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="camp-rules">ルール (JSON)</Label>
            <Input id="camp-rules" {...register('rules')} placeholder='{"minPurchase": 1000}' />
            {errors.rules && <p className="text-xs text-red-500">{errors.rules.message}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {isEdit ? '更新' : '作成'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
