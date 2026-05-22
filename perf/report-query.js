// =====================================================================
// perf/report-query.js
// Task: B6-A.1 — k6 性能基准脚本：报告查询
// Covers: R16.6（报告查询 P95 ≤ 2s）, R24.2（性能基准）
//
// 使用：
//   k6 run \
//     -e BASE_URL=http://localhost:8080 \
//     -e USERNAME=admin -e PASSWORD=Admin@123 \
//     -e TASK_ID_LIST=101,102,103,...,200 \
//     -e TIMESTAMP=$(date +%Y%m%d_%H%M%S) \
//     perf/report-query.js
//
// 行为：
//   - setup(): 调 POST /api/v1/auth/login 取 accessToken；
//     从 TASK_ID_LIST 解析任务 id 列表（前置须由 seeder 准备 100 个任务，
//     每任务 200 条 issue，详见 docs/it6-verification-report.md）；
//   - default 阶段：100 VU 持续 5 分钟 GET /api/v1/review-tasks/{id}/report；
//   - thresholds: http_req_duration p(95) < 2000ms, http_req_failed rate < 1%。
//
// 输出：
//   - perf/output/report-query-{TIMESTAMP}.html （k6 内置 htmlReport）
//   - perf/output/report-query-summary.json    （便于 CI 解析）
// =====================================================================

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'Admin@123';
const TIMESTAMP = __ENV.TIMESTAMP || 'latest';
const TASK_ID_LIST = (__ENV.TASK_ID_LIST || '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

export const options = {
    // R24.2 / 设计 §15.5：100 并发 5 分钟稳态压测
    scenarios: {
        steady: {
            executor: 'constant-vus',
            vus: 100,
            duration: '5m',
            gracefulStop: '30s',
        },
    },
    thresholds: {
        // R16.6 报告查询 P95 ≤ 2s
        http_req_duration: ['p(95)<2000'],
        // 错误率 < 1%
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    if (TASK_ID_LIST.length === 0) {
        fail('TASK_ID_LIST is empty — please pre-seed tasks and pass via env');
    }
    const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ username: USERNAME, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
    );
    check(loginRes, {
        'login status 200': (r) => r.status === 200,
        'login has accessToken': (r) => r.json('data.accessToken') !== undefined,
    }) || fail(`login failed: status=${loginRes.status} body=${loginRes.body}`);

    return {
        accessToken: loginRes.json('data.accessToken'),
        taskIds: TASK_ID_LIST,
    };
}

export default function (data) {
    const taskId = data.taskIds[Math.floor(Math.random() * data.taskIds.length)];
    const res = http.get(`${BASE_URL}/api/v1/review-tasks/${taskId}/report`, {
        headers: {
            Authorization: `Bearer ${data.accessToken}`,
            Accept: 'application/json',
        },
        tags: { endpoint: 'report' },
    });
    check(res, {
        'report status 200': (r) => r.status === 200,
        'report has data': (r) => r.json('data') !== undefined,
    });
    // 适度间隔，模拟真实页面 polling 行为
    sleep(0.2);
}

export function handleSummary(data) {
    const htmlPath = `perf/output/report-query-${TIMESTAMP}.html`;
    const jsonPath = `perf/output/report-query-summary.json`;
    return {
        [htmlPath]: htmlReport(data),
        [jsonPath]: JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
