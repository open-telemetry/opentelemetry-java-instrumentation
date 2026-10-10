Remove deprecated `setCapturedRequestHeaders` and `setCapturedResponseHeaders` methods from
HTTP library telemetry builders. Use `setRequestHeaders` and `setResponseHeaders`
with `IncludeExclude` selectors instead. Selector patterns interpret `*` and `?` as wildcards
rather than literal header-name characters.
