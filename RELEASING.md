# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](version.gradle.kts).

## Snapshot builds

Every successful CI build of the main branch automatically executes `./gradlew publishToSonatype`
as the last step, which publishes a snapshot build to
[Sonatype OSS snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/).

## Preparing a new major or minor release

* Close the release milestone if there is one.
* Merge a pull request to `main` updating the `CHANGELOG.md`.
  * The heading for the release should include the release version but not the release date, e.g.
  `## Version 1.9.0 (unreleased)`.
* Run the [Prepare release branch workflow](.github/workflows/prepare-release-branch.yml).
* Review and merge the two pull requests that it creates
  (one is targeted to the release branch and one is targeted to the `main` branch).

## Preparing a new patch release

All patch releases should include only bug-fixes, and must avoid adding/modifying the public APIs.

In general, patch releases are only made for regressions, security vulnerabilities, memory leaks
and deadlocks.

* Backport pull request(s) to the release branch.
  * Run the [Backport workflow](.github/workflows/backport.yml).
  * Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, then enter the pull request number that you want to backport,
    then click the "Run workflow" button below that.
  * Review and merge the backport pull request that it generates.
* Merge a pull request to the release branch updating the `CHANGELOG.md`.
  * The heading for the release should include the release version but not the release date, e.g.
  `## Version 1.9.0 (unreleased)`.
* Run the [Prepare patch release workflow](.github/workflows/prepare-patch-release.yml).
  * Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
* Review and merge the pull request that it creates.

## Making the release

Run the [Release workflow](.github/workflows/release.yml).

* Press the "Run workflow" button, then select the release branch from the dropdown list,
  e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
* This workflow will publish the artifacts to maven central and will publish a GitHub release
  with release notes based on the change log and with the javaagent jar attached.
* Review and merge the pull request that the release workflow creates against the release branch
  which adds the release date to the change log.

## After the release

Run the [Merge change log to main workflow](.github/workflows/merge-change-log-to-main.yml).

* Press the "Run workflow" button, then select the release branch from the dropdown list,
  e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
* This will create a pull request that merges the change log updates from the release branch
  back to main.
* Review and merge the pull request that it creates.
* This workflow will fail if there have been conflicting change log updates introduced in main,
  in which case you will need to merge the change log updates manually and send your own pull
  request against main.
