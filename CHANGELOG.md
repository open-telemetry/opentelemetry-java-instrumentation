# Changelog

## Version 1.1.0 - 2021-05-14

### 🌟 New javaagent instrumentation

- RxJava 3
  ([#2794](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2794))

### 🌟 New library instrumentation

- RxJava 3
  ([#2794](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2794))

### 📈 Enhancements

- Support sub-millisecond precision for start/end times on Java 9+
  ([#2600](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2600))
- `@WithSpan` async support added for methods returning async Reactor 3.x types
  ([#2714](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2714))
- `@WithSpan` async support added for methods returning Guava ListenableFuture
  ([#2811](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2811))
- Semantic attributes `code.namespace` and `code.function` captured on JAX-RS internal spans
  ([#2805](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2805))
- Context propagated to reactor-netty callbacks
  ([#2850](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2850))

### Behavioral changes

- AWS lambda flush timeout raised to 10 seconds
  ([#2855](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2855))
- `SERVER` span names improved for Spring MVC, Grails, Wicket, and Struts
  ([#2814](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2814))
- `SERVER` span names improved for Servlet filters
  ([#2887](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2887))
- `SERVER` span names improved for Resteasy
  ([#2900](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2900))
- `SERVER` span names improved for Jersey and CXF
  ([#2919](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2919))
- JAX-RS `@ApplicationPath` annotation captured as part of `SERVER` span name
  ([#2824](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2824))
- RequestDispatcher `forward()` and `include()` internal spans removed
  ([#2816](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2816))
- Raised gRPC min version supported to 1.6 in order to use new gRPC context bridge API
  ([#2948](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2948))

### 🛠️ Bug fixes

- gRPC context bridging issues
  ([#2564](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issue/2564),
  [#2959](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issue/2959))
- URL credentials of the form `https://username:password@www.example.com/` no longer captured
  ([#2707](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2707))
- Spring MVC instrumentation can cause Spring MVC to misroute requests under some conditions
  ([#2815](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2815))
- RxJava2 NoSuchFieldError
  ([#2836](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2836))
- Duplicate http client tracing headers
  ([#2842](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2842))
- Netty 4.1 listeners could not be removed by application
  ([#2851](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2851))
- NPE caused in gRPC ProtoReflectionService
  ([#2876](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2876))
- Context leak when using Ratpack
  ([#2910](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2910))
- Context leak when using Jetty
  ([#2920](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2920))
- Servlet instrumentation overwrites setStatus that was set manually earlier
  ([#2929](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2929))
- Spans not captured on interface default methods annotated with JAX-RS annotations
  ([#2930](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2930))

### 🧰 Tooling

- Documented how to write InstrumentationModule line by line
  ([#2793](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2793))
- New instrumenter API used in JMS instrumentation
  ([#2803](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2803))
- Instrumenter API improvements
  ([#2860](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2860))
- Muzzle checks whether used fields are actually declared somewhere
  ([#2870](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2870))
- Extracted javaagent-extension-api from tooling & spi
  ([#2879](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2879))
  - You no longer have to depend on the `javaagent-tooling` module to implement custom instrumentations: a new `javaagent-extension-api` module was introduced, containing all the necessary instrumentation classes and interfaces;
  - `InstrumentationModule` and `TypeInstrumentation` were moved to the `io.opentelemetry.javaagent.extension.instrumentation` package;
  - `AgentElementMatchers`, `ClassLoaderMatcher` and `NameMatchers` were moved to the `io.opentelemetry.javaagent.extension.matcher` package;
  - A new SPI `AgentExtension` was introduced: it replaces `ByteBuddyAgentCustomizer`;
  - `InstrumentationModule#getOrder()` was renamed to `order()`;
  - `InstrumentationModule#additionalHelperClassNames()` has been removed; use `isHelperClass(String)` instead if you use the muzzle compile plugin. If you're not using muzzle, you can override `getMuzzleHelperClassNames()` directly instead;
  - `InstrumentationModule#getAllHelperClassNames()` has been removed; you can call `getMuzzleHelperClassNames()` to retrieve all helper class names instead.

## Version 1.1.0 - 2021-04-14

### ☢️ Breaking changes

- Update servlet attribute names for log injection, from `traceId` and `spanId` to `trace_id` and
  `span_id`
  ([#2593](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2593))
- Renamed `runtime.jvm.gc.collection` metric to `runtime.jvm.gc.time`
  ([#2616](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2616))

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
- Fix StackOverflowError if jdbc driver implementation of Connection getMetaData calls Statement
  execute
  ([#2756](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2756))

### 🧰 Tooling

- Make muzzle reference creation package(s) configurable
  ([#2615](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2615))
- Instrumentations now can skip defining context store manually
  ([#2775](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2775))
- New Instrumenter API
  ([#2596](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2596))
