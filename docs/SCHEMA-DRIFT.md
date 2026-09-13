# Schema drift policy

Schema drift is any difference between what a source actually sends and the schema the hub was
built against (`SchemaContracts` in `hub/.../transform/SchemaContract.kt`). The hub never guesses:
it detects, records, and either quarantines or loads-with-a-warning according to the table below.

## Detection

Every raw payload is compared with the feed's contract *before* mapping (`SchemaDriftDetector`):

| Kind | What it means | Example from the simulator |
| --- | --- | --- |
| `FIELD_MISSING` | A required field is absent | `DROP_TIER` |
| `FIELD_RETYPED` | Present, but the JSON type differs | `RETYPE_POSTAL_CODE` (`"00-001"` → `1`) |
| `FIELD_RENAMED` | A required field is missing and a similarly named unknown field appeared next to it | `RENAME_EMAIL` (`email` → `emailAddress`) |
| `VALUE_OUT_OF_DOMAIN` | A closed-domain field carries an unknown value | `ORDER_STATUS_ON_HOLD` |
| `FIELD_ADDED` | An undeclared field appeared | `ADD_LOYALTY_POINTS` |

## Policy

| Severity | Kinds | Record | Event |
| --- | --- | --- | --- |
| **BLOCKING** | missing, retyped, renamed, value out of domain | **Quarantined** with the raw payload and the findings. System B is not touched. | Drift event raised (or an open one updated) |
| **WARNING** | added | **Loaded**: additive changes cannot corrupt System B; the mapper ignores unknown fields | Drift event raised so an operator knows the source grew |

Why block instead of "best effort": a renamed or retyped field means the mapping is now a guess.
Writing a guessed value into System B corrupts it silently and is far more expensive to undo than a
quarantine. The watermark still advances past quarantined records, because they are not lost: the
payload is stored verbatim and can be replayed.

## One event per finding, not per record

Drift events are keyed by a fingerprint (`kind:field:expected->actual`) and stay open until resolved.
A run that hits the same rename on 400 records produces one event with `affectedRecords = 400`, not
400 alerts. `sourceChangedAt` keeps the earliest source `updatedAt` among affected records.

## Metrics

- **MTTD** (mean time to detect) = `detectedAt - sourceChangedAt`: how long drifted data existed in
  the source before the hub caught it. Bounded by the sync interval.
- **MTTR** (mean time to resolve) = `resolvedAt - detectedAt`.

## Resolution

1. Fix the cause (the source rolls back, or the hub's contract and mapper are updated in a PR).
2. Replay quarantined records (`POST /api/quarantine/{id}/replay`) or run a backfill over the window.
3. Resolve the event (`POST /api/drift/{id}/resolve`). A clean backfill whose window covers the
   affected source changes resolves those open events automatically; a successful load of a quarantined key at or after the quarantined version
   marks that quarantine `SUPERSEDED`.
