import http from 'k6/http';
import { apiBaseUrl, checkAccepted, jsonHeaders, transactionEventPayload } from '../common.js';

export const options = {
  scenarios: {
    normal_load: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, transactionEventPayload(), jsonHeaders);
  checkAccepted(response);
}
