import type { ResortMeta } from './types';

/**
 * 行き先メタデータ。バックエンド `OpenMeteoClient.KNOWN_RESORTS` とキーを揃える。
 * Tip テンプレート内の {{resort}} {{snowDepth}} {{nearestOnsen}} {{accessIc}} {{km}}
 * 等のプレースホルダはここから解決される。
 *
 * 5 リゾート (苗場 / 白馬 / 志賀高原 / 野沢温泉 / ニセコ) はキュレーション付き。
 * ResortMeta の signature / courses / kidsFacility / recommendedRestaurants /
 * nearbyOnsen / safetyNotes / localTips を埋めている。
 *
 * 文体ポリシー: 出典の明示と表現緩和のため、文末は「〜と言われています」
 * 「〜だそうです」「〜と書かれています」などに統一。
 */
export const RESORTS: Record<string, ResortMeta> = {
  // ============================================================
  // 新潟県
  // ============================================================
  苗場: {
    canonicalName: '苗場',
    aliases: ['Naeba', 'naeba'],
    region: '新潟県',
    elevationM: 1789,
    typicalSnowDepthCm: 320,
    nearestOnsen: '苗場温泉',
    accessIc: '月夜野IC',
    accessKmFromIc: 35,
    famousLifts: ['ドラゴンドラ', '第3高速リフト'],
    nightSki: true,
    courseCount: 24,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
    signature:
      '苗場プリンスホテル直結。ゴンドラ「ドラゴンドラ」で隣の田代スキー場とも接続していると言われています。',
    courses: {
      beginnerRatio: 0.3,
      intermediateRatio: 0.4,
      advancedRatio: 0.3,
      longestRunM: 4000,
      maxSlopeDeg: 32,
      note: 'ファミリーから上級者までバランス良く楽しめると言われています',
    },
    kidsFacility: {
      name: 'スノーガーデン',
      ageMin: 1,
      ageMax: 6,
      price: '約 1,500円/2h',
      note: 'そり道や小さなスロープがあり、スキーデビューにもぴったりだそうです',
    },
    recommendedRestaurants: [
      {
        name: 'Blue Lagoon',
        location: '苗場プリンスホテル内',
        popularDish: 'ステーキランチ',
        priceRange: '~3,000円',
        viewPoint: '広い窓からゲレンデを眺めながらランチできると言われています',
      },
      {
        name: 'Sun Mall フードコート',
        location: 'ゲレンデベース',
        popularDish: '苗場カレー',
        priceRange: '~1,200円',
        note: 'カジュアルに立ち寄るのに丁度良いと評判です',
      },
    ],
    nearbyOnsen: [
      {
        name: '苗場温泉 雪ささの湯',
        spring: 'カルシウム硫酸塩・塩化物泉',
        price: '約 800円',
        distanceKm: 0.5,
        note: 'スキー場から歩いて行ける距離にあるそうです',
      },
      {
        name: '貝掛温泉館',
        spring: '不加温・不加水の名湯',
        price: '約 1,500円',
        distanceKm: 8,
        note: '「目の湯」とも呼ばれる古い一軒宿で、雰囲気が人気と言われています',
      },
    ],
    safetyNotes: [
      {
        area: '第4高速リフト上部',
        hazard: '霧が発生しやすい',
        advice:
          '視界不良時はゴーグルをクリアにし、一本下りるごとに休憩して身体を温めると安心と言われています',
      },
    ],
    localTips: [
      'ドラゴンドラは全長 5,481m、乗車時間は約 25 分と言われていて、天気が良ければ谷の景観が見渡せるそうです',
      '期間限定で薪ストーブを囲むゲレンデバーベキューイベントが開催されることもあるそうです',
    ],
    lastCuratedAt: '2026-04-18',
  },
  かぐら: {
    canonicalName: 'かぐら',
    aliases: ['Kagura', 'kagura'],
    region: '新潟県',
    elevationM: 1845,
    typicalSnowDepthCm: 350,
    nearestOnsen: '貝掛温泉',
    accessIc: '月夜野IC',
    accessKmFromIc: 40,
    famousLifts: ['みつまたロープウェー'],
    nightSki: false,
    courseCount: 32,
    skillFit: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
  },
  湯沢: {
    canonicalName: '湯沢',
    aliases: ['越後湯沢', 'Yuzawa', 'Echigo-Yuzawa'],
    region: '新潟県',
    elevationM: 1200,
    typicalSnowDepthCm: 250,
    nearestOnsen: '越後湯沢温泉',
    accessIc: '湯沢IC',
    accessKmFromIc: 3,
    nightSki: true,
    courseCount: 30,
    skillFit: ['BEGINNER', 'INTERMEDIATE'],
  },
  ガーラ湯沢: {
    canonicalName: 'ガーラ湯沢',
    aliases: ['Gala Yuzawa'],
    region: '新潟県',
    elevationM: 1181,
    typicalSnowDepthCm: 240,
    nearestOnsen: '越後湯沢温泉',
    accessIc: '湯沢IC',
    accessKmFromIc: 2,
    nightSki: false,
    courseCount: 16,
    skillFit: ['BEGINNER', 'INTERMEDIATE'],
  },
  妙高: {
    canonicalName: '妙高',
    aliases: ['Myoko'],
    region: '新潟県',
    elevationM: 1500,
    typicalSnowDepthCm: 380,
    nearestOnsen: '赤倉温泉',
    accessIc: '妙高高原IC',
    accessKmFromIc: 5,
    nightSki: false,
    skillFit: ['INTERMEDIATE', 'ADVANCED'],
  },
  赤倉: {
    canonicalName: '赤倉',
    aliases: ['Akakura'],
    region: '新潟県',
    elevationM: 1300,
    typicalSnowDepthCm: 300,
    nearestOnsen: '赤倉温泉',
    accessIc: '妙高高原IC',
    accessKmFromIc: 3,
    nightSki: true,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
  },

  // ============================================================
  // 長野県
  // ============================================================
  白馬: {
    canonicalName: '白馬',
    aliases: ['Hakuba', 'hakuba', '白馬八方', '八方尾根', 'Hakuba Happo-One'],
    region: '長野県',
    elevationM: 1831,
    typicalSnowDepthCm: 280,
    nearestOnsen: '白馬八方温泉',
    accessIc: '安曇野IC',
    accessKmFromIc: 50,
    famousLifts: ['アダム', 'ウサギ平クワッド'],
    nightSki: true,
    courseCount: 35,
    skillFit: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
    signature:
      '1998 年長野五輪のダウンヒルコースを辿れる、日本を代表するビッグゲレンデと言われています。',
    courses: {
      beginnerRatio: 0.3,
      intermediateRatio: 0.5,
      advancedRatio: 0.2,
      longestRunM: 8000,
      maxSlopeDeg: 35,
      note: 'スカイラインコースは中級者以上向けで、スケール感のあるロングランが楽しめると言われています',
    },
    kidsFacility: {
      name: '八方尾根スキースクール',
      ageMin: 4,
      ageMax: 12,
      price: '約 6,000円/半日',
      note: 'キッズレッスンも丁寧だと評判で、初めてのスキー体験に丁度良いと言われています',
    },
    recommendedRestaurants: [
      {
        name: 'スカイライン テラスシェフ',
        location: 'ウサギ平 (標高約 1,400m)',
        popularDish: '信州ソースカツ丼',
        priceRange: '~1,800円',
        viewPoint: 'テラス席から白馬三山を一望できると言われています',
      },
      {
        name: 'レストラン グラート',
        location: '八方スキー場ベース',
        popularDish: '信州牛のステーキ',
        priceRange: '~2,500円',
        note: 'ナイター営業日は夜も賑わうと言われています',
      },
      {
        name: 'Hakuba Tap Room',
        location: '八方ベース付近',
        popularDish: 'クラフトビールと信州ポーク',
        priceRange: '~3,500円',
        note: 'アフタースキーに人気だそうです',
      },
    ],
    nearbyOnsen: [
      {
        name: '白馬八方温泉 郷の湯',
        spring: '弱アルカリ性単純温泉',
        price: '約 800円',
        distanceKm: 1,
        note: '肉体疲労に効くと言われるアルカリ性の湯だそうです',
      },
      {
        name: '十郎の湯',
        spring: '弱アルカリ性単純温泉',
        price: '約 600円',
        distanceKm: 1.5,
        note: '露天風呂から雪景色を眺められるという声を見かけます',
      },
      {
        name: 'みずばしょう温泉',
        spring: '弱アルカリ性単純温泉',
        price: '約 700円',
        distanceKm: 2,
      },
    ],
    safetyNotes: [
      {
        area: 'リーゼンスラロームコース上部',
        hazard: 'コース外の雪崩リスク',
        advice:
          'ビーコン携帯とパトロール情報の確認の上、できればガイド同行を推奨すると案内されています',
      },
      {
        area: '上部スカイラインコース',
        hazard: '不整地・霧発生が多い',
        advice: 'ゴーグル・グローブは防曇加工されたものが安心と言われています',
      },
    ],
    localTips: [
      '1998 年長野五輪で K90/K120 ジャンプ台会場となったスキージャンプ競技場が近くにあり、見学も可能と言われています',
      '隣接する栂池・五竜・白馬岩岳と共通リフト券で複数スキー場を周れるプランもあるそうです',
    ],
    lastCuratedAt: '2026-04-18',
  },
  志賀高原: {
    canonicalName: '志賀高原',
    aliases: ['Shiga Kogen', '志賀'],
    region: '長野県',
    elevationM: 2305,
    typicalSnowDepthCm: 250,
    nearestOnsen: '湯田中・渋温泉',
    accessIc: '信州中野IC',
    accessKmFromIc: 30,
    famousLifts: ['東館山高速ペアリフト', '横手山スカイライナー'],
    nightSki: false,
    courseCount: 49,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'],
    signature:
      '国内最大級。多数のスキー場が連結し、共通リフト券で周れる「スキー銀座」とも呼ばれるエリアと言われています。',
    courses: {
      beginnerRatio: 0.4,
      intermediateRatio: 0.4,
      advancedRatio: 0.2,
      longestRunM: 6000,
      maxSlopeDeg: 36,
      note: '横手山スキー場は標高約 2,307m と、国内最高所のゲレンデと言われています',
    },
    kidsFacility: {
      name: '志賀高原スキースクール',
      ageMin: 3,
      ageMax: 12,
      price: '約 5,000円/半日',
      note:
        '一ノ瀬ファミリーや焼額山付近にキッズパークがあり、ソリやチューブスライダーも充実しているとされています',
    },
    recommendedRestaurants: [
      {
        name: '焼額山 オークバレー',
        location: '焼額山スキー場ベース',
        popularDish: 'キノコクリームシチュー',
        priceRange: '~1,500円',
        viewPoint: '中央の大きなストーブを囲んで休憩できると言われています',
      },
      {
        name: '一ノ瀬ファミリー サンライズ',
        location: '一ノ瀬スキー場ベース',
        popularDish: '信州そばと野沢菜丼セット',
        priceRange: '~1,400円',
        note: '子供連れに人気のキッズスペースがあるとされています',
      },
    ],
    nearbyOnsen: [
      {
        name: '渋温泉 外湯巡り (九湯)',
        spring: '含硫黄ナトリウム・カルシウム塩化物泉',
        price: '宿泊者無料・日帰りは 1 湯あたり約 500円',
        distanceKm: 8,
        note: '「九湯めぐり」を達成すると満願成就のご利益があると伝えられているそうです',
      },
      {
        name: '湯田中温泉 ゆけむりテルメ',
        spring: '単純温泉',
        price: '無料 (足湯)',
        distanceKm: 10,
        note: '駅前にある足湯で、電車待ち時間にちょうどよいという声もあるそうです',
      },
    ],
    safetyNotes: [
      {
        area: '横手山スカイライナー上部',
        hazard: '高標高ゆえの低体温・強風',
        advice:
          'フェイスマスクとネックウォーマーをひとつ余分に持っていくと安心と言われています',
      },
    ],
    localTips: [
      '志賀高原はニホンザル等も生息する、ユネスコ MAB 認定の「志賀高原ユネスコエコパーク」の一部と言われています',
      '高標高ゆえ長いシーズンと、雪質の良さでも知られているそうです',
    ],
    lastCuratedAt: '2026-04-18',
  },
  野沢温泉: {
    canonicalName: '野沢温泉',
    aliases: ['Nozawa Onsen', '野沢'],
    region: '長野県',
    elevationM: 1650,
    typicalSnowDepthCm: 280,
    nearestOnsen: '野沢温泉',
    accessIc: '豊田飯山IC',
    accessKmFromIc: 25,
    nightSki: false,
    courseCount: 36,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
    signature:
      'スキー場と温泉街が一体化し、唯一無二の「外湯めぐり (13 湯)」が誉れと言われています。',
    courses: {
      beginnerRatio: 0.4,
      intermediateRatio: 0.4,
      advancedRatio: 0.2,
      longestRunM: 10000,
      maxSlopeDeg: 39,
      note: '最長コース「スカイラインコース」は 10km 超と言われています',
    },
    kidsFacility: {
      name: 'スノーポケット',
      ageMin: 1,
      ageMax: 6,
      price: '約 1,200円/2h',
      note: '小さなスロープとそり道、チューブスライダーがあるとされています',
    },
    recommendedRestaurants: [
      {
        name: 'ハウス・サンアントン',
        location: '野沢温泉街',
        popularDish: '手作りパンとチーズフォンデュ',
        priceRange: '~3,000円',
        note: 'オーストリア人オーナーのベーカリー・レストランとして評判です',
      },
      {
        name: 'やまびこ',
        location: 'ゲレンデ上部',
        popularDish: '手打ちそばと野沢菜丼',
        priceRange: '~1,500円',
        note: 'ゲレンデ上部でも本格そばが食べられるという話を耳にします',
      },
    ],
    nearbyOnsen: [
      {
        name: '大湯',
        spring: '硫黄泉',
        price: '無料 (賽銭制)',
        distanceKm: 0.3,
        note: '野沢温泉 13 湯の中でも中心的な一湯と言われています',
      },
      {
        name: '麻釜',
        spring: '硫黄泉 (源泉約 90℃。野菜をゆでる「麻釜」に使用)',
        price: '見学のみ (立入厳禁)',
        distanceKm: 0.4,
        note: 'ここでタマゴや野菜をゆでる光景は野沢名物と言われています',
      },
      {
        name: '河原湯',
        spring: '硫黄泉',
        price: '無料 (賽銭制)',
        distanceKm: 0.3,
      },
    ],
    safetyNotes: [
      {
        area: 'スカイラインコース上部',
        hazard: '視界不良時のコースアウト事例',
        advice:
          'コースマップをスマホにダウンロードし、コース外に出ないようご注意くださいと案内されています',
      },
    ],
    localTips: [
      '野沢温泉は古い温泉街そのものがゲレンデ下として機能しており、スキーブーツで湯めぐりもできると言われています',
      '毎年 1 月 15 日の道祖神祭り (火まつり) は国重要無形民俗文化財に指定されているそうです',
    ],
    lastCuratedAt: '2026-04-18',
  },

  // ============================================================
  // 北海道
  // ============================================================
  ニセコ: {
    canonicalName: 'ニセコ',
    aliases: ['Niseko', 'niseko'],
    region: '北海道',
    elevationM: 1308,
    typicalSnowDepthCm: 400,
    nearestOnsen: 'ニセコ昆布温泉',
    accessIc: '札樽自動車道',
    accessKmFromIc: 90,
    nightSki: true,
    skillFit: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
    signature:
      'ニセコユナイテッド (4 スキー場連結)。世界有数のサラサラパウダースノーで知られていると言われています。',
    courses: {
      beginnerRatio: 0.3,
      intermediateRatio: 0.4,
      advancedRatio: 0.3,
      longestRunM: 5600,
      maxSlopeDeg: 40,
      note: '整備されたコースとゲート付きサイドカントリーを楽しめることで知られています',
    },
    kidsFacility: {
      name: 'NISS (Niseko International Snowsports School)',
      ageMin: 3,
      ageMax: 14,
      price: '約 12,000円/半日',
      note:
        '多言語対応 (英語・中国語・日本語) と言われ、国際色豊かなレッスンとされています',
    },
    recommendedRestaurants: [
      {
        name: 'King Bell Hut',
        location: 'Hirafu スキー場中腹',
        popularDish: 'ビーフステーキコロッケ',
        priceRange: '~2,500円',
        viewPoint: '羊蹄山を正面に見ながらランチできると言われています',
      },
      {
        name: 'ニセコピザ (Niseko Pizza)',
        location: 'ひらふ町',
        popularDish: '本格窯焼きピッツァ',
        priceRange: '~2,000円',
      },
      {
        name: 'Cafe BOO',
        location: 'ひらふ町',
        popularDish: 'スープカレー',
        priceRange: '~1,500円',
        note: '朝食セットが人気という話もあるそうです',
      },
    ],
    nearbyOnsen: [
      {
        name: '黄金温泉 黄金の湯',
        spring: 'ナトリウム塩化物・硫酸塩泉 (屋外露天)',
        price: '約 700円',
        distanceKm: 5,
        note: '雪見露天とチーズケーキが名物だそうです',
      },
      {
        name: 'ニセコ湯本温泉',
        spring: '含鉄・ナトリウム硫酸塩泉',
        price: '約 1,000円',
        distanceKm: 7,
        note: '鉄分が多く、湯上がりに身体が芯から温まると言われています',
      },
      {
        name: '五色温泉旅館',
        spring: '含硫黄・ナトリウム硫酸塩泉',
        price: '約 700円',
        distanceKm: 12,
      },
    ],
    safetyNotes: [
      {
        area: 'ゲート外サイドカントリー',
        hazard: '雪崩リスク・見えにくいクレバス',
        advice:
          'ゲートオープンを必ず確認し、ビーコン・スコップ・ゾンデ棒を携帯してくださいと案内されています',
      },
      {
        area: '上部コース (山頂付近)',
        hazard: '強風・低視界',
        advice: '強風時はリフト運休になることもあるため、その日のリフト運行情報を出発前に確認すると安心と言われています',
      },
    ],
    localTips: [
      'ニセコはトップシーズン (1〜2 月) にサラサラと言われる軽やかな雪質で、オーストラリアや欧米からのスキーヤーも多いと言われています',
      'ナイター営業は Hirafu を中心に長い時間帯で行われ、夜街に出る前に一本滑れるのがトレンドだそうです',
    ],
    lastCuratedAt: '2026-04-18',
  },
  ルスツ: {
    canonicalName: 'ルスツ',
    aliases: ['Rusutsu'],
    region: '北海道',
    elevationM: 994,
    typicalSnowDepthCm: 320,
    nearestOnsen: 'ルスツ温泉',
    accessIc: '札樽自動車道',
    accessKmFromIc: 90,
    nightSki: true,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
  },
  富良野: {
    canonicalName: '富良野',
    aliases: ['Furano'],
    region: '北海道',
    elevationM: 1074,
    typicalSnowDepthCm: 280,
    nearestOnsen: 'フラノ温泉',
    accessIc: '旭川IC',
    accessKmFromIc: 60,
    nightSki: false,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
  },
  トマム: {
    canonicalName: 'トマム',
    aliases: ['Tomamu'],
    region: '北海道',
    elevationM: 1239,
    typicalSnowDepthCm: 280,
    nearestOnsen: '木林の湯',
    accessIc: 'トマムIC',
    accessKmFromIc: 5,
    nightSki: false,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
  },
  キロロ: {
    canonicalName: 'キロロ',
    aliases: ['Kiroro'],
    region: '北海道',
    elevationM: 1180,
    typicalSnowDepthCm: 350,
    nearestOnsen: 'キロロ温泉',
    accessIc: '小樽IC',
    accessKmFromIc: 40,
    nightSki: false,
    skillFit: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
  },
};

/**
 * Intent から返る値（"Naeba" 等の英語表記、表記揺れ）を正規名に正規化する。
 * 該当が無ければ null（呼び出し側でジェネリック Tip にフォールバック）。
 */
export function canonicalizeResort(input?: string | null): string | null {
  if (!input) return null;
  const trimmed = input.trim();
  if (!trimmed) return null;
  if (RESORTS[trimmed]) return trimmed;
  const lower = trimmed.toLowerCase();
  for (const [key, meta] of Object.entries(RESORTS)) {
    if (key.toLowerCase() === lower) return key;
    if (meta.aliases.some((a) => a.toLowerCase() === lower)) return key;
  }
  // 部分一致 (例: "白馬八方尾根" -> "白馬")
  for (const [key, meta] of Object.entries(RESORTS)) {
    if (trimmed.includes(key) || key.includes(trimmed)) return key;
    if (meta.aliases.some((a) => trimmed.includes(a) || a.includes(trimmed))) return key;
  }
  return null;
}
