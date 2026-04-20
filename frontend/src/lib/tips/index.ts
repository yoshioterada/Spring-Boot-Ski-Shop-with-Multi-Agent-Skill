/**
 * Tip プールの構築・ランダム選択・本文レンダリングを提供する公開 API。
 * サーバ (BFF route handler) とクライアントの両方で使用される純粋 TS。
 */
import { canonicalizeResort, RESORTS } from './resort-dictionary';
import { TIP_TEMPLATES } from './templates';

import type {
  BuildTipPoolOptions,
  CurationField,
  RenderedTip,
  ResortMeta,
  TipTemplate,
} from './types';

export type { RenderedTip, TipTemplate, TipCategory } from './types';
export { canonicalizeResort, RESORTS } from './resort-dictionary';

const RECENT_HISTORY_RATIO = 0.5; // プールサイズの半分まで履歴を保持

/** resort が curation フィールドを持っているかを判定 */
function hasCurationField(meta: ResortMeta | undefined, field: CurationField): boolean {
  if (!meta) return false;
  switch (field) {
    case 'recommendedRestaurants':
      return Array.isArray(meta.recommendedRestaurants) && meta.recommendedRestaurants.length > 0;
    case 'nearbyOnsen':
      return Array.isArray(meta.nearbyOnsen) && meta.nearbyOnsen.length > 0;
    case 'safetyNotes':
      return Array.isArray(meta.safetyNotes) && meta.safetyNotes.length > 0;
    case 'localTips':
      return Array.isArray(meta.localTips) && meta.localTips.length > 0;
    case 'kidsFacility':
      return Boolean(meta.kidsFacility);
    case 'courses':
      return Boolean(meta.courses);
    case 'signature':
      return typeof meta.signature === 'string' && meta.signature.length > 0;
    default:
      return false;
  }
}

/**
 * 行き先・スキルレベル・月から、表示候補となる Tip プールを生成する。
 * 行き先未指定時はジェネリック (resorts 未指定) のテンプレのみ。
 * requires が指定されたテンプレは、対象 resort に該当データが揃っていなければ除外。
 */
export function buildTipPool(options: BuildTipPoolOptions): TipTemplate[] {
  const canonicalResort = canonicalizeResort(options.destination);
  const meta = canonicalResort ? RESORTS[canonicalResort] : undefined;
  const skillLevel = options.skillLevel?.toUpperCase() ?? null;
  const month = options.month ?? new Date().getMonth() + 1;

  return TIP_TEMPLATES.filter((tpl) => {
    if (tpl.resorts && tpl.resorts.length > 0) {
      if (!canonicalResort) return false;
      if (!tpl.resorts.includes(canonicalResort)) return false;
    }
    if (tpl.months && tpl.months.length > 0 && !tpl.months.includes(month)) {
      return false;
    }
    if (tpl.skillLevels && tpl.skillLevels.length > 0) {
      if (!skillLevel || !tpl.skillLevels.includes(skillLevel)) return false;
    }
    // requires: 指定されたキュレーションフィールドが全て resort に存在する必要がある
    if (tpl.requires && tpl.requires.length > 0) {
      if (!meta) return false;
      if (!tpl.requires.every((f) => hasCurationField(meta, f))) return false;
    }
    return true;
  });
}

/**
 * 重み付き乱択。priority 未指定は 1。
 */
function weightedRandom(pool: TipTemplate[]): TipTemplate {
  const weights = pool.map((t) => Math.max(0.001, t.priority ?? 1));
  const total = weights.reduce((a, b) => a + b, 0);
  let r = Math.random() * total;
  for (let i = 0; i < pool.length; i++) {
    r -= weights[i];
    if (r <= 0) return pool[i];
  }
  return pool[pool.length - 1];
}

/**
 * 直近 N 件と重複しない Tip Picker を生成する。
 * 全 Tip を一巡したら履歴をクリアして再循環する。
 */
export function createTipPicker(pool: TipTemplate[]) {
  const recent: string[] = [];
  const historyMax = Math.max(1, Math.floor(pool.length * RECENT_HISTORY_RATIO));

  return function pickNext(options: BuildTipPoolOptions = {}): RenderedTip | null {
    if (pool.length === 0) return null;
    const candidates = pool.filter((t) => !recent.includes(t.id));
    const target = candidates.length > 0 ? candidates : pool;
    const tpl = weightedRandom(target);
    recent.push(tpl.id);
    if (recent.length > historyMax) recent.shift();
    return renderTip(tpl, options);
  };
}

