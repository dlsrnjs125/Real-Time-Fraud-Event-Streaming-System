import http from 'k6/http';
import { check } from 'k6';
import { baseUrl, transactionEventPayload } from './common.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
};

export default function () {
  const response = http.post(`${baseUrl}/api/v1/transactions/events`, transactionEventPayload(`user-${__VU}`), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(response, {
    'accepted or not implemented yet': (res) => [200, 201, 202, 404].includes(res.status),
  });
}
