# [Semconv] Dual Semconv Testing

Use this article when changing semconv selection tests or their Gradle tasks.
It shows which modes each domain requires and how to express their
expected attributes without hiding mode differences.

## Selectable domains

Code and database conventions are stable-only. Use `otel.semconv-stability.preview`
or `OTEL_SEMCONV_STABILITY_PREVIEW`
for RPC and service-peer preview conventions. Tests must run in all applicable modes.

RPC preview selection:

| `otel.semconv-stability.preview` value | Old attrs emitted | Preview attrs emitted | Purpose                     |
| -------------------------------------- | :---------------: | :-------------------: | --------------------------- |
| _(unset)_                              |        ✅         |          ❌           | Default / legacy mode       |
| `rpc`                                  |        ❌         |          ✅           | Preview-only                |
| `rpc/dup`                              |        ✅         |          ✅           | Legacy and preview together |

Multiple preview domains can be comma-separated: `otel.semconv-stability.preview=rpc,service.peer`.

Legacy `opt-in` values for RPC and service-peer, including `/dup`, remain accepted and combine with
preview values outside v3-preview. V3-preview ignores those legacy tokens for preview domains.
Preserve tests that explicitly cover this compatibility behavior.

Available domains and their `SemconvStability` methods:

| Domain       | Property                         | Values                              | Methods                                                         |
| ------------ | -------------------------------- | ----------------------------------- | --------------------------------------------------------------- |
| RPC          | `otel.semconv-stability.preview` | `rpc` / `rpc/dup`                   | `emitOldRpcSemconv()`, `emitStableRpcSemconv()`                 |
| Service peer | `otel.semconv-stability.preview` | `service.peer` / `service.peer/dup` | `emitOldServicePeerSemconv()`, `emitStableServicePeerSemconv()` |

All methods are in `io.opentelemetry.instrumentation.api.internal.SemconvStability`.
The RPC and service-peer `emitStable*()` names select preview conventions; the method names do not
indicate convention stability.

## Gradle Test Task Setup

Every Gradle project whose tests exercise selectable semconv modes **must** define its own
`testStableSemconv` task. This includes `javaagent-unit-tests` projects whose tests branch on an
`emitOld*()` or `emitStable*()` accessor. The conventional task name covers RPC or service-peer
preview selection.

A `testBothSemconv` task (testing the `/dup` mode) is **only required for the RPC domain**.
The service-peer domain does not need a `testBothSemconv` task, only
`testStableSemconv` (and the default `test` task for the legacy/unset mode).

Preserve mixed variants that exercise selectable domains and variants for experimental telemetry,
disabled adapters, connection telemetry, exception signals, and dependency versions.

See [gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements. In a module that also
registers custom `JvmTestSuite`s, add selection tasks only for suites whose tests exercise the
affected semconv attributes. Use `testing.suites.withType(JvmTestSuite::class)` when every suite
is relevant and shares the same configuration. Otherwise keep the task bound to
`sourceSets.test`, or select the relevant suites explicitly when the added coverage justifies
the extra build-script complexity.

RPC preview selection requires preview-only and duplicate-mode tasks:

```kotlin
val testStableSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.preview=rpc")
  systemProperty("metadataConfig", "otel.semconv-stability.preview=rpc")
}

val testBothSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
  systemProperty("metadataConfig", "otel.semconv-stability.preview=rpc/dup")
}
```

Wire into `check`: for RPC modules `check { dependsOn(testStableSemconv, testBothSemconv) }`;
for other domains `check { dependsOn(testStableSemconv) }`.

## Asserting Attributes in Tests

For the cross-cutting shape — inline ternary with `null` for "absent", static-imported flag
accessors, and `assumeTrue(...)` guidance — see
[testing-general-patterns.md](testing-general-patterns.md#flag-gated--mode-dependent-assertions).
The semconv-specific patterns below build on that shape.

### Direct assertions

Assert keys, identifiers, span names, and metric names directly when they do not depend on a mode:

```java
span.hasAttributesSatisfyingExactly(
    equalTo(DB_SYSTEM_NAME, POSTGRESQL),
    equalTo(DB_QUERY_TEXT, "SELECT ?"));
```

Keep exact exported-telemetry assertions and coverage for query summaries, errors,
parameterization, sanitization, batches, namespaces, server targets, peers, and pool lifecycle.

### Inline mode-dependent expectations

When no established semconv utility applies, keep each mode-dependent expectation at the
assertion site. Gate the expected value with the matching `emitOld*()` or `emitStable*()`
accessor and use `null` to expect the attribute to be absent:

```java
equalTo(
    RPC_GRPC_STATUS_CODE,
    emitOldRpcSemconv() ? (long) Status.Code.OK.value() : null)
equalTo(
    RPC_RESPONSE_STATUS_CODE,
    emitStableRpcSemconv() ? Status.Code.OK.name() : null)
```

This paired form also covers `/dup` mode because both accessors return true. Do not hide
these expectations in separate conditional attribute blocks or helper-built lists.
