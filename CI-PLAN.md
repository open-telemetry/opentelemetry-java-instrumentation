# CI Failure Analysis Plan

## Failed Jobs Summary

- Job 1: common / spotless (job ID: 57496310483)
- Job 2: common / test0 (8, hotspot, indy false) (job ID: 57496310656)
- Job 3: common / test0 (8, hotspot, indy true) (job ID: 57496310669)
- Job 4: common / test1 (8, hotspot, indy false) (job ID: 57496310642)
- Job 5: common / test1 (8, hotspot, indy true) (job ID: 57496310651)
- Job 6: common / test0 (11, hotspot, indy false) (job ID: 57496310681)
- Job 7: common / test0 (11, hotspot, indy true) (job ID: 57496310659)
- Job 8: common / test1 (11, hotspot, indy false) (job ID: 57496310679)
- Job 9: common / test1 (11, hotspot, indy true) (job ID: 57496310764)
- Job 10: common / test0 (17, hotspot, indy false) (job ID: 57496310692)
- Job 11: common / test0 (17, hotspot, indy true) (job ID: 57496310671)
- Job 12: common / test1 (17, hotspot, indy false) (job ID: 57496310687)
- Job 13: common / test1 (17, hotspot, indy true) (job ID: 57496310675)
- Job 14: common / test0 (21, hotspot, indy false) (job ID: 57496310754)
- Job 15: common / test0 (21, hotspot, indy true) (job ID: 57496310746)
- Job 16: common / test1 (21, hotspot, indy false) (job ID: 57496310701)
- Job 17: common / test1 (21, hotspot, indy true) (job ID: 57496310760)
- Job 18: common / test0 (25, hotspot, indy false) (job ID: 57496310726)
- Job 19: common / test0 (25, hotspot, indy true) (job ID: 57496310712)
- Job 20: common / test1 (25, hotspot, indy false) (job ID: 57496310708)
- Job 21: common / test1 (25, hotspot, indy true) (job ID: 57496310718)
- Job 22: common / test0 (25-deny-unsafe, hotspot, indy false) (job ID: 57496310743)
- Job 23: common / test0 (25-deny-unsafe, hotspot, indy true) (job ID: 57496310735)
- Job 24: test-latest-deps / testLatestDeps0 (job ID: 57496310442)
- Job 25: test-latest-deps / testLatestDeps1 (job ID: 57496310446)
- Job 26: test-latest-deps / testLatestDeps2 (job ID: 57496310436)
- Job 27: markdown-lint-check / markdown-lint-check (job ID: 57496310468)

## Unique Failed Gradle Tasks

- [x] Task: :instrumentation:hibernate:hibernate-3.3:javaagent:spotlessJavaCheck
  - Seen in: common / spotless
  - Log files: /tmp/spotless.log
  - Issue: Format violation in QueryInstrumentation.java
  - Fixed: Applied spotless formatting

- [x] Task: :instrumentation:hibernate:hibernate-4.0:javaagent:spotlessJavaCheck
  - Seen in: common / spotless
  - Log files: /tmp/spotless.log
  - Issue: Format violation (similar to hibernate-3.3)
  - Fixed: Applied spotless formatting

- [x] Task: :instrumentation-api-incubator:testStableSemconv
  - Seen in: All test0/test1 jobs, testLatestDeps jobs
  - Log files: /tmp/test0-java8-indy-false.log, /tmp/testLatestDeps0.log
  - Issue: 18 test failures in DbClientSpanNameExtractorTest - tests expecting span names but getting null
  - Fixed: Updated DbClientSpanNameExtractor.computeSpanNameStable to properly fall back to DEFAULT_SPAN_NAME and include server address in target fallback

- [ ] Fix: Markdown lint errors
  - Seen in: markdown-lint-check
  - Log files: /tmp/markdown-lint.log
  - Issue: 55 markdown formatting errors across CI-PLAN.md, DB_QUERY_SUMMARY_PROGRESS.md, DB_STABLE_SEMCONV_TEST_PLAN.md

## Notes

- Spotless failures are formatting issues that can be fixed with spotlessApply
- Test failures are in DbClientSpanNameExtractorTest - tests expect span names but getting null, likely related to db.query.summary implementation
- Markdown lint failures are in tracking documentation files (CI-PLAN.md shouldn't exist yet, but DB_QUERY_SUMMARY_PROGRESS.md and DB_STABLE_SEMCONV_TEST_PLAN.md need fixing)
- All test jobs fail with the same test errors, so fixing the test code once will resolve all of them
