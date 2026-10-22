# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

The version is specified in [version.gradle.kts](version.gradle.kts).

## Development and release branches

| Branch | Purpose |
| --- | --- |
| `main` | Primary development line |
| `maintenance/v<major>.x` | Further compatible minor releases of a supported older major |
| `release/v<major>.<minor>.x` | Preparation and publication of a minor release and its patches |

For example, `maintenance/v2.x` can prepare `release/v2.33.x` while `main` develops a newer major.
Before activating another maintenance line, configure its branch protections, protected-environment
access, and explicit Renovate base branch, then initialize it from a reviewed commit of that major.
The snapshot version's major must match the maintenance branch name.

## Snapshot builds

The daily build workflow on the default branch executes `./gradlew publishToSonatype`,
which publishes a snapshot build to
[Sonatype snapshot repository](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/io/opentelemetry/).
To publish a maintenance snapshot, manually run the
[Build (daily) workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/build-daily.yml)
with the maintenance branch selected, for example `maintenance/v2.x`.

## Release cadence

This repository roughly targets monthly minor releases from the `main` branch on the Wednesday after
the second Monday of the month (roughly a few days after the monthly minor release of
[opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java)).

## Preparing a new major or minor release

Choose the development branch: `main` for the current major, or `maintenance/v<major>.x`
for a supported maintenance major.

- Check that
  [renovate has run](https://developer.mend.io/github/open-telemetry/opentelemetry-java-instrumentation)
  sometime in the past day (this link is only accessible if you have write access to the
  repository), and check that the applicable
  [renovate PRs](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pulls/app%2Frenovate)
  targeting the chosen development branch have been merged.
- For a maintenance release, backport the selected compatible changes and SDK upgrades to its development branch using the
  [Backport workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/backport.yml).
- Close the [release milestone](https://github.com/open-telemetry/opentelemetry-java-instrumentation/milestones)
  if there is one.
- Run the [Prepare release branch workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-release-branch.yml).
  - Press the "Run workflow" button, and select the chosen development branch.
  - Review and merge the two pull requests that it creates
    (one is targeted to the release branch and one advances the snapshot version on the development branch).
  - Both pull requests generate the changelog and remove the fragments included in
    that release. New fragments merged to the development branch afterwards remain pending.

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
- Run the [Prepare patch release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/prepare-patch-release.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
  - Review and merge the pull request that it creates for updating the version.
  - It also generates the patch changelog and removes the consumed fragments.
  - Release branches cut before the Towncrier adoption (those without `towncrier.toml` and the
    `<!-- towncrier release notes start -->` marker in `CHANGELOG.md`) still use the legacy
    workflow: backport pull requests must update `CHANGELOG.md` manually, and `changelog.d`
    fragments are ignored on those branches. The patch preparation workflow skips Towncrier
    when `towncrier.toml` is absent and uses the branch's legacy changelog updater to replace
    the `## Unreleased` heading with the release version, date, and standard SDK/alpha preamble.

## Making the release

- Run the [Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v1.9.x`, and click the "Run workflow" button below that.
  - This workflow will:
    - Publish the artifacts to Maven Central
    - Generate [GitHub Artifact Attestations](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations) for the Java agent jar and SBOM bundle
    - Publish a GitHub release with release notes, Java agent jar, and SBOM bundle
  - Review and merge the post-release pull request into `maintenance/vN.x` if it exists,
    otherwise into `main` with a matching snapshot major. Publication stops if the target is missing or mismatched.
    The PR always updates the changelog. Releases newer than the branch's API baseline also update
    the baseline and API diffs after reaching Maven Central, plus the README download link on maintenance branches.

## Credentials

Same as the core repo, see [opentelemetry-java/RELEASING.md#credentials](https://github.com/open-telemetry/opentelemetry-java/blob/main/RELEASING.md#credentials).
