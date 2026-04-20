/**
 * 待ち時間ストリーミング機能の型定義。
 * add-info-2-customer-spec.md §7 参照。
 */

export type TipCategory =
  | 'SNOW' // 雪質・ゲレンデコンディション
  | 'LIFT' // リフト運行・コース開放
  | 'ACCESS' // アクセス・積雪路面
  | 'RENTAL' // レンタル・チューンナップ
  | 'SCHOOL' // スクール・レッスン
  | 'LODGING' // 宿泊（乾燥室・温泉）
  | 'DINING' // ゲレンデ内飲食
  | 'SAFETY' // 安全・パトロール
  | 'GEAR' // ギア・装備
  | 'EVENT'; // イベント・ナイター

/**
 * Tip テンプレートが必要とするキュレーション済みフィールド。
 * いずれか一つでも resort に存在しなければそのテンプレは表示候補から除外される。
 * これにより「キュレーション済みリゾートだけ詳細 Tip を出す」という
 * グレースフルデグラデーションが実現できる。
 */
export type CurationField =
  | 'recommendedRestaurants'
  | 'nearbyOnsen'
  | 'kidsFacility'
  | 'safetyNotes'
  | 'courses'
  | 'localTips'
  | 'signature';

export interface TipTemplate {
  id: string;
  category: TipCategory;
  icon: string;
  title: string;
  /** {{placeholder}} はビルド時に解決される */
  message: string;
  /** どの行き先で出すか。未指定なら全リゾートで適用 */
  resorts?: string[];
  /** どの月で出すか (1-12)。未指定なら通年 */
  months?: number[];
  /** スキルレベル制約。指定があれば該当のみ表示 */
  skillLevels?: string[];
  /** 重み。同じプール内で重み付き乱択する。既定 1 */
  priority?: number;
  /** このテンプレが必要とするキュレーションデータ。
   *  指定された全フィールドが対象 resort に存在しないと候補から除外される。 */
  requires?: CurationField[];
}

/** キュレーション済みレストラン情報 */
export interface RestaurantInfo {
  name: string;
  location?: string; // 「山頂」「ベース」「ホテル内」など
  popularDish?: string;
  priceRange?: string; // 「~1,500円」など
  viewPoint?: string; // 「テラス席から白馬三山」など
  note?: string;
}

/** キュレーション済み温泉情報 */
export interface OnsenInfo {
  name: string;
  spring?: string; // 泉質 (硫黄泉/単純泉 等)
  price?: string; // 入湯料
  distanceKm?: number;
  note?: string; // 「ゲレンデから歩いて 3 分」など
}

/** キッズ向け施設・スクール情報 */
export interface KidsFacilityInfo {
  name: string;
  ageMin?: number;
  ageMax?: number;
  price?: string;
  note?: string;
}

/** 安全に関する注意点 (山岳パトロール掲示・公式案内ベース) */
export interface SafetyNoteInfo {
  area?: string;
  hazard?: string;
  advice: string;
}

/** コース構成のサマリー */
export interface CoursesInfo {
  beginnerRatio?: number; // 0-1
  intermediateRatio?: number;
  advancedRatio?: number;
  longestRunM?: number;
  maxSlopeDeg?: number;
  note?: string;
}

export interface ResortMeta {
  canonicalName: string;
  aliases: string[];
  region: string;
  elevationM?: number;
  typicalSnowDepthCm?: number;
  nearestOnsen?: string;
  accessIc?: string;
  accessKmFromIc?: number;
  famousLifts?: string[];
  nightSki?: boolean;
  courseCount?: number;
  skillFit?: string[];
  // ===== 以下は手動キュレーション or LLM スクレイパーで充填される拡張フィールド =====
  /** リゾートを一言で表す差別化ポイント (例:「ガーラ湯沢駅直結」) */
  signature?: string;
  /** コース構成のサマリー */
  courses?: CoursesInfo;
  /** キッズ向け施設・スクール */
  kidsFacility?: KidsFacilityInfo;
  /** 推奨ゲレ食・周辺グルメ (複数) */
  recommendedRestaurants?: RestaurantInfo[];
  /** 近隣温泉 (複数) */
  nearbyOnsen?: OnsenInfo[];
  /** 安全上の注意 (複数) */
  safetyNotes?: SafetyNoteInfo[];
  /** リゾート固有の小ネタ (複数). フリーフォーマットの 1 文 */
  localTips?: string[];
  /** キュレーションデータの最終更新日 (ISO8601). 表示時の鮮度バッジ用 */
  lastCuratedAt?: string;
}

export interface BuildTipPoolOptions {
  destination?: string | null;
  skillLevel?: string | null;
  month?: number;
}

export interface RenderedTip {
  id: string;
  category: TipCategory;
  icon: string;
  title: string;
  message: string;
  source: 'static';
}
