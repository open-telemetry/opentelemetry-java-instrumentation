# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/version.gradle.kts).

## Snapshot builds
Every successful CI build of the main branch automatically executes `./gradlew publishToSonatype`
as the last step, which publishes a snapshot build to
[Sonatype OSS snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/).

## Starting the Release

Before making the release:

* Close the release milestone if there is one
* Merge a PR to `main` updating the `CHANGELOG.md`
  * Use the script at `buildscripts/draft-change-log-entries.sh` to help create an initial draft.
    We typically only include end-user facing changes in the change log.
  * Specify the (estimated) release date (UTC)
* Run the [Prepare Release Branch workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-release-branch.yml).
* Review and merge the two PRs that it creates (one is targeted to the release branch and one is targeted to the `main` branch)

Open the [Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release.yml).

Press the "Run workflow" button, then select the release branch from the dropdown list,
e.g. `v1.9.x`, and click the "Run workflow" button below that.

This workflow will publish the artifacts to maven central and will publish a github release with the
javaagent jar attached and release notes based on the change log.

## Patch Release

All patch releases should include only bug-fixes, and must avoid adding/modifying the public APIs.

In general, patch releases are only made for bug-fixes for the following types of issues:
* Regressions
* Memory leaks
* Deadlocks

Before making the release:

* Merge PR(s) containing the desired patches to the release branch
* Merge a PR to the release branch updating the `CHANGELOG.md`
* Run the [Prepare Patch Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-patch-release.yml).
  * Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `v1.9.x`, and click the "Run workflow" button below that.
* Review and merge the PR that it creates

Open the [Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release.yml).

Press the "Run workflow" button, then select the release branch from the dropdown list,
e.g. `v1.9.x`, and click the "Run workflow" button below that.

This workflow will publish the artifacts to maven central and will publish a github release with the
javaagent jar attached and release notes based on the change log.

After making the release:

* Merge a PR to `main` with the change log updates from the patch release
