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

RPC telemetry and configured server service mappings emit their current conventions
unconditionally. Their tests do not need semconv-selection variants.

## Gradle Test Task Setup

Every Gradle project whose tests exercise selectable semconv modes **must** define its own
`testStableSemconv` task for stable selection or `testPreviewSemconv` task for preview selection.
Define both when the tests exercise both kinds of selection. This includes `javaagent-unit-tests`
projects whose tests branch on an `emitOld*()`, `emitStable*()`, or `emitPreview*()` accessor.
Keep the default `test` task for the default mode.

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
val testStableSemconv = register<Test>("testStableSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=<domain>")
  systemProperty("metadataConfig", "otel.semconv-stability.opt-in=<domain>")
}
```

For a preview domain, use `testPreviewSemconv` and the preview property. Add the second
task only when the domain requires duplicate-mode coverage:

```kotlin
val testPreviewSemconv = register<Test>("testPreviewSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.preview=<domain>")
  systemProperty("metadataConfig", "otel.semconv-stability.preview=<domain>")
}

val testBothSemconv = register<Test>("testBothSemconv") {
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

For any remaining mode-dependent expectations, use the matching accessor at the assertion site.
RPC and server service mapping expectations are not mode-dependent; assert their values directly.
