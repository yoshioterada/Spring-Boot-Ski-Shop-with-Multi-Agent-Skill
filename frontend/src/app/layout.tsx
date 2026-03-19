import { Inter, Noto_Sans_JP } from 'next/font/google';

import './globals.css';
import { Providers } from './providers';

import type { Metadata } from 'next';

const inter = Inter({
  variable: '--font-sans',
  subsets: ['latin'],
  display: 'swap',
});

const notoSansJP = Noto_Sans_JP({
  variable: '--font-heading',
  subsets: ['latin'],
  display: 'swap',
  weight: ['400', '500', '700'],
});

export const metadata: Metadata = {
  title: {
    default: 'Azure SkiShop',
    template: '%s | Azure SkiShop',
  },
  description:
    'スキー用品のオンラインショップ。最新のスキー板、ブーツ、ウェア、アクセサリーを取り揃えています。',
  keywords: ['スキー', 'スノーボード', 'ウィンタースポーツ', 'ski', 'snowboard'],
  openGraph: {
    title: 'Azure SkiShop',
    description: 'スキー用品のオンラインショップ',
    type: 'website',
    locale: 'ja_JP',
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ja"
      className={`${inter.variable} ${notoSansJP.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="flex min-h-full flex-col font-sans">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
