Remove `MessageOperation` and its overloads in the messaging attribute, span-name and span-kind
extractors from `opentelemetry-instrumentation-api-incubator`.
`MessagingAttributesGetter` no longer requires or exposes `getMessageBodySize()` or
`getMessageEnvelopeSize()`.
