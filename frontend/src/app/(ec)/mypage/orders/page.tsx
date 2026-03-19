'use client';

import { Package, Search } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';

import { Pagination } from '@/components/common/pagination';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatDate } from '@/lib/format';

interface OrderItem {
  id: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

interface Order {
  id: string;
  orderNumber: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  items?: OrderItem[];
}

interface PageResponse {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  PENDING: {
    label: '処理待ち',
    className:
      'border-yellow-300 bg-yellow-50 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-400',
  },
  CONFIRMED: {
    label: '確認済み',
    className: 'border-blue-300 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-400',
  },
  SHIPPED: {
    label: '発送済み',
    className:
      'border-purple-300 bg-purple-50 text-purple-700 dark:bg-purple-950 dark:text-purple-400',
  },
  DELIVERED: {
    label: '配達完了',
    className: 'border-green-300 bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400',
  },
  CANCELLED: {
    label: 'キャンセル',
    className: 'border-red-300 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400',
  },
};

function OrderStatusBadge({ status }: { status: string }) {
  const config = STATUS_CONFIG[status] ?? {
    label: status,
    className: '',
  };
  return (
    <Badge variant="outline" className={config.className}>
      {config.label}
    </Badge>
  );
}

export default function OrderHistoryPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<Order[]>([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResult, setSearchResult] = useState<Order | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const fetchOrders = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetch(`/api/orders?page=${page}&size=${pageSize}&sort=createdAt,desc`);
      if (!res.ok) throw new Error('Failed to fetch');
      const data: PageResponse = await res.json();
      setOrders(data.content ?? []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch {
      setOrders([]);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    void fetchOrders();
  }, [fetchOrders]);

  const handleSearch = async () => {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;
    setIsSearching(true);
    setSearchError('');
    setSearchResult(null);
    try {
      const res = await fetch(`/api/orders/search?orderNumber=${encodeURIComponent(trimmed)}`);
      if (!res.ok) {
        setSearchError('注文が見つかりませんでした');
        return;
      }
      const data: Order = await res.json();
      setSearchResult(data);
    } catch {
      setSearchError('検索に失敗しました');
    } finally {
      setIsSearching(false);
    }
  };

  const clearSearch = () => {
    setSearchQuery('');
    setSearchResult(null);
    setSearchError('');
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize);
    setPage(0);
  };

  const displayOrders = searchResult ? [searchResult] : orders;

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <Breadcrumb
        items={[{ label: 'マイページ', href: '/mypage' as never }, { label: '注文履歴' }]}
        className="mb-6"
      />

      <h1 className="mb-6 text-2xl font-bold">注文履歴</h1>

      {/* Search bar */}
      <Card className="mb-6">
        <CardContent className="pt-6">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
              <Input
                placeholder="注文番号で検索..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleSearch();
                }}
                className="pl-9"
              />
            </div>
            <Button onClick={() => void handleSearch()} disabled={isSearching}>
              検索
            </Button>
            {(searchResult || searchError) && (
              <Button variant="outline" onClick={clearSearch}>
                クリア
              </Button>
            )}
          </div>
          {searchError && <p className="mt-2 text-sm text-red-500">{searchError}</p>}
        </CardContent>
      </Card>

      {/* Loading */}
      {isLoading && !searchResult && (
        <div className="flex justify-center py-12">
          <div className="border-primary h-8 w-8 animate-spin rounded-full border-2 border-t-transparent" />
        </div>
      )}

      {/* Empty state */}
      {!isLoading && displayOrders.length === 0 && !searchError && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <Package className="text-muted-foreground mb-4 h-12 w-12" />
            <p className="text-muted-foreground text-lg">注文履歴はありません</p>
          </CardContent>
        </Card>
      )}

      {/* Desktop table */}
      {!isLoading && displayOrders.length > 0 && (
        <>
          <div className="hidden md:block">
            <Card>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>注文番号</TableHead>
                    <TableHead>注文日</TableHead>
                    <TableHead>合計金額</TableHead>
                    <TableHead>ステータス</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {displayOrders.map((order) => (
                    <TableRow
                      key={order.id}
                      className="cursor-pointer"
                      onClick={() => router.push(`/mypage/orders/${order.id}`)}
                    >
                      <TableCell className="font-medium">{order.orderNumber}</TableCell>
                      <TableCell>{formatDate(order.createdAt)}</TableCell>
                      <TableCell>{formatCurrency(order.totalAmount)}</TableCell>
                      <TableCell>
                        <OrderStatusBadge status={order.status} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Link
                          href={`/mypage/orders/${order.id}`}
                          className="text-primary text-sm hover:underline"
                          onClick={(e) => e.stopPropagation()}
                        >
                          詳細
                        </Link>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          </div>

          {/* Mobile cards */}
          <div className="space-y-3 md:hidden">
            {displayOrders.map((order) => (
              <Link key={order.id} href={`/mypage/orders/${order.id}`}>
                <Card className="transition-shadow hover:shadow-md">
                  <CardContent className="p-4">
                    <div className="mb-2 flex items-start justify-between">
                      <span className="text-sm font-medium">{order.orderNumber}</span>
                      <OrderStatusBadge status={order.status} />
                    </div>
                    <div className="text-muted-foreground flex items-center justify-between text-sm">
                      <span>{formatDate(order.createdAt)}</span>
                      <span className="text-foreground font-semibold">
                        {formatCurrency(order.totalAmount)}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>

          {/* Pagination */}
          {!searchResult && totalPages > 0 && (
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={pageSize}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}
    </div>
  );
}
