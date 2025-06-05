# Repository settings

This document describes any changes that have been made to the
settings for this repository beyond the [OpenTelemetry default repository
settings](https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md#repository-settings).

## General > Pull Requests

- Allow squash merging > Default to pull request title

- Allow auto-merge

## Actions > General

- Fork pull request workflows from outside collaborators:
  "Require approval for first-time contributors who are new to GitHub"

  (To reduce friction for new contributors,
  as the default is "Require approval for first-time contributors")

- Workflow permissions
  - Default permissions granted to the `GITHUB_TOKEN` when running workflows in this repository:
    Read repository contents and packages permissions
  - Allow GitHub Actions to create and approve pull requests: UNCHECKED

## Branch protections

The order of branch protection rules
[can be important](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/managing-a-branch-protection-rule#about-branch-protection-rules).
The branch protection rules below should be added before the `**/**` branch protection rule
(this may require deleting the `**/**` rule and recreating it at the end).

### `main`

- Require branches to be up to date before merging: UNCHECKED

  (PR jobs take too long, and leaving this unchecked has not been a significant problem)

- Status checks that are required:

  - EasyCLA
  - required-status-check
  - gradle-wrapper-validation
  - CodeQL

### `release/*`

Same settings as above for [`main`](#main).

### `v0.*` and `v1.*` (old-style release branches)

- Lock branch: CHECKED

- Do not allow bypassing the above settings: CHECKED

### `cloudfoundry`

Same settings as above for [`main`](#main),
except for the `required-status-check` required status check.

### `renovate/**/*` and `otelbot/**/*`

Same settings as
for [`dependabot/**/*`](https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md#branch-protection-rule-dependabot)

### `gh-pages`

- Everything UNCHECKED

  (This branch is currently only used for directly pushing benchmarking results from the
  [Nightly overhead benchmark](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/nightly-benchmark-overhead.yml)
  job)

## Code security and analysis

- Secret scanning: Enabled

## Secrets and variables > Actions

### Repository secrets

- `GPG_PASSWORD` - stored in OpenTelemetry-Java 1Password
- `GPG_PRIVATE_KEY` - stored in OpenTelemetry-Java 1Password
- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`
- `NVD_API_KEY` - stored in OpenTelemetry-Java 1Password
  - Generated at https://nvd.nist.gov/developers/request-an-api-key
  - Key is associated with [@trask](https://github.com/trask)'s gmail address
- `SONATYPE_KEY` - owned by [@trask](https://github.com/trask)
- `SONATYPE_USER` - owned by [@trask](https://github.com/trask)
- `FLAKY_TEST_REPORTER_ACCESS_KEY` - owned by [@laurit](https://github.com/laurit)

### Organization secrets

- `FOSSA_API_KEY`
- `OTELBOT_PRIVATE_KEY`
- `OTELBOT_JAVA_INSTRUMENTATION_PRIVATE_KEY`

### Organization variables

- `OTELBOT_APP_ID`
