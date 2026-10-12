Remove legacy database APIs from `io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator`.
Replace `DbClientAttributesGetter.getDbSystem`, `getDbName`, and `getDbOperation` with
`getDbSystemName`, `getDbNamespace`, and `getDbOperationName`. Return canonical system names
such as `oracle.db` and `h2database` from `getDbSystemName`; extractors no longer translate
legacy system names. Remove overrides of `getUser` and `getConnectionString`, which have no
stable database attribute replacements. SQL getters use
`getRawQueryTexts` instead of `getRawQueryTextsForOldSemconv`; use
`DbClientSpanNameExtractor.create` instead of `createWithGenericOldSpanName`.
Remove `SqlClientAttributesExtractorBuilder.setTableAttribute`; enable
`setSingleOperationAndCollection(true)` to derive `db.collection.name` for systems that support
only one collection and operation per non-batch query.
