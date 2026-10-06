# [Semconv] Dual Semconv Testing

Use this article when changing semconv selection tests or their Gradle tasks.
It shows which modes each domain requires and how to express their
expected attributes without hiding mode differences.

## Selectable domains

Use the property that matches the selected conventions' stability:

- `otel.semconv-stability.opt-in=<domain>` or `OTEL_SEMCONV_STABILITY_OPT_IN` for
  selectable stable conventions.
- `otel.semconv-stability.preview=<domain>` or `OTEL_SEMCONV_STABILITY_PREVIEW` for
  preview conventions.

`<domain>` is a documentation placeholder, not a literal configuration value. Replace it
with a supported selector before using an example; unrecognized selectors leave the mode
unchanged. Multiple domains can be comma-separated, for example
`otel.semconv-stability.preview=<domain>,<other-domain>`.

Test all modes the domain supports. A domain that always emits the same conventions needs
no semconv selection task. Code and database conventions are stable-only.

### RPC and service-peer preview selection

RPC and service-peer use `otel.semconv-stability.preview`. For RPC:

| `otel.semconv-stability.preview` value | Old attrs emitted | Preview attrs emitted | Purpose                     |
| -------------------------------------- | :---------------: | :-------------------: | --------------------------- |
| _(unset)_                              |        ✅         |          ❌           | Default / legacy mode       |
| `rpc`                                  |        ❌         |          ✅           | Preview-only                |
| `rpc/dup`                              |        ✅         |          ✅           | Legacy and preview together |

To select both domains, use `otel.semconv-stability.preview=rpc,service.peer`.

Legacy `opt-in` values for RPC and service-peer, including `/dup`, remain accepted and combine with
preview values outside v3-preview. V3-preview ignores those legacy tokens for preview domains.
Preserve tests that explicitly cover this compatibility behavior.

Their `SemconvStability` methods:

| Domain       | Property                         | Values                              | Methods                                                         |
| ------------ | -------------------------------- | ----------------------------------- | --------------------------------------------------------------- |
| RPC          | `otel.semconv-stability.preview` | `rpc` / `rpc/dup`                   | `emitOldRpcSemconv()`, `emitStableRpcSemconv()`                 |
| Service peer | `otel.semconv-stability.preview` | `service.peer` / `service.peer/dup` | `emitOldServicePeerSemconv()`, `emitStableServicePeerSemconv()` |

All methods are in `io.opentelemetry.instrumentation.api.internal.SemconvStability`.
The RPC and service-peer `emitStable*()` names select preview conventions; the method names do not
indicate convention stability.

## Gradle Test Task Setup

Every Gradle project whose tests exercise selectable semconv modes **must** define its own
`testStableSemconv` task for stable selection or `testPreviewSemconv` task for preview selection.
Define both when the tests exercise both kinds of selection. This includes `javaagent-unit-tests`
projects whose tests branch on an `emitOld*()` or `emitStable*()` accessor.
Keep the default `test` task for the default mode.

RPC requires a `testBothSemconv` task for the `/dup` mode. Service-peer does not need
that task; its default `test` and `testPreviewSemconv` tasks cover the required modes.

Preserve mixed variants that exercise selectable domains and variants for experimental telemetry,
disabled adapters, connection telemetry, exception signals, and dependency versions.

See [gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements. In a module that also
registers custom `JvmTestSuite`s, add selection tasks only for suites whose tests exercise the
affected semconv attributes. Use `testing.suites.withType(JvmTestSuite::class)` when every suite
is relevant and shares the same configuration. Otherwise keep the task bound to
`sourceSets.test`, or select the relevant suites explicitly when the added coverage justifies
the extra build-script complexity.

For a domain with selectable stable conventions:

```kotlin
val testStableSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=<domain>")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=<domain>")
}
```

For a preview domain, use `testPreviewSemconv` and the preview property. Add the second
task only when the domain requires duplicate-mode coverage:

```kotlin
val testPreviewSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.preview=<domain>")
  systemProperty("metadataConfig", "otel.semconv-stability.preview=<domain>")
}

val testBothSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.preview=<domain>/dup")
  systemProperty("metadataConfig", "otel.semconv-stability.preview=<domain>/dup")
}
```

For a selectable stable domain requiring duplicate-mode coverage, use
`otel.semconv-stability.opt-in=<domain>/dup` in the second task.

Wire every registered variant into `check`. For stable selection, use
`check { dependsOn(testStableSemconv) }`; for preview selection, use
`check { dependsOn(testPreviewSemconv) }`. Include both selection tasks when both are
needed, and add `testBothSemconv` when duplicate-mode coverage is required.

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
