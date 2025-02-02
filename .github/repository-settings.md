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

## Rules > Rulesets

### `main` and release branches

- Targeted branches:
  - `main`
  - `release/*`
  - `v0.*`
  - `v1.*`
- Branch rules
  - Restrict deletions: CHECKED
  - Require linear history: CHECKED
  - Require a pull request before merging: CHECKED
    - Required approvals: 1
    - Require review from Code Owners: CHECKED
    - Allowed merge methods: Squash
  - Require status checks to pass
    - EasyCLA
    - `required-status-check`
  - Block force pushes: CHECKED

### `cloudfoundry` branch

- Targeted branches:
  - `cloudfoundry`
- Branch rules
  - Restrict deletions: CHECKED
  - Require linear history: CHECKED
  - Require a pull request before merging: CHECKED
    - Required approvals: 1
    - Require review from Code Owners: CHECKED
    - Allowed merge methods: Squash
  - Require status checks to pass
    - EasyCLA
  - Block force pushes: CHECKED

### `gh-pages` branch

- Targeted branches:
  - `gh-pages`
- Branch rules
  - Restrict deletions: CHECKED
  - Require linear history: CHECKED
  - Block force pushes: CHECKED

### Restrict branch creation

- Targeted branches
  - Exclude:
    - `release/*`
    - `renovate/**/**`
    - `opentelemetrybot/**/**`
- Restrict creations: CHECKED

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

- `OPENTELEMETRYBOT_GITHUB_TOKEN`
- `OTELBOT_CLIENT_ID`
- `OTELBOT_PRIVATE_KEY`
