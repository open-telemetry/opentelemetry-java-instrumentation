# Repository settings

This document describes any changes that have been made to the
settings in this repository outside the settings tracked in the
private admin repo.

## Secrets and variables > Actions

### Repository secrets

- `FLAKY_TEST_REPORTER_ACCESS_KEY` - owned by [@laurit](https://github.com/laurit)
- `GPG_PASSWORD` - stored in OpenTelemetry-Java 1Password
- `GPG_PRIVATE_KEY` - stored in OpenTelemetry-Java 1Password
- `GRADLE_PUBLISH_KEY` - owned by [@trask](https://github.com/trask)
- `GRADLE_PUBLISH_SECRET` - owned by [@trask](https://github.com/trask)
- `NVD_API_KEY` - stored in OpenTelemetry-Java 1Password
  - Generated at <https://nvd.nist.gov/developers/request-an-api-key>
  - Key is associated with [@trask](https://github.com/trask)'s gmail address
- `SONATYPE_KEY` - owned by [@trask](https://github.com/trask)
- `SONATYPE_USER` - owned by [@trask](https://github.com/trask)

### Organization secrets

- `DEVELOCITY_ACCESS_KEY` (scoped only to Java repos)
- `FOSSA_API_KEY`
- `OTELBOT_JAVA_INSTRUMENTATION_PRIVATE_KEY` (scoped only to this repo)
- `OTELBOT_PRIVATE_KEY`

### Organization variables

- `OTELBOT_APP_ID`
- `OTELBOT_JAVA_INSTRUMENTATION_APP_ID` (scoped only to this repo)
