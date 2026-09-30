# Native-test CI optimization advisory

## Candidate summary

- Pull-request native-test jobs opt into `-PnativeTestQuickBuild=true`; daily and local builds keep
  the default fully optimized mode.
- The shared Spring convention and the Logback library apply `quickBuild` only to the native test
  binary. Main native binaries are unchanged.
- Native-test jobs now use the pinned `gradle/actions/setup-gradle` action and a 4 GB Gradle daemon
  heap. Pull requests restore the cache read-only, while trusted daily runs may populate it.

## Validation

| Command or check | Result |
| --- | --- |
| `./gradlew -p conventions spotlessApply` | Passed in 22 seconds using Temurin Java 25. |
| `./gradlew :instrumentation:logback:logback-appender-1.0:library:spotlessApply` | Passed in 3 minutes 51 seconds using Temurin Java 25. |
| `./gradlew nativeTest --no-configuration-cache --dry-run -I /tmp/verify-native-binaries.gradle` | Passed. All four Spring projects and Logback resolved both `main` and `test` with `quickBuild=false`. JVM prerequisite and native-test task wiring remained present. |
| `./gradlew nativeTest -PnativeTestQuickBuild=true --no-configuration-cache --dry-run -I /tmp/verify-native-binaries.gradle` | Passed. All four Spring projects and Logback resolved `test` with `quickBuild=true`; every `main` binary remained `false`. |
| `mise run lint` | Blocked: `mise` was not installed. Pinned Flint 0.22.14 and rumdl 0.2.77 were installed under `/tmp` and `flint run` was retried. All available checks except Lychee passed; Lychee requires the unavailable `GITHUB_TOKEN`. |
| Secret scan of all four implementation files | Passed; no secrets detected. |
| CodeQL Actions scan | Passed; zero alerts. |
| Automated code review | Unavailable because the configured review model was absent from the environment. |

The requested executable check,
`./gradlew :instrumentation:logback:logback-appender-1.0:library:nativeTest -PnativeTestQuickBuild=true --no-configuration-cache`,
was not run because no `native-image` executable or GraalVM JDK was installed. Consequently, local
validation did not directly observe `-Ob`; standalone PR CI must confirm the compiler argument and
perform the Java 22, 23, and 24 native executions. No speedup is claimed without that measurement.

## Existing performance baseline

Previous successful pull-request run:
https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/runs/36669329518

| GraalVM | Complete CI job duration | Running test step duration | Gradle reported duration | Sum of four native-image generation durations |
| --- | --- | --- | --- | --- |
| 22 | 48m 12s | 47m 54s | 47m 38s | 32m 45s |
| 23 | 44m 42s | 44m 23s | 44m 05s | 29m 28s |
| 24 | 45m 06s | 44m 48s | 44m 31s | 29m 35s |

Same-day default-branch daily latest-dependency run:
https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/runs/36667496733

Its Java 22, 23, and 24 jobs took 47m 12s, 47m 05s, and 47m 27s respectively.
