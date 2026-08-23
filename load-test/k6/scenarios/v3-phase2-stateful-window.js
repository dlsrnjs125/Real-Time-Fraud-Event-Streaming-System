import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { apiBaseUrl, jsonHeaders } from '../common.js';

if (!__ENV.V3_RUN_ID) {
  throw new Error('V3_RUN_ID is required');
}
if (!__ENV.V3_WORKLOAD_MANIFEST) {
  throw new Error('V3_WORKLOAD_MANIFEST is required');
}

const manifestName = __ENV.V3_WORKLOAD_MANIFEST;
const manifest = JSON.parse(open(`../../workloads/v3/${manifestName}`));
if (manifest.workloadRole !== 'STATEFUL_WINDOW_SCALING') {
  throw new Error(`Phase 2 stateful window workload requires STATEFUL_WINDOW_SCALING, got ${manifest.workloadRole}`);
}
if (!manifest.statefulWindowProfile) {
  throw new Error('Phase 2 stateful window workload requires statefulWindowProfile');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const preAllocatedVUs = Number(__ENV.V3_PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.V3_MAX_VUS || 300);
const eventAmount = Number(manifest.statefulWindowProfile.eventAmount);

export const options = {
  scenarios: {
    stateful_window: {
      executor: 'constant-arrival-rate',
      rate: manifest.targetEps,
      timeUnit: '1s',
      duration: manifest.duration,
      gracefulStop: '5s',
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

function deterministicValue(seed, vu, iteration) {
  let value = (seed + Math.imul(vu, 0x9e3779b1) + Math.imul(iteration, 0x85ebca6b)) >>> 0;
  value = Math.imul(value ^ (value >>> 16), 0x7feb352d) >>> 0;
  value = Math.imul(value ^ (value >>> 15), 0x846ca68b) >>> 0;
  return (value ^ (value >>> 16)) >>> 0;
}

export default function () {
  const globalIteration = exec.scenario.iterationInTest;
  if (globalIteration >= manifest.eventLimit) {
    return;
  }

  const value = deterministicValue(manifest.randomSeed, __VU, __ITER);
  const userId = `v3-phase2-state-user-${globalIteration % manifest.userCardinality}`;
  const payload = JSON.stringify({
    eventId: `v3-phase2-${runId}-${globalIteration}-${value}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: eventAmount,
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase2-state-merchant',
    deviceId: `synthetic-v3-phase2-state-device-${value % 100}`,
    location: 'KR',
    eventTime: new Date().toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 phase2 accepted': (res) => res.status === 202,
  });
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName] ? data.metrics[metricName].values[valueName] : null;
}

export function handleSummary(data) {
  const emittedEventCount = metricValue(data, 'http_reqs', 'count') || 0;
  const expectedAverage = emittedEventCount / manifest.userCardinality;
  const expectedMax = Math.ceil(emittedEventCount / manifest.userCardinality);
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    manifestName,
    runId,
    commitSha,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    targetEps: manifest.targetEps,
    duration: manifest.duration,
    configuredEventLimit: manifest.eventLimit,
    emittedEventCount,
    achievedEps: metricValue(data, 'http_reqs', 'rate'),
    httpRequestFailedRate: metricValue(data, 'http_req_failed', 'rate'),
    droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
    checksRate: metricValue(data, 'checks', 'rate'),
    httpRequestDurationP50Ms: metricValue(data, 'http_req_duration', 'p(50)'),
    httpRequestDurationP95Ms: metricValue(data, 'http_req_duration', 'p(95)'),
    httpRequestDurationP99Ms: metricValue(data, 'http_req_duration', 'p(99)'),
    userCardinality: manifest.userCardinality,
    statefulWindowProfile: manifest.statefulWindowProfile,
    achievedEventsPerUserInWindowAverage: expectedAverage,
    achievedMaxEventsPerUserInWindow: expectedMax,
    achievedAmountSumPerUserInWindowAverage: expectedAverage * eventAmount,
    stateEvidenceNote: 'Use Prometheus fraud.redis.window.event.count, fraud.redis.window.amount.sum, fraud.redis.state.latency, Consumer service latency, Redis memory, and Consumer Lag for runtime evidence.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/${manifest.workloadId}-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}
