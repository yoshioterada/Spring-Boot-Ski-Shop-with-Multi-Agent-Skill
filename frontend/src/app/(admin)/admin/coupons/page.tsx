'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Copy, Loader2, Plus, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
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

interface Coupon {
  id: string;
  code: string;
  type: string;
  discountValue: number;
  minPurchaseAmount: number | null;
  maxUses: number | null;
  usageCount: number;
  validFrom: string;
  validUntil: string;
  status: string;
  campaignId: string | null;
}

// --- Schema ---

const couponSchema = z.object({
  code: z.string().min(1, 'クーポンコードは必須です').max(50, '50文字以内で入力してください'),
  type: z.string().min(1, 'タイプは必須です'),
  discountValue: z.number().min(0, '0以上の値を入力してください'),
  minPurchaseAmount: z.number().min(0, '0以上の値を入力してください').optional(),
  maxUses: z.number().min(1, '1以上の値を入力してください').optional(),
  validFrom: z.string().min(1, '有効開始日は必須です'),
  validUntil: z.string().min(1, '有効期限は必須です'),
  campaignId: z.string().optional(),
});

type CouponFormData = z.infer<typeof couponSchema>;

const bulkSchema = z.object({
  count: z.number().min(1, '1以上を入力してください').max(1000, '1000以下で入力してください'),
  prefix: z.string().min(1, 'プレフィックスは必須です').max(10, '10文字以内で入力してください'),
  campaignId: z.string().optional(),
});

type BulkFormData = z.infer<typeof bulkSchema>;

// --- Constants ---

const typeOptions = [
  { value: 'PERCENTAGE', label: '割合割引' },
  { value: 'FIXED_AMOUNT', label: '固定額割引' },
];

const statusConfig: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  ACTIVE: { label: '有効', variant: 'default' },
  USED: { label: '使用済', variant: 'secondary' },
  EXPIRED: { label: '期限切れ', variant: 'destructive' },
  DISABLED: { label: '無効', variant: 'outline' },
};

// --- Main ---

