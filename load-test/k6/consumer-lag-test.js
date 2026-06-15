import http from 'k6/http';
import { check } from 'k6';
import { baseUrl, transactionEventPayload } from './common.js';

export const options = {
  vus: 20,
  duration: '2m',
};

export default function () {
  const response = http.post(`${baseUrl}/api/v1/transactions/events`, transactionEventPayload(`lag-user-${__VU}`), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(response, {
    'accepted or not implemented yet': (res) => [200, 201, 202, 404].includes(res.status),
  });
}
