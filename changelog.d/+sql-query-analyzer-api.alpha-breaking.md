Remove `SqlQueryAnalyzer.analyzeWithSummary` from
`io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator` for 3.0.
Use `SqlQueryAnalyzer.analyze`, which now always produces query summaries when sanitization is
enabled. The public `SqlQuery` factory signatures are unchanged.
