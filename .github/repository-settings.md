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
- `SONATYPE_KEY` - owned by [@trask](https://github.com/trask)
- `SONATYPE_OSS_INDEX_PASSWORD` - owned by [@trask](https://github.com/trask)
- `SONATYPE_OSS_INDEX_USER` - owned by [@trask](https://github.com/trask)
- `SONATYPE_USER` - owned by [@trask](https://github.com/trask)

## Secrets and variables > Actions

### Repository secrets

- `FLAKY_TEST_REPORTER_ACCESS_KEY` - owned by [@laurit](https://github.com/laurit)

### Organization secrets

- `DEVELOCITY_ACCESS_KEY` (scoped only to Java repos)
- `FOSSA_API_KEY`
- `OTELBOT_JAVA_INSTRUMENTATION_PRIVATE_KEY` (scoped only to this repo)
- `OTELBOT_PRIVATE_KEY`

### Organization variables

- `OTELBOT_APP_ID`
- `OTELBOT_JAVA_INSTRUMENTATION_APP_ID` (scoped only to this repo)
