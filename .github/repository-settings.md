# Repository settings

This document describes any changes that have been made to the
settings in this repository outside the settings tracked in the
private admin repo.

## Environments

### `protected` environment

Deployment branches: `main`, `release/*`

Secrets:

- `COPILOT_GITHUB_TOKEN` - owned by [@trask](https://github.com/trask)
- `GPG_PASSWORD` - stored in OpenTelemetry-Java 1Password
- `GPG_PRIVATE_KEY` - stored in OpenTelemetry-Java 1Password
- `GRADLE_PUBLISH_KEY` - owned by [@trask](https://github.com/trask)
- `GRADLE_PUBLISH_SECRET` - owned by [@trask](https://github.com/trask)
- `SONATYPE_GUIDE_PAT` - owned by [@trask](https://github.com/trask)
- `SONATYPE_KEY` - owned by [@trask](https://github.com/trask)
- `SONATYPE_USER` - owned by [@trask](https://github.com/trask)

To enable pending reviews from **Manual PR review**, add `PR_REVIEW_POST_TOKEN` to this
environment. Use a fine-grained personal access token scoped to this repository with
**Pull requests: Read and write**. The token must belong to the maintainer who dispatches
the workflow. Only dispatches from `main` can post. The model calls and the public
`No findings.` comment use the workflow token instead.

## Secrets and variables > Actions

### Organization secrets

- `DEVELOCITY_ACCESS_KEY` (scoped only to Java repos)
- `FOSSA_API_KEY`
- `OTELBOT_JAVA_INSTRUMENTATION_PRIVATE_KEY` (scoped only to this repo)
- `OTELBOT_PRIVATE_KEY`

### Organization variables

- `OTELBOT_APP_ID`
- `OTELBOT_JAVA_INSTRUMENTATION_APP_ID` (scoped only to this repo)
