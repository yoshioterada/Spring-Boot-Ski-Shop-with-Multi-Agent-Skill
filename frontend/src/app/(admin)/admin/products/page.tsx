'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, Pencil, Plus, Search, Trash2 } from 'lucide-react';
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
import { formatCurrency } from '@/lib/format';

// --- Types ---

interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: string;
  categoryName: string;
  regularPrice: number;
  salePrice: number | null;
  stockQuantity: number;
  status: string;
}

interface PagedProducts {
  content: Product[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  page?: {
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
}

// --- Schema ---

const productSchema = z.object({
  name: z.string().min(1, '商品名は必須です').max(200, '200文字以内で入力してください'),
  sku: z.string().min(1, 'SKUは必須です').max(50, '50文字以内で入力してください'),
  description: z.string().max(2000, '2000文字以内で入力してください'),
  brand: z.string().max(100, '100文字以内で入力してください'),
  categoryId: z.string().min(1, 'カテゴリは必須です'),
  regularPrice: z.number().min(0, '0以上の値を入力してください'),
  status: z.string().min(1, 'ステータスは必須です'),
});

type ProductFormData = z.infer<typeof productSchema>;

const priceSchema = z.object({
  regularPrice: z.number().min(0, '0以上の値を入力してください'),
  salePrice: z.number().min(0, '0以上の値を入力してください').optional(),
});

type PriceFormData = z.infer<typeof priceSchema>;

// --- Constants ---

const statusOptions = [
  { value: 'ACTIVE', label: '販売中' },
  { value: 'INACTIVE', label: '非公開' },
  { value: 'DRAFT', label: '下書き' },
  { value: 'DISCONTINUED', label: '販売終了' },
];

const categoryOptions = [
  { value: '1', label: 'スキー板' },
  { value: '2', label: 'ブーツ' },
  { value: '3', label: 'ビンディング' },
  { value: '4', label: 'ウェア' },
  { value: '5', label: 'ゴーグル・アクセサリ' },
  { value: '6', label: 'ポール' },
];

const statusConfig: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  ACTIVE: { label: '販売中', variant: 'default' },
  INACTIVE: { label: '非公開', variant: 'secondary' },
  DRAFT: { label: '下書き', variant: 'outline' },
  DISCONTINUED: { label: '販売終了', variant: 'destructive' },
};

// --- Main ---

export default function AdminProductsPage() {
  const [products, setProducts] = useState<PagedProducts | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');

  // Dialog states
  const [createOpen, setCreateOpen] = useState(false);
  const [editProduct, setEditProduct] = useState<Product | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);
  const [priceTarget, setPriceTarget] = useState<Product | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchProducts = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        page: String(page),
        size: String(pageSize),
      });
      if (search) params.set('keyword', search);
      if (categoryFilter !== 'all') params.set('categoryId', categoryFilter);

      const res = await fetch(`/api/admin/products?${params.toString()}`);
      if (res.ok) {
        setProducts(await res.json());
      }
    } catch {
      toast.error('商品一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, categoryFilter]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchProducts();
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/products/${deleteTarget.id}`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('商品を削除しました');
        setDeleteTarget(null);
        fetchProducts();
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
        <h1 className="text-2xl font-bold">商品管理</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" />
          新規登録
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-end gap-4">
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <Search className="text-muted-foreground size-4 shrink-0" />
              <Input
                placeholder="商品名・SKUで検索..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="min-w-48"
              />
            </div>
            <Select
              value={categoryFilter}
              onValueChange={(v) => {
                setCategoryFilter(v ?? 'all');
                setPage(0);
              }}
            >
              <SelectTrigger className="w-44">
                <SelectValue placeholder="カテゴリ" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全カテゴリ</SelectItem>
                {categoryOptions.map((c) => (
                  <SelectItem key={c.value} value={c.value}>
                    {c.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button type="submit" variant="outline" size="default">
              検索
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardHeader>
          <CardTitle>商品一覧</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <SkeletonTable rows={10} columns={8} />
          ) : !products || products.content.length === 0 ? (
            <p className="text-muted-foreground py-12 text-center text-sm">商品が見つかりません</p>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead>商品名</TableHead>
                    <TableHead>カテゴリ</TableHead>
                    <TableHead className="text-right">通常価格</TableHead>
                    <TableHead className="text-right">セール価格</TableHead>
                    <TableHead className="text-right">在庫</TableHead>
                    <TableHead>ステータス</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {products.content.map((p) => {
                    const cfg = statusConfig[p.status] ?? {
                      label: p.status,
                      variant: 'outline' as const,
                    };
                    return (
                      <TableRow key={p.id}>
                        <TableCell className="font-mono text-xs">{p.sku}</TableCell>
                        <TableCell className="max-w-48 truncate font-medium">{p.name}</TableCell>
                        <TableCell>{p.categoryName || '-'}</TableCell>
                        <TableCell className="text-right">
                          {formatCurrency(p.regularPrice)}
                        </TableCell>
                        <TableCell className="text-right">
                          {p.salePrice != null ? (
                            <span className="text-red-600">{formatCurrency(p.salePrice)}</span>
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <span className={p.stockQuantity <= 5 ? 'font-bold text-red-600' : ''}>
                            {p.stockQuantity}
                          </span>
                        </TableCell>
                        <TableCell>
                          <Badge variant={cfg.variant}>{cfg.label}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon-xs"
                              onClick={() => setPriceTarget(p)}
                              title="価格変更"
                            >
                              ¥
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon-xs"
                              onClick={() => setEditProduct(p)}
                              title="編集"
                            >
                              <Pencil className="size-3" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon-xs"
                              onClick={() => setDeleteTarget(p)}
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
              <Pagination
                currentPage={products.page?.number ?? products.number ?? 0}
                totalPages={products.page?.totalPages ?? products.totalPages ?? 1}
                totalElements={products.page?.totalElements ?? products.totalElements ?? 0}
                pageSize={products.page?.size ?? products.size ?? 20}
                onPageChange={setPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setPage(0);
                }}
                pageSizeOptions={[20, 50, 100]}
              />
            </>
          )}
        </CardContent>
      </Card>

      {/* Create Dialog */}
      <ProductFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => {
          setCreateOpen(false);
          fetchProducts();
        }}
      />

      {/* Edit Dialog */}
      <ProductFormDialog
        open={!!editProduct}
        onOpenChange={(open) => {
          if (!open) setEditProduct(null);
        }}
        product={editProduct ?? undefined}
        onSuccess={() => {
          setEditProduct(null);
          fetchProducts();
        }}
      />

      {/* Price Dialog */}
      <PriceDialog
        open={!!priceTarget}
        onOpenChange={(open) => {
          if (!open) setPriceTarget(null);
        }}
        product={priceTarget ?? undefined}
        onSuccess={() => {
          setPriceTarget(null);
          fetchProducts();
        }}
      />

      {/* Delete Confirm */}
      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title="商品を削除"
        description={`「${deleteTarget?.name ?? ''}」を削除してよろしいですか？この操作は元に戻せません。`}
        confirmLabel={submitting ? '削除中...' : '削除する'}
        onConfirm={handleDelete}
        variant="destructive"
      />
    </div>
  );
}

// --- Product Form Dialog ---

function ProductFormDialog({
  open,
  onOpenChange,
  product,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product?: Product;
  onSuccess: () => void;
}) {
  const isEdit = !!product;
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ProductFormData>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: '',
      sku: '',
      description: '',
      brand: '',
      categoryId: '',
      regularPrice: 0,
      status: 'DRAFT',
    },
  });

  const currentCategory = watch('categoryId');
  const currentStatus = watch('status');

  useEffect(() => {
    if (open && product) {
      reset({
        name: product.name,
        sku: product.sku,
        description: product.description ?? '',
        brand: product.brand ?? '',
        categoryId: product.categoryId ?? '',
        regularPrice: product.regularPrice,
        status: product.status,
      });
    } else if (open && !product) {
      reset({
        name: '',
        sku: '',
        description: '',
        brand: '',
        categoryId: '',
        regularPrice: 0,
        status: 'DRAFT',
      });
    }
  }, [open, product, reset]);

  const onSubmit = async (formData: ProductFormData) => {
    try {
      setSubmitting(true);
      const url = isEdit ? `/api/admin/products/${product.id}` : '/api/admin/products';
      const method = isEdit ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (res.ok) {
        toast.success(isEdit ? '商品を更新しました' : '商品を登録しました');
        onSuccess();
      } else {
        toast.error(isEdit ? '更新に失敗しました' : '登録に失敗しました');
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
          <DialogTitle>{isEdit ? '商品を編集' : '新規商品登録'}</DialogTitle>
          <DialogDescription>
            {isEdit ? '商品情報を編集します。' : '新しい商品を登録します。'}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">商品名 *</Label>
              <Input id="name" {...register('name')} />
              {errors.name && <p className="text-xs text-red-500">{errors.name.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sku">SKU *</Label>
              <Input id="sku" {...register('sku')} />
              {errors.sku && <p className="text-xs text-red-500">{errors.sku.message}</p>}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">説明</Label>
            <Input id="description" {...register('description')} />
            {errors.description && (
              <p className="text-xs text-red-500">{errors.description.message}</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="brand">ブランド</Label>
              <Input id="brand" {...register('brand')} />
              {errors.brand && <p className="text-xs text-red-500">{errors.brand.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="regularPrice">通常価格 *</Label>
              <Input
                id="regularPrice"
                type="number"
                min={0}
                {...register('regularPrice', { valueAsNumber: true })}
              />
              {errors.regularPrice && (
                <p className="text-xs text-red-500">{errors.regularPrice.message}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>カテゴリ *</Label>
              <Select
                value={currentCategory}
                onValueChange={(v) => setValue('categoryId', v ?? '', { shouldValidate: true })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="選択してください" />
                </SelectTrigger>
                <SelectContent>
                  {categoryOptions.map((c) => (
                    <SelectItem key={c.value} value={c.value}>
                      {c.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.categoryId && (
                <p className="text-xs text-red-500">{errors.categoryId.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>ステータス *</Label>
              <Select
                value={currentStatus}
                onValueChange={(v) => setValue('status', v ?? '', { shouldValidate: true })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="選択してください" />
                </SelectTrigger>
                <SelectContent>
                  {statusOptions.map((s) => (
                    <SelectItem key={s.value} value={s.value}>
                      {s.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.status && <p className="text-xs text-red-500">{errors.status.message}</p>}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {isEdit ? '更新' : '登録'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// --- Price Dialog ---

function PriceDialog({
  open,
  onOpenChange,
  product,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product?: Product;
  onSuccess: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PriceFormData>({
    resolver: zodResolver(priceSchema),
  });

  useEffect(() => {
    if (open && product) {
      reset({
        regularPrice: product.regularPrice,
        salePrice: product.salePrice ?? undefined,
      });
    }
  }, [open, product, reset]);

  const onSubmit = async (formData: PriceFormData) => {
    if (!product) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/prices/${product.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (res.ok) {
        toast.success('価格を更新しました');
        onSuccess();
      } else {
        toast.error('価格の更新に失敗しました');
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
          <DialogTitle>価格変更</DialogTitle>
          <DialogDescription>{product?.name ?? ''}</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="priceRegular">通常価格 *</Label>
            <Input
              id="priceRegular"
              type="number"
              min={0}
              {...register('regularPrice', { valueAsNumber: true })}
            />
            {errors.regularPrice && (
              <p className="text-xs text-red-500">{errors.regularPrice.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="priceSale">セール価格</Label>
            <Input
              id="priceSale"
              type="number"
              min={0}
              {...register('salePrice', { valueAsNumber: true })}
            />
            {errors.salePrice && <p className="text-xs text-red-500">{errors.salePrice.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              更新
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
