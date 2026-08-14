---
applyTo: "**/metadata.yaml"
---

# Metadata Rules (first-pass review)

## [Config] Experimental Span Attribute Descriptions

For `otel.instrumentation.*.experimental-span-attributes`, require the description to name every
attribute key that enabling the property can emit. Accept a precise prefix or wildcard only for a
genuinely dynamic key family. Generic descriptions such as "Enables experimental span attributes"
are insufficient.

Verify keys against the changed module's implementation or tests, account for version-specific key
sets, and do not speculate about keys that cannot be proven.
