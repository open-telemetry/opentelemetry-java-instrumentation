# CI Failure Analysis Plan

## Failed Jobs Summary

- Job: common / spotless (job ID: 57983607252)
- Job: common / build (job ID: 57983607261)
- Job: Analyze (java) (job ID: 57983599428)
- Job: markdown-lint-check / markdown-lint-check (job ID: 57983607254)
- Multiple test jobs (test0, test1, test2, test3) across different Java versions
- Multiple smoke-test jobs
- common / examples
- testLatestDeps (0-3)
- muzzle (1-4)

## Unique Failed Gradle Tasks

- [x] Task: :instrumentation:spring:spring-boot-autoconfigure:spotlessJavaCheck
  - Seen in: common / spotless, common / build
  - Log files: /tmp/spotless.log, /tmp/build.log
  - Issue: Format violation in OpenTelemetryAutoConfiguration.java
  - Fix: Ran spotlessApply to fix formatting

- [x] Task: :javaagent-tooling:compileJava
  - Seen in: common / build, all test jobs, all smoke-test jobs, examples, testLatestDeps, muzzle
  - Log files: /tmp/build.log, /tmp/test0-java8.log, /tmp/examples.log
  - Issue: Compilation errors - cannot find symbol EmptyConfigProperties, and IgnoredTypesConfigurer implementations missing required method
  - Fix: 
    - Fixed EmptyConfigProperties instantiation to use INSTANCE instead of new
    - Made deprecated configure(builder, config) method default in IgnoredTypesConfigurer
    - Updated IgnoredTypesConfigurer implementations to use deprecated method when config is needed
    - Added @SuppressWarnings("deprecation") with comments to all deprecated API usage

- [ ] Task: markdown-lint-check
  - Seen in: markdown-lint-check / markdown-lint-check
  - Log files: /tmp/markdown-lint.log
  - Issue: CI-PLAN.md format violations (will be fixed by not committing this file)

## Notes

- All failures appear related to the declarative-configuration-bridge changes
- The compilation error in javaagent-tooling is the root cause blocking all downstream jobs
- Need to fix: EmptyConfigProperties missing class and IgnoredTypesConfigurer interface changes
