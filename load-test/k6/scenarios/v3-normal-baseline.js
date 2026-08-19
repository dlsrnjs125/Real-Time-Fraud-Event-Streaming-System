import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { apiBaseUrl, jsonHeaders } from '../common.js';

const manifest = JSON.parse(open('../../workloads/v3/normal-baseline-v1.json'));
const runId = __ENV.V3_RUN_ID || `${Date.now()}`;

export const options = {
  scenarios: {
    v3_phase0_normal_baseline: {
      executor: 'constant-arrival-rate',
      rate: manifest.targetEps,
      timeUnit: '1s',
      duration: manifest.duration,
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
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
  const userId = `v3-baseline-user-${globalIteration % manifest.userCardinality}`;
  const payload = JSON.stringify({
    eventId: `v3-phase0-${runId}-${globalIteration}-${value}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: 1000 + (value % 299000),
    currency: 'KRW',
    merchantId: 'synthetic-v3-baseline-merchant',
    deviceId: `synthetic-v3-device-${value % 20}`,
    location: 'KR',
    eventTime: new Date().toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 baseline accepted': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const emittedEventCount = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const activeUsers = Math.min(manifest.userCardinality, emittedEventCount);
  const topUserCount = activeUsers > 0 ? Math.max(1, Math.ceil(activeUsers * 0.01)) : 0;
  const baseEventsPerUser = activeUsers > 0 ? Math.floor(emittedEventCount / activeUsers) : 0;
  const usersWithExtraEvent = activeUsers > 0 ? emittedEventCount % activeUsers : 0;
  const topUserEvents = topUserCount * baseEventsPerUser + Math.min(topUserCount, usersWithExtraEvent);
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    runId,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    targetEps: manifest.targetEps,
    emittedEventCount,
    achievedEps: data.metrics.http_reqs ? data.metrics.http_reqs.values.rate : null,
    httpRequestFailedRate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null,
    checkSuccessRate: data.metrics.checks ? data.metrics.checks.values.rate : null,
    httpRequestDurationP95Ms: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : null,
    achievedUserCardinality: activeUsers,
    achievedUserConcentration: emittedEventCount > 0 ? topUserEvents / emittedEventCount : null,
    achievedPartitionDistribution: null,
    distributionEvidenceNote: 'Query Prometheus/Kafka exporter after the run; the HTTP driver does not infer Kafka partition placement.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    'load-test/k6/results/v3-phase0-normal-baseline-summary.json': `${JSON.stringify(report, null, 2)}\n`,
  };
}
