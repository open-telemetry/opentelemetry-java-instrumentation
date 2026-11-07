---
applyTo: "instrumentation/**/javaagent/**/*.java,instrumentation/**/javaagent*/**/*.kt,instrumentation/**/bootstrap/**/*.java,instrumentation/**/bootstrap/**/*.kt,instrumentation/**/javaagent-integration-tests/**/*.java,instrumentation/**/javaagent-integration-tests/**/*.kt,javaagent/**/*.java,javaagent/**/*.kt,javaagent-bootstrap/**/*.java,javaagent-bootstrap/**/*.kt,javaagent-tooling/**/*.java,javaagent-tooling/**/*.kt,examples/**/instrumentation/**/*.java,examples/**/instrumentation/**/*.kt,smoke-tests/extensions/extension/**/*.java,smoke-tests/extensions/extension/**/*.kt,testing/javaagent-test-exporters/**/*.java,testing/javaagent-test-exporters/**/*.kt,testing/testing-common/integration-tests/**/*.java,testing/testing-common/integration-tests/**/*.kt"
---

# Javaagent modules and matching

Apply these rules to javaagent modules, type/method matching, and collaborator holders.
Inspect registration and the supported library lifecycle before choosing a pattern.

## Registration

Register each `InstrumentationModule` through `@AutoService(InstrumentationModule.class)` and
return its compatible `TypeInstrumentation` implementations from `typeInstrumentations()`.
Module classes normally use `{Library}{Version}InstrumentationModule` names.

Several `InstrumentationModule` classes may share a Gradle project and all public selectors.
Public names describe independently selectable behavior, not compatibility implementation classes.

## Enablement names

Apply these rules to names registered by an `InstrumentationModule`. Public enablement names
describe selectable behavior, not Muzzle implementation identities.

- The primary is the full module-directory name, including versions, except where a default-off
  feature needs a separate identity within that module. Example: `spring-webflux-5.0`.
  Names use kebab-case. Order selectors as primary, optional role/feature, version-stripped
  component, then API/product umbrellas so component settings take precedence.
- Normally include a secondary with all numeric versions removed from the module name.
  Example: `jaxrs-2.0-cxf-3.2` has secondary `jaxrs-cxf`.
- Omit unversioned selectors that merely distinguish implementations, while retaining full
  module-directory primaries. Examples: `mongo-async-3.3` has secondary `mongo`, not `mongo-async`;
  `spring-cloud-gateway-webmvc-4.3` has secondary `spring-cloud-gateway`, not
  `spring-cloud-gateway-webmvc`.
- Add role and feature selectors for useful, independently selectable behavior, not compatibility
  helpers. Examples: WebFlux has `spring-webflux-client` and `spring-webflux-server`; incubator API
  integration adds `opentelemetry-api-incubator` before `opentelemetry-api`. Reactor operator bridges
  share ordinary Reactor selectors.
- API/product umbrellas can group the base component and related integrations. Membership follows
  the product/API being instrumented, not dependencies. Example: `armeria` includes HTTP and gRPC
  instrumentation; Apache `kafka` does not include Spring Kafka. Keep `reactor` scoped to core
  propagation, excluding Reactor Kafka and Reactor Netty clients; keep `tomcat` scoped to server
  instrumentation, excluding DBCP and JDBC pools.
- A default-off feature within a default-on component must have an independent identity and share
  no selector with default-on registrations. All-default-off groups may share selectors.
  Example: `jdbc-datasource` does not share `jdbc`; `hibernate` groups default-off telemetry,
  including procedure calls, but excludes default-on Hibernate Reactive context propagation.
  A standalone default-off feature keeps its full directory primary and versionless feature
  secondary, such as `jaxrs-1.0-annotations`, `jaxrs-annotations`, without sharing `jaxrs`.
- Compound API/provider modules retain their full primary, version-stripped compound secondary,
  and API umbrella, such as `jaxrs-2.0-cxf-3.2`, `jaxrs-cxf`, `jaxrs`, without bare provider or
  API-generation aliases.
