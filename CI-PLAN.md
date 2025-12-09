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
The failures are all related to the db.query.summary implementation. The span name is being set to "localhost" instead of "DB Query". This is happening in test cases that involve getClientInfo exceptions.

The issue appears to be that when there's no actual SQL statement (e.g., during getClientInfo operations that fail), the query summary logic is returning the server name ("localhost") as the span name instead of falling back to "DB Query".

Root cause: The implementation needs to handle cases where there is no SQL statement and fall back to an appropriate default span name.
