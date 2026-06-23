# Final Readiness Checklist

이 체크리스트는 Phase 1~11 기준 운영 준비도를 빠르게 확인하기 위한 문서입니다. 체크된 항목은 현재 구현 또는 문서 evidence가 있는 항목이고, `Follow-up`은 후속 운영 고도화 후보입니다.

## 1. Functional Readiness

- [x] Transaction event API works
- [x] Kafka producer publishes transaction event
- [x] Consumer manual ack works
- [x] Processing log is stored
- [x] Rule Engine creates fraud result
- [x] Redis Sliding Window rule is applied
- [x] Redis degraded mode works
- [x] DLT is stored on failure
- [x] DLT reprocess/discard API works
- [ ] Batch DLT reprocess works - Follow-up

## 2. Consistency Readiness

- [x] `fraud_detection_results.event_id` unique constraint exists
- [x] `event_processing_logs(topic, partition_no, offset_no)` unique constraint exists
- [x] `dead_letter_events(source_topic, source_partition, source_offset)` unique constraint exists
- [x] Duplicate event replay does not create duplicate fraud result
- [x] DLT duplicate source offset does not create duplicate DLT row
- [ ] Reprocessing history table exists - Follow-up

## 3. Failure Readiness

- [x] Redis down drill works
- [x] Consumer restart drill documented
- [x] Kafka unavailable runbook exists
- [x] DLT publish failure behavior documented
- [x] DB failure no-ack policy documented
- [ ] Kafka/PostgreSQL/Redis full E2E failure drill is automated - Follow-up

## 4. Observability Readiness

- [x] Redis degraded metric exists
- [x] Skipped rule metric exists
- [x] Detection degraded metric exists
- [x] Redis latency timer exists
- [x] High-cardinality tag policy documented
- [x] traceId/eventId logging policy documented
- [ ] DLT pending/reprocess failed/discard metric exists - Follow-up
- [ ] Grafana dashboard evidence is captured - Follow-up
- [ ] Kafka consumer lag metric is dashboarded - Follow-up

## 5. Security Readiness

- [x] Admin API security limitation documented
- [x] DLT payload masking limitation documented
- [x] Redis key/value privacy policy documented
- [x] Metric tag privacy policy documented
- [x] Failure drill synthetic data policy documented
- [ ] Admin authentication/authorization is implemented - Follow-up
- [ ] Operator audit log is implemented - Follow-up

## 6. Documentation Readiness

- [x] README is concise
- [x] Architecture docs exist
- [x] Data model docs exist
- [x] API docs exist
- [x] Runbook exists
- [x] Failure scenario docs exist
- [x] Blog drafts exist
- [x] Evidence index exists
- [x] Troubleshooting index exists
