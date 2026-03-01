---
applyTo: "**/build.gradle.kts"
---

# Gradle Testing Instructions

## Rules

When registering custom Gradle `Test` tasks, set both:

- `testClassesDirs = sourceSets.test.get().output.classesDirs`
- `classpath = sourceSets.test.get().runtimeClasspath`

Wire each custom test task into `check`.

Never use `--rerun-tasks`. You may use `--rerun`.

## See Also

For detailed patterns, see:

- `.github/agents/knowledge/testing-gradle-tasks.md`
- `.github/agents/knowledge/build-conventions.md`
- `.github/agents/knowledge/testing-experimental-flags.md`
