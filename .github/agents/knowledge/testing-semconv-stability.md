# [Semconv] Dual Semconv Testing

## Quick Reference

- Use when: reviewing semconv dual-mode assertions or `testStableSemconv` / `testBothSemconv` tasks
- Review focus: mode-specific assertions, `maybeStable()` usage boundaries

## Background: The Three Modes

The system property `otel.semconv-stability.opt-in` (or env `OTEL_SEMCONV_STABILITY_OPT_IN`)
controls which attributes are emitted at runtime. Tests must run in all applicable modes.

| Property value | Old attrs emitted | Stable attrs emitted | Purpose |
| --- | :-: | :-: | --- |
| *(unset)* | ✅ | ❌ | Default / legacy mode — what most users run today |
| `database` | ❌ | ✅ | Stable-only — users who have opted in |
| `database/dup` | ✅ | ✅ | Both — migration period support |

Multiple domains can be comma-separated: `database,code,service.peer`.

Available domains and their `SemconvStability` methods:

| Domain | `opt-in` value | Methods |
| --- | --- | --- |
| Database | `database` / `database/dup` | `emitOldDatabaseSemconv()`, `emitStableDatabaseSemconv()` |
| Code | `code` / `code/dup` | `emitOldCodeSemconv()`, `emitStableCodeSemconv()` |
| RPC | `rpc` / `rpc/dup` | `emitOldRpcSemconv()`, `emitStableRpcSemconv()` |
| Service peer | `service.peer` / `service.peer/dup` | `emitOldServicePeerSemconv()`, `emitStableServicePeerSemconv()` |

All methods are in `io.opentelemetry.instrumentation.api.internal.SemconvStability`.

## Gradle Test Task Setup

Every instrumentation module with Semconv versioning **must** define a `testStableSemconv` task.
Only add it to modules whose tests actually exercise semconv attributes — not to sibling
submodules (e.g. unit-test modules) that don't touch semconv.

A `testBothSemconv` task (testing the `/dup` mode) is **only required for the RPC domain**.
Database, code, and service-peer domains do not need a `testBothSemconv` task — only
`testStableSemconv` (and the default `test` task for the legacy/unset mode).

See [gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements.

Database domain example (stable-only task):

```kotlin
val testStableSemconv by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=database")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
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

### `maybeStable(OLD_KEY)` for 1:1 key renames

Use `maybeStable(OLD_KEY)` when only the attribute *key* flips between old and stable
semconv and the value is identical:

```java
span.hasAttribute(equalTo(maybeStable(DB_STATEMENT), "SELECT ?"));
```

`maybeStable()` does **not** cover `/dup` mode (it returns one key, not both), and does
**not** apply where the mapping isn't 1:1 — for example `DB_RESPONSE_STATUS_CODE` →
`ERROR_TYPE`. Use `emitOld*()` / `emitStable*()` `if` blocks for those.

### `if` blocks (not `if/else`) when structure differs

When the *set* of asserted attributes differs between modes — not just values — use
separate top-level `if` blocks rather than `if/else`. For domains that support `/dup` mode
(currently RPC), this is required so both branches run; for other domains it's a habit
that keeps the assertion `/dup`-safe if the domain ever adopts it:

```java
if (emitStableCodeSemconv()) {
  assertThat(attributes).containsEntry(CODE_FUNCTION_NAME, "MyClass.myMethod");
}
if (emitOldCodeSemconv()) {
  assertThat(attributes).containsEntry(CODE_NAMESPACE, "MyClass");
}
```
