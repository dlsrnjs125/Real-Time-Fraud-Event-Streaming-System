import http from 'k6/http';
import { apiBaseUrl, checkAccepted, highRiskPayload, jsonHeaders } from '../common.js';

export const options = {
  scenarios: {
    peak_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: 50, duration: '1m' },
        { target: 100, duration: '1m' },
        { target: 20, duration: '1m' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, highRiskPayload(), jsonHeaders);
  checkAccepted(response);
}
