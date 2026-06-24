import { check } from 'k6';

export const apiBaseUrl = __ENV.API_BASE_URL || __ENV.BASE_URL || 'http://localhost:8080';
export const eventPrefix = __ENV.EVENT_PREFIX || 'phase13';
export const userPrefix = __ENV.USER_PREFIX || 'user-phase13';

export const jsonHeaders = {
  headers: { 'Content-Type': 'application/json' },
};

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomAmount() {
  return randomInt(1000, 300000);
}

export function uniqueEventId(label = 'event') {
  return `${eventPrefix}-${label}-${Date.now()}-${__VU}-${__ITER}-${randomInt(1000, 9999)}`;
}

export function duplicateEventId(label = 'duplicate') {
  return `${eventPrefix}-${label}-fixed-event-id`;
}

function valueOrDefault(value, fallback) {
  return value !== undefined ? value : fallback;
}

export function transactionEvent(overrides = {}) {
  const userId = valueOrDefault(overrides.userId, `${userPrefix}-${__VU}`);
  const eventId = valueOrDefault(overrides.eventId, uniqueEventId('transaction'));
  const amount = valueOrDefault(overrides.amount, randomAmount());

  return {
    eventId,
    userId,
    accountId: valueOrDefault(overrides.accountId, `synthetic-account-${userId}`),
    eventType: valueOrDefault(overrides.eventType, 'PAYMENT'),
    amount,
    currency: valueOrDefault(overrides.currency, 'KRW'),
    merchantId: valueOrDefault(overrides.merchantId, 'synthetic-merchant'),
    deviceId: valueOrDefault(overrides.deviceId, `synthetic-device-${__VU}`),
    location: valueOrDefault(overrides.location, 'KR'),
    eventTime: valueOrDefault(overrides.eventTime, new Date().toISOString()),
  };
}

export function transactionEventPayload(overrides = {}) {
  return JSON.stringify(transactionEvent(overrides));
}

export function highRiskPayload(overrides = {}) {
  return transactionEventPayload({
    amount: 5000000,
    merchantId: 'synthetic-high-risk-merchant',
    deviceId: `synthetic-new-device-${__VU}-${__ITER}`,
    ...overrides,
  });
}

export function duplicatePayload(overrides = {}) {
  return transactionEventPayload({
    eventId: duplicateEventId(),
    userId: `${userPrefix}-duplicate`,
    amount: 1000000,
    ...overrides,
  });
}

export function redisDownPayload(overrides = {}) {
  return transactionEventPayload({
    eventId: uniqueEventId('redis-down'),
    userId: `${userPrefix}-redis-${__VU}`,
    amount: 750000,
    ...overrides,
  });
}

export function checkAccepted(response) {
  return check(response, {
    'request accepted': (res) => [200, 201, 202].includes(res.status),
  });
}

export function checkAcceptedOrDuplicate(response) {
  return check(response, {
    'accepted or duplicate': (res) => [200, 201, 202, 409].includes(res.status),
  });
}
