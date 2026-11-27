# Settings for the Logback Appender instrumentation

| System property                                                                         | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| --------------------------------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.common.logging.structured-attributes.included`                    | String  |         | Comma-separated list of case-sensitive glob patterns for event-local structured log attribute keys to capture. Applies to Log4j `MapMessage` entries, SLF4J key-value pairs, Logback Logstash markers, and Logstash structured arguments. `*` matches any number of characters and `?` matches one character. All structured attributes are captured unless included or excluded patterns are configured. MDC and logger-context attributes remain separate and opt-in.                                                               |
| `otel.instrumentation.common.logging.structured-attributes.excluded`                    | String  |         | Comma-separated list of case-sensitive glob patterns for event-local structured log attribute keys not to capture. Applies to Log4j `MapMessage` entries, SLF4J key-value pairs, Logback Logstash markers, and Logstash structured arguments. Excluded patterns take precedence over included patterns. If only excluded patterns are configured, all other structured attributes are captured. `*` captures no structured attributes. This does not affect `otel.event.name`, MDC, or logger context.                                |
| `otel.instrumentation.logback-appender.experimental-log-attributes`                     | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `otel.instrumentation.logback-appender.experimental.capture-code-attributes`            | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                                                                                                                                                                                                                                                                                                                                         |
| `otel.instrumentation.logback-appender.experimental.capture-marker-attribute`           | Boolean | `false` | Enable the capture of Logback markers as attributes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes`  | Boolean | `false` | **Deprecated.** Enable the capture of all Logback logger context properties as attributes. It may be removed in the next minor release; use `otel.instrumentation.logback-appender.experimental.logger-context-attributes.included` instead. It is ignored when `otel.instrumentation.logback-appender.experimental.logger-context-attributes.included` or `otel.instrumentation.logback-appender.experimental.logger-context-attributes.excluded` is configured.                                                                     |
| `otel.instrumentation.logback-appender.experimental.capture-template`                   | Boolean | `false` | Enable the capture of Logback log event message template (if arguments are provided).                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `otel.instrumentation.logback-appender.experimental.capture-arguments`                  | Boolean | `false` | Enable the capture of Logback log event arguments.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `otel.instrumentation.logback-appender.experimental.logger-context-attributes.included` | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys to capture as log attributes. `*` matches any number of characters and `?` matches one character. No logger context attributes are captured unless included or excluded patterns, or the deprecated `otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes` setting, are configured. `*` captures all logger context attributes.                                                                         |
| `otel.instrumentation.logback-appender.experimental.logger-context-attributes.excluded` | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys not to capture as log attributes. `*` matches any number of characters and `?` matches one character. Excluded patterns take precedence over included patterns. If only excluded patterns are configured, all other logger context attributes are captured, which may expose sensitive information.                                                                                                                                     |
| `otel.instrumentation.logback-appender.experimental.mdc-attributes.included`            | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys to capture as log attributes. `*` matches any number of characters and `?` matches one character. No MDC attributes are captured unless included or excluded patterns, or the deprecated `otel.instrumentation.logback-appender.experimental.capture-mdc-attributes` setting, are configured. `*` captures all MDC attributes.                                                                                                                                      |
| `otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded`            | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys not to capture as log attributes. `*` matches any number of characters and `?` matches one character. Excluded patterns take precedence over included patterns. If only excluded patterns are configured, all other MDC attributes are captured, which may expose sensitive information.                                                                                                                                                                            |
| `otel.instrumentation.logback-appender.experimental.capture-mdc-attributes`             | String  |         | **Deprecated.** Comma-separated list of MDC keys to capture as log attributes. Keys are matched literally, including `*` and `?`, except that the single value `*` captures all MDC attributes. It may be removed in the next minor release; use `otel.instrumentation.logback-appender.experimental.mdc-attributes.included` instead. It is ignored when `otel.instrumentation.logback-appender.experimental.mdc-attributes.included` or `otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded` is configured. |

Captured MDC, structured, logger context, Logstash marker, and Logstash structured argument
attributes may contain sensitive information. Configure included and excluded patterns to limit
the data exported as log attributes.

MDC capture selects only the application's original event MDC. Trace IDs, span IDs, trace flags,
resource attributes, and baggage added by the Java agent's Logback MDC instrumentation are available
to application appenders and log patterns, but are not exported as MDC log attributes. Application-owned
values under the same names, such as `trace_id`, `service.name`, or `baggage.foo`, remain selectable.

The `otel.event.name` key is supported in key-value pairs (SLF4J 2.x fluent API), MDC entries, Logstash markers (e.g., `Markers.append("otel.event.name", ...)`), and Logstash structured arguments (e.g., `StructuredArguments.keyValue("otel.event.name", ...)`). When present, its value is used as the log event name and is not emitted as an attribute.

## Declarative configuration

The common structured attribute selector and the source-specific MDC and logger context attribute
selectors can also be configured with [declarative configuration]:

```yaml
file_format: "1.1"
instrumentation/development:
  java:
    common:
      logging:
        structured_attributes:
          included:
            - request-*
            - user-?
          excluded:
            - "*-secret"
    logback_appender:
      mdc_attributes/development:
        included:
          - request-*
          - user-?
        excluded:
          - "*-secret"
      logger_context_attributes/development:
        included:
          - app.*
        excluded:
          - "*-secret"
```

[declarative configuration]: https://opentelemetry.io/docs/languages/java/configuration/#declarative-configuration

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
