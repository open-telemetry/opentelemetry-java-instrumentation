# Changelog

## Unreleased:

### 🌟 New javaagent instrumentation

- Elasticsearch 7
  ([#2514](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2514),
   [#2528](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2528))
- Couchbase 3.1
  ([#2524](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2524))
- Grails
  ([#2512](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2512))
- RocketMQ
  ([#2263](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2263))
- Lettuce 6
  ([#2589](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2589))
- Servlet 5
  ([#2609](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2609))
- Vaadin web framework
  ([#2619](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2619))
- GWT
  ([#2652](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2652))
- Tapestry web framework
  ([#2690](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2690))
- `@WithSpan` support for methods returning CompletableFuture
  ([#2530](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2530))
- `@WithSpan` support for methods returning async RxJava 2 types
  ([#2530](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2530))

### 🌟 New library instrumentation

- Library instrumentation for AWS SDK v1
  ([#2525](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2525))
- Library instrumentation for Lettuce 5.1
  ([#2533](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2533))
- RocketMQ
  ([#2263](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2263))
- Lettuce 6
  ([#2589](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2589))
- Spring Boot Autoconfigure support for `@WithSpan` methods returning CompletableFuture
  ([#2618](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2618))
- Spring Boot Autoconfigure support for `@WithSpan` methods returning async RxJava 2 types
  ([#2530](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2530))

### 📈 Improvements

- Move attributes to span builder for use by samplers
  ([#2587](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2587))
- Apache Camel - SNS propagation
  ([#2562](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2562))
- Apache Camel - S3 to SQS propagation
  ([#2583](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2583))
- Added `runtime.jvm.gc.count` metric
  ([#2616](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2616))
- Support reactor netty `HttpClient.from` construction
  ([#2650](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2650))
- Improve akka instrumentation
  ([#2737](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2737))
- Record internal metric for SQL cache misses
  ([#2747](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2747))
- End Netty 4.1 client and server spans when the response has completed, instead of when the
  response has started
  ([#2641](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2641))

### 🛠️ Bug fixes

- Fix RestTemplateInterceptor so that it calls endExceptionally() on exception
  ([#2516](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2516))
- Fix app failure under Eclipse OSGi
  ([#2521](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2521))
- Fix undertow span ending too early
  ([#2560](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2560))
- Fix context leak in AWS SDK 2.2 and RocketMQ instrumentations
  ([#2637](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2637))
- Fix hang when a webflux http request is made inside of another webflux http request
  (e.g. auth filter)
  ([#2646](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2646))
- Fix `@WithSpan` instrumentation breaking Java 6 classes
  ([#2699](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2699))
- Fix context not propagated over JMS when explicit destination used
  ([#2702](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2702))
- Fix StackOverflowError if jdbc driver implementation of Connection getMetaData calls
  Statement execute
  ([#2756](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2756))

### 🧰 Tooling

- Run smoke tests on Windows too!
  ([#2568](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2568),
   [#2621](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2621),
   [#2617](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2617))
- Added concurrent http client test with connection reuse
  ([#2550](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2550),
   [#2630](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2630),
   [#2647](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2647))
- Verify in smoke tests that runtime metrics are exported
  ([#2603](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2603))
- Remove JCenter which is about to be sunset
  ([#2387](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2387))
- Make sure muzzle build-time check actually validates anything
  ([#2599](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2599))
- Use Caffeine for weak maps
  ([#2601](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2601))
- Reimplement finding open ports
  ([#2629](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2629))
- Make muzzle reference creation package(s) configurable
  ([#2615](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2615))
- New Instrumenter API
  ([#2596](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2596))
- Added Concurrent server test
  ([#2680](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2680),
   [#2750](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2750),
   [#2749](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2749))
- Added smoke tests for Tomcat 10 and Jetty 11 (Servlet API 5)
  ([#2703](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2703),
   [#2723](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2723))
- Add -Werror flag to the build
  ([#2712](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2712))
- Reduce test flakiness
  ([#2738](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2738),
   [#2728](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2728),
   [#2754](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2754),
   [#2753](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2753),
   [#2759](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2759))
- Split HttpClientTest execution methods for sync and callback
  ([#2675](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2675))

### ☢️ Breaking changes

- Update servlet attribute names for log injection, from `traceId` and `spanId` to `trace_id` and
  `span_id`
  ([#2593](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2593))
- Renamed `runtime.jvm.gc.collection` metric to `runtime.jvm.gc.time`
  ([#2616](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2616))
