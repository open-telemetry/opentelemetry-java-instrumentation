Remove deprecated `setCapturedRequestHeaders` and `setCapturedResponseHeaders` methods from
Ktor 1.0 configuration, and the `capturedRequestHeaders` and `capturedResponseHeaders`
overloads from Ktor 2.0/3.0 builders. Use `requestHeaders` and `responseHeaders` with
`IncludeExclude` selectors instead. Selector patterns interpret `*` and `?` as wildcards
rather than literal header-name characters.
