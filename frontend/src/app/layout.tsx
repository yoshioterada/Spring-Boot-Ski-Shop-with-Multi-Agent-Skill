import { Inter, Noto_Sans_JP } from 'next/font/google';
import { getServerSession } from 'next-auth';

import './globals.css';
import { authOptions } from '@/lib/auth-options';
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

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  let session = null;
  try {
    session = await getServerSession(authOptions);
  } catch {
    // getServerSession handles JWT errors internally; treat as unauthenticated
    session = null;
  }

  return (
    <html
      lang="ja"
      className={`${inter.variable} ${notoSansJP.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="flex min-h-full flex-col font-sans">
        <Providers session={session}>{children}</Providers>
      </body>
    </html>
  );
}
