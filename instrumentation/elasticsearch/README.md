# Settings for the elasticsearch instrumentation

## Settings for the [Elasticsearch Java API Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html) instrumentation

| System property                                                 | Type    | Default | Description                                                                                                                                                                                                                             |
| --------------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.elasticsearch.capture-search-query`       | Boolean | `false` | Enable the capture of search query bodies. The captured body is sanitized by default, see `otel.instrumentation.elasticsearch.query-sanitization.enabled`. Defaults to `true` when `otel.instrumentation.common.v3-preview` is enabled. |
| `otel.instrumentation.elasticsearch.query-sanitization.enabled` | Boolean | `true`  | Whether captured search query bodies are sanitized by replacing literal values with `?`. When disabled, bodies are captured verbatim and may contain personal or sensitive information.                                                 |

## Settings for the [Elasticsearch Transport Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html) instrumentation

| System property                                                   | Type    | Default | Description                                         |
| ----------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.elasticsearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
