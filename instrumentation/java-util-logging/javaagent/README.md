# Settings for the Java Util Logging instrumentation

| System property                                                         | Type    | Default | Description                                                                      |
| ----------------------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------- |
| `otel.instrumentation.java-util-logging.experimental-log-attributes`    | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`. |
| `otel.instrumentation.java-util-logging.experimental.capture-template`  | Boolean | `false` | Enable the capture of the log message template (if arguments are provided).      |
| `otel.instrumentation.java-util-logging.experimental.capture-arguments` | Boolean | `false` | Enable the capture of the log message arguments.                                 |
