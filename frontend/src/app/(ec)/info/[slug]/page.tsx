import { Breadcrumb } from '@/components/layout/breadcrumb';

import type { Route } from 'next';

export default function StaticPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return <StaticPageContent paramsPromise={params} />;
}

async function StaticPageContent({ paramsPromise }: { paramsPromise: Promise<{ slug: string }> }) {
  const { slug } = await paramsPromise;

  const pages: Record<string, { title: string; content: string }> = {
    contact: {
      title: 'お問い合わせ',
      content:
        'お問い合わせは以下のメールアドレスまでご連絡ください。\n\nメール: support@azure-skishop.example.com\n\n営業時間: 平日 9:00 - 18:00（土日祝日を除く）\n\nお問い合わせいただいてから、通常2営業日以内にご返信いたします。',
    },
    faq: {
      title: 'よくある質問',
      content:
        'Q. 注文後のキャンセルはできますか？\nA. 発送前であればキャンセル可能です。マイページの注文詳細からキャンセルをお願いします。\n\nQ. 送料はいくらですか？\nA. 10,000円以上のお買い上げで送料無料です。10,000円未満の場合は500円の送料がかかります。\n\nQ. 返品・交換はできますか？\nA. 商品到着後7日以内であれば、未使用品に限り返品・交換を承ります。マイページから返品申請をお願いします。\n\nQ. ポイントの有効期限は？\nA. ポイントは付与日から1年間有効です。失効前にメールでお知らせします。',
    },
    shipping: {
      title: '配送について',
      content:
        '■ 配送業者\nヤマト運輸または佐川急便で配送いたします。\n\n■ 配送日数\nご注文確定後、通常2〜5営業日でお届けいたします。\n\n■ 送料\n10,000円以上のお買い上げで送料無料\n10,000円未満: 全国一律 500円\n\n■ 配送先の変更\n発送前であれば、マイページの注文詳細から変更可能です。',
    },
    'returns-policy': {
      title: '返品・交換',
      content:
        '■ 返品条件\n商品到着後7日以内、未使用・未開封の商品に限り返品を承ります。\n\n■ 返品方法\n1. マイページ → 注文詳細 → 返品申請\n2. 返品理由と詳細を入力\n3. 承認後、返送先をメールでご案内\n\n■ 返金方法\n返品商品の到着・確認後、5営業日以内にご利用の決済方法へ返金いたします。\n\n■ 交換\n在庫がある場合に限り、同一商品のサイズ・カラー交換を承ります。',
    },
    about: {
      title: '会社概要',
      content:
        '会社名: Azure SkiShop 株式会社\n設立: 2024年\n事業内容: スキー用品・ウィンタースポーツ用品のオンライン販売\n所在地: 東京都渋谷区\nお問い合わせ: support@azure-skishop.example.com',
    },
    terms: {
      title: '利用規約',
      content:
        'この利用規約（以下「本規約」）は、Azure SkiShop（以下「当社」）が提供するサービスの利用条件を定めるものです。\n\n第1条 適用\n本規約は、当社のサービスを利用する全てのお客様に適用されます。\n\n第2条 会員登録\nお客様は、正確な情報を提供して会員登録を行うものとします。\n\n第3条 禁止事項\n不正行為、転売目的の大量購入、その他当社が不適切と判断する行為を禁止します。\n\n第4条 免責事項\n当社は、天災・システム障害等の不可抗力によるサービス中断については責任を負いません。',
    },
    privacy: {
      title: 'プライバシーポリシー',
      content:
        'Azure SkiShop（以下「当社」）は、お客様の個人情報の取り扱いについて以下の通り定めます。\n\n■ 収集する情報\n氏名、メールアドレス、住所、電話番号、購入履歴\n\n■ 利用目的\n商品の発送、サービスの提供・改善、お問い合わせ対応\n\n■ 第三者提供\n配送業者・決済事業者を除き、お客様の同意なく第三者に提供することはありません。\n\n■ 情報の管理\n適切なセキュリティ対策を講じ、個人情報の漏洩防止に努めます。\n\n■ Cookie の使用\n当サイトではサービス向上のため Cookie を使用しています。',
    },
    legal: {
      title: '特定商取引法に基づく表記',
      content:
        '販売事業者: Azure SkiShop 株式会社\n代表者: 寺田 佳央\n所在地: 東京都渋谷区\nメール: support@azure-skishop.example.com\n電話番号: 03-XXXX-XXXX\n販売価格: 各商品ページに記載\n送料: 10,000円以上無料、未満は500円\nお支払い方法: クレジットカード\n商品の引渡時期: ご注文確定後2〜5営業日\n返品・交換: 商品到着後7日以内、未使用品に限る\n動作環境: 最新版のChrome, Firefox, Safari, Edge',
    },
  };

  const page = pages[slug];

  if (!page) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-12">
        <h1 className="text-2xl font-bold">ページが見つかりません</h1>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      <Breadcrumb items={[{ label: page.title }]} className="mb-6" />
      <h1 className="mb-8 text-3xl font-bold">{page.title}</h1>
      <div className="prose prose-gray max-w-none">
        {page.content.split('\n\n').map((paragraph, i) => (
          <p key={i} className="mb-4 whitespace-pre-wrap leading-relaxed">
            {paragraph}
          </p>
        ))}
      </div>
    </div>
  );
}
