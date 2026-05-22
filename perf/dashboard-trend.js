// =====================================================================
// perf/dashboard-trend.js
// Task: B6-A.2 — k6 性能基准脚本：项目看板趋势查询
// Covers: R18.5（看板查询 P95 ≤ 2s）, R24.2（性能基准）
//
// 使用：
//   k6 run \
//     -e BASE_URL=http://localhost:8080 \
//     -e USERNAME=admin -e PASSWORD=Admin@123 \
//     -e PROJECT_ID_LIST=1,2,3 \
//     -e TIMESTAMP=$(date +%Y%m%d_%H%M%S) \
//     perf/dashboard-trend.js
//
// 行为：
//   - setup(): 调登录获 token；
//   - default 阶段：50 VU 持续 5 分钟；每次随机选择一个项目，调
//     GET /api/v1/projects/{id}/dashboard?days=30
//     （30 天看板按 R18.5 查询窗口）；
//   - thresholds: http_req_duration p(95) < 2000ms, http_req_failed rate < 1%。
//
// 输出：
//   - perf/output/dashboard-trend-{TIMESTAMP}.html
//   - perf/output/dashboard-trend-summary.json
// =====================================================================

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'Admin@123';
const TIMESTAMP = __ENV.TIMESTAMP || 'latest';
const DAYS = parseInt(__ENV.DAYS || '30', 10);
const PROJECT_ID_LIST = (__ENV.PROJECT_ID_LIST || '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

export const options = {
    scenarios: {
        steady: {
            executor: 'constant-vus',
            vus: 50,
            duration: '5m',
            gracefulStop: '30s',
        },
    },
    thresholds: {
        // R18.5 看板 P95 ≤ 2s
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    if (PROJECT_ID_LIST.length === 0) {
        fail('PROJECT_ID_LIST is empty — please pre-seed projects and pass via env');
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
        projectIds: PROJECT_ID_LIST,
    };
}

export default function (data) {
    const projectId = data.projectIds[Math.floor(Math.random() * data.projectIds.length)];
    const res = http.get(
        `${BASE_URL}/api/v1/projects/${projectId}/dashboard?days=${DAYS}`,
        {
            headers: {
                Authorization: `Bearer ${data.accessToken}`,
                Accept: 'application/json',
            },
            tags: { endpoint: 'dashboard' },
        },
    );
    check(res, {
        'dashboard status 200': (r) => r.status === 200,
        'dashboard has data': (r) => r.json('data') !== undefined,
    });
    sleep(0.5);
}

export function handleSummary(data) {
    const htmlPath = `perf/output/dashboard-trend-${TIMESTAMP}.html`;
    const jsonPath = `perf/output/dashboard-trend-summary.json`;
    return {
        [htmlPath]: htmlReport(data),
        [jsonPath]: JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
