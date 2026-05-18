# Package Name Exceptions Cleanup Plan

Generated on 2026-05-11 from the remaining exceptions in `.github/scripts/check-package-names.sh`.
Updated on 2026-05-13 after PRs 5-8 merged and their historical javaagent exceptions were removed from the checker.
Updated again on 2026-05-13 after PRs 9-12 were implemented and opened as draft PRs.
Updated on 2026-05-14 after PRs 9-12 merged and their historical javaagent exceptions were removed from the checker.
Updated again on 2026-05-14 to plan faster batches: up to 20 changed files per PR, still four PRs per batch.
Updated again on 2026-05-14 to bump the target to up to 40 changed files per PR and plan all remaining historical package renames.
Updated on 2026-05-17 after PRs 15-16 merged, and after deciding to handle Akka/Scala forkjoin as a module-name cleanup instead of a package-only cleanup.
Updated on 2026-05-18 after PRs 13, 18, 20, and 21 merged and their historical package exceptions were removed from the checker.
Updated again on 2026-05-18 after deciding that leading `java` is meaningful for JDK instrumentation packages.
Updated again on 2026-05-18 after applying #16090's `*-common` module naming convention and planning PRs 23-26.
Updated on 2026-05-19 after PRs 23-24 merged and their unversioned common-module allowances were removed from the checker.
Updated again on 2026-05-19 after deciding app-server/framework module names for Payara, Quarkus RESTEasy Reactive, and Tomcat JDBC.
Updated on 2026-05-22 after documenting how patch-level base versions map to module names.
Updated again on 2026-05-22 after PR 25 merged, Java util logging PR 22 was closed, and app-server/framework PR 27 was split into Payara and Quarkus/Tomcat PRs.

## Goal

Continue reducing package-name checker exceptions in reviewable PRs. Prefer changes where the current package name is only a historical abbreviation and the rename has a small blast radius. Use up to about 40 changed files per package-cleanup PR, while still submitting only four PRs at a time.

## Quick Audit Summary

The remaining exceptions fall into four buckets:

| Bucket | Current signal | Recommendation |
| --- | --- | --- |
| Module-wide skips | `jmx-metrics` has 43 files | Do not start here; this needs a broader naming decision. |
| Library packages under third-party namespaces | `grpc`, `lettuce`, `nats`, `rxjava` each have 1 file | Leave for later; these are likely compatibility/shim packages. |
| Javaagent advice under instrumented library namespaces | mostly 1 file each | Leave for later; the script already says these must live in the instrumented library namespace. |
| Historical javaagent packages | many one-dir modules remain, usually with one source directory | Best place to keep chipping away. |

## Completed Cleanups

These PRs have merged. Remove their historical javaagent package-name exceptions from `.github/scripts/check-package-names.sh` on `next` after the corresponding cleanup lands upstream:

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
- PR 23: `jetty-common` -> `jetty-common-8.0` and `tomcat-common` -> `tomcat-common-7.0`.
- PR 24: `opensearch-rest-common` -> `opensearch-rest-common-1.0`.
- PR 25: `spring-webmvc-common` -> `spring-webmvc-common-3.1`.

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

PRs 23-24 merged upstream as:

- #18786: `jetty-common` -> `jetty-common-8.0` and `tomcat-common` -> `tomcat-common-7.0`.
- #18787: `opensearch-rest-common` -> `opensearch-rest-common-1.0`.

PR 25 merged upstream as:

- #18788: `spring-webmvc-common` -> `spring-webmvc-common-3.1`.

`external-annotations` still remains in the unversioned-module allowlist as `javaagent:external-annotations`; that is a separate module-name exception, not a historical package exception.
`spring-boot-resources` keeps a narrow deprecated compatibility-package exception for `io.opentelemetry.instrumentation.spring.resources`; the replacement javaagent package is already present under `io.opentelemetry.javaagent.instrumentation.spring.boot.resources`.

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

PR 14 is open as #18747, PR 17 is open as #18772, Payara is open as #18835, and Quarkus/Tomcat JDBC is open as #18838. PR 22 (#18784) was closed without merging. Keep `.github/scripts/check-package-names.sh` and checker exception removals on `next` until cleanup PRs merge.

For JDK instrumentation modules, keep the leading `java` token in package paths. For example,
`java-util-logging` maps to `io.opentelemetry.javaagent.instrumentation.java.util.logging`, while
embedded `java` tokens in third-party library names are still elided, e.g. `graphql-java-20.0` maps
to `graphql.v20_0`.

When the actual minimum supported library version is a patch release, use the containing minor
version in the module and package name unless the patch is a meaningful compatibility boundary. For
example, `payara-embedded-web:5.2020.2` maps to `payara-5.2020` and package suffix `v5_2020`, not
`payara-5.2020.2` / `v5_2020_2`, because there is no known compatibility split inside the
`5.2020.x` line. Users should be on the latest patch for a given minor line, and patch-level suffixes
would create noisy module names unless they identify a real boundary such as a muzzle range or
sibling module split.

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

