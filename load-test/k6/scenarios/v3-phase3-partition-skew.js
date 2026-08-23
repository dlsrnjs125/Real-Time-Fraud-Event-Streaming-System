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
if (manifest.workloadRole !== 'PARTITION_SKEW') {
  throw new Error(`Phase 3 partition workload requires PARTITION_SKEW, got ${manifest.workloadRole}`);
}
if (manifest.userDistribution !== 'PARTITION_AFFINITY') {
  throw new Error('Phase 3 partition workload requires PARTITION_AFFINITY');
}
if (manifest.partitionAffinityStrategy !== 'KAFKA_MURMUR2_LOCAL_6_PARTITIONS') {
  throw new Error(`Unsupported partitionAffinityStrategy: ${manifest.partitionAffinityStrategy}`);
}
if (!manifest.targetPartitionDistribution) {
  throw new Error('Phase 3 partition workload requires targetPartitionDistribution');
}

const runId = __ENV.V3_RUN_ID;
const commitSha = __ENV.V3_COMMIT_SHA || 'unknown';
const preAllocatedVUs = Number(__ENV.V3_PRE_ALLOCATED_VUS || 300);
const maxVUs = Number(__ENV.V3_MAX_VUS || 800);
const targetDistribution = normalizeDistribution(manifest.targetPartitionDistribution);
const partitionCount = targetDistribution.length;
const cycleLength = Number(__ENV.V3_PARTITION_CYCLE_LENGTH || 600);
const partitionCycle = buildPartitionCycle(targetDistribution, cycleLength, manifest.randomSeed);
const userPools = buildUserPools(targetDistribution, manifest.userCardinality);
const eventPlan = buildEventPlan(manifest.eventLimit);

