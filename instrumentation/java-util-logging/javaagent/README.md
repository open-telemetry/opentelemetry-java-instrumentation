# Settings for the Java Util Logging instrumentation

| System property                                                         | Type    | Default | Description                                                                      |
| ----------------------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------- |
| `otel.instrumentation.java-util-logging.capture-thread-attributes`      | Boolean | `false` | Enable the capture of `thread.name` and `thread.id` log attributes. |
| `otel.instrumentation.java-util-logging.experimental.capture-template`  | Boolean | `false` | Enable the capture of the log message template (if arguments are provided).      |
| `otel.instrumentation.java-util-logging.experimental.capture-arguments` | Boolean | `false` | Enable the capture of the log message arguments.                                 |
