# Settings for the JBoss Log Manager instrumentation

| System property                                                             | Type    | Default | Description                                                                                                  |
|-----------------------------------------------------------------------------|---------|---------|--------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.jboss-logmanager.experimental-log-attributes`         | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                             |
| `otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes` | String  |         | Comma separated list of MDC attributes to capture. Use the wildcard character `*` to capture all attributes. |