- The generic Reactor Netty server registration housed in `spring-webflux-5.0` uses primary
  `spring-webflux-5.0` and secondaries `reactor-netty-server`, `spring-webflux-server`, and
  `spring-webflux`, without `reactor-netty` or `reactor`. This cross-family exception applies only
  to that registration.

Selectors and emitted instrumentation scope names are independent contracts. Changing one does
not require changing the other. Preserve existing controls and ordering when maintaining aliases.

## Type and method matching

- Match only the types genuinely instrumented. Prefer `named(...)` or `namedOneOf(...)` for
  known classes; use hierarchy or annotation matchers when the target is their implementations,
  subclasses, or annotated types.
- For expensive hierarchy or annotation type matching, use
  `TypeInstrumentation.classLoaderOptimization()` with `hasClassesNamed(...)` as an absence
  prefilter. Name-only type matchers need no additional optimization.
- In `transform()`, reference nested advice by `getClass().getName() + "$AdviceName"`, without
  redundant `this.`, class literals, or hard-coded outer-class names. Loading advice or library
  class literals from the agent loader can resolve unavailable application types.
- The matcher must establish compatibility for every non-optional, statically typed argument the
  advice reads. Match otherwise-unbound positions only to distinguish supported overloads or
  lifecycle boundaries; do not bind unused arguments merely to mirror a matcher.
- `optional = true` permits an absent argument, not an incompatible present type. Dynamic advice
  parameter typing inserts a runtime cast; every matched signature must guarantee assignability.
  An `Object` binding may intentionally support broad reference types.
- Positional argument and return-type matchers can use class literals for primitives and public,
  accessible `java.base` types available at the minimum supported Java version. Keep `named(...)`
  for instrumented-library types, inaccessible JDK types, and types unavailable at that minimum.
  Compiling against a type or having a `java.*` package name does not prove agent-loader safety.
- For complete, nonempty signatures with exact parameter types, prefer Byte Buddy's class-based
  `takesArguments(Type.class...)` when every parameter is a primitive or a public, safely accessible
  `java.base` type available at the module's minimum supported Java version. Otherwise use
  `AgentElementMatchers.takesArguments(...)` with one matcher per parameter, in order, for library,
  unsafe, or unavailable types and richer predicates. Use `is(Type.class)` for safe types and
  `named("fully.qualified.Type")` for instrumented-library types or types that are unsafe to load;
  for example, `takesArguments(named("redis.clients.jedis.commands.ProtocolCommand"),
  is(byte[][].class))` matches that erased signature without loading the library type. Mix matcher
  kinds as needed and preserve array dimensions. Richer predicates include subtype checks and
  parameter alternatives such as `namedOneOf(...)`. Do not infer safety from a `java.*` package
  prefix or the current build JDK.
  When a file needs both this helper and any Byte Buddy `takesArguments(...)` overload, explicitly
  import both methods and avoid a bare `takesArguments()` call. For a zero-parameter signature, use
  `takesArguments(0)`.
- Keep positional `takesArgument(...)` matchers for intentionally partial signatures. Use a complete
  ordered matcher list when the helper can express the signature, including richer predicates or
  parameter alternatives. Do not add otherwise unnecessary parameter constraints solely to make a
  partial signature complete.
- When advice binds a library argument as `Object` to support different known parameter types
  across versions, use `namedOneOf(...)` with those type names instead of `any()` or a custom
  argument-count check. Use `takesArgument(index, namedOneOf(...))` for an intentionally partial
  signature; include the predicate in `AgentElementMatchers.takesArguments(...)` when matching a
  complete signature. The positional matcher already guarantees that the argument exists.
  `takesArgument(index, Object.class)` matches a parameter declared exactly as `Object`, not any
  reference type.
- With positional matchers, use `takesArguments(n)` only when exact arity is needed to distinguish
  supported overloads, preserve helper-call compatibility, or select the required lifecycle
  boundary. Do not add an exact count merely to guarantee that an argument exists.
- Place an existing `takesArguments(n)` check before the positional `takesArgument(index, ...)`
  checks in the same conjunction, including partial-signature matchers. This ordering convention
  does not require adding count or type checks or changing overload selection.
