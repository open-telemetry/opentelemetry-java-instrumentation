# Settings for the OpenSearch instrumentation

| System property                                                | Type    | Default | Description                                         |
| -------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |

## Settings for the [OpenSearch Java Client](https://docs.opensearch.org/latest/clients/java/) instrumentation

| System property                                                | Type    | Default | Description                                                                                                                                                                                             |
| -------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                                                                                     |
| `otel.instrumentation.opensearch.query-sanitization.enabled`   | Boolean | `true`  | Whether captured search query bodies are sanitized by replacing literal values with `?`. When disabled, bodies are captured verbatim and may contain personal or sensitive information. |
| `otel.instrumentation.common.db.query-sanitization.enabled`    | Boolean | `true`  | Whether the query should be sanitized for all database instrumentations. Individual instrumentations may define a property with higher precedence.                                                      |
