# Settings for the Elasticsearch Java agent instrumentation

## Settings for the [Elasticsearch Java API Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html) instrumentation

Search query bodies are always captured. The `otel.instrumentation.elasticsearch.capture-search-query`
property and the equivalent YAML setting are no longer supported and have no replacement. Query
sanitization remains enabled by default. The instrumentation-specific setting below overrides
`otel.instrumentation.common.db.query-sanitization.enabled`; disabling sanitization captures bodies
verbatim.

| System property                                                 | Type    | Default | Description                                                                                                                                                                             |
| --------------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.elasticsearch.query-sanitization.enabled` | Boolean | `true`  | Whether captured search query bodies are sanitized by replacing literal values with `?`. When disabled, bodies are captured verbatim and may contain personal or sensitive information. |

## Settings for the [Elasticsearch Transport Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html) instrumentation

| System property                                                   | Type    | Default | Description                                         |
| ----------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.elasticsearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