### PR 22: Java util logging package (closed #18784, deferred)

Module:

- `java-util-logging`

Expected package change:

- `io.opentelemetry.javaagent.instrumentation.jul` -> `io.opentelemetry.javaagent.instrumentation.java.util.logging`

Notes:

- Javaagent-only package move.
- Keep the module name and instrumentation name `java-util-logging`.
- #18784 was closed without merging. Revisit later if this still looks worth doing after the current module-name cleanups land.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:java-util-logging:javaagent:test
```

### PR 25: Spring WebMVC common module name (merged #18788)

Module:

- `spring-webmvc-common` -> `spring-webmvc-common-3.1`

Expected package change:

- `io.opentelemetry.javaagent.instrumentation.spring.webmvc.common` -> `io.opentelemetry.javaagent.instrumentation.spring.webmvc.common.v3_1`

Notes:

- This is a #16090 common-module convention cleanup. The common javaagent module compiles against `org.springframework:spring-webmvc:3.1.0.RELEASE` and is shared by `spring-webmvc-3.1` and `spring-webmvc-6.0`.
- Estimated changed files: about 27 in the PR branch, excluding the later checker update on `next`.
- Update `settings.gradle.kts`, dependent Gradle project references, package declarations, and imports.
- Keep the `testing` project path with the module rename, but treat testing package changes under `spring.webmvc.boot` and `spring.webmvc.filter` as a separate public testing API decision unless the audit shows they are private-only.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:spring:spring-webmvc:spring-webmvc-common-3.1:javaagent:test :instrumentation:spring:spring-webmvc:spring-webmvc-3.1:javaagent:test :instrumentation:spring:spring-webmvc:spring-webmvc-6.0:javaagent:test
```

### PR 28: Spring WebMVC testing package alignment

Modules:

- `spring-webmvc-common-3.1` testing packages

Expected package decision:

- Move Spring WebMVC common testing helpers to include the `v3_1` package segment that now matches the common module name.
- Preserve public testing API compatibility if these helpers are consumed outside the repository; otherwise treat this as the follow-up requested in #18788's review.

Notes:

- This is a follow-up to #18788's review comment asking whether the testing module should add the `v3_1` directory too.
- Audit testing helpers currently under `io.opentelemetry.instrumentation.spring.webmvc.boot` and `io.opentelemetry.instrumentation.spring.webmvc.filter`; decide the exact target packages from their module-local usage before moving files.
- Keep this separate from the already-merged javaagent module rename so the public/private testing API decision is reviewable on its own.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:spring:spring-webmvc:spring-webmvc-common-3.1:javaagent:test :instrumentation:spring:spring-webmvc:spring-webmvc-3.1:javaagent:compileTestJava :instrumentation:spring:spring-webmvc:spring-webmvc-6.0:javaagent:compileTestJava
```

### PR 26: Spring Cloud Gateway common testing package decision

Module:

- `spring-cloud-gateway-common`

Expected package decision:

- Keep `spring-cloud-gateway-common` unversioned for the javaagent helper unless a deeper audit finds a direct Spring Cloud Gateway API dependency.
- Decide whether testing packages should move from `io.opentelemetry.instrumentation.spring.gateway.common` to `io.opentelemetry.instrumentation.spring.cloud.gateway.common`.

Notes:

- The javaagent helper has no direct Spring Cloud Gateway compile dependency and is shared by WebFlux/WebMVC instrumentations, so #16090 does not obviously require a versioned module name.
- Estimated changed files: about 7 if this is only the testing package rename; more if the audit expands scope beyond testing packages.
- The likely cleanup is only the testing package name, and this may be public testing API. Keep it separate from the module-name PRs above.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew :instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:javaagent:test :instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-2.0:javaagent:test :instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-webmvc-4.3:javaagent:test
```

### PR 27a: Payara module name (open #18835)

Modules:

- `payara` -> `payara-5.2020`

Expected package change:

- `io.opentelemetry.javaagent.instrumentation.payara` -> `io.opentelemetry.javaagent.instrumentation.payara.v5_2020`

Notes:

