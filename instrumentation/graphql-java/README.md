# Settings for the GraphQL instrumentation

| System property                                        | Type    | Default | Description                                                                                |
| ------------------------------------------------------ | ------- | ------- | ------------------------------------------------------------------------------------------ |
| `otel.instrumentation.graphql.query-sanitizer.enabled` | Boolean | `true`  | Whether to remove sensitive information from query source that is added as span attribute. |
