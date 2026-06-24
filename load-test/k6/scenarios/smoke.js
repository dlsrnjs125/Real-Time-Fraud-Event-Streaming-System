import http from 'k6/http';
import { apiBaseUrl, checkAccepted, jsonHeaders, transactionEventPayload, uniqueEventId } from '../common.js';

export const options = {
  scenarios: {
    smoke: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const response = http.post(
    `${apiBaseUrl}/api/v1/transactions/events`,
    transactionEventPayload({ eventId: uniqueEventId('smoke') }),
    jsonHeaders,
  );

  checkAccepted(response);
}
