'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
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

interface Category {
  id: number;
  name: string;
  description: string;
  parentId: number | null;
  parentName?: string;
  displayOrder: number;
  active: boolean;
}

const categorySchema = z.object({
  name: z
    .string()
    .min(1, 'カテゴリ名は必須です')
    .max(50, 'カテゴリ名は50文字以内で入力してください'),
  description: z.string().max(200, '説明は200文字以内で入力してください'),
  parentId: z.string().optional(),
  displayOrder: z.number().int('整数を入力してください').min(0, '0以上の値を入力してください'),
  active: z.boolean(),
});

type CategoryFormValues = z.output<typeof categorySchema>;

const NO_PARENT_VALUE = '__none__';

export default function AdminCategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  // Dialog state
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [saving, setSaving] = useState(false);

  // Delete state
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deletingCategory, setDeletingCategory] = useState<Category | null>(null);

  const form = useForm<CategoryFormValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      name: '',
      description: '',
      parentId: NO_PARENT_VALUE,
      displayOrder: 0,
      active: true,
    },
  });

  const fetchCategories = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/categories');
      if (!res.ok) throw new Error('カテゴリの取得に失敗しました');
      const data = await res.json();
      const list: Category[] = Array.isArray(data) ? data : (data.content ?? []);
      setCategories(list);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'カテゴリの取得に失敗しました');
      setCategories([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  const openCreateDialog = () => {
    setEditingCategory(null);
    form.reset({
      name: '',
      description: '',
      parentId: NO_PARENT_VALUE,
      displayOrder: 0,
      active: true,
    });
    setFormDialogOpen(true);
  };

  const openEditDialog = (category: Category) => {
    setEditingCategory(category);
    form.reset({
      name: category.name,
      description: category.description ?? '',
      parentId: category.parentId ? String(category.parentId) : NO_PARENT_VALUE,
      displayOrder: category.displayOrder,
      active: category.active,
    });
    setFormDialogOpen(true);
  };

  const openDeleteDialog = (category: Category) => {
    setDeletingCategory(category);
    setDeleteConfirmOpen(true);
  };

  const handleSave = async (values: CategoryFormValues) => {
    setSaving(true);
    try {
      const payload = {
        name: values.name,
        description: values.description,
        parentId:
          values.parentId && values.parentId !== NO_PARENT_VALUE ? Number(values.parentId) : null,
        displayOrder: values.displayOrder,
        active: values.active,
      };

      const isEdit = editingCategory !== null;
      const url = isEdit ? `/api/admin/categories/${editingCategory.id}` : '/api/admin/categories';
      const method = isEdit ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok)
        throw new Error(isEdit ? 'カテゴリの更新に失敗しました' : 'カテゴリの作成に失敗しました');

      toast.success(isEdit ? 'カテゴリを更新しました' : 'カテゴリを作成しました');
      setFormDialogOpen(false);
      fetchCategories();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingCategory) return;
    try {
      const res = await fetch(`/api/admin/categories/${deletingCategory.id}`, {
        method: 'DELETE',
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        if (errorData?.error === 'CAT_HAS_CHILDREN') {
          toast.error(
            'サブカテゴリが存在するため削除できません。先にサブカテゴリを削除してください。',
          );
          setDeleteConfirmOpen(false);
          return;
        }
        throw new Error('カテゴリの削除に失敗しました');
      }

      toast.success(`カテゴリ「${deletingCategory.name}」を削除しました`);
      setDeleteConfirmOpen(false);
      setDeletingCategory(null);
      fetchCategories();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'カテゴリの削除に失敗しました');
    }
  };

  const getParentName = (parentId: number | null) => {
    if (!parentId) return '-';
    const parent = categories.find((c) => c.id === parentId);
    return parent?.name ?? String(parentId);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">カテゴリ管理</h1>
          <p className="text-muted-foreground text-sm">商品カテゴリの作成・編集・削除を行います</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={fetchCategories} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            更新
          </Button>
          <Button onClick={openCreateDialog}>
            <Plus className="mr-2 h-4 w-4" />
            新規作成
          </Button>
        </div>
      </div>

      {/* Categories Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-16">ID</TableHead>
              <TableHead>カテゴリ名</TableHead>
              <TableHead>説明</TableHead>
              <TableHead>親カテゴリ</TableHead>
              <TableHead className="w-24 text-center">表示順</TableHead>
              <TableHead className="w-24 text-center">状態</TableHead>
              <TableHead className="w-32 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} className="text-muted-foreground py-12 text-center">
                  読み込み中...
                </TableCell>
              </TableRow>
            ) : categories.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-muted-foreground py-12 text-center">
                  カテゴリが登録されていません
                </TableCell>
              </TableRow>
            ) : (
              categories.map((cat) => (
                <TableRow key={cat.id}>
                  <TableCell className="text-muted-foreground text-sm">{cat.id}</TableCell>
                  <TableCell className="font-medium">{cat.name}</TableCell>
                  <TableCell className="text-muted-foreground max-w-xs truncate text-sm">
                    {cat.description || '-'}
                  </TableCell>
                  <TableCell className="text-sm">
                    {cat.parentName ?? getParentName(cat.parentId)}
                  </TableCell>
                  <TableCell className="text-center">{cat.displayOrder}</TableCell>
                  <TableCell className="text-center">
                    {cat.active ? (
                      <Badge className="bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
                        有効
                      </Badge>
                    ) : (
                      <Badge className="bg-gray-100 text-gray-600 dark:bg-gray-800/30 dark:text-gray-400">
                        無効
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => openEditDialog(cat)}
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => openDeleteDialog(cat)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* Create / Edit Dialog */}
      <Dialog open={formDialogOpen} onOpenChange={setFormDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingCategory ? 'カテゴリの編集' : 'カテゴリの新規作成'}</DialogTitle>
            <DialogDescription>
              {editingCategory
                ? `「${editingCategory.name}」を編集します`
                : '新しいカテゴリを作成します'}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={form.handleSubmit(handleSave)} className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="name">カテゴリ名 *</Label>
              <Input id="name" {...form.register('name')} placeholder="例: スキー板" />
              {form.formState.errors.name && (
                <p className="text-destructive text-sm">{form.formState.errors.name.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">説明</Label>
              <Input
                id="description"
                {...form.register('description')}
                placeholder="カテゴリの説明"
              />
              {form.formState.errors.description && (
                <p className="text-destructive text-sm">
                  {form.formState.errors.description.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label>親カテゴリ</Label>
              <Select
                value={form.watch('parentId') ?? NO_PARENT_VALUE}
                onValueChange={(val) =>
                  form.setValue('parentId', val ?? undefined, { shouldValidate: true })
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="なし" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_PARENT_VALUE}>なし（トップレベル）</SelectItem>
                  {categories
                    .filter((c) => c.id !== editingCategory?.id)
                    .map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.name}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="displayOrder">表示順</Label>
              <Input
                id="displayOrder"
                type="number"
                {...form.register('displayOrder', { valueAsNumber: true })}
                min={0}
              />
              {form.formState.errors.displayOrder && (
                <p className="text-destructive text-sm">
                  {form.formState.errors.displayOrder.message}
                </p>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="active"
                className="h-4 w-4 rounded border"
                {...form.register('active')}
              />
              <Label htmlFor="active">有効</Label>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormDialogOpen(false)}>
                キャンセル
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? '保存中...' : editingCategory ? '更新' : '作成'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirm */}
      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title="カテゴリの削除"
        description={`カテゴリ「${deletingCategory?.name ?? ''}」を削除します。この操作は取り消せません。`}
        confirmLabel="削除する"
        onConfirm={handleDelete}
        variant="destructive"
      />
    </div>
  );
}
