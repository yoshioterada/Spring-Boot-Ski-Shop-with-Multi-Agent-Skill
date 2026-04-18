'use client';

import { Search, Shield, UserCog, Users } from 'lucide-react';
import { useSession } from 'next-auth/react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDate } from '@/lib/format';

/* ---------- Types ---------- */

interface AdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  status: string;
  roles?: string[];
  roleName?: string;
  tier?: string;
  pointsBalance?: number;
  orderCount?: number;
  createdAt: string;
}

interface PageResponse {
  content: AdminUser[];
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

/* ---------- Status / Role styling ---------- */

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  ACTIVE: {
    label: '有効',
    className: 'border-green-300 bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400',
  },
  INACTIVE: {
    label: '無効',
    className: 'border-gray-300 bg-gray-50 text-gray-700 dark:bg-gray-800 dark:text-gray-400',
  },
  SUSPENDED: {
    label: '停止',
    className: 'border-red-300 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400',
  },
};

const ROLE_CONFIG: Record<string, { label: string; className: string }> = {
  ADMIN: {
    label: '管理者',
    className:
      'border-purple-300 bg-purple-50 text-purple-700 dark:bg-purple-950 dark:text-purple-400',
  },
  MANAGER: {
    label: 'マネージャー',
    className: 'border-blue-300 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-400',
  },
  CUSTOMER: {
    label: '顧客',
    className: '',
  },
};

const ALL_STATUSES = ['ACTIVE', 'INACTIVE', 'SUSPENDED'] as const;
const ALL_ROLES = ['ADMIN', 'MANAGER', 'CUSTOMER'] as const;

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CONFIG[status] ?? { label: status, className: '' };
  return (
    <Badge variant="outline" className={cfg.className}>
      {cfg.label}
    </Badge>
  );
}

function getUserRoles(user: AdminUser): string[] {
  if (user.roles && user.roles.length > 0) return user.roles;
  if (user.roleName) return [user.roleName];
  return [];
}

function RoleBadge({ role }: { role: string }) {
  const cfg = ROLE_CONFIG[role] ?? { label: role, className: '' };
  return (
    <Badge variant="outline" className={cfg.className}>
      {cfg.label}
    </Badge>
  );
}

/* ---------- Main Page ---------- */

