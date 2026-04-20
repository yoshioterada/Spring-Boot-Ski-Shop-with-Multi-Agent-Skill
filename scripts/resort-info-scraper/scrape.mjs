#!/usr/bin/env node
/**
 * resort-info-scraper / scrape.mjs
 *
 * 目的:
 *   スキー場公式サイトの HTML を Azure OpenAI (Structured Outputs) に渡し、
 *   ResortMeta のキュレーションフィールドを JSON で抽出する。
 *
 * 実行例:
 *   AZURE_OPENAI_ENDPOINT=https://xxx.openai.azure.com \
 *   AZURE_OPENAI_API_KEY=*** \
 *   AZURE_OPENAI_DEPLOYMENT=gpt-4o-mini \
 *   node scrape.mjs                    # 全リゾート
 *   node scrape.mjs --resort 白馬       # 1 件だけ
 *
 * 出力:
 *   ./output/{resortKey}.json
 *
 * 注意:
 *   - 本ファイルはプロトタイプ。robots.txt / 利用規約を遵守すること。
 *   - 公開された情報のみを対象とし、個人情報・課金情報は抽出しない。
 *   - 出力 JSON は人手レビュー後、frontend/src/lib/tips/resort-dictionary.ts に手動で取り込む。
 */

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { AzureOpenAI } from 'openai';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---------------------------------------------------------------
// 設定
// ---------------------------------------------------------------
const ENDPOINT = process.env.AZURE_OPENAI_ENDPOINT;
const API_KEY = process.env.AZURE_OPENAI_API_KEY;
const DEPLOYMENT = process.env.AZURE_OPENAI_DEPLOYMENT ?? 'gpt-4o-mini';
const API_VERSION = process.env.AZURE_OPENAI_API_VERSION ?? '2024-10-21';
const MAX_HTML_CHARS = Number(process.env.MAX_HTML_CHARS ?? 30_000);
const FETCH_TIMEOUT_MS = Number(process.env.FETCH_TIMEOUT_MS ?? 15_000);
const REQUEST_INTERVAL_MS = Number(process.env.REQUEST_INTERVAL_MS ?? 1_500);

if (!ENDPOINT || !API_KEY) {
  console.error('[ERROR] AZURE_OPENAI_ENDPOINT と AZURE_OPENAI_API_KEY を設定してください。');
  process.exit(1);
}

const client = new AzureOpenAI({
  endpoint: ENDPOINT,
  apiKey: API_KEY,
  apiVersion: API_VERSION,
  deployment: DEPLOYMENT,
});

// ---------------------------------------------------------------
// JSON Schema (ResortMeta のキュレーション部分に対応)
// ---------------------------------------------------------------
const RESORT_META_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  properties: {
    signature: {
      type: 'string',
      description:
        'スキー場を一言で表す特徴。100 文字程度。文末を「と言われています」「と書かれています」など出典を示す表現にすること。',
    },
    courses: {
      type: 'object',
      additionalProperties: false,
      properties: {
        beginnerRatio: { type: ['number', 'null'] },
        intermediateRatio: { type: ['number', 'null'] },
        advancedRatio: { type: ['number', 'null'] },
        longestRunM: { type: ['integer', 'null'] },
        maxSlopeDeg: { type: ['integer', 'null'] },
        note: { type: ['string', 'null'] },
      },
      required: [
        'beginnerRatio',
        'intermediateRatio',
        'advancedRatio',
        'longestRunM',
        'maxSlopeDeg',
        'note',
      ],
    },
    kidsFacility: {
      type: ['object', 'null'],
      additionalProperties: false,
      properties: {
        name: { type: 'string' },
        ageMin: { type: ['integer', 'null'] },
        ageMax: { type: ['integer', 'null'] },
        price: { type: ['string', 'null'] },
        note: { type: ['string', 'null'] },
      },
      required: ['name', 'ageMin', 'ageMax', 'price', 'note'],
    },
    recommendedRestaurants: {
      type: 'array',
      maxItems: 5,
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          name: { type: 'string' },
          location: { type: ['string', 'null'] },
          popularDish: { type: ['string', 'null'] },
          priceRange: { type: ['string', 'null'] },
          viewPoint: { type: ['string', 'null'] },
          note: { type: ['string', 'null'] },
        },
        required: ['name', 'location', 'popularDish', 'priceRange', 'viewPoint', 'note'],
      },
    },
    nearbyOnsen: {
      type: 'array',
      maxItems: 5,
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          name: { type: 'string' },
          spring: { type: ['string', 'null'] },
          price: { type: ['string', 'null'] },
          distanceKm: { type: ['number', 'null'] },
          note: { type: ['string', 'null'] },
        },
        required: ['name', 'spring', 'price', 'distanceKm', 'note'],
      },
    },
    safetyNotes: {
      type: 'array',
      maxItems: 5,
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          area: { type: 'string' },
          hazard: { type: 'string' },
          advice: { type: 'string' },
        },
        required: ['area', 'hazard', 'advice'],
      },
    },
    localTips: {
      type: 'array',
      maxItems: 5,
      items: { type: 'string' },
    },
  },
  required: [
    'signature',
    'courses',
    'kidsFacility',
    'recommendedRestaurants',
    'nearbyOnsen',
    'safetyNotes',
    'localTips',
  ],
};

