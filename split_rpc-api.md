I think this could be broken up into smaller PRs which would make reviews go faster, here's an AI generated suggestion that seems reasonable, though I haven't looked at it too closely:

# Plan: Break PR #15932 into Smaller PRs

**PR:** [Add RPC semantic convention stable opt-in support #15932](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/15932)

**TL;DR:** PR #15932 adds RPC stable semconv opt-in support across 25 files with +1,076/-350 changes. It can be decomposed into 5 incremental PRs that each deliver value independently while preserving backward compatibility. The key insight is that the `RpcAttributesGetter` interface change can be made non-breaking initially by adding a second type parameter with defaults, allowing the extractors and metrics to be updated separately from the instrumentation implementations.

---

## PR 1: Add RPC SemconvStability infrastructure (foundation)

**Files:**
- `instrumentation-api/src/main/java/io/opentelemetry/instrumentation/api/internal/SemconvStability.java`
- `instrumentation-api-incubator/build.gradle.kts` (test tasks only)

**Changes:**
- Add `emitOldRpcSemconv()` / `emitStableRpcSemconv()` to `SemconvStability`  
- Add `stableRpcSystemName()` mapping function (`apache_dubbo→dubbo`, `connect_rpc→connectrpc`)
- Add `getOldRpcMethodAttributeKey()` and `getOldRpcMetricAttributes()` helper methods
- Add `rpc` and `rpc/dup` to test task JVM args

**Impact:** No behavioral change - just adds flags (defaults to old semconv only)

---

## PR 2: Expand RpcAttributesGetter interface (backward-compatible API change)

**Files:**
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcAttributesGetter.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcClientAttributesExtractor.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcServerAttributesExtractor.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcSizeAttributesExtractor.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcSpanNameExtractor.java`

**Changes:**
- Add `RESPONSE` type parameter to `RpcAttributesGetter<REQUEST, RESPONSE>` with wildcard `?` usage in extractors where response type isn't needed
- Deprecate `getMethod(REQUEST)` and add new default methods: `getRpcMethod(REQUEST)`, `getErrorType(REQUEST, RESPONSE, Throwable)`, `isPredefined(REQUEST)`
- Update extractor method signatures to accept `RpcAttributesGetter<REQUEST, RESPONSE>`

**Impact:** No behavioral change - default implementations return null/false preserving existing behavior

---

## PR 3: Add dual-semconv support to RPC attributes extractors

**Files:**
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcCommonAttributesExtractor.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcSpanNameExtractor.java`
- `instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcAttributesExtractorTest.java`
- `instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcSpanNameExtractorTest.java`

**Changes:**
- Add stable attribute keys: `rpc.system.name`, `rpc.method_original`, `error.type`
- Update `onStart()` to emit old/stable attributes based on stability flags
- Update `onEnd()` to emit `error.type` for stable semconv
- Update `RpcSpanNameExtractor.extract()` to use `getRpcMethod()` for stable semconv
- Update tests to validate both semconv modes

---

## PR 4: Add dual-semconv support to RPC metrics

**Files:**
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcClientMetrics.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcServerMetrics.java`
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcMetricsAdvice.java`
- `instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcClientMetricsTest.java`
- `instrumentation-api-incubator/src/test/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcServerMetricsTest.java`

**Changes:**
- Add new metrics: `rpc.client.call.duration` (s), `rpc.server.call.duration` (s)
- Keep old metrics: `rpc.client.duration` (ms), `rpc.server.duration` (ms)
- Update `RpcMetricsAdvice` with stable attribute key list (`rpc.system.name`, `rpc.response.status_code`)
- Record to old/stable histograms based on stability flags
- Update tests for both semconv modes

---

## PR 5: Update instrumentation implementations

**Files:** All `*RpcAttributesGetter` implementations across instrumentation modules:

| Instrumentation | File |
|-----------------|------|
| apache-dubbo-2.7 | `DubboRpcAttributesGetter.java` |
| aws-sdk-1.11 | `AwsSdkRpcAttributesGetter.java`, `AwsSdkSpanNameExtractor.java` |
| aws-sdk-2.2 | `AwsSdkRpcAttributesGetter.java` |
| grpc-1.6 | `GrpcRpcAttributesGetter.java` |
| gwt-2.0 | `GwtRpcAttributesGetter.java` |
| rmi | `RmiClientAttributesGetter.java`, `RmiServerAttributesGetter.java` |
| spring-rmi-4.0 | `ClientAttributesGetter.java`, `ServerAttributesGetter.java` |

**Changes:**
- Update type signatures from `RpcAttributesGetter<REQUEST>` to `RpcAttributesGetter<REQUEST, RESPONSE>`
- Add `@Deprecated` to existing `getMethod()` implementations
- Optionally implement `getRpcMethod()`, `getErrorType()`, `isPredefined()` for richer stable semconv support
- Add `@SuppressWarnings("deprecation")` to call sites like `AwsSdkSpanNameExtractor`

---

## Verification

- Build passes: `./gradlew build`
- Unit tests: `./gradlew :instrumentation-api-incubator:test`
- Test with stability flags: `./gradlew :instrumentation-api-incubator:testStableSemconv :instrumentation-api-incubator:testBothSemconv`
- Verify no public API breaks with japicmp

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Incremental merging over atomic** | Each PR delivers reviewable, testable value and can be merged independently |
| **Interface change first** | Changing `RpcAttributesGetter` signature early with backward-compatible defaults allows extractors and instrumentations to be updated in any order |
| **Tests inline with implementation** | Test updates go with their corresponding implementation PR rather than a separate test-only PR |

---

## Dependencies

```
PR 1 (SemconvStability)
  │
  ├── PR 2 (RpcAttributesGetter interface)
  │     │
  │     ├── PR 3 (Attributes extractors) ──┐
  │     │                                  │
  │     └── PR 4 (Metrics) ────────────────┤
  │                                        │
  └────────────────────────────────────────┴── PR 5 (Instrumentation implementations)
```

- PR 1 must be merged first (provides the stability flags)
- PR 2 must be merged before PRs 3, 4, or 5 (changes the interface)
- PRs 3 and 4 can be merged in parallel after PR 2
- PR 5 can be merged after PR 2 (doesn't depend on 3 or 4)


