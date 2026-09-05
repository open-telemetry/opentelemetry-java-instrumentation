# Settings for the elasticsearch instrumentation

The Elasticsearch REST library artifacts are deprecated and may be removed in
the next minor release. The recommended replacement is the [Elasticsearch Java
API Client's native OpenTelemetry support](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.10/opentelemetry.html),
available in 7.17.20+ on the 7.x line and 8.10+. The javaagent remains supported
and is separate from this library deprecation.

## Settings for the [Elasticsearch Java API Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html) instrumentation

| System property                                                 | Type    | Default | Description                                                                                                                                                                                                                                                                                             |
| --------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.elasticsearch.capture-search-query`       | Boolean | `false` | Deprecated for removal in 3.0 with no replacement. Outside v3-preview, explicitly set this to capture or omit search query bodies. Under v3-preview and in 3.0, the setting is ignored and search query bodies are always captured. Sanitization remains separately configurable with the next setting. |
| `otel.instrumentation.elasticsearch.query-sanitization.enabled` | Boolean | `true`  | Whether captured search query bodies are sanitized by replacing literal values with `?`. When disabled, bodies are captured verbatim and may contain personal or sensitive information.                                                                                                                 |

## Settings for the [Elasticsearch Transport Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html) instrumentation

| System property                                                   | Type    | Default | Description                                         |
| ----------------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.elasticsearch.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
