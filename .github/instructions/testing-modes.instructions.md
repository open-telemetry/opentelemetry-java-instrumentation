---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/build.gradle.kts"
---

# Instrumentation test modes

Apply these rules when tests exercise configuration-dependent behavior or test-task wiring
selects such behavior. Do not create variants for features the tests do not exercise.

## Experimental and semconv coverage

Exercise experimental features and telemetry, including experimental metrics, in both default-off
and flag-on JVMs. Keep the flag off the default test task and use `testExperimental` or an existing
equivalent variant for flag-on assertions. Wire variants into `check` or CI; an existing wired
variant covering the mode is enough.

For a feature with distinct emission modes, keep DEFAULT coverage in the default JVM and configure
PREVIEW explicitly in another JVM. Cover DEFAULT-only, PREVIEW-only, and combined emission only
where supported. Do not assume a combined selector or a particular property exists. Preserve
meaningful mixed variants and supported configuration-compatibility coverage without duplicating
equivalent combinations.

DEFAULT telemetry covered by `VERSIONING.md` remains compatible until the next major version,
subject to the changes that policy permits. PREVIEW is provisional. These labels do not establish
formal semconv stability or extend guarantees to alpha artifacts.

Use the feature's actual supported configuration. For selectable stable conventions, the property
is `otel.semconv-stability.opt-in=<domain>`; preview conventions use
`otel.semconv-stability.preview=<domain>`. `<domain>` is a placeholder for a supported selector,
not a literal value. Unknown selectors do not change the mode. Domains can be comma-separated.
A domain with one fixed convention needs no selection variant.

For example, RPC supports these preview selections:

| Selection | Old attributes | Preview attributes |
| --- | --- | --- |
| Unset | Yes | No |
| `rpc` | No | Yes |
| `rpc/dup` | Yes | Yes |

RPC needs combined-mode coverage; service-peer's default and preview modes are sufficient.
RPC/service-peer legacy `opt-in` values, including `/dup`, remain supported and combine with
preview values, so preserve focused compatibility tests. These domain-specific contracts do not
make combined-mode tests mandatory for other features.

Scope variants to the suites exercising the behavior and preserve their common JVM settings.
Names such as `testStableSemconv`, `testPreviewSemconv`, and `testBothSemconv` describe conventional
variants, not required tasks in every module. Representative telemetry-producing variants collect
metadata; compatibility-only and disablement regressions need not collect.

## Shared mode accessors

Prefer static imports of existing shared accessors to repeated property reads:

| Mode | Accessor |
| --- | --- |
| Latest dependencies | `TestLatestDeps.testLatestDeps()` |
| Selectable stable semconv | Domain `emitOld*Semconv()` / `emitStable*Semconv()` |
| Preview semconv | Domain `emitOld*Semconv()` / `emitPreview*Semconv()` |

Semconv accessors live in `io.opentelemetry.instrumentation.api.internal.SemconvStability`.
Examples include `emitOldRpcSemconv()` and `emitPreviewRpcSemconv()`.
A module-specific experimental flag can use a per-class `EXPERIMENTAL_ATTRIBUTES` constant,
or a more specific name when several flags coexist.

## Builder-controlled library options

Plain-library fixtures configure builder options through the library API, not a javaagent
property that production library code never reads. Use an explicitly test-only switch to select
the fixture's builder option. Use production properties in library-autoconfigure and other
integrations that actually read them.

For example, suite-owned executions bind the variant to its source suite and wire it into `check`:

```kotlin
import io.opentelemetry.instrumentation.gradle.executions

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExperimentalAttributes") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)",
          )
        }
      }
    }
  }
}
```

In tests shared between agent and plain-library fixtures, resolve the configuration source through
an instance method on the shared base:

```java
protected boolean captureExperimentalSpanAttributes() {
  return testing() instanceof AgentInstrumentationExtension
      ? EXPERIMENTAL_ATTRIBUTES
      : Boolean.getBoolean("test.captureExperimentalSpanAttributes");
}
```

Use that method for builder setup and expectations. A library-autoconfigure fixture can override
it to read its real production setting; preserve intentional regression overrides. Do not cache a
fixture-dependent result in a shared static field. Test-only switches do not belong in metadata
labels; label actual builder calls.

## Expected values

Assert invariant keys and values directly. When a short expected value varies by mode, keep the
conditional at the assertion site. `null` means the attribute must be absent:

```java
span.hasAttributesSatisfyingExactly(
    equalTo(RPC_GRPC_STATUS_CODE, emitOldRpcSemconv() ? (long) Status.Code.OK.value() : null),
    equalTo(RPC_RESPONSE_STATUS_CODE, emitPreviewRpcSemconv() ? Status.Code.OK.name() : null));
```

Both accessors are true in supported duplicate mode. For mutually exclusive values, keep both
inline, such as `span.hasName(testLatestDeps() ? "GET" : "HTTP GET")`.

Put a conditional around the narrowest changing value, not whole assertion chains. Do not
conditionally build a `List<AttributeAssertion>` or introduce `spanName(...)`, `modeValue(...)`,
or `expectedNamespace()` solely to choose short expected values. Existing appropriate shared mode
utilities and helpers doing genuinely nontrivial derivation remain valid. Nested ternaries may
remain inline in attribute-assertion expected values; other test code follows normal Java rules.

Keep the conventional `experimental(value)` helper. Its meaning is specifically "value when
experimental attributes are enabled, otherwise absent":

```java
private static <T> T experimental(T value) {
  return EXPERIMENTAL_ATTRIBUTES ? value : null;
}
```

## Default instrumentation enablement

When a module's default enablement changes, exercise the same representative library operation
through the installed agent in enabled and disabled JVMs. The enabled run emits instrumentation
telemetry; the disabled run emits none. Do not substitute direct `defaultEnabled()` calls,
configuration mocks, or installation-internal assertions.

A dedicated `DefaultEnablementTest` can run under normal `test` and a filtered `testDisabled`.
Use a startup property to select the expected trace shape, not separate positive/negative methods.
For a default-off module, retain the explicitly enabled run and omit that enablement property
from `testDisabled`; a property shared across every task would defeat the disabled case.

Wrap the operation in a manual parent span and wait for asynchronous operation completion inside
`runWithSpan(...)`. In disabled mode, await the exact completed trace:

```java
testing.waitAndAssertTraces(
    trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
```

An immediate `spans().isEmpty()` can pass before export. The completed parent is a synchronization
signal only when the operation has finished; otherwise it can export before a late child.
For this focused enablement test, span names and exact counts are enough. Keep kinds, attributes,
status, and parentage in ordinary behavior tests. Do not collect metadata from `testDisabled`.

This tests actual default behavior, not generic enablement selectors. It does not justify
per-instrumentation alias, precedence, rename, or flat/YAML selector matrices.
