import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { apiBaseUrl, jsonHeaders } from '../common.js';

const manifestName = __ENV.V3_WORKLOAD_MANIFEST || 'late-out-of-order-v1.json';
const manifest = JSON.parse(open(`../../workloads/v3/${manifestName}`));
if (manifest.workloadRole !== 'LATE_OUT_OF_ORDER') {
  throw new Error(`Phase 5 workload requires LATE_OUT_OF_ORDER, got ${manifest.workloadRole}`);
}
if (!__ENV.V3_RUN_ID) {
  throw new Error('V3_RUN_ID is required');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const latenessProfile = manifest.latenessProfile;
const buckets = latenessProfile.buckets.map((bucket) => ({
  name: bucket.name,
  latenessSeconds: durationSeconds(bucket.lateness),
}));
const bucketByName = Object.fromEntries(buckets.map((bucket) => [bucket.name, bucket]));
const runtimePattern = buildRuntimePattern();
const allowedLatenessSeconds = durationSeconds(latenessProfile.allowedLateness);
const eventAmount = latenessProfile.eventAmount;

export const options = {
  scenarios: {
    v3_phase5_late_out_of_order: {
      executor: 'constant-arrival-rate',
      rate: manifest.targetEps,
      timeUnit: '1s',
      duration: manifest.duration,
      preAllocatedVUs: 20,
      maxVUs: 80,
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

  const userIndex = globalIteration % manifest.userCardinality;
  const userEventIndex = Math.floor(globalIteration / manifest.userCardinality);
  const bucket = selectBucket(userIndex, userEventIndex);
  const arrivalTime = Date.now();
  const eventTime = new Date(arrivalTime - bucket.latenessSeconds * 1000);
  const userId = `v3-phase5-late-user-${userIndex}`;
  const payload = JSON.stringify({
    eventId: `v3-phase5-${runId}-${globalIteration}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: eventAmount,
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase5-late-merchant',
    deviceId: `synthetic-v3-phase5-late-device-${globalIteration % 10}`,
    location: 'KR',
    eventTime: eventTime.toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 phase5 accepted': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const emittedEventCount = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const bucketCounts = expectedBucketCounts(emittedEventCount);
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    runId,
    commitSha,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    sourceProfile: manifest.sourceProfile,
    targetEps: manifest.targetEps,
    emittedEventCount,
    allowedLateness: latenessProfile.allowedLateness,
    tooLateAge: latenessProfile.tooLateAge,
    runtimePattern: runtimePattern.map((bucket) => bucket.name),
    outOfOrderPatternApplied: latenessProfile.outOfOrderPattern || null,
    bucketCounts,
    expectedTooLateEvents: expectedTooLateCount(emittedEventCount),
    expectedAcceptedLateEvents: emittedEventCount - expectedTooLateCount(emittedEventCount),
    manifestExpectedTooLateEvents: latenessProfile.expectedTooLateEvents,
    manifestExpectedAcceptedLateEvents: latenessProfile.expectedAcceptedLateEvents,
    achievedEps: data.metrics.http_reqs ? data.metrics.http_reqs.values.rate : null,
    httpRequestFailedRate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null,
    checkSuccessRate: data.metrics.checks ? data.metrics.checks.values.rate : null,
    httpRequestDurationP95Ms: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : null,
    note: 'Runtime evidence should compare accepted-late Redis state mutation, too-late Redis skip count, final DB consistency, and final Consumer Lag.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/v3-phase5-late-out-of-order-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}

function expectedBucketCounts(eventCount) {
  const counts = {};
  for (const bucket of buckets) {
    counts[bucket.name] = 0;
  }
  for (let index = 0; index < eventCount; index += 1) {
    const userIndex = index % manifest.userCardinality;
    const userEventIndex = Math.floor(index / manifest.userCardinality);
    const bucket = selectBucket(userIndex, userEventIndex);
    counts[bucket.name] += 1;
  }
  return counts;
}

function expectedTooLateCount(eventCount) {
  let count = 0;
  for (let index = 0; index < eventCount; index += 1) {
    const userIndex = index % manifest.userCardinality;
    const userEventIndex = Math.floor(index / manifest.userCardinality);
    const bucket = selectBucket(userIndex, userEventIndex);
    if (bucket.latenessSeconds > allowedLatenessSeconds) {
      count += 1;
    }
  }
  return count;
}

function selectBucket(userIndex, userEventIndex) {
  return runtimePattern[(userEventIndex + userIndex) % runtimePattern.length];
}

function buildRuntimePattern() {
  const patternNames = latenessProfile.outOfOrderPattern || [];
  const pattern = [];
  for (const name of patternNames) {
    if (!bucketByName[name]) {
      throw new Error(`outOfOrderPattern references unknown bucket: ${name}`);
    }
    pattern.push(bucketByName[name]);
  }
  for (const bucket of buckets) {
    if (!patternNames.includes(bucket.name)) {
      pattern.push(bucket);
    }
  }
  if (pattern.length !== buckets.length) {
    throw new Error('runtime pattern must contain each lateness bucket exactly once');
  }
  return pattern;
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
