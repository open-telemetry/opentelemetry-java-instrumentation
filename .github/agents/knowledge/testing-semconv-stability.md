# [Semconv] Dual Semconv Testing

## Quick Reference

- Use when: reviewing semconv dual-mode assertions or `testStableSemconv` / `testBothSemconv` tasks
- Review focus: mode-specific assertions, `maybeStable()` usage boundaries, class-level deprecation suppression

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

Preferred approach (most compact) — inline ternary with `emitStable*()`, where `null` means
the attribute is expected absent:

```java
equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB)
equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "42601" : null)
span.hasName(emitStableDatabaseSemconv() ? "SELECT" : "SELECT dbname")
```

Use `maybeStable(oldKey)` when only the attribute key changes and the value stays the same.
`maybeStable()` does NOT cover `dup` mode — use separate `if` blocks for that.

```java
span.hasAttribute(equalTo(maybeStable(DB_STATEMENT), "SELECT ?"));
```

Use separate `if` blocks (not `if/else`) when assertion structure differs significantly between
modes — this ensures both branches execute in `dup` mode:

```java
if (emitStableCodeSemconv()) {
  assertThat(attributes).containsEntry(CODE_FUNCTION_NAME, "MyClass.myMethod");
}
if (emitOldCodeSemconv()) {
  assertThat(attributes).containsEntry(CODE_NAMESPACE, "MyClass");
}
```

Use `assumeTrue(emitStable*())` only when an entire test is meaningful in one mode only.

## Key Rules

- Add `@SuppressWarnings("deprecation")` at class level when tests use old Semconv constants.
- Use `if` (not `if/else`) for dual-mode assertions so both branches run in `/dup` mode.
- Do NOT use `maybeStable()` for `DB_RESPONSE_STATUS_CODE` → `ERROR_TYPE` — these don't have
  a 1:1 mapping. Use `emitOld*()`/`emitStable*()` `if` blocks instead.
