# Settings for the OpenSearch instrumentation

| System property                                                | Type    | Default | Description                                         |
| -------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |

## Settings for the [OpenSearch Java Client](https://docs.opensearch.org/latest/clients/java/) instrumentation

| System property                                                | Type    | Default | Description                                          |
| -------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------- |
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.  |
| `otel.instrumentation.opensearch.capture-search-query`         | Boolean | `true`  | Deprecated and will be removed in 3.0, when sanitized search query bodies are always captured. There is no replacement. Until then, set this to false to disable capture. Search queries may contain personal or sensitive information. |
