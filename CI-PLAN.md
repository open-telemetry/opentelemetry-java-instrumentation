# CI Failure Analysis Plan

## Failed Jobs Summary
- All `common / test0-3` jobs (Java 8, 11, 17, 21, 25, 25-deny-unsafe, hotspot, indy true/false) - 44 jobs
- All `test-latest-deps / testLatestDeps0-3` jobs - 4 jobs
- `required-status-check` (depends on above)

Representative job logs:
- Job: common / test0 (8, hotspot, indy false) (ID: 63676566816), log: /tmp/test0-java8-indy-false.log
- Job: test-latest-deps / testLatestDeps0 (ID: 63676566678), log: /tmp/testLatestDeps0.log

## Unique Failed Gradle Tasks

- [ ] Task: `:instrumentation:gwt-2.0:javaagent:testExceptionSignalLogs`
  - Seen in: all `common / test0-3` jobs, all `test-latest-deps / testLatestDeps0-3` jobs
  - Log files: /tmp/test0-java8-indy-false.log, /tmp/testLatestDeps0.log
  - 6 tests completed, 6 failed - all `GwtTest > testGwt()` with `NoSuchElementException`

## Notes
- The `testExceptionSignalLogs` task in `instrumentation/gwt-2.0/javaagent/build.gradle.kts` is missing
  the GWT-specific test setup that the regular `test` task has:
  - `dependsOn(sourceSets["testapp"].output)` and `dependsOn(copyTestWebapp)`
  - Testapp classes added to classpath
  - `usesService(testcontainersBuildService)`
- Without these, the GWT webapp isn't compiled/deployed, causing Selenium to fail locating elements
- This is the only unique failure; all other failures cascade from this one
