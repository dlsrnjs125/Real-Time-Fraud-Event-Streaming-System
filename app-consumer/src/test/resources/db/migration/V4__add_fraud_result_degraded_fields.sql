alter table fraud_detection_results
    add column skipped_rules text;

alter table fraud_detection_results
    add column degraded boolean not null default false;
