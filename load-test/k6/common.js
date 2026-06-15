export const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export function transactionEventPayload(userId = 'user-1001') {
  return JSON.stringify({
    userId,
    accountId: `acc-${userId}`,
    amount: 1500000,
    merchantId: 'merchant-777',
    deviceId: 'device-abc',
    location: 'KR',
    eventTime: new Date().toISOString(),
  });
}