export default function AdminCouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [cpnPage, setCpnPage] = useState(0);
  const [cpnPageSize, setCpnPageSize] = useState(20);
  const [loading, setLoading] = useState(true);

  const [createOpen, setCreateOpen] = useState(false);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Coupon | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchCoupons = useCallback(async () => {
    try {
      setLoading(true);
      const res = await fetch('/api/admin/coupons');
      if (res.ok) {
        const data = await res.json();
        setCoupons(Array.isArray(data) ? data : (data?.content ?? []));
      }
    } catch {
      toast.error('クーポン一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCoupons();
  }, [fetchCoupons]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/coupons/${deleteTarget.id}`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('クーポンを削除しました');
        setDeleteTarget(null);
        fetchCoupons();
      } else {
        toast.error('削除に失敗しました');
      }
    } catch {
      toast.error('削除に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">クーポン管理</h1>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setBulkOpen(true)}>
            <Copy className="size-4" />
            一括生成
          </Button>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            新規作成
          </Button>
        </div>
      </div>

      {/* Table */}
      <Card>
        <CardHeader>
          <CardTitle>クーポン一覧</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <SkeletonTable rows={5} columns={7} />
          ) : coupons.length === 0 ? (
            <p className="text-muted-foreground py-12 text-center text-sm">
              クーポンが見つかりません
            </p>
          ) : (
            <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>コード</TableHead>
                  <TableHead>タイプ</TableHead>
                  <TableHead className="text-right">割引値</TableHead>
                  <TableHead>使用状況</TableHead>
                  <TableHead>有効期限</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {coupons.slice(cpnPage * cpnPageSize, (cpnPage + 1) * cpnPageSize).map((c) => {
                  const cfg = statusConfig[c.status] ?? {
                    label: c.status,
                    variant: 'outline' as const,
                  };
                  return (
                    <TableRow key={c.id}>
                      <TableCell className="font-mono text-xs">{c.code}</TableCell>
                      <TableCell>{c.type === 'PERCENTAGE' ? '割合' : '固定額'}</TableCell>
                      <TableCell className="text-right">
                        {c.type === 'PERCENTAGE'
                          ? `${c.discountValue}%`
                          : `¥${c.discountValue.toLocaleString()}`}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            c.usageCount >= (c.maxUses ?? Infinity) ? 'destructive' : 'secondary'
                          }
                        >
                          {c.usageCount} / {c.maxUses ?? '∞'}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {c.validUntil ? formatDate(c.validUntil) : '-'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={cfg.variant}>{cfg.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
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
            {coupons.length > cpnPageSize && (
              <Pagination
                currentPage={cpnPage}
                totalPages={Math.ceil(coupons.length / cpnPageSize)}
                totalElements={coupons.length}
                pageSize={cpnPageSize}
                onPageChange={setCpnPage}
                onPageSizeChange={(size) => { setCpnPageSize(size); setCpnPage(0); }}
              />
            )}
            </>
          )}
        </CardContent>
      </Card>

      {/* Create Dialog */}
      <CouponFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => {
          setCreateOpen(false);
          fetchCoupons();
        }}
      />

      {/* Bulk Generate Dialog */}
      <BulkGenerateDialog
        open={bulkOpen}
        onOpenChange={setBulkOpen}
        onSuccess={() => {
          setBulkOpen(false);
          fetchCoupons();
        }}
      />

      {/* Delete Confirm */}
      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title="クーポンを削除"
        description={`クーポン「${deleteTarget?.code ?? ''}」を削除してよろしいですか？`}
        confirmLabel={submitting ? '削除中...' : '削除する'}
        onConfirm={handleDelete}
        variant="destructive"
      />
    </div>
  );
}

// --- Coupon Form Dialog ---

function CouponFormDialog({
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
  } = useForm<CouponFormData>({
    resolver: zodResolver(couponSchema),
    defaultValues: {
      code: '',
      type: 'PERCENTAGE',
      discountValue: 0,
      validFrom: '',
      validUntil: '',
      campaignId: '',
    },
  });

  const currentType = watch('type');

  useEffect(() => {
    if (open) {
      reset({
        code: '',
        type: 'PERCENTAGE',
        discountValue: 0,
        validFrom: '',
        validUntil: '',
        campaignId: '',
      });
    }
  }, [open, reset]);

  const onSubmit = async (formData: CouponFormData) => {
    try {
      setSubmitting(true);
      const body = {
        ...formData,
        campaignId: formData.campaignId || undefined,
      };
      const res = await fetch('/api/admin/coupons', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (res.ok) {
        toast.success('クーポンを作成しました');
        onSuccess();
      } else {
        toast.error('作成に失敗しました');
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
          <DialogTitle>新規クーポン作成</DialogTitle>
          <DialogDescription>新しいクーポンを作成します。</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="coupon-code">クーポンコード *</Label>
              <Input id="coupon-code" {...register('code')} />
              {errors.code && <p className="text-xs text-red-500">{errors.code.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>タイプ *</Label>
              <Select
                value={currentType}
                onValueChange={(v) => setValue('type', v ?? '', { shouldValidate: true })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="選択" />
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
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="coupon-discount">割引値 *</Label>
              <Input
                id="coupon-discount"
                type="number"
                min={0}
                {...register('discountValue', { valueAsNumber: true })}
              />
              {errors.discountValue && (
                <p className="text-xs text-red-500">{errors.discountValue.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="coupon-min">最低購入額</Label>
              <Input
                id="coupon-min"
                type="number"
                min={0}
                {...register('minPurchaseAmount', { valueAsNumber: true })}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="coupon-max">最大利用回数</Label>
              <Input
                id="coupon-max"
                type="number"
                min={1}
                {...register('maxUses', { valueAsNumber: true })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="coupon-campaign">キャンペーンID</Label>
              <Input id="coupon-campaign" {...register('campaignId')} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="coupon-from">有効開始日 *</Label>
              <Input id="coupon-from" type="date" {...register('validFrom')} />
              {errors.validFrom && (
                <p className="text-xs text-red-500">{errors.validFrom.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="coupon-until">有効期限 *</Label>
              <Input id="coupon-until" type="date" {...register('validUntil')} />
              {errors.validUntil && (
                <p className="text-xs text-red-500">{errors.validUntil.message}</p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              作成
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// --- Bulk Generate Dialog ---

function BulkGenerateDialog({
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
    formState: { errors },
  } = useForm<BulkFormData>({
    resolver: zodResolver(bulkSchema),
    defaultValues: { count: 10, prefix: 'BULK', campaignId: '' },
  });

  useEffect(() => {
    if (open) {
      reset({ count: 10, prefix: 'BULK', campaignId: '' });
    }
  }, [open, reset]);

  const onSubmit = async (formData: BulkFormData) => {
    try {
      setSubmitting(true);
      const body = {
        ...formData,
        campaignId: formData.campaignId || undefined,
      };
      const res = await fetch('/api/admin/coupons/bulk', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (res.ok) {
        toast.success(`${formData.count}件のクーポンを一括生成しました`);
        onSuccess();
      } else {
        toast.error('一括生成に失敗しました');
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
          <DialogTitle>クーポン一括生成</DialogTitle>
          <DialogDescription>指定件数のクーポンを一括で生成します。</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="bulk-count">生成件数 *</Label>
            <Input
              id="bulk-count"
              type="number"
              min={1}
              max={1000}
              {...register('count', { valueAsNumber: true })}
            />
            {errors.count && <p className="text-xs text-red-500">{errors.count.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="bulk-prefix">プレフィックス *</Label>
            <Input id="bulk-prefix" {...register('prefix')} />
            {errors.prefix && <p className="text-xs text-red-500">{errors.prefix.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="bulk-campaign">キャンペーンID</Label>
            <Input id="bulk-campaign" {...register('campaignId')} />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              生成
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
