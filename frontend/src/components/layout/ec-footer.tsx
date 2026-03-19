import { Mountain } from 'lucide-react';
import Link from 'next/link';

import { t } from '@/lib/i18n';

import type { Route } from 'next';

const footerLinks = {
  shop: {
    title: t('shared.footer.shop'),
    links: [
      { name: t('shared.footer.allProducts'), href: '/catalog' as Route },
      { name: t('shared.footer.newArrivals'), href: '/catalog?sort=createdAt,desc' as Route },
      { name: t('shared.footer.sale'), href: '/catalog?sale=true' as Route },
    ],
  },
  support: {
    title: t('shared.footer.support'),
    links: [
      { name: t('shared.footer.contact'), href: '/info/contact' as Route },
      { name: t('shared.footer.faq'), href: '/info/faq' as Route },
      { name: t('shared.footer.shipping'), href: '/info/shipping' as Route },
      { name: t('shared.footer.returns'), href: '/info/returns-policy' as Route },
    ],
  },
  company: {
    title: t('shared.footer.company'),
    links: [
      { name: t('shared.footer.about'), href: '/info/about' as Route },
      { name: t('shared.footer.terms'), href: '/info/terms' as Route },
      { name: t('shared.footer.privacy'), href: '/info/privacy' as Route },
      { name: t('shared.footer.legal'), href: '/info/legal' as Route },
    ],
  },
};

export function ECFooter() {
  return (
    <footer className="bg-muted/50 border-t">
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-4">
          {/* Brand */}
          <div className="space-y-4">
            <Link href="/" className="flex items-center gap-2">
              <Mountain className="text-primary h-6 w-6" />
              <span className="text-lg font-bold">Azure SkiShop</span>
            </Link>
            <p className="text-muted-foreground text-sm">
              {/* TODO: i18n */}
              プレミアムスキー用品のオンラインショップ。最高品質のギアで、最高の滑りを。
            </p>
          </div>

          {/* Links */}
          {Object.values(footerLinks).map((section) => (
            <div key={section.title}>
              <h3 className="mb-4 text-sm font-semibold">{section.title}</h3>
              <ul className="space-y-2">
                {section.links.map((link) => (
                  <li key={link.name}>
                    <Link
                      href={link.href}
                      className="text-muted-foreground hover:text-foreground text-sm transition-colors"
                    >
                      {link.name}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-8 border-t pt-8">
          <p className="text-muted-foreground text-center text-sm">
            &copy; {new Date().getFullYear()} Azure SkiShop. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