export const options = {
  scenarios: {
    partition_affinity: {
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

function normalizeDistribution(rawDistribution) {
  const entries = Object.entries(rawDistribution)
    .map(([partition, share]) => [Number(partition), Number(share)])
    .sort((left, right) => left[0] - right[0]);
  if (entries.length === 0) {
    throw new Error('targetPartitionDistribution must not be empty');
  }
  entries.forEach(([partition], index) => {
    if (partition !== index) {
      throw new Error(`targetPartitionDistribution must use contiguous partitions from 0; got ${partition} at index ${index}`);
    }
  });
  return entries.map(([partition, share]) => ({ partition, share }));
}

function buildShareCounts(distribution, total) {
  const counts = distribution.map(({ share }) => Math.floor(share * total));
  let remaining = total - counts.reduce((sum, count) => sum + count, 0);
  const remainders = distribution
    .map(({ share }, index) => ({ index, remainder: (share * total) - Math.floor(share * total) }))
    .sort((left, right) => right.remainder - left.remainder || left.index - right.index);
  for (let i = 0; remaining > 0; i += 1) {
    counts[remainders[i % remainders.length].index] += 1;
    remaining -= 1;
  }
  return counts;
}

function buildPartitionCycle(distribution, total, seed) {
  const counts = buildShareCounts(distribution, total);
  const sequence = [];
  for (let index = 0; index < distribution.length; index += 1) {
    for (let i = 0; i < counts[index]; i += 1) {
      sequence.push(distribution[index].partition);
    }
  }
  for (let i = sequence.length - 1; i > 0; i -= 1) {
    const j = deterministicValue(seed, i, counts[i % counts.length]) % (i + 1);
    const tmp = sequence[i];
    sequence[i] = sequence[j];
    sequence[j] = tmp;
  }
  return sequence;
}

function buildUserPools(distribution, userCardinality) {
  const userCounts = buildShareCounts(distribution, userCardinality);
  const pools = {};
  for (let index = 0; index < distribution.length; index += 1) {
    const partition = distribution[index].partition;
    pools[partition] = findUsersForPartition(partition, userCounts[index]);
  }
  return pools;
}

function findUsersForPartition(targetPartition, count) {
  const users = [];
  let candidate = 0;
  while (users.length < count) {
    const userId = `v3-phase3-${manifest.workloadId}-p${targetPartition}-user-${candidate}`;
    if (partitionForKey(userId, partitionCount) === targetPartition) {
      users.push(userId);
    }
    candidate += 1;
    if (candidate > count * partitionCount * 30 + 1000) {
      throw new Error(`Could not build enough users for partition ${targetPartition}`);
    }
  }
  return users;
}

function partitionForKey(key, partitions) {
  return toPositive(murmur2Ascii(key)) % partitions;
}

function toPositive(value) {
  return value & 0x7fffffff;
}

function murmur2Ascii(value) {
  const data = [];
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code > 127) {
      throw new Error('partition affinity keys must be ASCII');
    }
    data.push(code);
  }

  const seed = 0x9747b28c;
  const m = 0x5bd1e995;
  const r = 24;
  let h = (seed ^ data.length) >>> 0;
  let length = data.length;
  let index = 0;

  while (length >= 4) {
    let k = (data[index] & 0xff)
      | ((data[index + 1] & 0xff) << 8)
      | ((data[index + 2] & 0xff) << 16)
      | ((data[index + 3] & 0xff) << 24);
    k = Math.imul(k, m) >>> 0;
    k ^= k >>> r;
    k = Math.imul(k, m) >>> 0;

    h = Math.imul(h, m) >>> 0;
    h ^= k;

    index += 4;
    length -= 4;
  }

  switch (length) {
    case 3:
      h ^= (data[index + 2] & 0xff) << 16;
    // falls through
    case 2:
      h ^= (data[index + 1] & 0xff) << 8;
    // falls through
    case 1:
      h ^= data[index] & 0xff;
      h = Math.imul(h, m) >>> 0;
      break;
    default:
      break;
  }

  h ^= h >>> 13;
  h = Math.imul(h, m) >>> 0;
  h ^= h >>> 15;
  return h >>> 0;
}

function deterministicValue(seed, vu, iteration) {
  let value = (seed + Math.imul(vu, 0x9e3779b1) + Math.imul(iteration, 0x85ebca6b)) >>> 0;
  value = Math.imul(value ^ (value >>> 16), 0x7feb352d) >>> 0;
  value = Math.imul(value ^ (value >>> 15), 0x846ca68b) >>> 0;
  return (value ^ (value >>> 16)) >>> 0;
}

function buildEventPlan(eventLimit) {
  const partitionOccurrences = {};
  for (const { partition } of targetDistribution) {
    partitionOccurrences[partition] = 0;
  }

  const plan = [];
  for (let iteration = 0; iteration < eventLimit; iteration += 1) {
    const partition = partitionCycle[iteration % partitionCycle.length];
    const pool = userPools[partition];
    const userOccurrence = partitionOccurrences[partition];
    const userId = pool[userOccurrence % pool.length];
    partitionOccurrences[partition] += 1;
    plan.push({ partition, userId });
  }
  return plan;
}

function selectedEvent(iteration) {
  return eventPlan[iteration];
}

export default function () {
  const globalIteration = exec.scenario.iterationInTest;
  if (globalIteration >= manifest.eventLimit) {
    return;
  }

  const selected = selectedEvent(globalIteration);
  const partition = selected.partition;
  const userId = selected.userId;
  const value = deterministicValue(manifest.randomSeed, __VU, __ITER);
  const payload = JSON.stringify({
    eventId: `v3-phase3-${runId}-${globalIteration}-${value}`,
    userId,
    accountId: `synthetic-account-${userId}`,
    eventType: 'PAYMENT',
    amount: 1000 + (value % 499000),
    currency: 'KRW',
    merchantId: 'synthetic-v3-phase3-partition-merchant',
    deviceId: `synthetic-v3-phase3-partition-device-${value % 100}`,
    location: 'KR',
    eventTime: new Date().toISOString(),
  });

  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, payload, jsonHeaders);
  check(response, {
    'v3 phase3 accepted': (res) => res.status === 202,
  });
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName] ? data.metrics[metricName].values[valueName] : null;
}

