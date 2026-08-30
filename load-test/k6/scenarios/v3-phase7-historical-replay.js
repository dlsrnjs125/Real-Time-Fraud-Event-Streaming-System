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
if (manifest.workloadRole !== 'HISTORICAL_REPLAY') {
  throw new Error(`Phase 7 historical replay workload requires HISTORICAL_REPLAY, got ${manifest.workloadRole}`);
}
if (manifest.driverType !== 'HTTP_K6') {
  throw new Error(`Phase 7 historical replay workload requires HTTP_K6, got ${manifest.driverType}`);
}
if (manifest.eventTimeMode !== 'PRESERVE_SOURCE_TIME') {
  throw new Error(`Phase 7 historical replay workload requires PRESERVE_SOURCE_TIME, got ${manifest.eventTimeMode}`);
}
if (!manifest.replayIsolationProfile) {
  throw new Error('Phase 7 historical replay workload requires replayIsolationProfile');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const profile = manifest.replayIsolationProfile;
const historicalAgeSeconds = durationSeconds(profile.historicalAge);
const preAllocatedVUs = Number(__ENV.V3_PRE_ALLOCATED_VUS || 75);
const maxVUs = Number(__ENV.V3_MAX_VUS || 300);

export const options = {
  scenarios: {
    v3_phase7_historical_replay: {
      executor: 'constant-arrival-rate',
      rate: manifest.replayRate,
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

  const now = Date.now();
  const eventTime = now - historicalAgeSeconds * 1000;
  const userId = `v3-phase7-replay-user-${globalIteration % manifest.userCardinality}`;
  const payload = JSON.stringify({
    eventId: `v3-phase7-replay-${runId}-${globalIteration}`,
    userId,
    accountId: `synthetic-replay-account-${userId}`,
    eventType: 'PAYMENT',
    amount: profile.eventAmount,
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase7-replay-merchant',
    deviceId: `synthetic-v3-phase7-replay-device-${globalIteration % 100}`,
    location: 'KR',
    eventTime: new Date(eventTime).toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, {
    headers: Object.assign({}, jsonHeaders.headers, {
      'X-Source-System': 'v3-phase7-historical-replay',
      'X-Source-Delivery-Profile': manifest.sourceProfile,
    }),
  });
  check(response, {
    'v3 phase7 replay accepted': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const emittedEventCount = metricValue(data, 'http_reqs', 'count') || 0;
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
    targetEps: manifest.targetEps,
    replayRate: manifest.replayRate,
    duration: manifest.duration,
    configuredEventLimit: manifest.eventLimit,
    emittedEventCount,
    expectedReplayAcceptedEvents: profile.expectedReplayAcceptedEvents,
    historicalAge: profile.historicalAge,
    isolationProfile: profile,
    achievedEps: metricValue(data, 'http_reqs', 'rate'),
    httpRequestFailedRate: metricValue(data, 'http_req_failed', 'rate'),
    droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
    checksRate: metricValue(data, 'checks', 'rate'),
    httpRequestDurationP50Ms: metricValue(data, 'http_req_duration', 'p(50)'),
    httpRequestDurationP95Ms: metricValue(data, 'http_req_duration', 'p(95)'),
    httpRequestDurationP99Ms: metricValue(data, 'http_req_duration', 'p(99)'),
    replayRoutingRequirement: 'app-api must publish to replayTopic and app-consumer must consume replayTopic with replayConsumerGroup and replayRedisNamespace',
    isolationEvidenceNote: 'After Live + Replay, compare live/replay consumer group lag by topic and Redis keys under liveRedisNamespace versus replayRedisNamespace. Live Redis collision count must remain zero.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/${manifest.workloadId}-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName] ? data.metrics[metricName].values[valueName] : null;
}

function durationSeconds(value) {
  const match = /^([1-9][0-9]*)(s|m|h)$/.exec(value);
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
