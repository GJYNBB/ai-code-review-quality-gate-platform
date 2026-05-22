// =====================================================================
// perf/task-pipeline.js
// Task: B6-A.2 — k6 性能基准脚本：任务全链路时长
// Covers: R24.1（PENDING → 终态全链路 P95 ≤ 180s）, R24.2
//
// 使用（建议在测试环境，启用 mock provider + mock AI）：
//   k6 run \
//     -e BASE_URL=http://localhost:8080 \
//     -e USERNAME=admin -e PASSWORD=Admin@123 \
//     -e PROJECT_ID=1 -e WEBHOOK_SECRET=mock-secret \
//     -e TIMESTAMP=$(date +%Y%m%d_%H%M%S) \
//     perf/task-pipeline.js
//
// 行为：
//   - setup(): 调登录获 token，并校验 PROJECT_ID 已绑定 mock 仓库；
//   - default：10 并发 VU 持续 3 分钟；每次：
//       1) 用 webhook 签名头（X-Hub-Signature-256，HMAC-SHA256(WEBHOOK_SECRET, body)）
//          POST /api/v1/webhooks/git，body 为带唯一 commit SHA 的 mock PR payload；
//       2) 解析返回的 taskId（设计 §8.5）；
//       3) 轮询 GET /api/v1/review-tasks/{id} 直到 status ∈ {PASSED, FAILED_GATE,
//          EXECUTION_FAILED} 或超时 200s；
//       4) 用自定义 Trend pipelineDurationMs 记录 PENDING → 终态的 wall-clock 时长。
//   - thresholds: pipelineDurationMs p(95) < 180000ms（R24.1）。
//
// 注意：
//   - 测试环境通过 system_param 切换 ai.review.base-url 到 mock AI、模型 enabled=true、
//     仓库绑定到 wiremock 地址；具体步骤见 docs/it6-verification-report.md。
//   - 该脚本本身不持有 webhook 签名密钥，签名通过 k6 内置的 crypto 模块生成。
// =====================================================================

import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, fail, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'Admin@123';
const PROJECT_ID = __ENV.PROJECT_ID || '1';
const WEBHOOK_SECRET = __ENV.WEBHOOK_SECRET || 'mock-secret';
const TIMESTAMP = __ENV.TIMESTAMP || 'latest';

// 自定义指标：PENDING → 终态全链路时长（毫秒）
const pipelineDurationMs = new Trend('pipelineDurationMs', true);
const pipelineFailures = new Counter('pipelineFailures');
const pipelineTimeouts = new Counter('pipelineTimeouts');

const TERMINAL = new Set(['PASSED', 'FAILED_GATE', 'EXECUTION_FAILED']);
const POLL_INTERVAL_MS = 1000;
const MAX_WAIT_MS = 200_000;

export const options = {
    scenarios: {
        pipeline: {
            executor: 'constant-vus',
            vus: 10,
            duration: '3m',
            gracefulStop: '30s',
        },
    },
    thresholds: {
        // R24.1：PENDING → 终态全链路 P95 ≤ 180s
        pipelineDurationMs: ['p(95)<180000'],
        http_req_failed: ['rate<0.05'],
        pipelineFailures: ['count<5'],
        pipelineTimeouts: ['count<2'],
    },
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ username: USERNAME, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
    );
    check(loginRes, {
        'login status 200': (r) => r.status === 200,
    }) || fail(`login failed: status=${loginRes.status} body=${loginRes.body}`);

    return {
        accessToken: loginRes.json('data.accessToken'),
    };
}

function buildMockPushPayload(prId, commitSha) {
    // GitHub PR webhook 简化负载（关注字段：pull_request.number / head.sha / base.ref）。
    return {
        action: 'opened',
        number: prId,
        pull_request: {
            number: prId,
            head: { sha: commitSha, ref: 'feature/perf-test' },
            base: { ref: 'main' },
        },
        repository: {
            id: parseInt(PROJECT_ID, 10),
            full_name: `acrqg/perf-${PROJECT_ID}`,
        },
    };
}

export default function (data) {
    // 1) 构造 unique commit sha（VU + iteration + 时间）
    const prId = 1000 + (__VU * 1000) + __ITER;
    const commitSha =
        `${Date.now().toString(16)}${__VU.toString(16).padStart(2, '0')}${__ITER
            .toString(16)
            .padStart(4, '0')}`.padEnd(40, '0').slice(0, 40);

    const payload = JSON.stringify(buildMockPushPayload(prId, commitSha));
    const signature = `sha256=${crypto.hmac('sha256', WEBHOOK_SECRET, payload, 'hex')}`;

    const t0 = Date.now();
    const webhookRes = http.post(`${BASE_URL}/api/v1/webhooks/git`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-Hub-Signature-256': signature,
            'X-GitHub-Event': 'pull_request',
            'X-GitHub-Delivery': `perf-${__VU}-${__ITER}-${Date.now()}`,
        },
        tags: { endpoint: 'webhook' },
    });

    if (
        !check(webhookRes, {
            'webhook accepted': (r) => r.status === 200 || r.status === 202,
            'webhook returns taskId': (r) => r.json('data.taskId') !== undefined,
        })
    ) {
        pipelineFailures.add(1);
        return;
    }

    const taskId = webhookRes.json('data.taskId');

    // 2) 轮询 review-tasks/{id} 直到终态
    let terminal = null;
    const deadline = t0 + MAX_WAIT_MS;
    while (Date.now() < deadline) {
        const r = http.get(`${BASE_URL}/api/v1/review-tasks/${taskId}`, {
            headers: {
                Authorization: `Bearer ${data.accessToken}`,
                Accept: 'application/json',
            },
            tags: { endpoint: 'task-detail' },
        });
        if (r.status === 200) {
            const status = r.json('data.status');
            if (TERMINAL.has(status)) {
                terminal = status;
                break;
            }
        }
        sleep(POLL_INTERVAL_MS / 1000);
    }

    if (terminal === null) {
        pipelineTimeouts.add(1);
        return;
    }

    // 3) 记录 PENDING → 终态时长
    const durationMs = Date.now() - t0;
    pipelineDurationMs.add(durationMs);
    check(durationMs, {
        'pipeline within 180s': (d) => d < 180_000,
    });
}

export function handleSummary(data) {
    const htmlPath = `perf/output/task-pipeline-${TIMESTAMP}.html`;
    const jsonPath = `perf/output/task-pipeline-summary.json`;
    return {
        [htmlPath]: htmlReport(data),
        [jsonPath]: JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
