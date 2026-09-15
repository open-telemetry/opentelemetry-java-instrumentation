# Remote consistency review: PR #20062

## Task identity

- Request ID: `f2a55796-abb6-48ff-b51a-aed4296bdcbd`
- Repository: `open-telemetry/opentelemetry-java-instrumentation`
- Source PR: `#20062` — Add MCP Java SDK client instrumentation
- Captured source head: `walleseve/opentelemetry-java-instrumentation:mcp-java-sdk-client-0.14`
- Captured source head SHA: `eb589d9422d0befaf8d0c2a67b7a09a8fcf344fa`
- Live identity verification: passed. GitHub reported the exact captured head repository, branch, and SHA before review.
- Review scope: the 21 files in the source PR's authoritative GitHub diff.

## Rules and precedence

The review applied `CONTRIBUTING.md`, `docs/contributing/style-guide.md`,
`docs/contributing/writing-instrumentation.md`,
`docs/contributing/documenting-instrumentation.md`, the applicable Java style
and Java test instructions, and the instrumentation-list instruction. Explicit
repository and path-specific rules took precedence over generic guidance and
raw frequency. Closest exemplars were accepted only when compliant with those
rules. CI-only issues and findings excluded by the repository review
instructions were not reported.

## Pre-edit ordered avoidable list

1. Move `McpProtocolVersionState`'s private constructor before its methods.

## Complete ordered findings inventory

### 1. Custom MCP operation-duration listener

- Changed code: `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpClientMetrics.java:34-94`
- Closest compliant exemplar: `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/genai/GenAiClientMetrics.java:36-122`
- Frequency: rare
- Disposition: needed
- Applicable rules and precedence: `docs/contributing/style-guide.md:194-211` requires direct semconv constant reuse in javaagent code; `docs/contributing/writing-instrumentation.md:285-333` permits javaagent-specific instrumentation logic. Those explicit rules outrank the more common use of an existing shared metrics helper.
- Why: MCP defines a distinct `mcp.client.operation.duration` histogram, and no shared MCP operation-metrics helper exists. The implementation follows the established `OperationListener`/`OperationMetricsUtil` lifecycle used by the compliant GenAI exemplar while reusing canonical MCP semconv names, description, unit, and attributes.
- Rule-conflict gate: not applicable; needed findings were not changed.

### 2. Session protocol capture through `VirtualField` and Reactor

- Changed code: `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpProtocolVersionState.java:19-49`
- Closest compliant exemplar: `instrumentation/servlet/servlet-3.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/servlet/v3_0/snippet/ServletOutputStreamInjectionState.java:13-35`
- Frequency: rare
- Disposition: needed
- Applicable rules and precedence: the javaagent per-object-state rule prefers `VirtualField` for state attached to a third-party carrier; `docs/contributing/writing-instrumentation.md:285-333` governs javaagent helper placement. These topic and repository rules outrank simpler but lifecycle-unsafe global maps.
- Why: the negotiated protocol version belongs to the exact `McpClientSession` instance and becomes available only from the asynchronous initialize result. `VirtualField` follows the repository's carrier-state pattern, and the reactive callback captures the value without mutating SDK objects.
- Rule-conflict gate: not applicable; needed findings were not changed.

### 3. Constructor placed after methods

- Changed code: original source diff at `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpProtocolVersionState.java:51-53`; fixed location at the same path, lines `25-27`
- Closest compliant exemplar: `instrumentation/kubernetes-client-7.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/kubernetesclient/v7_0/CurrentState.java:20-57`
- Frequency: rare
- Disposition: avoidable
- Applicable rules and precedence: `CONTRIBUTING.md:119-137`, `docs/contributing/style-guide.md:76-101`, `.github/instructions/java-style.instructions.md:21-26`, Google Java Style, and generic minimal-change guidance. The explicit path-specific Java rule and repository style guide outrank raw precedent.
- Why: repository class organization requires instance fields, then constructors, then methods. Moving the existing private constructor provides identical behavior and matches the compliant state-class exemplar.
- Rule-conflict gate: all governing repository, path-specific, and generic rules were checked. No test, metadata, or topic-specific rule conflicts. The explicit Java class-organization rule wins, the exemplar was rechecked as compliant, and the move preserves visibility and behavior.
- Fix commit: `4fd6e48ca86210e5c45cc89b703a23ce3b6b9966`

