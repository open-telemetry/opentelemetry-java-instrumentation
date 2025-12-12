# CI Failure Analysis Plan

## Failed Jobs Summary
- Job 1: common / test1 (multiple Java versions) (job IDs: 57490440498, 57490440505, 57490440450, 57490440447, 57490440473, etc.)
- Job 2: common / test2 (multiple Java versions) (job IDs: 57490440520, 57490440521, 57490440540, 57490440566, etc.)
- Job 3: test-latest-deps / testLatestDeps1 (job ID: 57490440183)
- Job 4: test-latest-deps / testLatestDeps2 (job ID: 57490440203)

Note: All jobs with parameters in parentheses (e.g., Java version, VM, indy settings) are variations of the same base jobs.

## Unique Failed Gradle Tasks
**Note**: Spotless tasks are excluded from this analysis as they are formatting-only checks.

- [x] Task: `:instrumentation:jdbc:javaagent:testStableSemconv`
  - Seen in: test1 (all Java versions), testLatestDeps1
  - Log files: /tmp/test1-java17-indy-false.log, /tmp/testLatestDeps1.log
  - Error: `Expected span to have name <DB Query> but was <localhost>`
  - Test: `JdbcInstrumentationTest > test getClientInfo exception`
  - Location: AbstractJdbcInstrumentationTest.java:1223
  - Fix: Updated DbClientSpanNameExtractor to not use server address alone as span name

- [x] Task: `:instrumentation:jdbc:library:testStableSemconv`
  - Seen in: test2 (all Java versions)
  - Log files: /tmp/test2-java25-indy-false.log
  - Error: `Expected span to have name <DB Query> but was <localhost>`
  - Test: `JdbcInstrumentationTest > test getClientInfo exception`
  - Location: AbstractJdbcInstrumentationTest.java:1223
  - Fix: Same as above

- [x] Task: `:instrumentation:cassandra:cassandra-3.0:javaagent:testStableSemconv`
  - Seen in: test1 (all Java versions), testLatestDeps1
  - Log files: /tmp/test1-java17-indy-false.log, /tmp/testLatestDeps1.log
  - Fix: Same as above

## Notes
The failures were all related to the db.query.summary implementation. The span name was being set to "localhost" instead of "DB Query" when SQL could not be parsed.

**Root cause**: When SQL statement cannot be parsed (e.g., "testing 123" which is not valid SQL), the sanitizer returns null for operation, collection, and querySummary. The span name logic was incorrectly falling back to server.address as the span name.

**Solution**: According to OpenTelemetry semantic conventions for database spans, `server.address` should only be used as part of the `{target}` when combined with an operation (e.g., "SELECT localhost"). When there is no operation, the fallback should be `db.system.name` or the default "DB Query" span name.

**Fix applied**: Updated `DbClientSpanNameExtractor.computeSpanNameStable()` to only use server.address when an operation is available. When no operation exists, it now properly falls back to db.system.name or "DB Query".

**Commit**: 11e7b49ed1
