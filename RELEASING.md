# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](version.gradle.kts).

## Snapshot builds

Every successful CI build of the main branch automatically executes `./gradlew publishToSonatype`
as the last step, which publishes a snapshot build to
[Sonatype OSS snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/).

## Release cadence

This repository roughly targets monthly minor releases from the `main` branch on the Wednesday after
the second Monday of the month (roughly a few of days after the monthly minor release of
[opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java)).

## Preparing a new major or minor release

- Check that
  [renovate has run](https://developer.mend.io/github/open-telemetry/opentelemetry-java-instrumentation)
  sometime in the past day (this link is only accessible if you have write access to the
  repository), and check that all
  [renovate PRs](https://github.com/open-telemetry/opentelemetry-java-contrib/pulls/app%2Frenovate)
  have been merged.
- Close the [release milestone](https://github.com/open-telemetry/opentelemetry-java-instrumentation/milestones)
  if there is one.
- Merge a pull request to `main` updating the `CHANGELOG.md`.
  - The heading for the unreleased entries should be `## Unreleased`.
  - Use `.github/scripts/draft-change-log-entries.sh` as a starting point for writing the change log.
- Run the [Prepare release branch workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-release-branch.yml).
  - Press the "Run workflow" button, and leave the default branch `main` selected.
  - Review and merge the two pull requests that it creates
    (one is targeted to the release branch and one is targeted to `main`).

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
    `opentelemetrybot` token doesn't have write permission to this repository at all, so while it
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
  - This workflow will publish the artifacts to maven central and will publish a GitHub release
    with release notes based on the change log and with the javaagent jar attached.
  - Review and merge the pull request that it creates for updating the change log in main
    (note that if this is not a patch release then the change log on main may already be up-to-date,
    in which case no pull request will be created).

## Update release versions in documentations

After releasing is done, you need to first update the docs. This needs to happen after artifacts have propagated
to Maven Central so should probably be done an hour or two after the release workflow finishes.

```sh
./gradlew japicmp -PapiBaseVersion=a.b.c -PapiNewVersion=x.y.z
./gradlew --refresh-dependencies japicmp
```

Where `x.y.z` is the version just released and `a.b.c` is the previous version.

Create a PR to mark the new release in docs on the main branch.

## Credentials

Same as the core repo, see [opentelemetry-java/RELEASING.md#credentials](https://github.com/open-telemetry/opentelemetry-java/blob/main/RELEASING.md#credentials).
