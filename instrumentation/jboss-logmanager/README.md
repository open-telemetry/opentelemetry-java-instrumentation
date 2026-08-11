# Settings for the JBoss Log Manager instrumentation

| System property                                                              | Type    | Default | Description                                                                                                                                                                                                          |
| ---------------------------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.jboss-logmanager.experimental-log-attributes`          | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                     |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included` | String  |         | Comma-separated list of case-sensitive MDC attribute key patterns to capture. `?` matches one character and `*` matches zero or more characters.                                                                     |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded` | String  |         | Comma-separated list of case-sensitive MDC attribute key patterns to exclude. Excluded patterns take precedence over included patterns. If included is not configured, all non-excluded MDC attributes are captured. |
| `otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes`  | String  |         | Deprecated include-only alias for `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included`. It will be removed in 3.0.                                                                           |

If neither selector property is configured, no MDC attributes are captured. Use `included=*` to capture every MDC attribute. An exclude-only selector captures every MDC attribute except those matching an excluded pattern and may expose sensitive information.

Declarative configuration uses the equivalent selector:

```yaml
java:
  jboss_logmanager:
    mdc_attributes/development:
      included: ["request.*", "user-?"]
      excluded: ["password", "*-token"]
```

The deprecated declarative `capture_mdc_attributes/development` list remains an include-only alias through 2.x. Deprecated aliases are ignored when `otel.instrumentation.common.v3-preview` is enabled.

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is never emitted as an attribute.
