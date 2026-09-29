# Conclusion

**Yes, the conditional `strictly("1.0.0")` is necessary to make the default test runtime exercise
exactly `kotlinx-coroutines-core` 1.0.0. It is not necessary for the build or tests to succeed.**

Without the strict constraint, Gradle's normal conflict resolution selects 1.0.1, the highest
requested version for the same module. The source of 1.0.1 is
`io.vertx:vertx-lang-kotlin-coroutines:3.6.0`. The direct
`testLibrary("...kotlinx-coroutines-core:1.0.0")` and
`testLibrary("...kotlinx-coroutines-reactor:1.0.0")` (through
`kotlinx-coroutines-reactive:1.0.0`) only request core 1.0.0; they do not constrain it against the
higher transitive request.

There is therefore no existing mechanism that makes the unconstrained default runtime select
1.0.0. The conditional strict constraint is the mechanism that preserves that minimum-version
coverage.

# Resolution evidence

## Default runtime with the PR constraint

Command:

```text
./gradlew :instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent:dependencyInsight --configuration testRuntimeClasspath --dependency kotlinx-coroutines-core
```

Selected version: **1.0.0**.

The report showed:

- the root request as `kotlinx-coroutines-core:{strictly 1.0.0}`;
- `kotlinx-coroutines-reactor:1.0.0` and `kotlinx-coroutines-reactive:1.0.0`
  requesting core 1.0.0;
- `vertx-lang-kotlin-coroutines:3.6.0` requesting core 1.0.1, downgraded to 1.0.0 by
  the strict constraint.

`testCompileClasspath` selected the same 1.0.0 for the same reasons.

## Default runtime without the constraint

I created a detached temporary worktree, changed only this declaration to the plain
`testLibrary("...:1.0.0")`, and ran the same `dependencyInsight` command there.

Selected version: **1.0.1**.

Gradle reported `By conflict resolution: between versions 1.0.1 and 1.0.0`, with
`vertx-lang-kotlin-coroutines:3.6.0` supplying 1.0.1. Both the direct core request and reactor's
transitive 1.0.0 request were upgraded to 1.0.1.

I also ran:

```text
./gradlew :instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent:test
```

in that temporary worktree. It completed successfully. This establishes the distinction:
the constraint is needed for exact 1.0.0 compatibility coverage, not for build success.

# Why `compileOnly` does not change the default test runtime

The PR changes the direct declaration to
`compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")`. Dependency insight on
`compileClasspath` selected **1.3.0**, as expected, but that declaration did not appear in either
default test classpath report.

This matches the convention implementation in
`conventions/src/main/kotlin/io.opentelemetry.instrumentation.base.gradle.kts`:

- dependencies declared through the custom `library` configuration are copied to
  `testImplementation`, and `compileOnly` extends from `library`;
- dependencies declared through `testLibrary` are copied to `testImplementation`;
- a direct `compileOnly(...)` declaration is not copied to `testImplementation`.

Thus the repository note about a compile-only dependency contributing to tests applies when it is
declared through `library(...)`, not to this direct `compileOnly(...)`.

# `version13Test` and minimum boundaries

Command:

```text
./gradlew :instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent:dependencyInsight --configuration version13TestRuntimeClasspath --dependency kotlinx-coroutines-core
```

Selected version: **1.3.0**. The suite explicitly requests both core 1.3.0 and reactor 1.3.0, and
reactor/reactive also request core 1.3.0. Its `implementation(project())` does not pull the main
project's direct compile-only 1.3.0 into the suite; the suite's own declarations establish the
version.

The two default-mode suites therefore cover distinct intended boundaries:

- default `test`: core 1.0.0, enforced by the strict constraint;
- `version13Test`: core/reactor 1.3.0, the Flow compatibility boundary introduced by the
  consolidation.

Without the constraint, the first boundary silently becomes core 1.0.1. Reactor 1.0.0 remains in
the graph, but its ordinary transitive request cannot prevent that upgrade.

# `-PtestLatestDeps=true`

Command:

```text
./gradlew :instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent:dependencyInsight --configuration testRuntimeClasspath --dependency kotlinx-coroutines-core -PtestLatestDeps=true
```

Selected version: **1.11.0**. The conditional deliberately omits `strictly` in this mode. The
repository convention copies `testLibrary` into `testImplementation` with a latest-release
requirement, and the checked-in latest-dependency pin maps both coroutines core and reactor `+` to
1.11.0. The coroutines BOM then contributes matching 1.11.0 constraints. Vert.x requested core
1.10.2 and was upgraded.

The equivalent `version13TestRuntimeClasspath` command with `-PtestLatestDeps=true` also selected
**1.11.0**. `baseVersion("1.3.0").orLatest("+")` changes that suite's core and reactor requests to
`+`, and the convention's pinning rule selects the checked-in 1.11.0 version.

This conditional shape is important: making the strict constraint unconditional would defeat
latest-dependency testing, while removing it entirely would lose exact 1.0.0 coverage in normal
tests.