- Omit return-type predicates unless needed to select supported methods or prevent a concrete
  incompatibility. A typed `@Advice.Return` alone does not justify a predicate; consider the supported
  API contract, covariant returns, and the actual transformation pipeline. If advice replaces the
  return value, ensure the replacement fits the method's declared return type; replacement alone does
  not require a predicate.
- Use `isDeclaredBy` on a Byte Buddy method matcher only
  when a type matcher covers several types but advice targets a member declared by one
  specific type; a name and signature that already identify the method need no extra
  restriction. `isMethod()` is redundant after a non-empty `named(...)` or
  `namedOneOf(...)` matcher. Keep it when the name can be empty, because `named("")`
  can match constructors and class initializers.

## Class-loader version boundaries

Override `InstrumentationModule.classLoaderMatcher()` only for a real version or optional-artifact
boundary Muzzle cannot establish from referenced symbols. It is not an absence optimization.
Method/parameter changes, field removals, and package relocations Muzzle already sees do not need
an override. Most modules use the default matcher.

Use cheap `hasClassesNamed(...)` checks, not hierarchy inspection. Multiple class arguments require
all classes to be present; prefer one landmark when sufficient.

Verify each landmark against concrete artifact versions, including backports and newer siblings.
Do not infer a boundary from the module directory name.

| Landmark role | Comment |
| --- | --- |
| Positive lower bound | `// added in X.Y` |
| Positive class that disappears at the upper bound | `// removed in Y.Z` |
| Negated class excluding newer versions | `// added in Y.Z` |
| One positive class establishes both bounds | `// added in X.Y, removed in Y.Z` |

A ceiling depends on disappearance, not the class's earlier introduction. For example:

```java
return hasClassesNamed(
    // added in 3.7
    "com.mongodb.MongoClientSettings$Builder",
    // removed in 4.0
    "com.mongodb.async.SingleResultCallback");
```

For one inline class string, keep the call compact and put the role comment immediately above it,
including on chained calls. For multiple strings, put each comment directly above its string.
Keep brief backport, namespace, or native-instrumentation context only when useful.

Bare versions refer to the module's main target artifact. When one Gradle project contains modules
targeting different artifacts, identify the target at the start of each `classLoaderMatcher()`.
For a landmark in another artifact, use `// added in group:artifact X.Y` when the boundary is that
artifact's own, or `// added in X.Y (via group:artifact A.B)` when documenting the target's boundary
through a required transitive artifact. The same qualification applies to removal comments.
Use `Servlet` for servlet-api coordinates and omit the group for `io.opentelemetry*` artifacts.

An independent opt-in artifact is a presence gate, not a target-version boundary. Use both lines:

```java
// artifact presence gate (native OTel support)
// added in co.elastic.clients:elasticsearch-java 8.10
.and(not(hasClassesNamed("co.elastic.clients.transport.instrumentation.Instrumentation")));
```

Do not label target-artifact or required-transitive landmarks as artifact-presence gates.

## Call depth

When supported instrumented calls recurse, use `CallDepth.forClass(TargetClass.class)`. Increment
on entry and decrement on every applicable exit, including failure. Skip nested span completion
while the remaining depth is positive; do not leave unmatched increments on suppressed paths.

## Holder conventions

- Initialize shared instrumenters and collaborators at class load, using `GlobalOpenTelemetry.get()`
  where an OpenTelemetry instance is needed. A new instrumentation normally uses
  `io.opentelemetry.<module-name>` as its scope name; preserve established telemetry identity during
  module maintenance.
- In `*Singletons`, `*SpanNaming`, and similar holders, a zero-argument accessor directly
  returning a stored collaborator has the same lower-camel name as its field, without `get`.
  Callers static-import that accessor. This does not apply to methods that compute values,
  take arguments, or perform work; do not static-import verb-named helper methods on this
  basis. Keep collaborator fields private and lower camel case. Uppercase constant-like fields
  may be exposed directly. A `static final VirtualField` handle is uppercase regardless of visibility;
  other semantic keys and handles are candidates, not an unconditional uppercase requirement.
