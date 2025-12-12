# CI Failure Analysis Plan

## Failed Jobs Summary
- Job: common / test0 (all Java versions, both indy true/false)
- Job: common / test1 (all Java versions, both indy true/false)
- Job: common / test2 (all Java versions, both indy true/false)
- Job: common / test3 (all Java versions, both indy true/false)
- Job: common / smoke-test (ubuntu-latest, websphere)
- Job: test-latest-deps / testLatestDeps0-3

## Unique Failed Gradle Tasks

- [ ] Task: :instrumentation:jdbc:javaagent:test
  - Seen in: test0 jobs, testLatestDeps jobs
  - Log files: /tmp/test0-java8-indy-false.log, /tmp/testLatestDeps0.log
  - Error: Tests expecting `peer.service` attribute that is missing

- [ ] Task: :instrumentation:netty:netty-3.8:javaagent:test
  - Seen in: test0 jobs
  - Log files: /tmp/test0-java8-indy-false.log
  - Error: Tests expecting `peer.service` attribute that is missing

- [ ] Task: :instrumentation:servlet:servlet-3.0:javaagent-testing:test
  - Seen in: test0 jobs
  - Log files: /tmp/test0-java8-indy-false.log
  - Error: Tests expecting HTTP headers to be captured (`captureHttpHeaders` tests failing)

- [ ] Task: :instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent:test
  - Seen in: test0 jobs, testLatestDeps jobs
  - Log files: /tmp/test0-java8-indy-false.log, /tmp/testLatestDeps0.log
  - Error: Tests expecting `peer.service` attribute that is missing

- [ ] Task: :instrumentation:vertx:vertx-redis-client-4.0:javaagent:test
  - Seen in: testLatestDeps jobs
  - Log files: /tmp/testLatestDeps0.log
  - Error: Unknown, need to investigate

- [ ] Task: :instrumentation:elasticsearch:elasticsearch-api-client-7.16:javaagent:test
  - Seen in: testLatestDeps jobs
  - Log files: /tmp/testLatestDeps0.log
  - Error: Unknown, need to investigate

- [ ] Task: :smoke-tests:test
  - Seen in: smoke-test websphere job
  - Log files: /tmp/smoke-test-websphere.log
  - Error: Missing `http.request.header.x-test-request` attribute

## Notes
The common thread is that tests are expecting certain attributes that are not being captured:
1. `peer.service` attribute missing in client spans (netty, jdbc, spring-webflux)
2. HTTP request headers not being captured in smoke tests
3. Some servlet tests also failing on HTTP header capture

Root cause analysis:
- Commit bc9ba668 introduced declarative config bridge infrastructure
- Commit 016e5a41 changed PeerServiceResolver to use DeclarativeConfigUtil.getStructuredList()
- The configuration mapping is already in place (general.peer.service_mapping → otel.instrumentation.common.peer-service-mapping)
- Tests set -Dotel.instrumentation.common.peer-service-mapping=127.0.0.1=test-peer-service,localhost=test-peer-service
- HTTP headers are set via -Dotel.instrumentation.http.server.capture-request-headers=X-Test-Request in smoke tests

Need to verify:
1. Are the singleton classes being initialized before GlobalOpenTelemetry is set?
2. Is the ConfigProvider properly returning the mapped values?
3. Are there any timing issues with static initialization?
