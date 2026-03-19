'use client';

import {
  BarChart3,
  Bot,
  ChevronLeft,
  Gift,
  LayoutDashboard,
  Mail,
  Mountain,
  Package,
  ShoppingCart,
  Star,
  Tag,
  Ticket,
  Users,
  Warehouse,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';

import { ThemeToggle } from '@/components/admin/theme-toggle';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { useAuth } from '@/hooks/use-auth';
import { t } from '@/lib/i18n';

import type { Route } from 'next';

const sidebarItems = [
  { name: t('admin.sidebar.dashboard'), href: '/admin/dashboard', icon: LayoutDashboard },
  { type: 'separator' as const, label: t('admin.sidebar.sectionProducts') },
  { name: t('admin.sidebar.products'), href: '/admin/products', icon: Package },
  { name: t('admin.sidebar.categories'), href: '/admin/categories', icon: Tag },
  { name: t('admin.sidebar.inventory'), href: '/admin/inventory', icon: Warehouse },
  { type: 'separator' as const, label: t('admin.sidebar.sectionSales') },
  { name: t('admin.sidebar.orders'), href: '/admin/orders', icon: ShoppingCart },
  { name: t('admin.sidebar.users'), href: '/admin/users', icon: Users, adminOnly: true },
  { type: 'separator' as const, label: t('admin.sidebar.sectionMarketing') },
  { name: t('admin.sidebar.points'), href: '/admin/points', icon: Star },
  { name: t('admin.sidebar.campaigns'), href: '/admin/campaigns', icon: Gift },
  { name: t('admin.sidebar.coupons'), href: '/admin/coupons', icon: Ticket },
  { type: 'separator' as const, label: t('admin.sidebar.sectionSystem') },
  { name: t('admin.sidebar.mailLogs'), href: '/admin/mail-logs', icon: Mail },
  { name: t('admin.sidebar.analytics'), href: '/admin/analytics', icon: BarChart3 },
  { name: t('admin.sidebar.ai'), href: '/admin/ai', icon: Bot, adminOnly: true },
];

export function AdminSidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const { isAdmin } = useAuth();

  return (
    <TooltipProvider delay={0}>
      <aside
        className={`bg-card flex h-screen flex-col border-r transition-all duration-300 ${
          collapsed ? 'w-16' : 'w-64'
        }`}
      >
        {/* Logo */}
        <div className="flex h-16 items-center justify-between border-b px-4">
          {!collapsed && (
            <Link href="/admin/dashboard" className="flex items-center gap-2">
              <Mountain className="text-primary h-6 w-6" />
              <span className="font-bold">Admin</span>
            </Link>
          )}
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setCollapsed(!collapsed)}
              className="h-8 w-8"
            >
              <ChevronLeft
                className={`h-4 w-4 transition-transform ${collapsed ? 'rotate-180' : ''}`}
              />
            </Button>
          </div>
        </div>

        {/* Navigation */}
        <ScrollArea className="flex-1 py-2">
          <nav className="flex flex-col gap-0.5 px-2">
            {sidebarItems.map((item, index) => {
              if ('type' in item && item.type === 'separator') {
                return (
                  <div key={index} className="py-2">
                    <Separator />
                    {!collapsed && (
                      <p className="text-muted-foreground mt-2 px-3 text-xs font-medium tracking-wider uppercase">
                        {item.label}
                      </p>
                    )}
                  </div>
                );
              }

              const navItem = item as {
                name: string;
                href: Route;
                icon: React.ComponentType<{ className?: string }>;
                adminOnly?: boolean;
              };

              if (navItem.adminOnly && !isAdmin) return null;

              const isActive = pathname === navItem.href || pathname.startsWith(navItem.href + '/');

              const linkContent = (
                <Link
                  href={navItem.href}
                  className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-primary/10 text-primary'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                  } ${collapsed ? 'justify-center' : ''}`}
                >
                  <navItem.icon className="h-4 w-4 shrink-0" />
                  {!collapsed && <span>{navItem.name}</span>}
                </Link>
              );

              if (collapsed) {
                return (
                  <Tooltip key={navItem.href}>
                    <TooltipTrigger>{linkContent}</TooltipTrigger>
                    <TooltipContent side="right">{navItem.name}</TooltipContent>
                  </Tooltip>
                );
              }

              return <div key={navItem.href}>{linkContent}</div>;
            })}
          </nav>
        </ScrollArea>
      </aside>
    </TooltipProvider>
  );
}
