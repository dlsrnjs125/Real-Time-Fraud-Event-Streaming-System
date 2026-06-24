import http from 'k6/http';
import { apiBaseUrl, checkAcceptedOrDuplicate, duplicateEventId, duplicatePayload, jsonHeaders } from '../common.js';

export const options = {
  scenarios: {
    duplicate_replay: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export function setup() {
  return {
    eventId: duplicateEventId(),
  };
}

export default function (data) {
  const response = http.post(
    `${apiBaseUrl}/api/v1/transactions/events`,
    duplicatePayload({ eventId: data.eventId }),
    jsonHeaders,
  );
  checkAcceptedOrDuplicate(response);
}
