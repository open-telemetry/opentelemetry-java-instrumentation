# GraphQL Instrumentation

## Settings for the GraphQL instrumentation

| System property                                                    | Type    | Default | Description                                                                                                                                                                                                             |
| ------------------------------------------------------------------ | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.graphql.query-sanitization.enabled`          | Boolean | `true`  | Whether to remove sensitive information from query source that is added as span attribute.                                                                                                                              |
| `otel.instrumentation.graphql.operation-name-in-span-name.enabled` | Boolean | `false` | Whether GraphQL operation name is added to the span name. <p>**WARNING**: GraphQL operation name is provided by the client and can have high cardinality. Use only when the server is not exposed to malicious clients. |
| `otel.instrumentation.graphql.capture-query`                       | Boolean | `true`  | Whether to capture the query in `graphql.document` span attribute.                                                                                                                                                      |

GraphQL operation spans use the
[v1.44.0 OpenTelemetry schema URL](https://opentelemetry.io/schemas/1.44.0). GraphQL 20
data-fetcher spans have no OpenTelemetry schema URL.

## Settings for the GraphQL 20 instrumentation

| System property                                             | Type    | Default | Description                                                                                                                       |
| ----------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.graphql.data-fetcher.enabled`         | Boolean | `false` | Whether to create spans for data fetchers.                                                                                        |
| `otel.instrumentation.graphql.trivial-data-fetcher.enabled` | Boolean | `false` | Whether to create spans for trivial data fetchers. A trivial data fetcher is one that simply maps data from an object to a field. |
