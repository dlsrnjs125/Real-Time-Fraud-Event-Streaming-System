import http from 'k6/http';
import { apiBaseUrl, checkAccepted, jsonHeaders, redisDownPayload } from '../common.js';

export const options = {
  scenarios: {
    redis_down_load: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  const response = http.post(`${apiBaseUrl}/api/v1/transactions/events`, redisDownPayload(), jsonHeaders);
  checkAccepted(response);
}