- This module-name cleanup is open as #18835.
- Use `payara-5.2020` because `5.2020.2` is the earliest `5.2020.x` release, the javaagent builds against it, and it contains both `fish.payara.opentracing.OpenTracingService` and `org.apache.catalina.core.StandardWrapper`. Per the patch-floor rule above, use the minor line in the module/package name instead of `payara-5.2020.2`.
- Keep `payara` as the main instrumentation name and add `payara-5.2020` as the versioned alias.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew generateFossaConfiguration :instrumentation:payara-5.2020:javaagent:test
```

### PR 27b: Quarkus RESTEasy Reactive and Tomcat JDBC module names (open #18838)

Modules:

- `quarkus-resteasy-reactive` -> `quarkus-resteasy-reactive-1.11`
- `tomcat-jdbc` -> `tomcat-jdbc-8.5`

Expected package changes:

- `io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive` -> `io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive.v1_11`
- `io.opentelemetry.javaagent.instrumentation.tomcat.jdbc` -> `io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5`

Notes:

- This module-name cleanup is open as #18838.
- Keep existing instrumentation names as compatibility aliases. For Quarkus RESTEasy Reactive, deprecate the old `quarkus-resteasy-reactive-3.0` suppression key using the repo's `expandDeprecatedNames(...)` convention, and keep the muzzle split across `io.quarkus:quarkus-resteasy-reactive:(,3.9.0)` and `io.quarkus:quarkus-rest:[3.9.0,)`; `3.9` is the artifact rename boundary, not the module's minimum supported version.
- Use `quarkus-resteasy-reactive-1.11` because the artifact starts at `1.11.0` prereleases, the first final is `1.11.0.Final`, the javaagent compiles against `1.11.0.Final`, and the original muzzle range covered all versions of the old artifact.
- Use `tomcat-jdbc-8.5` because the javaagent compiles/tests against `org.apache.tomcat:tomcat-jdbc:8.5.0` and docs list support from `[8.5.0,)`.

Suggested verification:

```bash
.github/scripts/check-package-names.sh
./gradlew generateFossaConfiguration :instrumentation:quarkus-resteasy-reactive-1.11:javaagent:test :instrumentation:quarkus-resteasy-reactive-1.11:quarkus-2.0-testing:compileTestJava :instrumentation:quarkus-resteasy-reactive-1.11:quarkus-3.0-testing:compileTestJava :instrumentation:quarkus-resteasy-reactive-1.11:quarkus-3.9-testing:compileTestJava :instrumentation:tomcat:tomcat-jdbc-8.5:javaagent:test :instrumentation:tomcat:tomcat-jdbc-8.5:javaagent:testStableSemconv
```

## Do Later

These are probably not the next easiest wins:

- `java-http-client` and `java-http-server`: these have published library/testing packages, so package renames need a dedicated public API decision instead of a package-only javaagent cleanup.
- `jmx-metrics`: current packages are under `jmx`, while the module says `jmx-metrics`. This touches 43 files and may be user-facing enough to deserve a dedicated PR.
- Library-specific third-party packages: `io.grpc.override`, `io.lettuce.core.protocol`, `io.nats.client.impl`, and `rx` are likely intentional shims or package-private access points.
- Advice-native package exceptions: packages under `com.clickhouse`, `com.twitter`, `io.netty`, `reactor.netty`, `org.springframework`, and `io.vertx` should stay until each one is proven not to need native package placement.
- OpenTelemetry API and Akka/Scala forkjoin package/module renames are planned above; AWS SDK remains deferred.
- Remaining unversioned module allowlist entries split into policy buckets:
  - JDK/platform modules such as `executors`, `http-url-connection`, `jdbc`, `methods`, `rmi`, and `runtime-telemetry` probably deserve explicit checker allowances instead of version suffixes.
  - `internal-*` modules such as `internal-class-loader`, `internal-lambda`, `internal-reflection`, and `internal-url-class-loader` probably deserve explicit checker allowances; version suffixes would be misleading.
  - `*-common` modules need case-by-case module naming review:
    - Apply #16090's convention: `<library>-common` is for pure utility/abstraction code with no direct library version dependency, `<library>-common-<major.minor>` is for shared code that requires a minimum library version, and `<library>-common-<variant>` is for variants such as `javax`.
    - `spring-webmvc-common` was completed in PR 25, its testing package follow-up is planned above as PR 28, and the `spring-cloud-gateway-common` testing package decision is planned above as PR 26. `jetty-common`, `tomcat-common`, and `opensearch-rest-common` were completed in PRs 23-24.
    - `jaxrs-common`: keep unversioned. The javaagent module has no direct JAX-RS API dependency and acts as cross-generation helper/bootstrap code used by JAX-RS 1.0, 2.0, 3.0, and Quarkus RESTEasy Reactive. Keep it separate from the already version-scoped `jaxrs-2.0-common`, `jaxrs-3.0-common`, `jaxrs-common-2.0`, and `jaxrs-common-3.0` modules.
    - `servlet-common`: keep unversioned. This matches #16090's pure abstraction/variant shape: shared code for both `javax.servlet` and `jakarta.servlet`, with `servlet-common-javax` as the variant-specific module. Because it includes published `library` packages, treat any future package changes as public API policy, not package-only cleanup.
  - App-server/framework module-name cleanups for `payara`, `quarkus-resteasy-reactive`, and `tomcat-jdbc` are open above as PRs 27a-27b.
  - Treat this as a checker-policy cleanup first: document legitimate unversioned javaagent module shapes, then only rename leftovers that are true module-name debt.

## Re-Audit Command

After each PR, run:

```bash
.github/scripts/check-package-names.sh
```

Then look for remaining broad exceptions in `.github/scripts/check-package-names.sh` and prefer candidates where `rg --fixed-strings <current.package>` only reports package declarations and local imports.
