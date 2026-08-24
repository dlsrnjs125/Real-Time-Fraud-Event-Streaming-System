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
if (!['ORGANIC_BURST', 'CATCH_UP_BURST'].includes(manifest.workloadRole)) {
  throw new Error(`Phase 6 source-delay workload requires ORGANIC_BURST or CATCH_UP_BURST, got ${manifest.workloadRole}`);
}
if (manifest.driverType !== 'HTTP_SOURCE_EMULATOR') {
  throw new Error(`Phase 6 source-delay workload requires HTTP_SOURCE_EMULATOR, got ${manifest.driverType}`);
}
if (!manifest.sourceDelayProfile) {
  throw new Error('Phase 6 source-delay workload requires sourceDelayProfile');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const sourceDelayProfile = manifest.sourceDelayProfile;
const sourceDelaySeconds = durationSeconds(sourceDelayProfile.sourceDelay);
const eventAmount = sourceDelayProfile.eventAmount;
const preAllocatedVUs = Number(__ENV.V3_PRE_ALLOCATED_VUS || 100);
const maxVUs = Number(__ENV.V3_MAX_VUS || 500);

export const options = {
  scenarios: {
    v3_phase6_source_delay: {
      executor: 'constant-arrival-rate',
      rate: manifest.targetEps,
      timeUnit: '1s',
      duration: manifest.duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const globalIteration = exec.scenario.iterationInTest;
  if (globalIteration >= manifest.eventLimit) {
    return;
  }

  const sourceSentAt = Date.now();
  const eventTime = eventTimeFor(sourceSentAt);
  const userId = `v3-phase6-user-${globalIteration % manifest.userCardinality}`;
  const payload = JSON.stringify({
    eventId: `v3-phase6-${runId}-${globalIteration}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: eventAmount,
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase6-merchant',
    deviceId: `synthetic-v3-phase6-device-${globalIteration % 100}`,
    location: 'KR',
    eventTime: new Date(eventTime).toISOString(),
  });

  const headers = {
    headers: Object.assign({}, jsonHeaders.headers, {
      'X-Source-System': 'v3-phase6-source-emulator',
      'X-Source-Sent-At': new Date(sourceSentAt).toISOString(),
      'X-Source-Delivery-Profile': manifest.sourceProfile,
    }),
  };

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, headers);
  check(response, {
    'v3 phase6 accepted': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const emittedEventCount = metricValue(data, 'http_reqs', 'count') || 0;
  const expectedTooLateEvents = expectedTooLateCount(emittedEventCount);
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    manifestName,
    runId,
    commitSha,
    driverType: manifest.driverType,
    workloadRole: manifest.workloadRole,
    sourceProfile: manifest.sourceProfile,
    eventTimeMode: manifest.eventTimeMode,
    sourceTimeResolution: manifest.sourceTimeResolution,
    timeScaleFactor: manifest.timeScaleFactor,
    targetEps: manifest.targetEps,
    duration: manifest.duration,
    configuredEventLimit: manifest.eventLimit,
    emittedEventCount,
    sourceDelayProfile,
    expectedSourceDelaySeconds: sourceDelaySeconds,
    expectedAcceptedEvents: emittedEventCount - expectedTooLateEvents,
    expectedTooLateEvents,
    achievedEps: metricValue(data, 'http_reqs', 'rate'),
    httpRequestFailedRate: metricValue(data, 'http_req_failed', 'rate'),
    droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
    checksRate: metricValue(data, 'checks', 'rate'),
    httpRequestDurationP50Ms: metricValue(data, 'http_req_duration', 'p(50)'),
    httpRequestDurationP95Ms: metricValue(data, 'http_req_duration', 'p(95)'),
    httpRequestDurationP99Ms: metricValue(data, 'http_req_duration', 'p(99)'),
    sourceSentAtOwner: 'k6 HTTP source emulator before POST dispatch',
    sourceSentAtPropagation: 'HTTP headers only; not persisted in app-api receipts or Kafka payloads',
    attributionEvidenceNote: 'Use this summary for source-emulator configuration only. Runtime attribution should compare persisted eventTime/receivedAt, fraud.event.ingress.age, API/Kafka/Redis/Consumer metrics, and final Consumer Lag.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/${manifest.workloadId}-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}

function eventTimeFor(sourceSentAtMillis) {
  if (manifest.eventTimeMode === 'REBASE_TO_ARRIVAL') {
    return sourceSentAtMillis;
  }
  if (manifest.eventTimeMode === 'CONTROLLED_LATENESS') {
    return sourceSentAtMillis - sourceDelaySeconds * 1000;
  }
  throw new Error(`Unsupported Phase 6 eventTimeMode: ${manifest.eventTimeMode}`);
}

function expectedTooLateCount(eventCount) {
  const allowedLatenessSeconds = durationSeconds(sourceDelayProfile.allowedLateness);
  if (sourceDelaySeconds > allowedLatenessSeconds) {
    return eventCount;
  }
  return 0;
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName] ? data.metrics[metricName].values[valueName] : null;
}

function durationSeconds(value) {
  const match = /^([0-9]+)(s|m|h)$/.exec(value);
  if (!match) {
    throw new Error(`Unsupported duration: ${value}`);
  }
  const amount = Number(match[1]);
  if (match[2] === 's') {
    return amount;
  }
  if (match[2] === 'm') {
    return amount * 60;
  }
  return amount * 60 * 60;
}
