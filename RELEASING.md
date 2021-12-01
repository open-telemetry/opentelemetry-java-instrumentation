# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/version.gradle.kts).

## Snapshot builds
Every successful CI build of the main branch automatically executes `./gradlew publishToSonatype`
as the last step, which publishes a snapshot build to
[Sonatype OSS snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/).

## Starting the Release

Before making the release:

* Merge a PR to `main` updating the `CHANGELOG.md`
* Create a release branch, e.g. `v1.9.x`
  ```
  git checkout -b v1.9.x upstream/main
  git push upstream v1.9.x
  ```
* Merge a PR to the release branch with the following changes
  * Remove `-SNAPSHOT` from the version in these files:
    * version.gradle.kts
    * examples/distro/build.gradle
    * examples/extension/build.gradle
  * Bump the version in the download link in the root `README.md` file

Open the release build workflow in your browser [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release-build.yml).

You will see a button that says "Run workflow". Press the button, then enter the release branch
to use (e.g. `v1.9.x`) and the version of the release (e.g. `1.9.0`). Yes there is redundancy
between these two inputs that we plan to address.

You will see a button that says "Run workflow". Press the button, enter the release branch to use
and the version of the release.

This triggers the release process, which builds the artifacts, publishes the artifacts, and creates
and pushes a git tag with the version number.

After making the release:

* Merge a PR to `main` with the following changes
  * Bump version in these files to the next `-SNAPSHOT` version (e.g. from `1.9.0-SNAPSHOT` to `1.10.0-SNAPSHOT`)
    * version.gradle.kts
    * examples/distro/build.gradle
    * examples/extension/build.gradle
  * Bump the version in the download link in the root `README.md` file

## Announcement

Once the GitHub workflow completes, go to Github [release
page](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases), press
`Draft a new release` to write release notes about the new release. If there is already a draft
release notes, just point it at the created tag.

## Patch Release

All patch releases should include only bug-fixes, and must avoid
adding/modifying the public APIs.

In general, patch releases are only made for bug-fixes for the following types of issues:
* Regressions
* Memory leaks
* Deadlocks

Before making the release:

* Merge PR(s) containing the desired patches to the release branch
* Merge a PR to the release branch updating the `CHANGELOG.md`
* Merge a PR to the release branch updating the version in these files:
  * version.gradle.kts
  * examples/distro/build.gradle
  * examples/extension/build.gradle

To make a patch release, open the patch release build workflow in your browser
[here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/patch-release-build.yml).

You will see a button that says "Run workflow". Press the button, then enter the release branch
to use (e.g. `v1.9.x`) and the version of the release (e.g. `1.9.1`). Yes there is redundancy
between these two inputs that we plan to address.
