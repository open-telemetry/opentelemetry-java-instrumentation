Remove the deprecated GraphQL configuration properties
`otel.instrumentation.graphql.add-operation-name-to-span-name.enabled` and
`otel.instrumentation.graphql.query-sanitizer.enabled`. Use
`otel.instrumentation.graphql.operation-name-in-span-name.enabled` and
`otel.instrumentation.graphql.query-sanitization.enabled`, respectively.
