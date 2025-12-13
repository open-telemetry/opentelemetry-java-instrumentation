# Settings for the OpenSearch instrumentation

| System property                                                | Type    | Default | Description                                         |
| -------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |

## Settings for the [OpenSearch Java Client](https://docs.opensearch.org/latest/clients/java/) instrumentation

| System property                                                   | Type    | Default | Description                                          |
| ----------------------------------------------------------------- | ------- | ------- |------------------------------------------------------|
| `otel.instrumentation.opensearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.  |
| `otel.instrumentation.opensearch.capture-search-query` | Boolean | `false` | Enable the capture of sanitized search query bodies. |
