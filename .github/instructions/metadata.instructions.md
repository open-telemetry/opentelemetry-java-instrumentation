---
applyTo: "**/metadata.yaml"
---

# Metadata Rules (first-pass review)

## [Config] Experimental Telemetry Descriptions

For `otel.instrumentation.*.experimental-span-attributes`, require the description to name every
attribute key that enabling the property can emit. Accept a precise prefix or wildcard only for a
genuinely dynamic key family. Generic descriptions such as "Enables experimental span attributes"
are insufficient.

For broader enablement properties such as `otel.instrumentation.*.emit-experimental-telemetry` and
`otel.instrumentation.*.experimental.*-telemetry.enabled`, require the description to identify the
enabled telemetry. Name fixed attribute keys, metric names, and event names, and describe span
creation, naming, or linking behavior. Do not demand attribute keys when the property enables only
broader span behavior; accept a precise prefix or wildcard for genuinely dynamic telemetry.

Verify telemetry against the changed module's implementation or tests, account for version-specific
behavior, and do not speculate about telemetry that cannot be proven.