export default function AdminUsersPage() {
  const { data: session } = useSession();
  const myRole = session?.user?.role;

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  // Detail dialog
  const [detailUser, setDetailUser] = useState<AdminUser | null>(null);
  const [detailSummary, setDetailSummary] = useState<{
    orderCount: number | null;
    pointsBalance: number | null;
    tier: string | null;
  } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 詳細ダイアログを開いたときに、証几/ポイント/会員ランクを集計 BFF から取得する。
  useEffect(() => {
    if (!detailUser?.id) {
      setDetailSummary(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailSummary(null);
    (async () => {
      try {
        const res = await fetch(`/api/admin/users/${detailUser.id}/summary`, {
          cache: 'no-store',
        });
        if (!res.ok) throw new Error('failed');
        const data = (await res.json()) as {
          orderCount?: number;
          pointsBalance?: number;
          tier?: string | null;
        };
        if (!cancelled) {
          setDetailSummary({
            orderCount: data.orderCount ?? 0,
            pointsBalance: data.pointsBalance ?? 0,
            tier: data.tier ?? null,
          });
        }
      } catch {
        if (!cancelled) {
          setDetailSummary({ orderCount: 0, pointsBalance: 0, tier: null });
        }
      } finally {
        if (!cancelled) setDetailLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [detailUser?.id]);

  // Status change confirm
  const [statusTarget, setStatusTarget] = useState<{
    user: AdminUser;
    newStatus: string;
  } | null>(null);

  // Role change confirm
  const [roleTarget, setRoleTarget] = useState<{
    user: AdminUser;
    newRole: string;
  } | null>(null);

  /* --- Fetch --- */

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: String(pageSize),
        sort: 'createdAt,desc',
      });
      if (searchQuery.trim()) params.set('keyword', searchQuery.trim());

      const res = await fetch(`/api/admin/users?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch');
      const data: PageResponse = await res.json();
      setUsers(data.content ?? []);
      setTotalElements(data.page?.totalElements ?? data.totalElements ?? 0);
      setTotalPages(data.page?.totalPages ?? data.totalPages ?? 0);
    } catch {
      setUsers([]);
      toast.error('ユーザー一覧の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, searchQuery]);

  useEffect(() => {
    void fetchUsers();
  }, [fetchUsers]);

  /* --- Handlers --- */

  const handleSearch = () => {
    setPage(0);
    // searchQuery change triggers fetchUsers via dependency
  };

  const handlePageChange = (newPage: number) => setPage(newPage);

  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize);
    setPage(0);
  };

  const handleStatusChange = async () => {
    if (!statusTarget) return;
    try {
      const res = await fetch(`/api/admin/users/${statusTarget.user.id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: statusTarget.newStatus }),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(
        `${statusTarget.user.lastName} ${statusTarget.user.firstName} のステータスを変更しました`,
      );
      void fetchUsers();
    } catch {
      toast.error('ステータスの変更に失敗しました');
    } finally {
      setStatusTarget(null);
    }
  };

  const handleRoleChange = async () => {
    if (!roleTarget) return;
    try {
      const res = await fetch(`/api/admin/users/${roleTarget.user.id}/roles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roleName: roleTarget.newRole }),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(
        `${roleTarget.user.lastName} ${roleTarget.user.firstName} のロールを変更しました`,
      );
      void fetchUsers();
    } catch {
      toast.error('ロールの変更に失敗しました');
    } finally {
      setRoleTarget(null);
    }
  };

  const canChangeStatus = (targetUser: AdminUser) => {
    if (myRole === 'ADMIN') return true;
    if (myRole === 'MANAGER' && !getUserRoles(targetUser).includes('ADMIN')) return true;
    return false;
  };

  const canChangeRole = (targetUser: AdminUser) => {
    if (myRole === 'ADMIN') return true;
    if (myRole === 'MANAGER' && !getUserRoles(targetUser).includes('ADMIN')) return true;
    return false;
  };

  const fullName = (u: AdminUser) => `${u.lastName} ${u.firstName}`;

  /* --- Render --- */

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Users className="h-6 w-6" />
        <h1 className="text-2xl font-bold">ユーザー管理</h1>
      </div>

      {/* Search */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
              <Input
                placeholder="メールアドレスまたは名前で検索..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleSearch();
                }}
                className="pl-9"
              />
            </div>
            <Button onClick={handleSearch}>検索</Button>
            {searchQuery && (
              <Button
                variant="outline"
                onClick={() => {
                  setSearchQuery('');
                  setPage(0);
                }}
              >
                クリア
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Loading */}
      {isLoading && (
        <div className="flex justify-center py-12">
          <div className="border-primary h-8 w-8 animate-spin rounded-full border-2 border-t-transparent" />
        </div>
      )}

      {/* Empty state */}
      {!isLoading && users.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <Users className="text-muted-foreground mb-4 h-12 w-12" />
            <p className="text-muted-foreground text-lg">ユーザーが見つかりません</p>
          </CardContent>
        </Card>
      )}

      {/* Table */}
      {!isLoading && users.length > 0 && (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>メールアドレス</TableHead>
                  <TableHead>氏名</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead>ロール</TableHead>
                  <TableHead>登録日</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => (
                  <TableRow
                    key={user.id}
                    className="cursor-pointer"
                    onClick={() => setDetailUser(user)}
                  >
                    <TableCell className="font-medium">{user.email}</TableCell>
                    <TableCell>{fullName(user)}</TableCell>
                    <TableCell>
                      <StatusBadge status={user.status} />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {getUserRoles(user).map((r) => (
                          <RoleBadge key={r} role={r} />
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>{formatDate(user.createdAt)}</TableCell>
                    <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-2">
                        {/* Status change */}
                        {canChangeStatus(user) && (
                          <Select
                            value={user.status}
                            onValueChange={(val) =>
                              val && setStatusTarget({ user, newStatus: val })
                            }
                          >
                            <SelectTrigger className="h-8 w-24">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {ALL_STATUSES.map((s) => (
                                <SelectItem key={s} value={s}>
                                  {STATUS_CONFIG[s]?.label ?? s}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}

                        {/* Role change */}
                        {canChangeRole(user) && (
                          <Select
                            value={getUserRoles(user)[0] ?? 'CUSTOMER'}
                            onValueChange={(val) => val && setRoleTarget({ user, newRole: val })}
                          >
                            <SelectTrigger className="h-8 w-32">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {ALL_ROLES.map((r) => (
                                <SelectItem key={r} value={r}>
                                  {ROLE_CONFIG[r]?.label ?? r}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          {/* Pagination */}
          {totalPages > 0 && (
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={pageSize}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
              pageSizeOptions={[20, 50, 100]}
            />
          )}
        </>
      )}

      {/* ----- User Detail Dialog ----- */}
      <Dialog open={!!detailUser} onOpenChange={(open) => !open && setDetailUser(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <UserCog className="h-5 w-5" />
              ユーザー詳細
            </DialogTitle>
            <DialogDescription>ユーザーのプロフィール情報</DialogDescription>
          </DialogHeader>

          {detailUser && (
            <div className="space-y-4">
              {/* Profile */}
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-muted-foreground">氏名</p>
                  <p className="font-medium">{fullName(detailUser)}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">メール</p>
                  <p className="font-medium">{detailUser.email}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">電話番号</p>
                  <p className="font-medium">{detailUser.phone || '未登録'}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">登録日</p>
                  <p className="font-medium">{formatDate(detailUser.createdAt)}</p>
                </div>
              </div>

              <Separator />

              {/* Status & Roles */}
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-muted-foreground mb-1">ステータス</p>
                  <StatusBadge status={detailUser.status} />
                </div>
                <div>
                  <p className="text-muted-foreground mb-1">ロール</p>
                  <div className="flex flex-wrap gap-1">
                    {getUserRoles(detailUser).map((r) => (
                      <RoleBadge key={r} role={r} />
                    ))}
                  </div>
                </div>
              </div>

              <Separator />

              {/* Additional info */}
              <div className="grid grid-cols-3 gap-3 text-sm">
                <Card>
                  <CardHeader className="p-3">
                    <CardTitle className="text-muted-foreground text-xs font-normal">
                      注文数
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="px-3 pb-3">
                    <p className="text-lg font-bold">
                      {detailLoading
                        ? '…'
                        : (detailSummary?.orderCount ?? detailUser.orderCount ?? 0).toLocaleString()}
                    </p>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader className="p-3">
                    <CardTitle className="text-muted-foreground text-xs font-normal">
                      ポイント
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="px-3 pb-3">
                    <p className="text-lg font-bold">
                      {detailLoading
                        ? '…'
                        : `${(detailSummary?.pointsBalance ?? detailUser.pointsBalance ?? 0).toLocaleString()} pt`}
                    </p>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader className="p-3">
                    <CardTitle className="text-muted-foreground text-xs font-normal">
                      会員ランク
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="px-3 pb-3">
                    <div className="flex items-center gap-1">
                      <Shield className="h-4 w-4" />
                      <p className="text-lg font-bold">
                        {detailLoading
                          ? '…'
                          : (detailSummary?.tier ?? detailUser.tier ?? '未設定')}
                      </p>
                    </div>
                  </CardContent>
                </Card>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* ----- Status Change Confirm ----- */}
      <ConfirmDialog
        open={!!statusTarget}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title="ステータス変更の確認"
        description={
          statusTarget
            ? `${fullName(statusTarget.user)} のステータスを「${STATUS_CONFIG[statusTarget.newStatus]?.label ?? statusTarget.newStatus}」に変更しますか？`
            : ''
        }
        confirmLabel="変更"
        cancelLabel="キャンセル"
        onConfirm={() => void handleStatusChange()}
        variant={statusTarget?.newStatus === 'SUSPENDED' ? 'destructive' : 'default'}
      />

      {/* ----- Role Change Confirm ----- */}
      <ConfirmDialog
        open={!!roleTarget}
        onOpenChange={(open) => {
          if (!open) setRoleTarget(null);
        }}
        title="ロール変更の確認"
        description={
          roleTarget
            ? `${fullName(roleTarget.user)} のロールを「${ROLE_CONFIG[roleTarget.newRole]?.label ?? roleTarget.newRole}」に変更しますか？`
            : ''
        }
        confirmLabel="変更"
        cancelLabel="キャンセル"
        onConfirm={handleRoleChange}
        variant={roleTarget?.newRole === 'ADMIN' ? 'destructive' : 'default'}
      />
    </div>
  );
}
