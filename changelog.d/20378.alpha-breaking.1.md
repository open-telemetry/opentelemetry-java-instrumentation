Rename Ktor 1.0 configuration methods to match Ktor 2.0/3.0:
`setRequestHeaders` to `requestHeaders`, `setResponseHeaders` to `responseHeaders`,
`setKnownMethods` to `knownMethods`, `addAttributesExtractor` to `attributesExtractor`,
`setSpanNameExtractorCustomizer` to `spanNameExtractor`, `setStatusExtractor` to
`spanStatusExtractor`, and `setSpanKindExtractor` to `spanKindExtractor`.
Parameter types and behavior are unchanged.
