# Change events: delivery semantics

Every applied change in System B (created, updated, deleted) is published as a change event on Kafka,
topic `synchub.changes.<feed>`, keyed by the business key.

## Guarantee: at-least-once, in per-key order

The hub uses a **transactional outbox**:

1. The System B write and the outbox row are committed in **one database transaction**
   (`ChangeEventRecorder`, `Propagation.MANDATORY`). There is no state where the destination changed
   but no event exists, or an event exists for a change that rolled back.
2. `OutboxPublisher` sends pending rows oldest-first and marks each as published **only after Kafka
   acknowledged it** (`acks=all`, idempotent producer, synchronous `get()`).
3. If the hub dies between the send and the mark, the row is sent again on the next pass.

Hence: never lost, possibly duplicated, ordered per key (single sender, creation order, same key →
same partition).

## What consumers must do

Consumers are expected to be **idempotent**. Two easy strategies, both supported by the payload:

- Deduplicate on `eventId` (a UUID) in a small processed-events table.
- Or apply "only if newer": `version` is System B's per-row counter, so an event whose `version` is
  not greater than what the consumer already holds is a duplicate or stale and can be dropped.

## Payload

```json
{
  "eventId": "6f1c…",
  "feed": "customers",
  "type": "UPDATED",
  "businessKey": "cus_00007",
  "version": 3,
  "occurredAt": "2026-03-01T10:00:00Z",
  "sourceUpdatedAt": "2026-03-01T09:59:58Z",
  "runId": "d0a4…",
  "before": { "...System B row before..." },
  "after":  { "...System B row after..." }
}
```

`before` is null for creates, `after` is null for deletes. Both are System B records (the
destination's shape), so consumers never see System A field names.

## What is *not* an event

Skipped loads (`SKIPPED_UNCHANGED` on replays/backfills/overlap re-reads, `SKIPPED_STALE` on
late-arriving records) produce no event: nothing in System B changed.

## Local development

With `SYNCHUB_EVENTS_TRANSPORT=log` (the `dev` profile default) the same outbox pipeline runs, but
events are written to the application log instead of Kafka, so the H2 path needs no broker.