function distributionForEvents(eventCount) {
  const counts = {};
  for (const { partition } of targetDistribution) {
    counts[partition] = 0;
  }
  for (let i = 0; i < eventCount; i += 1) {
    counts[selectedEvent(i).partition] += 1;
  }
  const shares = {};
  for (const partition of Object.keys(counts)) {
    shares[partition] = eventCount > 0 ? counts[partition] / eventCount : 0;
  }
  return { counts, shares };
}

function userDistributionForEvents(eventCount) {
  const userCounts = {};
  const partitionUsers = {};
  for (const { partition } of targetDistribution) {
    partitionUsers[partition] = {};
  }

  for (let i = 0; i < eventCount; i += 1) {
    const selected = selectedEvent(i);
    userCounts[selected.userId] = (userCounts[selected.userId] || 0) + 1;
    partitionUsers[selected.partition][selected.userId] = true;
  }

  const eventCountsPerUser = Object.values(userCounts).sort((left, right) => left - right);
  const perPartitionUniqueUsers = {};
  for (const partition of Object.keys(partitionUsers)) {
    perPartitionUniqueUsers[partition] = Object.keys(partitionUsers[partition]).length;
  }

  const maxEventsPerUser = eventCountsPerUser.length > 0 ? eventCountsPerUser[eventCountsPerUser.length - 1] : 0;
  return {
    generatedUniqueUsers: Object.keys(userCounts).length,
    generatedEventsPerUser: {
      p50: nearestRank(eventCountsPerUser, 0.50),
      p95: nearestRank(eventCountsPerUser, 0.95),
      p99: nearestRank(eventCountsPerUser, 0.99),
      max: maxEventsPerUser,
    },
    generatedTopUserShare: eventCount > 0 ? maxEventsPerUser / eventCount : 0,
    generatedPerPartitionUniqueUsers: perPartitionUniqueUsers,
  };
}

function nearestRank(sortedValues, quantile) {
  if (sortedValues.length === 0) {
    return 0;
  }
  const rank = Math.ceil(quantile * sortedValues.length);
  return sortedValues[Math.max(0, rank - 1)];
}

export function handleSummary(data) {
  const emittedEventCount = metricValue(data, 'http_reqs', 'count') || 0;
  const expectedDistribution = distributionForEvents(emittedEventCount);
  const expectedUserDistribution = userDistributionForEvents(emittedEventCount);
  const userPoolSizes = {};
  for (const partition of Object.keys(userPools)) {
    userPoolSizes[partition] = userPools[partition].length;
  }
  const report = {
    workloadId: manifest.workloadId,
    workloadVersion: manifest.workloadVersion,
    manifestName,
    runId,
    commitSha,
    driverType: manifest.driverType,
    eventTimeMode: manifest.eventTimeMode,
    partitionAffinityStrategy: manifest.partitionAffinityStrategy,
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
    configuredTargetPartitionDistribution: manifest.targetPartitionDistribution,
    generatedExpectedPartitionCounts: expectedDistribution.counts,
    generatedExpectedPartitionShares: expectedDistribution.shares,
    generatedUserPoolSizes: userPoolSizes,
    generatedUniqueUsers: expectedUserDistribution.generatedUniqueUsers,
    generatedEventsPerUser: expectedUserDistribution.generatedEventsPerUser,
    generatedTopUserShare: expectedUserDistribution.generatedTopUserShare,
    generatedPerPartitionUniqueUsers: expectedUserDistribution.generatedPerPartitionUniqueUsers,
    partitionEvidenceNote: 'The k6 generator preselects userIds whose Kafka murmur2 key hash maps to target partitions. Confirm achieved partition distribution with Kafka exporter or processing logs after the run.',
  };
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`load-test/k6/results/${manifest.workloadId}-${runId}-summary.json`]: `${JSON.stringify(report, null, 2)}\n`,
  };
}
