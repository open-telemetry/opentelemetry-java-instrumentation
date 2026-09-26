# Knowledge Index

These articles explain repository behavior, implementation steps, examples, and
exceptions for coding agents or a specific investigation. GitHub Copilot code
review's applicable rules are in
`.github/copilot-instructions.md` and `.github/instructions/*.instructions.md`;
review quality must not depend on following links from those instructions to
these articles.

Load only articles relevant to the current change or investigation; a coding
workflow may require one when its technical detail is needed. Keep reportable
review rules and their exceptions in the native instructions rather than
repeating them as "what to flag" lists here. If an article and an applicable
native instruction differ on review policy, use the native instruction and
update the stale article separately.

## Topics

| File                               | Load when                                                                                                                                                                                                   |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `api-deprecation-policy.md`        | Public API removal, rename, or deprecation; stable vs alpha breaking changes                                                                                                                                |
| `config-property-stability.md`     | `otel.instrumentation.*` property add, remove, rename, or deprecation                                                                                                                                       |
| `java-nullability-contracts.md`    | Upstream `TextMapGetter`/`TextMapSetter` nullable-carrier table; Java attribute-setter overload examples                                                                                                    |
| `metadata-yaml-format.md`          | Instrumentation metadata or configuration shape and validation                                                                                                                                              |
| `gradle-conventions.md`            | `build.gradle.kts` or `settings.gradle.kts` changes, custom test task registration or wiring                                                                                                                |
| `java-reflection.md`               | `Method`, `MethodHandle`, `Constructor`, `Field`, reflective compatibility helpers, or package-local lookups                                                                                                |
| `javaagent-advice-patterns.md`     | ByteBuddy `@Advice` classes or methods, helpers called by advice, or `Java8BytecodeBridge` usage                                                                                                            |
| `javaagent-module-patterns.md`     | `InstrumentationModule`, `TypeInstrumentation`, `CallDepth`                                                                                                                                                 |
| `javaagent-singletons-patterns.md` | `*Singletons`, `*SpanNaming`, and similar holder classes; singleton accessors; callers of singleton accessors/fields                                                                                        |
| `javaagent-thread-local-state.md`  | Temporary `ThreadLocal` state in javaagent advice or helpers; choosing whether cleanup removes the value or restores a previous value                                                                       |
| `javaagent-virtual-fields.md`      | `VirtualField`; javaagent or shared bootstrap state associated with third-party object instances; weak references, weak-key caches/maps, identity registries, or `Object`-keyed side tables                 |
| `javaagent-locking.md`             | Necessary synchronization around javaagent or library instrumentation state; supported lifecycle ownership, bounded critical sections, publication, and external calls near locks                           |
| `library-patterns.md`              | Library instrumentation telemetry, builder, getter, or setter pattern changes                                                                                                                               |
| `messaging-telemetry-ownership.md` | Overlapping messaging observers; processing ownership or invocation naming, integration/client handoff, copied carriers, nested callbacks, scoped suppression, fallback, or best-effort Kafka/SQS traversal |
| `module-naming.md`                 | New or renamed modules or packages; settings includes                                                                                                                                                       |
| `semconv-conformance.md`           | Changes implementing or emitting telemetry governed by semantic conventions; explicit semantic-convention conformance reviews                                                                               |
| `spring-boot-starter-testing.md`   | Locating or adding tests for `opentelemetry-spring-boot-starter` / `OpenTelemetryAutoConfiguration`; judging whether smoke-test coverage exists for it                                                      |
| `testing-default-enablement.md`    | `DefaultEnablementTest`, `testDisabled`, instrumentation disabled by default, or instrumentation that becomes disabled under v3 preview                                                                     |
| `testing-general-patterns.md`      | Test files in scope — assertion style, test method signatures and throws clauses, resource cleanup patterns, abstract test base class state shape, attribute assertion patterns, `satisfies()` lambda usage |
| `testing-experimental-flags.md`    | `testExperimental` task or experimental span-attribute assertions                                                                                                                                           |
| `testing-semconv-stability.md`     | Semconv opt-in modes, `emitOld*`/`emitStable*`, `maybeStable`, Semconv test tasks                                                                                                                           |

## Naming Conventions

- File names are topic-oriented and kebab-cased.
- Prefer `<domain>-<focus>.md` patterns (for example `testing-semconv-stability.md`).
- Keep titles aligned with category tags used in the native review instructions (`[Build]`, `[Testing]`, etc.).
