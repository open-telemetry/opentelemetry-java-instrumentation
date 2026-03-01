# Knowledge Index

Reusable repository guidance for review and coding agents.

Load only files relevant to the current scope to reduce noise and avoid over-constraining edits.

## Topics

| File | Load when |
| --- | --- |
| `api-deprecation-policy.md` | Public API removal, rename, or deprecation; stable vs alpha breaking changes |
| `gradle-conventions.md` | `build.gradle.kts` or `settings.gradle.kts` changes, custom test task registration or wiring |
| `config-property-stability.md` | `otel.instrumentation.*` property add, remove, rename, or deprecation |
| `javaagent-advice-patterns.md` | ByteBuddy `@Advice` class or advice-method changes |
| `javaagent-module-patterns.md` | `InstrumentationModule`, `TypeInstrumentation`, `Singletons`, `VirtualField`, `CallDepth` |
| `library-patterns.md` | Library instrumentation telemetry, builder, getter, or setter pattern changes |
| `naming-modules.md` | New or renamed modules or packages; settings includes |
| `testing-experimental-flags.md` | `testExperimental` task or experimental span-attribute assertions |
| `testing-semconv-dual.md` | Semconv opt-in modes, `emitOld*`/`emitStable*`, `maybeStable`, Semconv test tasks |

## Naming Conventions

- File names are topic-oriented and kebab-cased.
- Prefer `<domain>-<focus>.md` patterns (for example `testing-semconv-dual.md`).
- Keep titles aligned with category tags used in agent checklists (`[Build]`, `[Testing]`, etc.).
