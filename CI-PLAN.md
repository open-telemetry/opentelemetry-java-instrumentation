# CI Failure Analysis Plan

## Failed Jobs Summary
- Multiple test jobs (test0, test1, test2, test3 across Java 8, 11, 17, 21, 25 with various indy settings)
- testLatestDeps jobs (0, 1, 2, 3)

## Unique Failed Gradle Tasks

- [ ] Task: :instrumentation-api-incubator:javaagent-testing:test
  - Seen in: test1 (Java 8, indy false)
  - Log files: /tmp/test1-java8-indy-false.log
  - Failure: Test assertions failing for http.response.body.size attribute

- [ ] Task: :smoke-tests-otel-starter:spring-boot-2:testDeclarativeConfig  
  - Seen in: test1 (Java 8, indy false)
  - Log files: /tmp/test1-java8-indy-false.log
  - Failure: 6 tests failed - AssertionError for [LONG attribute 'http.response.body.size'] - Expecting actual not to be null

## Notes
- All failures appear related to missing `http.response.body.size` attribute in test assertions
- The attribute is expected to be present in HTTP spans but is null
- This is likely related to the declarative-configuration-bridge changes
- Both regular instrumentation API tests and Spring Boot smoke tests are affected
