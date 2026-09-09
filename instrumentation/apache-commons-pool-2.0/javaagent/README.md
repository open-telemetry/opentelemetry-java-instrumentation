# Auto-instrumentation for Apache Commons Pool version 2.0 and higher

Provides OpenTelemetry auto-instrumentation
for [Apache Commons Pool](https://commons.apache.org/proper/commons-pool/).

This instrumentation is disabled by default because its metrics do not currently follow
OpenTelemetry semantic conventions.

The `apache_commons_pool.pool.name` attribute uses the configured JMX name prefix. An `unknown`
fallback is used when the prefix is null or empty. Keyed pool names use the `keyed-` prefix.
Applications with multiple pools should configure unique JMX name prefixes.

## Settings

| System property                                    | Type    | Default | Description                                      |
| -------------------------------------------------- | ------- | ------- | ------------------------------------------------ |
| `otel.instrumentation.apache-commons-pool.enabled` | Boolean | `false` | Enables the Apache Commons Pool instrumentation. |
