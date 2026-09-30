# OSHI process metric retirement

Implemented the v3-preview retirement of automatic OSHI process metrics while preserving all system metrics and explicit library registration. Deprecated the published `ProcessMetrics` API and experimental setting for 3.0 removal, added the startup warning outside preview, updated the mode-matrix tests, and documented migration considerations.

## Validation

- `./gradlew :instrumentation:oshi-5.0:javaagent:spotlessApply :instrumentation:oshi-5.0:library:spotlessApply :instrumentation:oshi-5.0:testing:spotlessApply` passed with Java 25.
- The baseline OSHI matrix passed: `javaagent:test`, `javaagent:testExperimental`, `javaagent:testV3Preview`, `javaagent:testV3PreviewExperimental`, `library:test`, and `library:testV3Preview`.
- `./gradlew :instrumentation-docs:test --tests '*DeclarativeConfigValidationTest'` passed.
- `./gradlew :instrumentation-docs:runAnalysis` passed; only the deprecated OSHI setting was removed from `docs/declarative-configuration-example.yaml`, and unrelated generated changes were discarded.
- Secret scanning found no secrets in changed files.
- Final automated validation reported no findings, but its code review binary was unavailable and CodeQL skipped Java analysis because the database was too large.

## Environment blockers

- The latest-dependency OSHI matrix could not configure because it requires a Java 27 toolchain. Java 27 was not installed, and the Foojay resolver could not reach `api.foojay.io`.
- `mise exec -- rumdl check --fix --config .github/config/.rumdl.toml instrumentation/oshi-5.0/README.md instrumentation/oshi-5.0/library/README.md CHANGELOG.md` could not run because `mise` was absent. An attempted installation from `mise.run` was blocked by DNS resolution.
