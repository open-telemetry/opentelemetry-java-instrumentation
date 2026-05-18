# Package Name Exceptions Cleanup Plan

Generated on 2026-05-11 from the remaining exceptions in `.github/scripts/check-package-names.sh`.
Updated on 2026-05-13 after PRs 5-8 merged and their historical javaagent exceptions were removed from the checker.
Updated again on 2026-05-13 after PRs 9-12 were implemented and opened as draft PRs.
Updated on 2026-05-14 after PRs 9-12 merged and their historical javaagent exceptions were removed from the checker.
Updated again on 2026-05-14 to plan faster batches: up to 20 changed files per PR, still four PRs per batch.
Updated again on 2026-05-14 to bump the target to up to 40 changed files per PR and plan all remaining historical package renames.
Updated on 2026-05-17 after PRs 15-16 merged, and after deciding to handle Akka/Scala forkjoin as a module-name cleanup instead of a package-only cleanup.
Updated on 2026-05-18 after PRs 13, 18, 20, and 21 merged and their historical package exceptions were removed from the checker.

## Goal

Continue reducing package-name checker exceptions in reviewable PRs. Prefer changes where the current package name is only a historical abbreviation and the rename has a small blast radius. Use up to about 40 changed files per package-cleanup PR, while still submitting only four PRs at a time.

## Quick Audit Summary

The remaining exceptions fall into four buckets:

| Bucket | Current signal | Recommendation |
| --- | --- | --- |
| Module-wide skips | `java-*` has 39 files, `jmx-metrics` has 43 files | Do not start here; these need broader naming decisions. |
| Library packages under third-party namespaces | `grpc`, `lettuce`, `nats`, `rxjava` each have 1 file | Leave for later; these are likely compatibility/shim packages. |
| Javaagent advice under instrumented library namespaces | mostly 1 file each | Leave for later; the script already says these must live in the instrumented library namespace. |
| Historical javaagent packages | many one-dir modules remain, usually with one source directory | Best place to keep chipping away. |

## Completed Cleanups

These PRs have merged, and their historical javaagent package-name exceptions have been removed from `.github/scripts/check-package-names.sh`:

- PR 1: `internal-eclipse-osgi-3.6` and `opentelemetry-extension-kotlin-1.0`.
- PR 2: `spark-2.3`.
- PR 3: `external-annotations`.
- PR 4: `hibernate-procedure-call-4.3`.
- PR 5: `elasticsearch-rest-common-5.0`.
- PR 6: `kotlinx-coroutines-flow-1.3`, `liberty-dispatcher-20.0`, and `scala-fork-join-2.8`.
- PR 7: `jaxws-jws-api-1.1`.
- PR 8: `jsf-mojarra-1.2`, `jsf-mojarra-3.0`, `jsf-myfaces-1.2`, and `jsf-myfaces-3.0`.
- PR 9: `elasticsearch-api-client-7.16`.
- PR 10: `elasticsearch-transport-common`.
- PR 11: `opensearch-rest-common`.
- PR 12: `spring-boot-actuator-autoconfigure-2.0`.
- PR 13: `internal-application-logger` and `spring-boot-resources`.
- PR 18: `jaxrs-common`.
- PR 20: `servlet-common` snippet package.
- PR 21: `servlet-common` root helper package.

PRs 5-8 merged upstream as:

- #18720: `elasticsearch-rest-common-5.0`.
- #18721: `kotlinx-coroutines-flow-1.3`, `liberty-dispatcher-20.0`, and `scala-fork-join-2.8`.
- #18722: `jaxws-jws-api-1.1`.
- #18723: `jsf-mojarra-1.2`, `jsf-mojarra-3.0`, `jsf-myfaces-1.2`, and `jsf-myfaces-3.0`.

PRs 9-12 merged upstream as:

- #18730: `elasticsearch-api-client-7.16`.
- #18731: `elasticsearch-transport-common`.
- #18732: `opensearch-rest-common`.
- #18733: `spring-boot-actuator-autoconfigure-2.0`.

PRs 15-16 merged upstream as:

- #18748: `jaxrs-2.0-annotations` and `jaxrs-2.0-common`.
- #18749: `jaxrs-3.0-annotations` and `jaxrs-3.0-common`.

PRs 13, 18, 20, and 21 merged upstream as:

- #18746: `internal-application-logger` and `spring-boot-resources`.
- #18776: `jaxrs-common`.
- #18777: `servlet-common` snippet package.
- #18778: `servlet-common` root helper package.

`external-annotations` still remains in the unversioned-module allowlist as `javaagent:external-annotations`; that is a separate module-name exception, not a historical package exception.

## PR Creation Notes

Use draft PRs against upstream `main` for the package-only cleanup batches. These PR branches must be based directly on `upstream/main`, not on `next`, so the PR diffs contain only package moves and dependent import updates. Do not include `.github/scripts/check-package-names.sh` changes in those PRs; checker exception removals stay on the tracking/checker branch and should be updated separately after the package cleanup PRs merge.

Batch sizing: keep submitting four PRs at a time, but each PR can include up to about 40 changed files when the modules are closely related or the reference spread is easy to audit. Count dependent import updates and test package moves in that limit. Avoid combining package moves that require broad cross-module rewrites just to fill the file budget. The known outlier is the root `servlet-common` helper package move, which likely exceeds 40 changed files because many servlet modules import those helpers; treat that as a dedicated final PR unless a clean split emerges during implementation.

Preferred PR description for future package cleanup PRs:

```text
Part of
- https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/18428
```

When splitting several already-implemented package moves into separate PRs:

