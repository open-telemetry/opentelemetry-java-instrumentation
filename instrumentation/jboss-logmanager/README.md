# Settings for the JBoss Log Manager instrumentation

| System property                                                              | Type    | Default | Description                                                                                                                                                                                                                           |
| ---------------------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.jboss-logmanager.experimental-log-attributes`          | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                      |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included` | String  |         | Comma-separated list of case-sensitive MDC attribute key patterns to capture. `?` matches one character and `*` matches zero or more characters.                                                                                      |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded` | String  |         | Comma-separated list of case-sensitive MDC attribute key patterns to exclude. Excluded patterns take precedence over included patterns. If included is not configured, all non-excluded MDC attributes are captured.                  |
| `otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes`  | String  |         | Deprecated: use `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included` instead. A comma-separated list of exact MDC attribute keys to capture, or `*` to capture all MDC attributes. It may be removed in the next minor release. |

If neither selector property is configured, no MDC attributes are captured. Use `included=*` to capture every MDC attribute. An exclude-only selector captures every MDC attribute except those matching an excluded pattern and may expose sensitive information.

Declarative configuration uses the equivalent selector:

```yaml
java:
  jboss_logmanager:
    mdc_attributes/development:
      included: ["request.*", "user-?"]
      excluded: ["password", "*-token"]
```

The deprecated declarative `capture_mdc_attributes/development` list may be removed in the next minor release. It matches exact MDC attribute keys rather than patterns, except that a lone `*` captures all MDC attributes.

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is never emitted as an attribute.
