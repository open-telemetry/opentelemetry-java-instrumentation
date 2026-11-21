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
- Merge a pull request to the chosen development branch updating the `CHANGELOG.md`.
  - The heading for the unreleased entries should be `## Unreleased`.
  - Use `python3 .github/scripts/draft-release-notes/draft-release-notes.py`
    as the starting point for writing the change log. The default range starts after the highest patch
    of the preceding minor (or the highest full version of the preceding major for a major release).
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

- Confirm that the inherited
  [Immutable tags with maintainer bypass](https://github.com/open-telemetry/opentelemetry-java-instrumentation/rules/5576619)
  ruleset is active. It blocks tag updates and deletions. Maintainers can bypass the ruleset, but
  must not bypass it for release tags.
- Create and push the release tag from the head of the release branch:

  ```bash
  git fetch upstream release/vX.Y.x
  git tag vX.Y.Z upstream/release/vX.Y.x
  git push upstream vX.Y.Z
  ```

- Run the [Release workflow](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release.yml).
  - Dispatch the workflow from the command line because the GitHub Actions web interface only
    offers branches in its "Use workflow from" selector:

    ```bash
    gh workflow run release.yml \
      --repo open-telemetry/opentelemetry-java-instrumentation \
      --ref vX.Y.Z
    ```

  - This workflow will:
    - Publish the artifacts to Maven Central
    - Sign the Java agent jar and SBOM bundle with Sigstore Cosign
    - Generate [GitHub Artifact Attestations](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations) for the Java agent jar and SBOM bundle
    - Publish a GitHub release with release notes, the Java agent jar and SBOM bundle, and their
      Sigstore bundles
  - Review and merge the post-release pull request into `maintenance/vN.x` if it exists,
    otherwise into `main` with a matching snapshot major. Publication stops if the target is missing or mismatched.
    The PR always updates the changelog. Releases newer than the branch's API baseline also update
    the baseline and API diffs after reaching Maven Central, plus the README download link on maintenance branches.

## Verifying release artifacts

Install [Cosign](https://docs.sigstore.dev/cosign/system_config/installation/) and the
[GitHub CLI](https://cli.github.com/), then replace the version below with the release to verify:

```bash
VERSION=2.XX.0
REPOSITORY=open-telemetry/opentelemetry-java-instrumentation

gh release download "v${VERSION}" --repo "${REPOSITORY}" \
  --pattern opentelemetry-javaagent.jar \
  --pattern opentelemetry-javaagent.jar.sigstore.json \
  --pattern opentelemetry-java-instrumentation-SBOM.zip \
  --pattern opentelemetry-java-instrumentation-SBOM.zip.sigstore.json

CERTIFICATE_IDENTITY="https://github.com/${REPOSITORY}/.github/workflows/release.yml@refs/tags/v${VERSION}"
CERTIFICATE_OIDC_ISSUER=https://token.actions.githubusercontent.com

cosign verify-blob opentelemetry-javaagent.jar \
  --bundle opentelemetry-javaagent.jar.sigstore.json \
  --certificate-identity "${CERTIFICATE_IDENTITY}" \
  --certificate-oidc-issuer "${CERTIFICATE_OIDC_ISSUER}"

cosign verify-blob opentelemetry-java-instrumentation-SBOM.zip \
  --bundle opentelemetry-java-instrumentation-SBOM.zip.sigstore.json \
  --certificate-identity "${CERTIFICATE_IDENTITY}" \
  --certificate-oidc-issuer "${CERTIFICATE_OIDC_ISSUER}"
```

If verification fails, do not run, deploy, or otherwise use the artifact.

## Credentials

Same as the core repo, see [opentelemetry-java/RELEASING.md#credentials](https://github.com/open-telemetry/opentelemetry-java/blob/main/RELEASING.md#credentials).
