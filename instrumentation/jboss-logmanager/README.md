# Settings for the JBoss Log Manager instrumentation

| System property                                                              | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                                                                                               |
| ---------------------------------------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.jboss-logmanager.experimental-log-attributes`          | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                                                                                                                                                                                                          |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included` | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys to capture as log attributes. `*` matches any number of characters and `?` matches one character. Excluded patterns take precedence over included patterns. No MDC attributes are captured unless an included or excluded pattern, or the deprecated setting, is configured; configuring only excluded patterns captures all MDC attributes that do not match them.     |
| `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded` | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys not to capture as log attributes. `*` matches any number of characters and `?` matches one character. Excluded patterns take precedence over included patterns. No MDC attributes are captured unless an included or excluded pattern, or the deprecated setting, is configured; configuring only excluded patterns captures all MDC attributes that do not match them. |
| `otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes`  | String  |         | Deprecated: use `otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included` instead. Entries are matched as exact MDC keys rather than as glob patterns, except that a list containing only `*` captures all MDC attributes; in a multi-entry list, `*` and `?` are literal characters. It is ignored when an included or excluded pattern is configured, and it may be removed in the next minor release.               |

Captured MDC attributes may contain sensitive information. Configure included and excluded patterns to limit the data exported as log attributes.

Below is an example of a declarative configuration YAML file that captures MDC attributes:

```yaml
file_format: "1.1"
instrumentation/development:
  java:
    jboss_logmanager:
      mdc_attributes/development:
        included:
          - request.*
          - user-?
        excluded:
          - password
          - "*-token"
```

The deprecated declarative equivalent, `capture_mdc_attributes/development`, keeps the exact-key matching described above and may be removed in the next minor release.

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is not emitted as an attribute.