const SYSTEM_PROMPT = `あなたはスキー場の公式 Web サイトから情報を抽出する編集者です。以下のルールを厳守してください。

【ルール】
1. 提示された HTML テキストに「明示的に書かれている事実」のみを抽出する。推測や創作は禁止。
2. HTML に該当情報が無い項目は null または空配列にする。
3. 出力する文章は必ず断定を避け、「〜と書かれています」「〜と言われています」「〜だそうです」など出典が明確な表現にすること。
4. 個人情報・連絡先・アカウント情報・課金情報・URL・電話番号は抽出しない。
5. 法令や公的安全情報に反する記述は出力しない。
6. JSON Schema の制約を厳守する。`;

// ---------------------------------------------------------------
// HTML 取得 + クリーニング
// ---------------------------------------------------------------
async function fetchAndClean(url) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (compatible; SkiShopResortBot/0.1; +https://example.com/bot)',
        'Accept-Language': 'ja,en;q=0.8',
      },
    });
    if (!res.ok) {
      console.warn(`[WARN] ${url} -> HTTP ${res.status}`);
      return '';
    }
    const html = await res.text();
    // 軽量クリーニング (cheerio 等を使わずに依存ゼロで処理)
    return html
      .replace(/<script[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style[\s\S]*?<\/style>/gi, ' ')
      .replace(/<svg[\s\S]*?<\/svg>/gi, ' ')
      .replace(/<noscript[\s\S]*?<\/noscript>/gi, ' ')
      .replace(/<!--[\s\S]*?-->/g, ' ')
      .replace(/<[^>]+>/g, ' ')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&')
      .replace(/\s+/g, ' ')
      .trim();
  } catch (err) {
    console.warn(`[WARN] fetch failed: ${url}`, err.message);
    return '';
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------
// LLM 呼び出し
// ---------------------------------------------------------------
async function extractResortMeta(resortKey, combinedText) {
  const userPrompt = `スキー場名: ${resortKey}\n\n以下は ${resortKey} の公式サイト等から取得したテキストです。\nこの中に書かれている事実のみを基に、JSON Schema に従って出力してください。\n\n----- HTML TEXT (先頭 ${MAX_HTML_CHARS} 文字) -----\n${combinedText.slice(0, MAX_HTML_CHARS)}`;

  const response = await client.chat.completions.create({
    model: DEPLOYMENT,
    temperature: 0.1,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: userPrompt },
    ],
    response_format: {
      type: 'json_schema',
      json_schema: {
        name: 'resort_meta',
        strict: true,
        schema: RESORT_META_SCHEMA,
      },
    },
  });

  const content = response.choices?.[0]?.message?.content;
  if (!content) throw new Error('Empty LLM response');
  return JSON.parse(content);
}

// ---------------------------------------------------------------
// メイン
// ---------------------------------------------------------------
function parseArgs() {
  const args = process.argv.slice(2);
  const out = { resort: null };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--resort' && args[i + 1]) {
      out.resort = args[i + 1];
      i++;
    }
  }
  return out;
}

async function main() {
  const { resort: filter } = parseArgs();
  const urlsPath = join(__dirname, 'resort-urls.json');
  const urlsRaw = await readFile(urlsPath, 'utf-8');
  const urlsConfig = JSON.parse(urlsRaw);

  const targets = filter
    ? Object.fromEntries(Object.entries(urlsConfig).filter(([k]) => k === filter))
    : urlsConfig;

  if (Object.keys(targets).length === 0) {
    console.error(`[ERROR] 対象リゾートが見つかりません: ${filter ?? '(全件)'}`);
    process.exit(1);
  }

  const outDir = join(__dirname, 'output');
  await mkdir(outDir, { recursive: true });

  for (const [resortKey, conf] of Object.entries(targets)) {
    console.log(`\n=== ${resortKey} ===`);
    const texts = [];
    for (const url of conf.officialUrls) {
      console.log(`  fetching: ${url}`);
      const text = await fetchAndClean(url);
      if (text) texts.push(`# Source: ${url}\n${text}`);
      await new Promise((r) => setTimeout(r, REQUEST_INTERVAL_MS));
    }
    if (texts.length === 0) {
      console.warn(`  [SKIP] no html collected`);
      continue;
    }

    try {
      console.log(`  extracting via Azure OpenAI (${DEPLOYMENT})...`);
      const meta = await extractResortMeta(resortKey, texts.join('\n\n'));
      meta.lastCuratedAt = new Date().toISOString().slice(0, 10);
      meta._meta = {
        sourceUrls: conf.officialUrls,
        deployment: DEPLOYMENT,
        extractedAt: new Date().toISOString(),
        note: 'LLM 抽出結果。人手レビュー後に取り込むこと。',
      };
      const outPath = join(outDir, `${resortKey}.json`);
      await writeFile(outPath, JSON.stringify(meta, null, 2), 'utf-8');
      console.log(`  -> ${outPath}`);
    } catch (err) {
      console.error(`  [ERROR] LLM extraction failed:`, err.message);
    }
  }

  console.log('\nDone.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
