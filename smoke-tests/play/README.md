# Play framework smoke test
Play application used by smoke tests of OpenTelemetry java agent.
Builds and publishes Docker image containing a trivial Play application.

This is a separate gradle project, independent from the rest. This was done to allow
to build and publish it only when actually needed and not on every project build. 