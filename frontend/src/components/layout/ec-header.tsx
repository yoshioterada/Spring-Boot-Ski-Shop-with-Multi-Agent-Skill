'use client';

import {
  Menu,
  Mountain,
  Search,
  ShoppingCart,
  User,
  LogOut,
  Package,
  Heart,
  Settings,
  Bot,
  Ticket,
  RotateCcw,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';

import { SearchBar } from '@/components/ec/search-bar';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet';
import { useAuth } from '@/hooks/use-auth';
import { useCart } from '@/hooks/use-cart';
import { t } from '@/lib/i18n';

import type { Route } from 'next';

const navigation = [
  { name: t('shared.nav.home'), href: '/' as Route },
  { name: t('shared.nav.catalog'), href: '/catalog' as Route },
  { name: 'AIアドバイザー', href: '/agent' as Route },
];

export function ECHeader() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { isAuthenticated, isAdmin, logout } = useAuth();
  const { cart } = useCart();
  const cartItemCount = cart?.items?.length ?? 0;

  return (
    <header className="bg-primary sticky top-0 z-50 border-b shadow-sm">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between gap-4">
          {/* Logo */}
          <Link href="/" className="flex shrink-0 items-center gap-2">
            <Mountain className="h-7 w-7 text-white" />
            <span className="hidden text-lg font-bold text-white sm:block">Azure SkiShop</span>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden items-center gap-1 md:flex" aria-label="メインナビゲーション">
            {navigation.map((item) => (
              <Link
                key={item.name}
                href={item.href}
                className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  pathname === item.href
                    ? 'bg-white/20 text-white'
                    : 'text-white/80 hover:bg-white/10 hover:text-white'
                }`}
              >
                {item.name}
              </Link>
            ))}
            <button
              onClick={() => window.dispatchEvent(new Event('open-ai-chat'))}
              className="flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium text-white/80 transition-colors hover:bg-white/10 hover:text-white"
            >
              <Bot className="h-4 w-4" />
              AI相談
            </button>
          </nav>

          {/* Search Bar */}
          <div className="hidden max-w-md flex-1 md:block">
            <SearchBar variant="header" />
          </div>

          {/* Right Actions */}
          <div className="flex items-center gap-2">
            {/* Mobile Search Toggle */}
            <Button variant="ghost" size="icon" className="text-white md:hidden">
              <Search className="h-5 w-5" />
            </Button>

            {/* Cart */}
            <Link href="/cart">
              <Button variant="ghost" size="icon" className="relative text-white">
                <ShoppingCart className="h-5 w-5" />
                {cartItemCount > 0 && (
                  <Badge
                    variant="destructive"
                    className="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full p-0 text-xs"
                  >
                    {cartItemCount}
                  </Badge>
                )}
              </Button>
            </Link>

            {/* User Menu */}
            {isAuthenticated ? (
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <button className="focus-visible:ring-ring/50 inline-flex size-8 shrink-0 items-center justify-center rounded-lg text-white transition-all outline-none hover:bg-white/10 focus-visible:ring-3" />
                  }
                >
                  <User className="h-5 w-5" />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-48">
                  {isAdmin && (
                    <>
                      <DropdownMenuItem
                        render={<Link href={'/admin/dashboard' as Route} />}
                        className="flex items-center gap-2 font-semibold text-blue-600"
                      >
                        <Settings className="h-4 w-4" />
                        管理者ダッシュボード
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  <DropdownMenuItem
                    render={<Link href={'/mypage' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <User className="h-4 w-4" />
                    {t('shared.nav.mypage')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    render={<Link href={'/mypage/orders' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <Package className="h-4 w-4" />
                    {t('shared.nav.orders')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    render={<Link href={'/mypage/points' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <Heart className="h-4 w-4" />
                    {t('shared.nav.points')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    render={<Link href={'/mypage/profile' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <Settings className="h-4 w-4" />
                    {t('shared.nav.profile')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    render={<Link href={'/mypage/coupons' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <Ticket className="h-4 w-4" />
                    {t('mypage.coupons.title')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    render={<Link href={'/mypage/returns' as Route} />}
                    className="flex items-center gap-2"
                  >
                    <RotateCcw className="h-4 w-4" />
                    {t('ecReturns.title')}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" className="flex items-center gap-2" onClick={() => logout()}>
                    <LogOut className="h-4 w-4" />
                    {t('shared.nav.logout')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <div className="hidden items-center gap-2 sm:flex">
                <Link
                  href="/login"
                  className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'text-white' })}
                >
                  {t('shared.nav.login')}
                </Link>
                <Link
                  href="/register"
                  className={buttonVariants({ variant: 'secondary', size: 'sm', className: 'text-primary bg-white hover:bg-white/90' })}
                >
                  {t('shared.nav.register')}
                </Link>
              </div>
            )}

            {/* Mobile Menu */}
            <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
              <SheetTrigger
                render={
                  <button className="focus-visible:ring-ring/50 inline-flex size-8 shrink-0 items-center justify-center rounded-lg text-white transition-all outline-none hover:bg-white/10 focus-visible:ring-3 md:hidden" />
                }
              >
                <Menu className="h-5 w-5" />
              </SheetTrigger>
              <SheetContent side="right" className="w-72" aria-label="モバイルメニュー">
                <div className="mt-4 px-3">
                  <SearchBar variant="standalone" />
                </div>
                <nav className="mt-4 flex flex-col gap-2" aria-label="モバイルナビゲーション">
                  {navigation.map((item) => (
                    <Link
                      key={item.name}
                      href={item.href}
                      onClick={() => setMobileMenuOpen(false)}
                      className={`rounded-md px-3 py-2 text-sm font-medium ${
                        pathname === item.href
                          ? 'bg-primary/10 text-primary'
                          : 'text-foreground hover:bg-muted'
                      }`}
                    >
                      {item.name}
                    </Link>
                  ))}
                  <button
                    onClick={() => {
                      setMobileMenuOpen(false);
                      window.dispatchEvent(new Event('open-ai-chat'));
                    }}
                    className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-muted"
                  >
                    <Bot className="h-4 w-4" />
                    {t('shared.nav.aiChat')}
                  </button>
                  <div className="my-2 border-t" />
                  {isAuthenticated ? (
                    <>
                      {isAdmin && (
                        <>
                          <Link href="/admin/dashboard" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-semibold text-blue-600 hover:bg-blue-50">
                            <Settings className="h-4 w-4" />
                            管理者ダッシュボード
                          </Link>
                          <div className="my-2 border-t" />
                        </>
                      )}
                      <Link href="/mypage" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-muted">
                        <User className="h-4 w-4" />
                        {t('shared.nav.mypage')}
                      </Link>
                      <Link href="/mypage/orders" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-muted">
                        <Package className="h-4 w-4" />
                        {t('shared.nav.orders')}
                      </Link>
                      <Link href="/mypage/points" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-muted">
                        <Heart className="h-4 w-4" />
                        {t('shared.nav.points')}
                      </Link>
                      <Link href="/mypage/profile" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-muted">
                        <Settings className="h-4 w-4" />
                        {t('shared.nav.profile')}
                      </Link>
                      <div className="my-2 border-t" />
                      <button
                        onClick={() => { setMobileMenuOpen(false); void logout(); }}
                        className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
                      >
                        <LogOut className="h-4 w-4" />
                        {t('shared.nav.logout')}
                      </button>
                    </>
                  ) : (
                    <>
                      <Link
                        href="/login"
                        onClick={() => setMobileMenuOpen(false)}
                        className="rounded-md px-3 py-2 text-sm font-medium"
                      >
                        {t('shared.nav.login')}
                      </Link>
                      <Link
                        href="/register"
                        onClick={() => setMobileMenuOpen(false)}
                        className="rounded-md px-3 py-2 text-sm font-medium"
                      >
                        {t('shared.nav.register')}
                      </Link>
                    </>
                  )}
                </nav>
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </div>
    </header>
  );
}
