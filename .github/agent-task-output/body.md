Normalizes Lambda SQS processing ownership around the exact `SQSEvent` observed by each invocation.

- Reuses one processing selection when a handler delegates the same event, while distinct nested events retain their own spans and delivery counts.
- Keeps per-message child processing visible in stable semantic conventions, with batch and child durations owned by their respective operations.
- Renames internal message-oriented handles to event and selected-message terminology.

Selection is local to the participating `ContextKey` and does not bridge independently loaded application and javaagent instrumentation.
