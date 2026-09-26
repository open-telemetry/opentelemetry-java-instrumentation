GraphQL 12/20 operation spans emit the v1.44.0 scope schema URL by default and in v3-preview. GraphQL 20 data-fetcher spans remain schema-less.

For example, `query findBookById` remains an `INTERNAL` span with scope schema URL `https://opentelemetry.io/schemas/1.44.0` in both modes. The GraphQL convention recommends `SERVER` kind, but GraphQL execution can nest beneath HTTP server or controller spans; keeping `INTERNAL` avoids nested-server suppression and preserves the existing span behavior.
