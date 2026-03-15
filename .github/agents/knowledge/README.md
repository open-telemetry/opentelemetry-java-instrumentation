# Knowledge Index

Reusable repository guidance for review and coding agents.

Load only files relevant to the current scope to reduce noise and avoid over-constraining edits.

## Topics

| File | Load when |
| --- | --- |
| `api-deprecation-policy.md` | Public API removal, rename, or deprecation; stable vs alpha breaking changes |
| `config-property-stability.md` | `otel.instrumentation.*` property add, remove, rename, or deprecation |
| `general-rules.md` | Always — review checklist table and core rules enforced on every review |
| `gradle-conventions.md` | `build.gradle.kts` or `settings.gradle.kts` changes, custom test task registration or wiring |
| `javaagent-advice-patterns.md` | ByteBuddy `@Advice` class or advice-method changes |
| `javaagent-module-patterns.md` | `InstrumentationModule`, `TypeInstrumentation`, `Singletons`, `VirtualField`, `CallDepth` |
| `library-patterns.md` | Library instrumentation telemetry, builder, getter, or setter pattern changes |
| `module-naming.md` | New or renamed modules or packages; settings includes |
| `testing-general-patterns.md` | Test files in scope — assertion style, attribute assertion patterns, `satisfies()` lambda usage |
| `testing-experimental-flags.md` | `testExperimental` task or experimental span-attribute assertions |
| `testing-semconv-stability.md` | Semconv opt-in modes, `emitOld*`/`emitStable*`, `maybeStable`, Semconv test tasks |

## Naming Conventions

- File names are topic-oriented and kebab-cased.
- Prefer `<domain>-<focus>.md` patterns (for example `testing-semconv-stability.md`).
- Keep titles aligned with category tags used in agent checklists (`[Build]`, `[Testing]`, etc.).
