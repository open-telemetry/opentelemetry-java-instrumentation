# Settings for the Log4j Appender instrumentation

| System property                                                            | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| -------------------------------------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.log4j-appender.experimental-log-attributes`          | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `otel.instrumentation.log4j-appender.experimental.capture-code-attributes` | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                                                                                                                                                                                                                                                                                                                                                               |
| `otel.instrumentation.log4j-appender.experimental.mdc-attributes.included` | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys to capture as log attributes. `*` matches any number of characters and `?` matches one character, so `*` captures all MDC attributes. Excluded patterns take precedence over included patterns. No MDC attributes are captured unless an included pattern, an excluded pattern, or the deprecated `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes` setting is configured; configuring only excluded patterns captures all MDC attributes that do not match them. |
| `otel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded` | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys not to capture as log attributes. `*` matches any number of characters and `?` matches one character. Excluded patterns take precedence over included patterns. No MDC attributes are captured unless an included pattern, an excluded pattern, or the deprecated `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes` setting is configured; configuring only excluded patterns captures all MDC attributes that do not match them.                                 |
| `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes`  | String  |         | Deprecated include-only alias for `otel.instrumentation.log4j-appender.experimental.mdc-attributes.included`. It does not support glob patterns: a list containing only `*` captures all MDC attributes, and otherwise every entry, including one containing `*` or `?`, is matched as a literal MDC key. It is ignored when an included or excluded pattern is configured, and it may be removed in the next minor release.                                                                                                                                |

Captured MDC attributes may contain sensitive information. Configure included and excluded patterns to limit the data exported as log attributes.

Declarative configuration example:

```yaml
file_format: "1.1"
instrumentation/development:
  java:
    log4j_appender:
      mdc_attributes/development:
        included:
          - request-*
          - user-?
        excluded:
          - "*-secret"
```

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is not emitted as an attribute.

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