1. Keep a local WIP branch or commit that contains the full tested implementation.
2. Recreate each PR branch from `upstream/main`, not `next`.
3. Apply only the module paths for that PR, for example:

```bash
git switch -C <branch-name> upstream/main
git diff --binary <base-before-package-moves> <wip-branch> -- <module-paths> | git apply --index
git commit -m "<PR title>"
git push -u origin <branch-name> --force-with-lease
gh pr create --repo open-telemetry/opentelemetry-java-instrumentation --base main --head trask:<branch-name> --draft --title "<PR title>" --body "Part of
- https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/18428"
```

After pushing or force-pushing, verify that each PR file list contains only the intended module paths and does not include `.github/scripts/check-package-names.sh`:

```bash
gh pr diff <pr-number> --repo open-telemetry/opentelemetry-java-instrumentation --name-only
```

For common-module package moves, search for downstream versioned modules importing the moved helper package. Compile those dependent modules too; the common module's own test can pass while the full smoke-test build fails on stale imports.

## Open Cleanup PRs

PR 14 is open as #18747 and PR 17 is open as #18772. Keep `.github/scripts/check-package-names.sh` and checker exception removals on `next` until cleanup PRs merge.

### PR 14: OpenTelemetry annotation and instrumentation API modules (open #18747)

Modules:

- `opentelemetry-extension-annotations-1.0`
- `opentelemetry-instrumentation-api`
- `opentelemetry-instrumentation-annotations-1.16`

Expected package changes:

- `io.opentelemetry.javaagent.instrumentation.extensionannotations.v1_0` -> `io.opentelemetry.javaagent.instrumentation.opentelemetry.extension.annotations.v1_0`
- `io.opentelemetry.javaagent.instrumentation.instrumentationapi` -> `io.opentelemetry.javaagent.instrumentation.opentelemetry.instrumentation.api`
- `io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16` -> `io.opentelemetry.javaagent.instrumentation.opentelemetry.instrumentation.annotations.v1_16`

Notes:

- Around 30 changed Java files after the dependent Kotlin coroutines import is included.
- Reference audit found only local package declarations/imports for `opentelemetry-extension-annotations-1.0`.
- `opentelemetry-instrumentation-api` has local tests in `src/test` and `src/testOldServerSpan` that must move with the main package.
- Update dependent import(s) in `kotlinx-coroutines-1.0` for instrumentation annotations.
- `opentelemetry-instrumentation-api` still remains in the unversioned-module allowlist unless the module name changes; the PR removes only the broad historical package skip after merge.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:opentelemetry-extension-annotations-1.0:javaagent:test :instrumentation:opentelemetry-instrumentation-api:javaagent:test :instrumentation:opentelemetry-instrumentation-annotations-1.16:javaagent:test :instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent:compileJava
```

### PR 17: Akka and Scala forkjoin module/package names (open #18772)

Modules:

- `akka-actor-fork-join-2.5` -> `akka-actor-forkjoin-2.5`
- `scala-fork-join-2.8` -> `scala-forkjoin-2.8`

Expected package changes:

- `io.opentelemetry.javaagent.instrumentation.akkaforkjoin` -> `io.opentelemetry.javaagent.instrumentation.akka.actor.forkjoin.v2_5`
- `io.opentelemetry.javaagent.instrumentation.scala.fork.join.v2_8` -> `io.opentelemetry.javaagent.instrumentation.scala.forkjoin.v2_8`

Notes:

- This is intentionally a module-name cleanup, not just a package cleanup: `forkjoin` is a single upstream package/concept in both Akka and Scala.
- Update `settings.gradle.kts`, documentation inventory, instrumentation module names, and dependent Gradle project references.
- Keep `akka-actor` as a compatibility instrumentation alias for the Akka module.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:akka:akka-actor-forkjoin-2.5:javaagent:test :instrumentation:scala-forkjoin-2.8:javaagent:test :instrumentation:akka:akka-http-10.0:javaagent:compileTestJava
```

### PR 19: OpenTelemetry API package

Module:

- `opentelemetry-api-1.0`

Expected package change:

- `io.opentelemetry.javaagent.instrumentation.opentelemetryapi` -> `io.opentelemetry.javaagent.instrumentation.opentelemetry.api.v1_0`

Notes:

- Around 25 changed Java files.
- Package names may be more compatibility-sensitive or user-facing than pure internal helper modules; keep this as its own PR.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent:test
```

## Do Later

These are probably not the next easiest wins:

- `java-*` module skip: the current packages use names like `javahttpclient`, while the checker would expect `http.client` after eliding `java`. This touches 39 files and needs a naming decision.
- `jmx-metrics`: current packages are under `jmx`, while the module says `jmx-metrics`. This touches 43 files and may be user-facing enough to deserve a dedicated PR.
- Library-specific third-party packages: `io.grpc.override`, `io.lettuce.core.protocol`, `io.nats.client.impl`, and `rx` are likely intentional shims or package-private access points.
- Advice-native package exceptions: packages under `com.clickhouse`, `com.twitter`, `io.netty`, `reactor.netty`, `org.springframework`, and `io.vertx` should stay until each one is proven not to need native package placement.
- OpenTelemetry API and Akka/Scala forkjoin package/module renames are planned above; AWS SDK remains deferred.
- Other unversioned module allowlist entries: these need package/module naming decisions beyond a package-only cleanup.

## Re-Audit Command

After each PR, run:

```bash
.github/scripts/check-package-names.sh
```

Then look for remaining broad exceptions in `.github/scripts/check-package-names.sh` and prefer candidates where `rg --fixed-strings <current.package>` only reports package declarations and local imports.