function pickRandom<T>(arr: T[] | undefined): T | undefined {
  if (!arr || arr.length === 0) return undefined;
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * テンプレートのプレースホルダを行き先メタデータで置換する。
 *
 * 対応プレースホルダ:
 *   基本:
 *     {{resort}} {{snowDepth}} {{nearestOnsen}} {{accessIc}} {{km}}
 *   キュレーション (該当データがあれば):
 *     {{signature}}
 *     {{restaurant.name}} {{restaurant.dish}} {{restaurant.location}}
 *       {{restaurant.price}} {{restaurant.viewPoint}} {{restaurant.note}}
 *     {{onsen.name}} {{onsen.spring}} {{onsen.price}} {{onsen.distance}} {{onsen.note}}
 *     {{kids.name}} {{kids.ageMin}} {{kids.ageMax}} {{kids.price}} {{kids.note}}
 *     {{safety.area}} {{safety.hazard}} {{safety.advice}}
 *     {{localTip}}
 *     {{course.longestRun}} {{course.maxSlope}} {{course.note}}
 *
 * 配列系 (restaurant/onsen/safety/localTip) はテンプレ 1 件あたり 1 つランダム選択する。
 */
export function renderTip(tpl: TipTemplate, options: BuildTipPoolOptions = {}): RenderedTip {
  const canonicalResort = canonicalizeResort(options.destination);
  const meta: ResortMeta | undefined = canonicalResort ? RESORTS[canonicalResort] : undefined;

  // 配列からランダムに 1 つピック (テンプレ 1 件あたり 1 つに固定するため、ここで決める)
  const restaurant = pickRandom(meta?.recommendedRestaurants);
  const onsen = pickRandom(meta?.nearbyOnsen);
  const safety = pickRandom(meta?.safetyNotes);
  const localTip = pickRandom(meta?.localTips);

  const replacements: Record<string, string> = {
    // 基本
    resort: canonicalResort ?? 'スキー場',
    snowDepth: meta?.typicalSnowDepthCm?.toString() ?? '200',
    nearestOnsen: meta?.nearestOnsen ?? '近くの温泉',
    accessIc: meta?.accessIc ?? '最寄りIC',
    km: meta?.accessKmFromIc?.toString() ?? '30',

    // 拡張
    signature: meta?.signature ?? '',
    'restaurant.name': restaurant?.name ?? '',
    'restaurant.dish': restaurant?.popularDish ?? '',
    'restaurant.location': restaurant?.location ?? '',
    'restaurant.price': restaurant?.priceRange ?? '',
    'restaurant.viewPoint': restaurant?.viewPoint ?? '',
    'restaurant.note': restaurant?.note ?? '',
    'onsen.name': onsen?.name ?? '',
    'onsen.spring': onsen?.spring ?? '',
    'onsen.price': onsen?.price ?? '',
    'onsen.distance': onsen?.distanceKm?.toString() ?? '',
    'onsen.note': onsen?.note ?? '',
    'kids.name': meta?.kidsFacility?.name ?? '',
    'kids.ageMin': meta?.kidsFacility?.ageMin?.toString() ?? '',
    'kids.ageMax': meta?.kidsFacility?.ageMax?.toString() ?? '',
    'kids.price': meta?.kidsFacility?.price ?? '',
    'kids.note': meta?.kidsFacility?.note ?? '',
    'safety.area': safety?.area ?? '',
    'safety.hazard': safety?.hazard ?? '',
    'safety.advice': safety?.advice ?? '',
    localTip: localTip ?? '',
    'course.longestRun': meta?.courses?.longestRunM?.toString() ?? '',
    'course.maxSlope': meta?.courses?.maxSlopeDeg?.toString() ?? '',
    'course.note': meta?.courses?.note ?? '',
  };
  const interpolate = (s: string): string =>
    s.replace(/\{\{([\w.]+)\}\}/g, (_, key) => replacements[key] ?? '');
  const title = interpolate(tpl.title);
  const message = interpolate(tpl.message);
  return {
    id: tpl.id,
    category: tpl.category,
    icon: tpl.icon,
    title,
    message,
    source: 'static',
  };
}
