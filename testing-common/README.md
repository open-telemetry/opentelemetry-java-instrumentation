# Javaagent instrumentation testing

`io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing` provides this
repository's instrumentation test infrastructure. It supports reusable HTTP suites,
instrumentation-scope-version checks, telemetry metadata collection, and running shared suites
against both javaagent and library implementations. Library mode serves these shared repository
tests; this artifact is not a general-purpose alternative to SDK testing.

External Javaagent instrumentation and extension authors can also use it. Agent tests require
the testing agent and testing dependencies from matching releases, plus JVM agent setup; adding
the test-support dependency alone does not attach an agent. See the
[testing guide](../docs/contributing/javaagent-test-infra.md) and
[distro example](../examples/distro).

For ordinary tests needing an SDK, in-memory telemetry, and assertions, use
`io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension` from
`io.opentelemetry:opentelemetry-sdk-testing` instead.
