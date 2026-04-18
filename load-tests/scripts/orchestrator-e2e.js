// =============================================================================
// Multi-Agent Orchestrator E2E Test (Phase 8)
// =============================================================================
// このスクリプトは Multi-Agent パイプラインの End-to-End 動作を検証する。
// 実行: k6 run --vus 1 --iterations 3 load-tests/scripts/orchestrator-e2e.js
//
// ⚠ 注意:
//   - Azure OpenAI のレート制限 (TPM/RPM) に配慮し、直列実行（vus=1）を強く推奨
//   - GPT のツール呼び出し順は確率的なため、最終結果のフィールド存在のみを strict 検証
// =============================================================================

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8090';
// 既定の自然言語シナリオ: 初心者 + 5 万円以内 + 装備一式
const USER_MESSAGE =
  __ENV.USER_MESSAGE ||
  '週末に苗場でスキーをしたいので、初心者向けのスキー板とウェアを 5 万円以内で揃えたい';

export const options = {
  vus: 1,
  iterations: parseInt(__ENV.ITERATIONS || '1', 10),
  thresholds: {
    // Orchestrator は LLM 呼び出しを含むため通常 5～15 秒
    http_req_duration: ['p(95)<30000'],
    checks: ['rate>0.95'],
  },
};

function registerAndLogin() {
  const uid = randomString(8);
  const email = `agent-e2e-${uid}@example.com`;
  const password = `Test@${uid}123`;

  const registerRes = http.post(
    `${GATEWAY_URL}/api/v1/auth/register`,
    JSON.stringify({
      email,
      password,
      firstName: 'Agent',
      lastName: 'E2E',
      role: 'CUSTOMER',
    }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'register' } },
  );
  check(registerRes, { 'register 200/201': (r) => r.status === 200 || r.status === 201 });

  const loginRes = http.post(
    `${GATEWAY_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  check(loginRes, { 'login 200': (r) => r.status === 200 });

  const body = loginRes.json();
  return {
    accessToken: body.accessToken || body.token,
    userId: body.userId || body.id || email,
  };
}

export default function () {
  let auth;

  group('01_auth', () => {
    auth = registerAndLogin();
  });

  if (!auth || !auth.accessToken) {
    console.error('認証失敗のためスキップ');
    return;
  }

  group('02_orchestrator_recommend', () => {
    const payload = JSON.stringify({
      userId: auth.userId,
      message: USER_MESSAGE,
      sessionId: `e2e-${randomString(6)}`,
      usePoints: true,
    });

    const res = http.post(`${GATEWAY_URL}/api/v1/orchestrator/recommend`, payload, {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${auth.accessToken}`,
      },
      timeout: '60s',
      tags: { name: 'orchestrator_recommend' },
    });

    const ok = check(res, {
      'orchestrator status 200': (r) => r.status === 200,
      'response has userId': (r) => {
        try {
          return r.json('userId') !== undefined;
        } catch {
          return false;
        }
      },
      'response has orchestrationSummary': (r) => {
        try {
          return typeof r.json('orchestrationSummary') === 'string';
        } catch {
          return false;
        }
      },
      'response has generatedAt timestamp': (r) => {
        try {
          return typeof r.json('generatedAt') === 'string';
        } catch {
          return false;
        }
      },
    });

    if (!ok) {
      console.error(`Orchestrator E2E 失敗: status=${res.status}, body=${res.body?.substring(0, 500)}`);
    } else {
      try {
        const body = res.json();
        console.log(`✅ Orchestrator OK: intent=${body.intentSummary?.substring(0, 60)}`);
      } catch (e) {
        // ignore
      }
    }
  });

  group('03_orchestrator_blocks_worker_paths', () => {
    // /api/v1/agents/** は外部公開禁止 → ゲートウェイで 404 になることを確認
    const res = http.get(`${GATEWAY_URL}/api/v1/agents/weather/current?location=Naeba`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
      tags: { name: 'agents_blocked' },
    });
    check(res, {
      'worker path returns 404 from gateway': (r) => r.status === 404,
    });
  });

  // LLM 呼び出しのレート制限考慮
  sleep(2);
}
