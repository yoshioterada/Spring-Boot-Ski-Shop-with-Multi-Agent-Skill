'use client';

import { Package, Plus, Search } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { Breadcrumb } from '@/components/layout/breadcrumb';
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
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { formatCurrency, formatDate } from '@/lib/format';

import type { Route } from 'next';

interface ReturnRequest {
  id: string;
  orderId: string;
  orderNumber: string;
  reason: string;
  description: string;
  status: string;
  createdAt: string;
}

const RETURN_REASONS = [
  { value: 'DEFECTIVE', label: '商品の不良・破損' },
  { value: 'WRONG_ITEM', label: '注文と異なる商品が届いた' },
  { value: 'SIZE_ISSUE', label: 'サイズが合わない' },
  { value: 'NOT_AS_DESCRIBED', label: '商品説明と異なる' },
  { value: 'OTHER', label: 'その他' },
];

const STATUS_CONFIG: Record<string, { label: string; variant: string }> = {
  PENDING: { label: '申請中', variant: 'bg-yellow-50 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400' },
  APPROVED: { label: '承認済み', variant: 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400' },
  REJECTED: { label: '却下', variant: 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400' },
  COMPLETED: { label: '完了', variant: 'bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-400' },
};

export default function ReturnsPage() {
  const [returns, setReturns] = useState<ReturnRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [reason, setReason] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchReturns = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/returns');
      if (res.ok) {
        const data = await res.json();
        setReturns(Array.isArray(data) ? data : data.content ?? []);
      }
    } catch {
      toast.error('返品履歴の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReturns();
  }, [fetchReturns]);

  const handleSubmit = async () => {
    if (!orderId || !reason) {
      toast.error('注文IDと返品理由を入力してください');
      return;
    }
    setSubmitting(true);
    try {
      const res = await fetch('/api/returns', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, reason, description }),
      });
      if (res.ok) {
        toast.success('返品申請を送信しました');
        setDialogOpen(false);
        setOrderId('');
        setReason('');
        setDescription('');
        fetchReturns();
      } else {
        const err = await res.json().catch(() => null);
        toast.error(err?.detail ?? '返品申請に失敗しました');
      }
    } catch {
      toast.error('返品申請に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
      <Breadcrumb
        items={[
          { label: 'マイページ', href: '/mypage' as Route },
          { label: '返品・交換' },
        ]}
        className="mb-6"
      />

      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">返品・交換</h1>
        <Button onClick={() => setDialogOpen(true)} className="gap-2">
          <Plus className="h-4 w-4" />
          返品申請
        </Button>
      </div>

      {/* Return History */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">返品履歴</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : returns.length === 0 ? (
            <div className="py-12 text-center">
              <Package className="text-muted-foreground mx-auto mb-4 h-12 w-12" />
              <p className="text-muted-foreground">返品履歴はありません</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>注文番号</TableHead>
                  <TableHead>申請日</TableHead>
                  <TableHead>理由</TableHead>
                  <TableHead>ステータス</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {returns.map((ret) => {
                  const status = STATUS_CONFIG[ret.status] ?? STATUS_CONFIG.PENDING;
                  return (
                    <TableRow key={ret.id}>
                      <TableCell className="font-mono text-sm">
                        {ret.orderNumber ?? ret.orderId}
                      </TableCell>
                      <TableCell>{formatDate(ret.createdAt)}</TableCell>
                      <TableCell>
                        {RETURN_REASONS.find((r) => r.value === ret.reason)?.label ?? ret.reason}
                      </TableCell>
                      <TableCell>
                        <Badge className={status.variant}>{status.label}</Badge>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Return Request Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>返品申請</DialogTitle>
            <DialogDescription>
              返品する注文の情報を入力してください
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>注文ID</Label>
              <Input
                value={orderId}
                onChange={(e) => setOrderId(e.target.value)}
                placeholder="注文IDを入力"
              />
            </div>
            <div className="space-y-2">
              <Label>返品理由</Label>
              <Select value={reason} onValueChange={setReason}>
                <SelectTrigger>
                  <SelectValue placeholder="理由を選択" />
                </SelectTrigger>
                <SelectContent>
                  {RETURN_REASONS.map((r) => (
                    <SelectItem key={r.value} value={r.value}>
                      {r.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>詳細説明（任意）</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="返品の詳細をご記入ください"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              キャンセル
            </Button>
            <Button onClick={handleSubmit} disabled={submitting || !orderId || !reason}>
              {submitting ? '送信中...' : '申請する'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
