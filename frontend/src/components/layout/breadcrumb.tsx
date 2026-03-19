import { ChevronRight, Home } from 'lucide-react';
import Link from 'next/link';

import type { Route } from 'next';

export interface BreadcrumbItem {
  label: string;
  href?: Route;
}

interface BreadcrumbProps {
  items: BreadcrumbItem[];
  className?: string;
}

export function Breadcrumb({ items, className = '' }: BreadcrumbProps) {
  return (
    <nav aria-label="パンくずリスト" className={`flex items-center text-sm ${className}`}>
      <Link
        href="/"
        className="text-muted-foreground hover:text-foreground flex items-center transition-colors"
      >
        <Home className="h-4 w-4" />
      </Link>
      {items.map((item, index) => (
        <span key={index} className="flex items-center">
          <ChevronRight className="text-muted-foreground mx-2 h-4 w-4" />
          {item.href && index < items.length - 1 ? (
            <Link
              href={item.href}
              className="text-muted-foreground hover:text-foreground transition-colors"
            >
              {item.label}
            </Link>
          ) : (
            <span className="text-foreground font-medium">{item.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
