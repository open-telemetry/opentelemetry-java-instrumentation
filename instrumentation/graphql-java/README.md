# Settings for the GraphQL instrumentation

| System property                                        | Type    | Default | Description                                                                                |
|--------------------------------------------------------|---------|---------|--------------------------------------------------------------------------------------------|
| `otel.instrumentation.graphql.query-sanitizer.enabled` | Boolean | `true`  | Whether to remove sensitive information from query source that is added as span attribute. |

# Settings for the GraphQL 20 instrumentation

| System property                                             | Type    | Default | Description                                                                                                                       |
|-------------------------------------------------------------|---------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.graphql.data-fetcher.enabled`         | Boolean | `false` | Whether to create spans for data fetchers.                                                                                        |
| `otel.instrumentation.graphql.trivial-data-fetcher.enabled` | Boolean | `false` | Whether to create spans for trivial data fetchers. A trivial data fetcher is one that simply maps data from an object to a field. |
