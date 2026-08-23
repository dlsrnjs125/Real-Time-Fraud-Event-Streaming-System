import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { apiBaseUrl, jsonHeaders } from '../common.js';

const manifestName = __ENV.V3_WORKLOAD_MANIFEST || 'stateful-redelivery-v1.json';
const manifest = JSON.parse(open(`../../workloads/v3/${manifestName}`));
if (manifest.workloadRole !== 'STATEFUL_REDELIVERY') {
  throw new Error(`Phase 4 workload requires STATEFUL_REDELIVERY, got ${manifest.workloadRole}`);
}
if (!__ENV.V3_RUN_ID) {
  throw new Error('V3_RUN_ID is required');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const eventAmount = manifest.statefulWindowProfile.eventAmount;
const drillTargetIndex = manifest.statefulWindowProfile.redeliveryDrillTargetIndex;
const drillNextIndex = manifest.statefulWindowProfile.redeliveryDrillNextIndex;

export const options = {
  scenarios: {
    v3_phase4_stateful_redelivery: {
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

export default function () {
  const globalIteration = exec.scenario.iterationInTest;
  if (globalIteration >= manifest.eventLimit) {
    return;
  }

  const userId = `v3-phase4-stateful-redelivery-user-${runId}`;
  const payload = JSON.stringify({
    eventId: `v3-phase4-${runId}-${globalIteration}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: eventAmount,
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase4-merchant',
    deviceId: `synthetic-v3-phase4-device-${globalIteration % 2}`,
    location: 'KR',
    eventTime: new Date().toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 phase4 accepted': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const emittedEventCount = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    runId,
    commitSha,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    targetEps: manifest.targetEps,
    emittedEventCount,
    drillTargetEventIndex: drillTargetIndex,
    drillTargetEventId: `v3-phase4-${runId}-${drillTargetIndex}`,
    drillNextEventIndex: drillNextIndex,
    drillNextEventId: `v3-phase4-${runId}-${drillNextIndex}`,
    achievedEps: data.metrics.http_reqs ? data.metrics.http_reqs.values.rate : null,
    httpRequestFailedRate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null,
    checkSuccessRate: data.metrics.checks ? data.metrics.checks.values.rate : null,
    httpRequestDurationP95Ms: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : null,
    expectedUserCardinality: manifest.userCardinality,
    expectedEventsPerUserInWindow: manifest.statefulWindowProfile.expectedEventsPerUserInWindow,
    expectedAmountSumPerUserInWindow: manifest.statefulWindowProfile.expectedAmountSumPerUserInWindow,
    expectedNextEventTransactionCount: manifest.statefulWindowProfile.expectedNextEventTransactionCount,
    expectedNextEventAmountSum: manifest.statefulWindowProfile.expectedNextEventAmountSum,
    expectedNextEventRiskScore: manifest.statefulWindowProfile.expectedNextEventRiskScore,
    expectedNextEventMatchedRule: manifest.statefulWindowProfile.expectedNextEventMatchedRule,
    redeliveryEvidenceNote: 'Start app-consumer with fraud.consumer.redelivery-drill.enabled=true and event-id equal to drillTargetEventId for each failure-point run.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/v3-phase4-stateful-redelivery-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}