### 4. Subscription-time Reactor span wrapper

- Changed code: `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpToolCallMono.java:23-53`
- Closest compliant exemplar: `instrumentation/reactor/reactor-3.1/library/src/main/java/io/opentelemetry/instrumentation/reactor/v3_1/ReactorAsyncOperationEndStrategy.java:21-36`
- Frequency: unique
- Disposition: needed
- Applicable rules and precedence: `docs/contributing/style-guide.md:230-239` governs hot-path allocations, while `docs/contributing/writing-instrumentation.md:285-333` permits library-specific javaagent wrapping. Correct reactive lifecycle behavior takes precedence over the frequency of direct `Mono.deferContextual` use.
- Why: the MCP SDK returns a cold `Mono`; the span must start on subscription, inherit Reactor context, and end on completion, error, or cancellation. The wrapper delegates termination to the repository's Reactor async-operation strategy and therefore uses the closest compliant lifecycle API.
- Rule-conflict gate: not applicable; needed findings were not changed.

### 5. Nested thread-local request-ID correlation

- Changed code: `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpToolCallState.java:22-99` and `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpClientSessionInstrumentation.java:40-42,77-83`
- Closest compliant exemplar: `instrumentation/kubernetes-client-7.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/kubernetesclient/v7_0/CurrentState.java:13-57`
- Frequency: rare
- Disposition: needed
- Applicable rules and precedence: `docs/contributing/style-guide.md:120-157` requires lower camel case for runtime collaborators, and the javaagent state rule applies `VirtualField` only to per-object carrier state. Those explicit distinctions outrank examples that uppercase every `static final` thread local.
- Why: the SDK creates the request ID in a separate nested method during `sendRequest`. The thread local bridges those synchronous calls, preserves nesting, and is removed or restored before returning. It is transient call state rather than state owned by a third-party object, so `VirtualField` is not the appropriate replacement.
- Rule-conflict gate: not applicable; needed findings were not changed.

### 6. Separate javaagent unit-test module

- Changed code: `instrumentation/mcp/mcp-java-sdk-0.14/javaagent-unit-tests/build.gradle.kts:1-13` and `instrumentation/mcp/mcp-java-sdk-0.14/javaagent-unit-tests/src/test/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpToolCallAttributesExtractorTest.java:25-95`
- Closest compliant exemplar: `docs/contributing/writing-instrumentation.md:455-488`
- Frequency: rare
- Disposition: needed
- Applicable rules and precedence: `docs/contributing/writing-instrumentation.md:455-488` explicitly prescribes a `javaagent-unit-tests` module when tests must directly access javaagent implementation classes; `.github/instructions/java-tests.instructions.md:11-70` governs assertions. The explicit repository testing guidance outranks the more common single javaagent test module.
- Why: normal javaagent tests cannot directly access implementation classes because of agent class-loader separation. The separate module is the documented mechanism for focused extractor tests, and its assertions follow the applicable AssertJ guidance.
- Rule-conflict gate: not applicable; needed findings were not changed.

## Disposition totals

- Needed: 5
- Avoidable: 1
- Unclear: 0 — no unclear findings.

Every avoidable occurrence was fixed. No needed or unclear finding was changed.

## Validation

- Passed: live GitHub PR identity check returned head repository `walleseve/opentelemetry-java-instrumentation`, branch `mcp-java-sdk-client-0.14`, and SHA `eb589d9422d0befaf8d0c2a67b7a09a8fcf344fa`.
- Passed: `./gradlew :instrumentation:mcp:mcp-java-sdk-0.14:javaagent:compileJava`.
- Passed: `./gradlew :instrumentation:mcp:mcp-java-sdk-0.14:javaagent:spotlessCheck`.
- Passed: repository secret scan of `instrumentation/mcp/mcp-java-sdk-0.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/mcp/v0_14/McpProtocolVersionState.java`.
- Passed: parallel validation CodeQL assessment; the constructor-only reorder was classified as trivial and the scan completed successfully with no security finding.
- Passed: deterministic commit review verified fix commit `4fd6e48ca86210e5c45cc89b703a23ce3b6b9966` changes only `McpProtocolVersionState.java`.

