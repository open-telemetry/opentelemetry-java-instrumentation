# [Semconv] Dual Semconv Testing

Use this article when changing semconv opt-in tests or their Gradle tasks.
It shows which modes each domain requires and how to express their
expected attributes without hiding mode differences.

## Selectable domains

The system property `otel.semconv-stability.opt-in` (or env `OTEL_SEMCONV_STABILITY_OPT_IN`)
controls which attributes are emitted at runtime. Tests must run in all applicable modes.

| Property value | Old attrs emitted | Stable attrs emitted | Purpose                                           |
| -------------- | :---------------: | :------------------: | ------------------------------------------------- |
| _(unset)_      |        ✅         |          ❌          | Default / legacy mode — what most users run today |
| `rpc`          |        ❌         |          ✅          | Stable-only — users who have opted in             |
| `rpc/dup`      |        ✅         |          ✅          | Both — migration period support                   |

Multiple selectable domains can be comma-separated: `rpc,code,service.peer`.
Database semantic conventions are always stable; retired database opt-in tokens do not
change database telemetry.

Available domains and their `SemconvStability` methods:

| Domain       | `opt-in` value                      | Methods                                                         |
| ------------ | ----------------------------------- | --------------------------------------------------------------- |
| Code         | `code` / `code/dup`                 | `emitOldCodeSemconv()`, `emitStableCodeSemconv()`               |
| RPC          | `rpc` / `rpc/dup`                   | `emitOldRpcSemconv()`, `emitStableRpcSemconv()`                 |
| Service peer | `service.peer` / `service.peer/dup` | `emitOldServicePeerSemconv()`, `emitStableServicePeerSemconv()` |

All methods are in `io.opentelemetry.instrumentation.api.internal.SemconvStability`.

## Gradle Test Task Setup

Every Gradle project whose tests exercise selectable semconv modes **must** define its own
`testStableSemconv` task. This includes `javaagent-unit-tests` projects whose tests branch on an
`emitOld*()` or `emitStable*()` accessor.

A `testBothSemconv` task (testing the `/dup` mode) is **only required for the RPC domain**.
Code and service-peer domains do not need a `testBothSemconv` task — only
`testStableSemconv` (and the default `test` task for the legacy/unset mode).

Database-only tests use the default task. Do not register copies solely for database opt-in.
Preserve mixed variants that exercise other domains and variants for experimental telemetry,
disabled adapters, connection telemetry, exception signals, and dependency versions.

See [gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements. In a module that also
registers custom `JvmTestSuite`s, add opt-in tasks only for suites whose tests exercise the
affected semconv attributes. Use `testing.suites.withType(JvmTestSuite::class)` when every suite
is relevant and shares the same configuration. Otherwise keep the task bound to
`sourceSets.test`, or select the relevant suites explicitly when the added coverage justifies
the extra build-script complexity.

Code domain example (stable-only task):

```kotlin
val testStableSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=code")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=code")
}
```

RPC domain example (stable + both tasks — `testBothSemconv` required only for RPC):

```kotlin
val testStableSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=rpc")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc")
}

val testBothSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup")
}
```

Wire into `check`: for RPC modules `check { dependsOn(testStableSemconv, testBothSemconv) }`;
for other domains `check { dependsOn(testStableSemconv) }`.

## Asserting Attributes in Tests

For the cross-cutting shape — inline ternary with `null` for "absent", static-imported flag
accessors, and `assumeTrue(...)` guidance — see
[testing-general-patterns.md](testing-general-patterns.md#flag-gated--mode-dependent-assertions).
The semconv-specific patterns below build on that shape.

### Database assertions

Assert stable database keys, identifiers, span names, and metric names directly:

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
