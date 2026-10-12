# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](version.gradle.kts).

## Snapshot builds

The daily build workflow on the default branch executes `./gradlew publishToSonatype`,
which publishes a snapshot build to
[Sonatype snapshot repository](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/opentelemetry/).
To publish a 2.x snapshot, manually run the
[Build (daily) workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/build-daily.yml)
with `2.x` selected.

## Release cadence

This repository roughly targets monthly minor releases from the `main` branch on the Wednesday after
the second Monday of the month (roughly a few days after the monthly minor release of
[opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java)).

## Preparing a new major or minor release

Choose the development branch: `main` for 3.x development, or `2.x` for 2.x development.
Minor releases must preserve the stable compatibility contracts in [VERSIONING.md](VERSIONING.md).

- Check that
  [renovate has run](https://developer.mend.io/github/open-telemetry/opentelemetry-java-instrumentation)
  sometime in the past day (this link is only accessible if you have write access to the
  repository), and check that the applicable
  [renovate PRs](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pulls/app%2Frenovate)
  targeting the chosen development branch have been merged.
- For 2.x, backport the selected compatible changes and SDK upgrades to `2.x` using the
  [Backport workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/backport.yml).
- Close the [release milestone](https://github.com/open-telemetry/opentelemetry-java-instrumentation/milestones)
  if there is one.
- Merge a pull request to the chosen development branch updating the `CHANGELOG.md`.
  - The heading for the unreleased entries should be `## Unreleased`.
  - Use `python3 .github/scripts/draft-release-notes/draft-release-notes.py`
    as the starting point for writing the change log.
- Run the [Prepare release branch workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-release-branch.yml).
  - Press the "Run workflow" button, and select the chosen development branch.
  - Review and merge the two pull requests that it creates
    (one is targeted to the release branch and one advances the snapshot version on the development branch).

## Preparing a new patch release

All patch releases should include only bug-fixes, and must avoid adding/modifying the public APIs.

In general, patch releases are only made for regressions, security vulnerabilities, memory leaks
and deadlocks.

- Backport pull request(s) to the release branch.
  - Run the [Backport workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/backport.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, then enter the pull request number that you want to backport,
    then click the "Run workflow" button below that.
  - Review and merge the backport pull request that it generates.
  - Note: if the PR contains any changes to workflow files, it will have to be manually backported,
    because the default `GITHUB_TOKEN` does not have permission to update workflow files (and the
    `otelbot` token doesn't have write permission to this repository at all, so while it
    can be used to open a PR, it can't be used to push to a local branch).
- Merge a pull request to the release branch updating the `CHANGELOG.md`.
  - The heading for the unreleased entries should be `## Unreleased`.
- Run the [Prepare patch release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-patch-release.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
  - Review and merge the pull request that it creates for updating the version.

## Making the release

- Run the [Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
  - This workflow will:
    - Publish the artifacts to Maven Central
    - Generate [GitHub Artifact Attestations](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations) for the Java agent jar and SBOM bundle
    - Publish a GitHub release with release notes, Java agent jar, and SBOM bundle
  - Review and merge the post-release pull request against the development branch.
    It updates the apidiff baseline and synchronizes the change log after the release is available in Maven Central.
    For 2.x, it also updates the README download link to the released 2.x version.
    Releases from `release/v2.N.x` update `2.x`; releases from the main development line update `main`.

## Credentials

Same as the core repo, see [opentelemetry-java/RELEASING.md#credentials](https://github.com/open-telemetry/opentelemetry-java/blob/main/RELEASING.md#credentials).
