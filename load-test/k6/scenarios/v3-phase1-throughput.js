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
if (!manifest.stages || manifest.stages.length === 0) {
  throw new Error('Phase 1 throughput workload requires stages');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const preAllocatedVUs = Number(__ENV.V3_PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.V3_MAX_VUS || 300);

function parseDurationSeconds(duration) {
  const matched = /^(\d+)s$/.exec(duration);
  if (!matched) {
    throw new Error(`Only second-based stage durations are supported: ${duration}`);
  }
  return Number(matched[1]);
}

function stageScenarios(stages) {
  const scenarios = {};
  let startSeconds = 0;
  let iterationOffset = 0;
  for (const stage of stages) {
    const durationSeconds = parseDurationSeconds(stage.duration);
    scenarios[`stage_${Object.keys(scenarios).length}_${stage.name}`] = {
      executor: 'constant-arrival-rate',
      rate: stage.targetEps,
      timeUnit: '1s',
      duration: stage.duration,
      startTime: `${startSeconds}s`,
      gracefulStop: '5s',
      preAllocatedVUs,
      maxVUs,
      exec: 'phase1Stage',
      env: {
        V3_STAGE_ITERATION_OFFSET: String(iterationOffset),
        V3_STAGE_EVENT_LIMIT: String(stage.targetEps * durationSeconds),
      },
    };
    startSeconds += durationSeconds;
    iterationOffset += stage.targetEps * durationSeconds;
  }
  if (iterationOffset !== manifest.eventLimit) {
    throw new Error(`Stage contract emits ${iterationOffset} events but manifest eventLimit is ${manifest.eventLimit}`);
  }
  return scenarios;
}

export const options = {
  scenarios: stageScenarios(manifest.stages),
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

export function phase1Stage() {
  const stageIteration = exec.scenario.iterationInTest;
  if (stageIteration >= Number(__ENV.V3_STAGE_EVENT_LIMIT || 0)) {
    return;
  }
  const globalIteration = Number(__ENV.V3_STAGE_ITERATION_OFFSET || 0) + stageIteration;
  if (globalIteration >= manifest.eventLimit) {
    return;
  }
  const value = deterministicValue(manifest.randomSeed, __VU, __ITER);
  const userId = `v3-phase1-user-${globalIteration % manifest.userCardinality}`;
  const payload = JSON.stringify({
    eventId: `v3-phase1-${runId}-${globalIteration}-${value}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: 1000 + (value % 499000),
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase1-merchant',
    deviceId: `synthetic-v3-phase1-device-${value % 100}`,
    location: 'KR',
    eventTime: new Date().toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 phase1 accepted': (res) => res.status === 202,
  });
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName] ? data.metrics[metricName].values[valueName] : null;
}

export function handleSummary(data) {
  const emittedEventCount = metricValue(data, 'http_reqs', 'count') || 0;
  const activeUsers = Math.min(manifest.userCardinality, emittedEventCount);
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    manifestName,
    runId,
    commitSha,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    targetEps: manifest.targetEps,
    stages: manifest.stages,
    configuredEventLimit: manifest.eventLimit,
    emittedEventCount,
    achievedEps: metricValue(data, 'http_reqs', 'rate'),
    httpRequestFailedRate: metricValue(data, 'http_req_failed', 'rate'),
    droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
    vusMax: metricValue(data, 'vus_max', 'value'),
    checksRate: metricValue(data, 'checks', 'rate'),
    httpRequestDurationP50Ms: metricValue(data, 'http_req_duration', 'p(50)'),
    httpRequestDurationP95Ms: metricValue(data, 'http_req_duration', 'p(95)'),
    httpRequestDurationP99Ms: metricValue(data, 'http_req_duration', 'p(99)'),
    achievedUserCardinality: activeUsers,
    achievedPartitionDistribution: null,
    distributionEvidenceNote: 'Use kafka-exporter partition metrics for achieved partition distribution.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/${manifest.workloadId}-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}
